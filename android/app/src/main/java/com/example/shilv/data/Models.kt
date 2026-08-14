package com.example.shilv.data

import kotlinx.serialization.Serializable
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 经纬度坐标。距离使用球面 haversine 近似，与 iOS CLLocation.distance 一致量级。 */
@Serializable
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
) {
    fun distance(other: GeoPoint): Double {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLon = Math.toRadians(other.longitude - longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(latitude)) * cos(Math.toRadians(other.latitude)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }
}

fun List<GeoPoint>.centroid(): GeoPoint? {
    if (isEmpty()) return null
    return GeoPoint(
        latitude = sumOf { it.latitude } / size,
        longitude = sumOf { it.longitude } / size,
    )
}

@Serializable
data class PhotoRecord(
    val id: String,
    val creationDate: Long, // epoch millis
    val location: GeoPoint? = null,
    val pixelWidth: Int = 0,
    val pixelHeight: Int = 0,
    val isFavorite: Boolean = false,
    val isScreenshot: Boolean = false,
)

@Serializable
data class MemoryEvent(
    val id: String,
    var title: String,
    var startDate: Long,
    var endDate: Long,
    var photoIDs: List<String>,
    var location: GeoPoint? = null,
    var placeName: String? = null,
    var cityName: String? = null,
    var countryName: String? = null,
    var note: String,
    var isHidden: Boolean = false,
    var summary: String? = null,
    var featuredPhotoIDs: List<String>? = null,
    var coverPhotoIDOverride: String? = null,
    var excludedPhotoIDs: List<String>? = null,
    var isUserCreated: Boolean? = null,
) {
    val photoCount: Int get() = photoIDs.size
    val duration: Long get() = endDate - startDate
    val coverPhotoID: String? get() = coverPhotoIDOverride ?: featuredPhotoIDs?.firstOrNull() ?: photoIDs.firstOrNull()
    val visiblePhotoIDs: List<String>
        get() {
            val preferred = featuredPhotoIDs?.filter { photoIDs.contains(it) }.orEmpty()
            return if (preferred.isEmpty()) photoIDs.take(5) else preferred
        }
}

@Serializable
data class TravelDay(
    val id: String,
    val date: Long, // start of day epoch millis
    var title: String,
    var events: List<MemoryEvent> = emptyList(),
) {
    val visibleEvents: List<MemoryEvent>
        get() = events.filter { !it.isHidden }.sortedBy { it.startDate }
    val photoCount: Int get() = visibleEvents.sumOf { it.photoCount }
    val coverPhotoID: String? get() = visibleEvents.firstOrNull()?.coverPhotoID
}

@Serializable
data class DiscoveredTrip(
    val id: String,
    var title: String,
    var startDate: Long,
    var endDate: Long,
    var photoIDs: List<String>,
    var days: List<TravelDay>,
    val center: GeoPoint? = null,
    val distanceFromHomeMeters: Double = 0.0,
    var isConfirmed: Boolean = false,
    var isHidden: Boolean = false,
    val detectedAt: Long,
    var summary: String? = null,
    var isFavorite: Boolean? = null,
    var coverPhotoIDOverride: String? = null,
    var suppressedEventIDs: List<String>? = null,
) {
    val favorite: Boolean get() = isFavorite ?: false

    val visibleEvents: List<MemoryEvent>
        get() = days.flatMap { it.events }.filter { !it.isHidden }.sortedBy { it.startDate }

    val photoCount: Int
        get() = visibleEvents.flatMap { it.photoIDs }.distinct().count()

    val dayCount: Int
        get() {
            val start = TripDates.startOfDay(startDate)
            val end = TripDates.startOfDay(endDate)
            val days = ((end - start) / MILLIS_PER_DAY).toInt() + 1
            return maxOf(1, days)
        }

    val eventCount: Int get() = days.sumOf { d -> d.events.count { !it.isHidden } }

    val visiblePhotoIDs: List<String>
        get() = visibleEvents.flatMap { it.photoIDs }.distinct()

    val coverPhotoID: String?
        get() {
            if (coverPhotoIDOverride != null && visiblePhotoIDs.contains(coverPhotoIDOverride)) return coverPhotoIDOverride
            return days.firstNotNullOfOrNull { it.coverPhotoID }
        }

    val locatedEventCount: Int get() = visibleEvents.count { it.location != null }

    val placeCount: Int
        get() {
            val named = visibleEvents.mapNotNull { it.placeName }.toSet()
            return if (named.isEmpty()) locatedEventCount else named.size
        }

    val cityCount: Int get() = visibleEvents.mapNotNull { it.cityName }.toSet().size

    val routeDistanceMeters: Double get() {
        val points = days.flatMap { it.events }.sortedBy { it.startDate }.mapNotNull { it.location }
        var total = 0.0
        for (i in 0 until points.size - 1) total += points[i].distance(points[i + 1])
        return total
    }

    val latestPhotoTime: Long get() = endDate
    val busiestDay: TravelDay? get() = days.maxByOrNull { it.photoCount }
    val mostPhotographedEvent: MemoryEvent? get() = visibleEvents.maxByOrNull { it.photoCount }

    fun reconcileDerivedFields() {
        days = days.filter { it.events.isNotEmpty() }
        days = days.sortedBy { it.date }
        val allEvents = days.flatMap { it.events }.sortedBy { it.startDate }
        if (allEvents.isEmpty()) {
            photoIDs = emptyList()
            return
        }
        val rangeEvents = if (visibleEvents.isEmpty()) allEvents else visibleEvents
        startDate = rangeEvents.minOf { it.startDate }
        endDate = rangeEvents.maxOf { it.endDate }
        photoIDs = allEvents.flatMap { it.photoIDs }.distinct()
        if (coverPhotoIDOverride != null && !visiblePhotoIDs.contains(coverPhotoIDOverride)) {
            coverPhotoIDOverride = null
        }
    }

    companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}

@Serializable
data class ScanSnapshot(
    val version: Int,
    val scannedAt: Long,
    val accessiblePhotoCount: Int,
    val locatedPhotoCount: Int,
    val inferredHome: GeoPoint? = null,
    var trips: List<DiscoveredTrip> = emptyList(),
)

enum class PhotoAccessState { NotDetermined, Full, Limited, Denied, Restricted }

sealed class ScanPhase {
    object Idle : ScanPhase()
    object RequestingPermission : ScanPhase()
    data class ReadingMetadata(val current: Int, val total: Int) : ScanPhase()
    object DetectingTrips : ScanPhase()
    object Saving : ScanPhase()
    object Complete : ScanPhase()
    data class Failed(val message: String) : ScanPhase()

    val label: String
        get() = when (this) {
            Idle -> "准备扫描"
            RequestingPermission -> "等待照片授权"
            is ReadingMetadata -> "正在读取照片信息 · $current/$total"
            DetectingTrips -> "正在还原旅行"
            Saving -> "正在保存到本机"
            Complete -> "扫描完成"
            is Failed -> message
        }

    val progress: Float?
        get() = (this as? ReadingMetadata)?.let { if (it.total > 0) it.current.toFloat() / it.total else null }
}

/** 日期工具：基于 java.time（API 26+ 原生支持），用于与 iOS Calendar 对齐的取整与时段判定。 */
object TripDates {
    val zone: java.time.ZoneId = java.time.ZoneId.systemDefault()

    fun startOfDay(millis: Long): Long =
        java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()

    fun hourOf(millis: Long): Int =
        java.time.Instant.ofEpochMilli(millis).atZone(zone).hour

    fun yearOf(millis: Long): Int =
        java.time.Instant.ofEpochMilli(millis).atZone(zone).year

    fun monthOf(millis: Long): Int =
        java.time.Instant.ofEpochMilli(millis).atZone(zone).monthValue

    fun monthDay(millis: Long): Pair<Int, Int> {
        val d = java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        return Pair(d.monthValue, d.dayOfMonth)
    }
}