package com.example.shilv.service

import com.example.shilv.data.DiscoveredTrip
import com.example.shilv.data.MemoryEvent
import com.example.shilv.data.ScanSnapshot
import com.example.shilv.data.TripDates
import com.example.shilv.data.TravelDay
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 旅行索引存储：把 ScanSnapshot 写入本机文件，并保留用户编辑（命名/确认/笔记/精选等）。
 * 逻辑与 iOS TripStore 对齐：重扫时通过 ID 或照片引用匹配旧旅行，合并用户修改。
 */
class TripStore(
    private val storageDirectory: File,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val file: File = File(storageDirectory, "travel-index-v1.json")

    var snapshot: ScanSnapshot?
        private set

    init {
        storageDirectory.mkdirs()
        load()
    }

    val storedDataSize: Long get() = if (file.exists()) file.length() else 0L

    fun replace(newSnapshot: ScanSnapshot): Result<Unit> = runCatching {
        val merged = mergeUserEdits(old = snapshot, new = newSnapshot)
        persist(merged)
    }

    fun updateTrip(trip: DiscoveredTrip): Result<Unit> = runCatching {
        val value = snapshot ?: return@runCatching
        val reconciled = trip.also { it.reconcileDerivedFields() }
        val index = value.trips.indexOfFirst { it.id == trip.id }
        if (index >= 0) {
            value.trips = value.trips.toMutableList().also { it[index] = reconciled }
        }
        persist(value)
    }

    fun deleteLocalIndex(): Result<Unit> = runCatching {
        if (file.exists()) file.delete()
        snapshot = null
    }

    private fun load() {
        if (!file.exists()) return
        snapshot = runCatching {
            json.decodeFromString<ScanSnapshot>(file.readText())
        }.getOrNull()
    }

    private fun persist(value: ScanSnapshot) {
        file.writeText(json.encodeToString(ScanSnapshot.serializer(), value))
        snapshot = value
    }

    private fun mergeUserEdits(old: ScanSnapshot?, new: ScanSnapshot): ScanSnapshot {
        if (old == null) return new
        val previousTrips = old.trips.associateBy { it.id }
        val merged = new.copy()

        merged.trips = new.trips.map { fresh ->
            val previous = previousTrips[fresh.id]
                ?: old.trips.filter { matchScore(it, fresh) >= 0.5 }.maxByOrNull { matchScore(it, fresh) }
            if (previous == null) return@map fresh
            mergeTrip(previous, fresh)
        }

        val matchedOldIDs = merged.trips.mapNotNull { fresh ->
            old.trips.filter { matchScore(it, fresh) >= 0.5 }.maxByOrNull { matchScore(it, fresh) }?.id
        }.toSet()
        val preservedMemories = old.trips.filter { it.isConfirmed && !matchedOldIDs.contains(it.id) }
        merged.trips = (merged.trips + preservedMemories).sortedByDescending { it.startDate }
        return merged
    }

    private fun mergeTrip(previous: DiscoveredTrip, fresh: DiscoveredTrip): DiscoveredTrip {
        val result = fresh.copy()
        result.title = previous.title
        result.isConfirmed = previous.isConfirmed
        result.isHidden = previous.isHidden
        result.summary = previous.summary
        result.isFavorite = previous.isFavorite
        result.coverPhotoIDOverride = previous.coverPhotoIDOverride
        result.suppressedEventIDs = previous.suppressedEventIDs

        val oldEvents = previous.days.flatMap { it.events }.associateBy { it.id }
        result.days = fresh.days.map { day ->
            var updated = day.copy()
            val oldDay = previous.days.minByOrNull { kotlin.math.abs(it.date - day.date) }
            if (oldDay != null && kotlin.math.abs(oldDay.date - day.date) < 12 * 60 * 60 * 1000L) {
                updated = updated.copy(title = oldDay.title)
            }
            updated = updated.copy(events = day.events.map { event ->
                val oldEvent = oldEvents[event.id]
                    ?: previous.days.flatMap { it.events }.filter { kotlin.math.abs(it.startDate - event.startDate) <= 30 * 60 * 1000L }
                        .minByOrNull { kotlin.math.abs(it.startDate - event.startDate) }
                if (oldEvent == null) return@map event
                mergeEvent(oldEvent, event)
            })
            val customEvents = previous.days.flatMap { it.events }
                .filter { it.isUserCreated == true && TripDates.startOfDay(it.startDate) == TripDates.startOfDay(day.date) }
            val existingIDs = updated.events.map { it.id }.toSet()
            updated = updated.copy(events = updated.events + customEvents.filter { it.id !in existingIDs })
            removeSuppressed(updated, previous.suppressedEventIDs)
        }

        val existingEventIDs = result.days.flatMap { it.events }.map { it.id }.toSet()
        val unmatchedCustomEvents = previous.days.flatMap { it.events }
            .filter { it.isUserCreated == true && it.id !in existingEventIDs }
        for (custom in unmatchedCustomEvents) {
            val dayIndex = result.days.indexOfFirst { TripDates.startOfDay(it.date) == TripDates.startOfDay(custom.startDate) }
            if (dayIndex >= 0) {
                result.days = result.days.toMutableList().also {
                    it[dayIndex] = it[dayIndex].copy(events = it[dayIndex].events + custom)
                }
            } else {
                result.days = result.days + TravelDay(
                    id = custom.id + "-day", date = TripDates.startOfDay(custom.startDate),
                    title = "补充的一天", events = listOf(custom),
                )
            }
        }
        result.days = result.days.map { removeSuppressed(it, previous.suppressedEventIDs) }
        result.days = normalizeEventDays(result.days)
        result.reconcileDerivedFields()
        return result
    }

    private fun mergeEvent(old: MemoryEvent, fresh: MemoryEvent): MemoryEvent {
        val value = fresh.copy()
        value.title = old.title
        value.placeName = old.placeName
        value.cityName = old.cityName
        value.countryName = old.countryName
        value.note = old.note
        value.isHidden = old.isHidden
        value.startDate = old.startDate
        value.endDate = old.endDate
        value.location = old.location
        value.summary = old.summary
        value.featuredPhotoIDs = old.featuredPhotoIDs
        value.coverPhotoIDOverride = old.coverPhotoIDOverride
        value.excludedPhotoIDs = old.excludedPhotoIDs
        value.isUserCreated = old.isUserCreated
        val existing = value.photoIDs
        value.photoIDs = value.photoIDs + old.photoIDs.filter { it !in existing }
        old.excludedPhotoIDs?.let { excluded ->
            value.photoIDs = value.photoIDs.filter { it !in excluded }
            value.featuredPhotoIDs = value.featuredPhotoIDs?.filter { it !in excluded }
        }
        return value
    }

    private fun removeSuppressed(day: TravelDay, suppressed: List<String>?): TravelDay =
        if (suppressed == null) day else day.copy(events = day.events.filter { it.id !in suppressed })

    private fun normalizeEventDays(days: List<TravelDay>): List<TravelDay> {
        val grouped = days.flatMap { it.events }.groupBy { TripDates.startOfDay(it.startDate) }
        return grouped.keys.sorted().map { date ->
            val existing = days.firstOrNull { TripDates.startOfDay(it.date) == date }
            TravelDay(
                id = existing?.id ?: "day-$date",
                date = date,
                title = existing?.title ?: "补充的一天",
                events = grouped[date].orEmpty().sortedBy { it.startDate },
            )
        }
    }

    private fun overlapRatio(a: DiscoveredTrip, b: DiscoveredTrip): Double {
        val intersectionStart = maxOf(a.startDate, b.startDate)
        val intersectionEnd = minOf(a.endDate, b.endDate)
        if (intersectionEnd <= intersectionStart) return 0.0
        val intersection = intersectionEnd - intersectionStart
        val smaller = minOf(a.endDate - a.startDate, b.endDate - b.startDate)
        return if (smaller > 0) intersection.toDouble() / smaller else 0.0
    }

    private fun photoOverlapRatio(a: DiscoveredTrip, b: DiscoveredTrip): Double {
        val left = a.photoIDs.toSet()
        val right = b.photoIDs.toSet()
        if (left.isEmpty() || right.isEmpty()) return 0.0
        return left.intersect(right).size.toDouble() / minOf(left.size, right.size)
    }

    private fun matchScore(a: DiscoveredTrip, b: DiscoveredTrip): Double {
        if (a.id == b.id) return 1.0
        return maxOf(overlapRatio(a, b), photoOverlapRatio(a, b))
    }
}