package com.example.shilv.domain

import com.example.shilv.data.DiscoveredTrip
import com.example.shilv.data.GeoPoint
import com.example.shilv.data.MemoryEvent
import com.example.shilv.data.PhotoRecord
import com.example.shilv.data.ScanSnapshot
import com.example.shilv.data.TravelDay
import com.example.shilv.data.TripDates
import com.example.shilv.service.TripStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TripDetectorTest {

    private val base = 1_700_000_000_000L

    @Test
    fun detectsMultiDayTripFarFromHomeAndIncludesPhotosWithoutGps() {
        val home = GeoPoint(31.2304, 121.4737)
        val kyoto = GeoPoint(35.0116, 135.7681)
        val photos = mutableListOf<PhotoRecord>()
        for (day in 0 until 30) photos.add(record("home-$day", base + day * 86_400_000L, home))
        for (index in 0 until 24) {
            photos.add(record("trip-$index", base + 40 * 86_400_000L + index * 7_200_000L, if (index == 7) null else kyoto))
        }
        val snapshot = TripDetector().detect(photos)
        assertEquals(1, snapshot.trips.size)
        assertEquals(24, snapshot.trips[0].photoCount)
        assertTrue(snapshot.trips[0].dayCount >= 2)
        assertTrue(snapshot.trips[0].photoIDs.contains("trip-7"))
    }

    @Test
    fun doesNotTreatLocalWeekendAsTravel() {
        val shanghai = GeoPoint(31.2304, 121.4737)
        val near = GeoPoint(31.3000, 121.6000)
        val photos = mutableListOf<PhotoRecord>()
        for (day in 0 until 30) photos.add(record("h-$day", base + day * 86_400_000L, shanghai))
        for (index in 0 until 40) photos.add(record("w-$index", base + 40 * 86_400_000L + index * 1_800_000L, near))
        assertTrue(TripDetector().detect(photos).trips.isEmpty())
    }

    @Test
    fun homePhotoSeparatesTwoNearbyTrips() {
        val homePoint = GeoPoint(31.2304, 121.4737)
        val awayPoint = GeoPoint(35.0116, 135.7681)
        val photos = mutableListOf<PhotoRecord>()
        for (day in 0 until 30) photos.add(record("home-history-$day", base + day * 86_400_000L, homePoint))
        for (index in 0 until 15) photos.add(record("trip-a-$index", base + 40 * 86_400_000L + index * 7_200_000L, awayPoint))
        photos.add(record("returned-home", base + 42 * 86_400_000L, homePoint))
        for (index in 0 until 15) photos.add(record("trip-b-$index", base + 43 * 86_400_000L + index * 7_200_000L, awayPoint))
        assertEquals(2, TripDetector().detect(photos).trips.size)
    }

    @Test
    fun buildsMultipleEventsFromSpatialGap() {
        val home = GeoPoint(31.2304, 121.4737)
        val osaka = GeoPoint(34.6937, 135.5023)
        val kyoto = GeoPoint(35.0116, 135.7681)
        val photos = mutableListOf<PhotoRecord>()
        for (day in 0 until 30) photos.add(record("h-$day", base + day * 86_400_000L, home))
        for (index in 0 until 10) photos.add(record("o-$index", base + 40 * 86_400_000L + index * 600_000L, osaka))
        for (index in 0 until 10) photos.add(record("k-$index", base + 40 * 86_400_000L + 8_000_000L + index * 600_000L, kyoto))
        val trip = TripDetector().detect(photos).trips.first()
        assertTrue(trip.eventCount >= 2)
    }

    @Test
    fun screenshotsDoNotCreateATrip() {
        val homePoint = GeoPoint(31.2304, 121.4737)
        val awayPoint = GeoPoint(35.0116, 135.7681)
        val photos = mutableListOf<PhotoRecord>()
        for (day in 0 until 30) photos.add(record("home-$day", base + day * 86_400_000L, homePoint))
        for (index in 0 until 40) {
            photos.add(PhotoRecord(id = "s-$index", creationDate = base + 40 * 86_400_000L + index * 3_600_000L, location = awayPoint, isScreenshot = true))
        }
        assertTrue(TripDetector().detect(photos).trips.isEmpty())
    }

    @Test
    fun representativeSelectionUsesAtMostFivePhotos() {
        val homePoint = GeoPoint(31.2304, 121.4737)
        val awayPoint = GeoPoint(35.0116, 135.7681)
        val photos = mutableListOf<PhotoRecord>()
        for (day in 0 until 30) photos.add(record("home-$day", base + day * 86_400_000L, homePoint))
        for (index in 0 until 30) photos.add(record("away-$index", base + 40 * 86_400_000L + index * 1_800_000L, awayPoint))
        val event = TripDetector().detect(photos).trips.first().days.first().events.first()
        assertTrue(event.visiblePhotoIDs.size <= 5)
    }

    @Test
    fun storePreservesUserEditsWhenRescanBoundaryMoves() {
        val dir = File.createTempFile("shilv", ".tmp").parentFile
        val storeDir = File(dir, "store-preserves-${System.nanoTime()}")
        val store = TripStore(storeDir)
        val eventID = java.util.UUID.randomUUID().toString()
        var original = makeTrip(id = java.util.UUID.randomUUID().toString(), start = base, end = base + 3 * 86_400_000L, eventID = eventID, title = "7月旅行", note = "")
        store.replace(ScanSnapshot(1, base, 40, 35, null, listOf(original)))
        original = original.copy(title = "京都之旅", isConfirmed = true)
        original.days = original.days.map { it.copy(title = "雨中的京都", events = it.events.map { e -> e.copy(note = "那天下了一场很轻的雨", placeName = "清水寺", featuredPhotoIDs = listOf("b"), excludedPhotoIDs = listOf("a")) }) }
        original.isFavorite = true
        store.updateTrip(original)

        val shifted = makeTrip(id = java.util.UUID.randomUUID().toString(), start = base + 30 * 60_000L, end = base + 3 * 86_400_000L + 30 * 60_000L, eventID = java.util.UUID.randomUUID().toString(), title = "7月旅行", note = "")
        store.replace(ScanSnapshot(1, base + 100, 41, 36, null, listOf(shifted)))

        val saved = store.snapshot!!.trips.first()
        assertEquals("京都之旅", saved.title)
        assertTrue(saved.isConfirmed)
        assertEquals("雨中的京都", saved.days[0].title)
        assertEquals("那天下了一场很轻的雨", saved.days[0].events[0].note)
        assertEquals("清水寺", saved.days[0].events[0].placeName)
        assertTrue(saved.favorite)
        storeDir.deleteRecursively()
    }

    @Test
    fun storeKeepsMergedEventSuppressedAcrossRescan() {
        val dir = File.createTempFile("shilv", ".tmp").parentFile
        val storeDir = File(dir, "store-suppressed-${System.nanoTime()}")
        val store = TripStore(storeDir)
        val eventID = java.util.UUID.randomUUID().toString()
        var trip = makeTrip(id = java.util.UUID.randomUUID().toString(), start = base, end = base + 2 * 86_400_000L, eventID = eventID, title = "旅行", note = "")
        val removedID = java.util.UUID.randomUUID().toString()
        val removedEvent = MemoryEvent(
            id = removedID, title = "下一站", startDate = base + 5_000, endDate = base + 6_000,
            photoIDs = listOf("c"), note = "", isHidden = false,
        )
        trip.days = trip.days.map { it.copy(events = it.events + removedEvent) }
        store.replace(ScanSnapshot(1, base, 3, 3, null, listOf(trip)))
        trip = trip.copy(days = trip.days.map { it.copy(events = it.events.filterNot { e -> e.id == removedID }) }, suppressedEventIDs = listOf(removedID))
        store.updateTrip(trip)
        var fresh = trip
        fresh.days = fresh.days.map { it.copy(events = it.events + removedEvent) }
        store.replace(ScanSnapshot(1, base + 1, 3, 3, null, listOf(fresh)))
        assertFalse(store.snapshot!!.trips.first().visibleEvents.any { it.id == removedID })
        storeDir.deleteRecursively()
    }

    @Test
    fun tripStatisticsUseRouteAndDistinctNames() {
        val osaka = GeoPoint(34.6937, 135.5023)
        val kyoto = GeoPoint(35.0116, 135.7681)
        val first = MemoryEvent(
            id = "e1", title = "大阪", startDate = base, endDate = base + 1_000,
            photoIDs = listOf("a", "b"), location = osaka, placeName = "大阪城", cityName = "大阪市", countryName = "日本", note = "", isHidden = false,
        )
        val second = MemoryEvent(
            id = "e2", title = "京都", startDate = base + 2_000, endDate = base + 3_000,
            photoIDs = listOf("c"), location = kyoto, placeName = "清水寺", cityName = "京都市", countryName = "日本", note = "", isHidden = false,
        )
        val day = TravelDay("d1", TripDates.startOfDay(base), "第一天", listOf(first, second))
        val trip = DiscoveredTrip(
            id = "t1", title = "关西", startDate = base, endDate = base + 3_000, photoIDs = listOf("a", "b", "c"),
            days = listOf(day), center = null, distanceFromHomeMeters = 800_000.0, isConfirmed = true, isHidden = false, detectedAt = base,
        )
        assertEquals(2, trip.placeCount)
        assertEquals(2, trip.cityCount)
        assertTrue(trip.routeDistanceMeters > 30_000)
        assertEquals("e1", trip.mostPhotographedEvent?.id)
    }

    @Test
    fun reconcileUpdatesTripRangeAndPhotoReferences() {
        val visible = MemoryEvent(
            id = "ve", title = "新的事件", startDate = base + 86_400_000L, endDate = base + 90_000,
            photoIDs = listOf("new-a", "new-b"), note = "", isHidden = false,
        )
        val hidden = MemoryEvent(
            id = "he", title = "隐藏事件", startDate = base, endDate = base + 1_000,
            photoIDs = listOf("old"), note = "", isHidden = false,
        )
        val day = TravelDay("d2", TripDates.startOfDay(visible.startDate), "第二天", listOf(hidden, visible))
        var trip = DiscoveredTrip(
            id = "t", title = "旅行", startDate = base, endDate = base + 1_000, photoIDs = listOf("old"),
            days = listOf(day), center = null, distanceFromHomeMeters = 100_000.0, isConfirmed = true, isHidden = false, detectedAt = base, coverPhotoIDOverride = "old",
        )
        trip.reconcileDerivedFields()
        assertEquals(visible.startDate, trip.startDate)
        assertEquals(visible.endDate, trip.endDate)
        assertEquals(listOf("old", "new-a", "new-b"), trip.photoIDs)
        assertEquals(2, trip.photoCount)
        assertNull(trip.coverPhotoIDOverride)
    }

    @Test
    fun hiddenOnlyEventKeepsMatchingReferencesButNotVisibleCount() {
        val hidden = MemoryEvent(
            id = "he", title = "隐藏", startDate = base, endDate = base + 1_000,
            photoIDs = listOf("a", "b"), note = "", isHidden = true,
        )
        val day = TravelDay("d1", TripDates.startOfDay(base), "第一天", listOf(hidden))
        var trip = DiscoveredTrip(
            id = "t", title = "旅行", startDate = base, endDate = base + 1_000, photoIDs = listOf("a", "b"),
            days = listOf(day), center = null, distanceFromHomeMeters = 100_000.0, isConfirmed = true, isHidden = false, detectedAt = base,
        )
        trip.reconcileDerivedFields()
        assertEquals(listOf("a", "b"), trip.photoIDs)
        assertEquals(0, trip.photoCount)
        assertNull(trip.coverPhotoID)
    }

    private fun record(id: String, date: Long, point: GeoPoint?): PhotoRecord =
        PhotoRecord(id = id, creationDate = date, location = point, pixelWidth = 4032, pixelHeight = 3024)

    private fun makeTrip(id: String, start: Long, end: Long, eventID: String, title: String, note: String): DiscoveredTrip {
        val event = MemoryEvent(
            id = eventID, title = "下午漫步", startDate = start + 2_000, endDate = start + 4_000,
            photoIDs = listOf("a", "b"), note = note, isHidden = false,
        )
        val day = TravelDay("day-$id", TripDates.startOfDay(start), "第一天", listOf(event))
        return DiscoveredTrip(
            id = id, title = title, startDate = start, endDate = end, photoIDs = listOf("a", "b"),
            days = listOf(day), center = null, distanceFromHomeMeters = 100_000.0, isConfirmed = false, isHidden = false, detectedAt = start,
        )
    }
}