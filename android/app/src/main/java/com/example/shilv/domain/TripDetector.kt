package com.example.shilv.domain

import com.example.shilv.data.DiscoveredTrip
import com.example.shilv.data.GeoPoint
import com.example.shilv.data.MemoryEvent
import com.example.shilv.data.PhotoRecord
import com.example.shilv.data.ScanSnapshot
import com.example.shilv.data.TravelDay
import com.example.shilv.data.TripDates
import com.example.shilv.data.centroid
import java.util.UUID

/**
 * 旅行检测器：从照片元数据中还原“一次旅行”。
 * 逻辑与 iOS TripDetector 完全对齐：
 *  - 推断常驻区域（home）
 *  - 将远离 home 且时间连续的带定位照片切分为候选组
 *  - 补入无定位照片、按天/按事件聚类
 */
class TripDetector(
    private val minimumDistanceFromHome: Double = 80_000.0,
    private val maximumGapWithinTrip: Long = 36 * 60 * 60 * 1000L,
    private val minimumLocatedPhotos: Int = 5,
    private val minimumTotalPhotos: Int = 12,
    private val eventTimeGap: Long = 2 * 60 * 60 * 1000L,
    private val eventDistanceGap: Double = 3_000.0,
) {
    fun detect(photos: List<PhotoRecord>, now: Long = System.currentTimeMillis()): ScanSnapshot {
        val sorted = photos.sortedBy { it.creationDate }
        val eligible = sorted.filter { !it.isScreenshot }
        val located = eligible.filter { it.location != null }
        val home = inferHome(located)
        if (home == null) {
            return ScanSnapshot(
                version = 1, scannedAt = now, accessiblePhotoCount = sorted.size,
                locatedPhotoCount = located.size, inferredHome = null, trips = emptyList(),
            )
        }

        val locatedGroups = splitAwayAnchors(located, home)
        val trips = mutableListOf<DiscoveredTrip>()

        for (group in locatedGroups) {
            if (group.size < minimumLocatedPhotos) continue
            val first = group.first()
            val last = group.last()
            val paddedStart = first.creationDate - 6 * 60 * 60 * 1000L
            val paddedEnd = last.creationDate + 6 * 60 * 60 * 1000L
            val allTripPhotos = eligible.filter { it.creationDate in paddedStart..paddedEnd }
            if (allTripPhotos.size < minimumTotalPhotos) continue
            val uniqueDays = allTripPhotos.map { TripDates.startOfDay(it.creationDate) }.toSet()
            if (uniqueDays.size < 2 && group.size < 30) continue

            val points = group.mapNotNull { it.location }
            val center = points.centroid()
            val distance = center?.distance(home) ?: minimumDistanceFromHome
            val days = buildDays(allTripPhotos)
            trips.add(
                DiscoveredTrip(
                    id = stableTripID(first.creationDate, last.creationDate),
                    title = defaultTripTitle(first.creationDate),
                    startDate = first.creationDate,
                    endDate = last.creationDate,
                    photoIDs = allTripPhotos.map { it.id },
                    days = days,
                    center = center,
                    distanceFromHomeMeters = distance,
                    isConfirmed = false,
                    isHidden = false,
                    detectedAt = now,
                ),
            )
        }

        return ScanSnapshot(
            version = 1, scannedAt = now, accessiblePhotoCount = sorted.size,
            locatedPhotoCount = located.size, inferredHome = home,
            trips = mergeOverlapping(trips).sortedByDescending { it.startDate },
        )
    }

    fun inferHome(photos: List<PhotoRecord>): GeoPoint? {
        val located = photos.mapNotNull { p -> p.location?.let { it to p.creationDate } }
        if (located.size < minimumLocatedPhotos) return null
        data class Cell(val lat: Int, val lon: Int)
        val buckets = mutableMapOf<Cell, MutableList<Pair<GeoPoint, Long>>>()
        for ((point, date) in located) {
            val cell = Cell((point.latitude * 5).toInt(), (point.longitude * 5).toInt())
            buckets.getOrPut(cell) { mutableListOf() }.add(point to date)
        }
        val best = buckets.maxWithOrNull { lhs, rhs ->
            val lhsDays = lhs.value.map { TripDates.startOfDay(it.second) }.toSet().size
            val rhsDays = rhs.value.map { TripDates.startOfDay(it.second) }.toSet().size
            if (lhsDays == rhsDays) lhs.value.size.compareTo(rhs.value.size) else lhsDays.compareTo(rhsDays)
        }?.value
        return best?.map { it.first }?.centroid()
    }

    private fun splitAwayAnchors(locatedPhotos: List<PhotoRecord>, home: GeoPoint): List<List<PhotoRecord>> {
        val groups = mutableListOf<List<PhotoRecord>>()
        var current = mutableListOf<PhotoRecord>()
        val sparseTravelGap = maxOf(maximumGapWithinTrip, 72 * 60 * 60 * 1000L)
        for (photo in locatedPhotos) {
            val point = photo.location ?: continue
            val isAway = point.distance(home) >= minimumDistanceFromHome
            if (!isAway) {
                if (current.isNotEmpty()) { groups.add(current); current = mutableListOf() }
                continue
            }
            val previous = current.lastOrNull()
            if (previous != null && photo.creationDate - previous.creationDate > sparseTravelGap) {
                groups.add(current); current = mutableListOf()
            }
            current.add(photo)
        }
        if (current.isNotEmpty()) groups.add(current)
        return groups
    }

    private fun buildDays(photos: List<PhotoRecord>): List<TravelDay> {
        val byDay = photos.groupBy { TripDates.startOfDay(it.creationDate) }
        return byDay.keys.sorted()
            .mapIndexed { index, date ->
                val dayPhotos = byDay[date]!!.sortedBy { it.creationDate }
                TravelDay(
                    id = stableDayID(date),
                    date = date,
                    title = "第 ${index + 1} 天",
                    events = buildEvents(dayPhotos),
                )
            }
    }

    private fun buildEvents(photos: List<PhotoRecord>): List<MemoryEvent> {
        val first = photos.firstOrNull() ?: return emptyList()
        val groups = mutableListOf<MutableList<PhotoRecord>>(mutableListOf(first))
        for (photo in photos.drop(1)) {
            val previous = groups.last().last()
            val timeGap = photo.creationDate - previous.creationDate
            val distance = previous.location?.let { a -> photo.location?.let { a.distance(it) } } ?: 0.0
            if (timeGap > eventTimeGap || distance > eventDistanceGap) {
                groups.add(mutableListOf(photo))
            } else {
                groups.last().add(photo)
            }
        }
        return groups.mapNotNull { group ->
            val gFirst = group.first()
            val gLast = group.last()
            val points = group.mapNotNull { it.location }
            val start = gFirst.creationDate
            MemoryEvent(
                id = stableEventID(start, gFirst.id),
                title = eventTitle(start),
                startDate = start,
                endDate = gLast.creationDate,
                photoIDs = group.map { it.id },
                location = points.centroid(),
                note = "",
                isHidden = false,
                featuredPhotoIDs = representativePhotoIDs(group),
            )
        }
    }

    private fun representativePhotoIDs(photos: List<PhotoRecord>): List<String> {
        val candidates = photos.filter { !it.isScreenshot }
        val source = if (candidates.isEmpty()) photos else candidates
        if (source.size <= 5) return source.map { it.id }
        var indices = listOf(0, source.size / 4, source.size / 2, source.size * 3 / 4, source.size - 1)
        val favoriteIndex = source.indexOfFirst { it.isFavorite }
        if (favoriteIndex >= 0) indices = indices.mapIndexed { i, v -> if (i == 2) favoriteIndex else v }
        return indices.map { source[it].id }.distinct()
    }

    private fun mergeOverlapping(trips: List<DiscoveredTrip>): List<DiscoveredTrip> {
        val sorted = trips.sortedBy { it.startDate }
        val current = sorted.firstOrNull() ?: return emptyList()
        val result = mutableListOf<DiscoveredTrip>()
        var cur = current
        for (next in sorted.drop(1)) {
            if (next.startDate <= cur.endDate + 12 * 60 * 60 * 1000L) {
                val photoIDs = (cur.photoIDs + next.photoIDs).distinct()
                val allDays = mergeDays(cur.days + next.days)
                cur = DiscoveredTrip(
                    id = cur.id, title = cur.title, startDate = cur.startDate,
                    endDate = maxOf(cur.endDate, next.endDate), photoIDs = photoIDs, days = allDays,
                    center = listOfNotNull(cur.center, next.center).centroid(),
                    distanceFromHomeMeters = maxOf(cur.distanceFromHomeMeters, next.distanceFromHomeMeters),
                    isConfirmed = cur.isConfirmed, isHidden = false, detectedAt = cur.detectedAt,
                    summary = cur.summary, isFavorite = cur.isFavorite,
                    coverPhotoIDOverride = cur.coverPhotoIDOverride,
                    suppressedEventIDs = cur.suppressedEventIDs,
                )
            } else {
                result.add(cur); cur = next
            }
        }
        result.add(cur)
        return result
    }

    private fun mergeDays(days: List<TravelDay>): List<TravelDay> {
        val grouped = days.groupBy { TripDates.startOfDay(it.date) }
        return grouped.keys.sorted().map { date ->
            val events = grouped[date].orEmpty().flatMap { it.events }.sortedBy { it.startDate }
            TravelDay(
                id = stableDayID(date),
                date = date,
                title = grouped[date]?.firstOrNull()?.title ?: "旅途中的一天",
                events = events,
            )
        }
    }

    private fun defaultTripTitle(start: Long): String = "${TripDates.monthOf(start)}月旅行"

    private fun eventTitle(date: Long): String = when (TripDates.hourOf(date)) {
        in 0 until 6 -> "夜里的片段"
        in 6 until 11 -> "上午的记忆"
        in 11 until 14 -> "午间停留"
        in 14 until 18 -> "下午的漫步"
        else -> "傍晚与夜晚"
    }

    private fun stableTripID(start: Long, end: Long): String = stableUUID("trip-$start-$end")
    private fun stableDayID(date: Long): String = stableUUID("day-$date")
    private fun stableEventID(date: Long, firstPhotoID: String): String = stableUUID("event-$date-$firstPhotoID")

    private fun stableUUID(value: String): String {
        val bytes = ByteArray(16)
        val utf8 = value.toByteArray(Charsets.UTF_8)
        for ((index, byte) in utf8.withIndex()) bytes[index % 16] = (bytes[index % 16] * 31 + byte).toByte()
        bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
        val msb = java.nio.ByteBuffer.wrap(bytes, 0, 8).long
        val lsb = java.nio.ByteBuffer.wrap(bytes, 8, 8).long
        return UUID(msb, lsb).toString()
    }
}