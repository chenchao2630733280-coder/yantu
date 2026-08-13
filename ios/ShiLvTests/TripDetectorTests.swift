import XCTest
@testable import ShiLv

final class TripDetectorTests: XCTestCase {
    private let calendar = Calendar(identifier: .gregorian)

    func testDetectsMultiDayTripFarFromHomeAndIncludesPhotosWithoutGPS() throws {
        let home = GeoPoint(latitude: 31.2304, longitude: 121.4737)
        let kyoto = GeoPoint(latitude: 35.0116, longitude: 135.7681)
        var photos: [PhotoRecord] = []
        let base = Date(timeIntervalSince1970: 1_700_000_000)

        for day in 0..<30 {
            photos.append(record("home-\(day)", base.addingTimeInterval(Double(day) * 86_400), home))
        }
        for index in 0..<24 {
            photos.append(record("trip-\(index)", base.addingTimeInterval(40 * 86_400 + Double(index) * 7_200), index == 7 ? nil : kyoto))
        }

        let snapshot = TripDetector().detect(from: photos)
        XCTAssertEqual(snapshot.trips.count, 1)
        XCTAssertEqual(snapshot.trips[0].photoCount, 24)
        XCTAssertGreaterThanOrEqual(snapshot.trips[0].dayCount, 2)
        XCTAssertTrue(snapshot.trips[0].photoIDs.contains("trip-7"))
    }

    func testDoesNotTreatLocalWeekendAsTravel() {
        let shanghai = GeoPoint(latitude: 31.2304, longitude: 121.4737)
        let near = GeoPoint(latitude: 31.3000, longitude: 121.6000)
        let base = Date(timeIntervalSince1970: 1_700_000_000)
        let home = (0..<30).map { record("h-\($0)", base.addingTimeInterval(Double($0) * 86_400), shanghai) }
        let weekend = (0..<40).map { record("w-\($0)", base.addingTimeInterval(40 * 86_400 + Double($0) * 1_800), near) }
        XCTAssertTrue(TripDetector().detect(from: home + weekend).trips.isEmpty)
    }

    func testHomePhotoSeparatesTwoNearbyTrips() {
        let homePoint = GeoPoint(latitude: 31.2304, longitude: 121.4737)
        let awayPoint = GeoPoint(latitude: 35.0116, longitude: 135.7681)
        let base = Date(timeIntervalSince1970: 1_700_000_000)
        var photos = (0..<30).map { record("home-history-\($0)", base.addingTimeInterval(Double($0) * 86_400), homePoint) }
        photos += (0..<15).map { record("trip-a-\($0)", base.addingTimeInterval(40 * 86_400 + Double($0) * 7_200), awayPoint) }
        photos.append(record("returned-home", base.addingTimeInterval(42 * 86_400), homePoint))
        photos += (0..<15).map { record("trip-b-\($0)", base.addingTimeInterval(43 * 86_400 + Double($0) * 7_200), awayPoint) }
        XCTAssertEqual(TripDetector().detect(from: photos).trips.count, 2)
    }

    func testBuildsMultipleEventsFromSpatialGap() throws {
        let home = GeoPoint(latitude: 31.2304, longitude: 121.4737)
        let osaka = GeoPoint(latitude: 34.6937, longitude: 135.5023)
        let kyoto = GeoPoint(latitude: 35.0116, longitude: 135.7681)
        let base = Date(timeIntervalSince1970: 1_700_000_000)
        var photos = (0..<30).map { record("h-\($0)", base.addingTimeInterval(Double($0) * 86_400), home) }
        photos += (0..<10).map { record("o-\($0)", base.addingTimeInterval(40 * 86_400 + Double($0) * 600), osaka) }
        photos += (0..<10).map { record("k-\($0)", base.addingTimeInterval(40 * 86_400 + 8_000 + Double($0) * 600), kyoto) }
        let trip = try XCTUnwrap(TripDetector().detect(from: photos).trips.first)
        XCTAssertGreaterThanOrEqual(trip.eventCount, 2)
    }

    func testScreenshotsDoNotCreateATrip() {
        let homePoint = GeoPoint(latitude: 31.2304, longitude: 121.4737)
        let awayPoint = GeoPoint(latitude: 35.0116, longitude: 135.7681)
        let base = Date(timeIntervalSince1970: 1_700_000_000)
        let home = (0..<30).map { record("home-\($0)", base.addingTimeInterval(Double($0) * 86_400), homePoint) }
        let screenshots = (0..<40).map { index in PhotoRecord(id: "s-\(index)", creationDate: base.addingTimeInterval(40 * 86_400 + Double(index) * 3_600), location: awayPoint, pixelWidth: 1170, pixelHeight: 2532, isFavorite: false, isScreenshot: true) }
        XCTAssertTrue(TripDetector().detect(from: home + screenshots).trips.isEmpty)
    }

    func testRepresentativeSelectionUsesAtMostFivePhotos() throws {
        let homePoint = GeoPoint(latitude: 31.2304, longitude: 121.4737)
        let awayPoint = GeoPoint(latitude: 35.0116, longitude: 135.7681)
        let base = Date(timeIntervalSince1970: 1_700_000_000)
        var photos = (0..<30).map { record("home-\($0)", base.addingTimeInterval(Double($0) * 86_400), homePoint) }
        photos += (0..<30).map { record("away-\($0)", base.addingTimeInterval(40 * 86_400 + Double($0) * 1_800), awayPoint) }
        let event = try XCTUnwrap(TripDetector().detect(from: photos).trips.first?.days.first?.events.first)
        XCTAssertLessThanOrEqual(event.visiblePhotoIDs.count, 5)
    }

    @MainActor
    func testStorePreservesUserEditsWhenRescanBoundaryMoves() throws {
        let directory = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString, directoryHint: .isDirectory)
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = TripStore(storageDirectory: directory)
        let base = Date(timeIntervalSince1970: 1_700_000_000)
        let eventID = UUID()
        var original = makeTrip(
            id: UUID(),
            start: base,
            end: base.addingTimeInterval(3 * 86_400),
            eventID: eventID,
            title: "7月旅行",
            note: ""
        )
        try store.replace(with: ScanSnapshot(version: 1, scannedAt: base, accessiblePhotoCount: 40, locatedPhotoCount: 35, inferredHome: nil, trips: [original]))
        original.title = "京都之旅"
        original.isConfirmed = true
        original.days[0].title = "雨中的京都"
        original.days[0].events[0].note = "那天下了一场很轻的雨"
        original.days[0].events[0].placeName = "清水寺"
        original.days[0].events[0].featuredPhotoIDs = ["b"]
        original.days[0].events[0].excludedPhotoIDs = ["a"]
        original.isFavorite = true
        try store.updateTrip(original)

        let shifted = makeTrip(
            id: UUID(),
            start: base.addingTimeInterval(30 * 60),
            end: base.addingTimeInterval(3 * 86_400 + 30 * 60),
            eventID: UUID(),
            title: "7月旅行",
            note: ""
        )
        try store.replace(with: ScanSnapshot(version: 1, scannedAt: base.addingTimeInterval(100), accessiblePhotoCount: 41, locatedPhotoCount: 36, inferredHome: nil, trips: [shifted]))

        let saved = try XCTUnwrap(store.snapshot?.trips.first)
        XCTAssertEqual(saved.title, "京都之旅")
        XCTAssertTrue(saved.isConfirmed)
        XCTAssertEqual(saved.days[0].title, "雨中的京都")
        XCTAssertEqual(saved.days[0].events[0].note, "那天下了一场很轻的雨")
        XCTAssertEqual(saved.days[0].events[0].placeName, "清水寺")
        XCTAssertEqual(saved.days[0].events[0].photoIDs, ["b"])
        XCTAssertEqual(saved.days[0].events[0].featuredPhotoIDs, ["b"])
        XCTAssertTrue(saved.favorite)
    }

    @MainActor
    func testStoreKeepsMergedEventSuppressedAcrossRescan() throws {
        let directory = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString, directoryHint: .isDirectory)
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = TripStore(storageDirectory: directory)
        let base = Date(timeIntervalSince1970: 1_700_000_000)
        var trip = makeTrip(id: UUID(), start: base, end: base.addingTimeInterval(2 * 86_400), eventID: UUID(), title: "旅行", note: "")
        let removedID = UUID()
        trip.days[0].events.append(MemoryEvent(id: removedID, title: "下一站", startDate: base.addingTimeInterval(5_000), endDate: base.addingTimeInterval(6_000), photoIDs: ["c"], location: nil, placeName: nil, note: "", isHidden: false))
        try store.replace(with: ScanSnapshot(version: 1, scannedAt: base, accessiblePhotoCount: 3, locatedPhotoCount: 3, inferredHome: nil, trips: [trip]))
        trip.days[0].events.removeLast(); trip.suppressedEventIDs = [removedID]
        try store.updateTrip(trip)
        var fresh = trip
        fresh.days[0].events.append(MemoryEvent(id: removedID, title: "下一站", startDate: base.addingTimeInterval(5_000), endDate: base.addingTimeInterval(6_000), photoIDs: ["c"], location: nil, placeName: nil, note: "", isHidden: false))
        try store.replace(with: ScanSnapshot(version: 1, scannedAt: base.addingTimeInterval(1), accessiblePhotoCount: 3, locatedPhotoCount: 3, inferredHome: nil, trips: [fresh]))
        XCTAssertFalse(try XCTUnwrap(store.snapshot?.trips.first).visibleEvents.contains { $0.id == removedID })
    }

    @MainActor
    func testStoreKeepsUserCreatedSplitEventAcrossRescan() throws {
        let directory = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString, directoryHint: .isDirectory)
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = TripStore(storageDirectory: directory)
        let base = Date(timeIntervalSince1970: 1_700_000_000)
        var original = makeTrip(id: UUID(), start: base, end: base.addingTimeInterval(2 * 86_400), eventID: UUID(), title: "旅行", note: "")
        try store.replace(with: ScanSnapshot(version: 1, scannedAt: base, accessiblePhotoCount: 4, locatedPhotoCount: 4, inferredHome: nil, trips: [original]))
        var split = original.days[0].events[0]
        split = MemoryEvent(id: UUID(), title: "后半段", startDate: base.addingTimeInterval(86_400 + 4_000), endDate: base.addingTimeInterval(86_400 + 5_000), photoIDs: ["c", "d"], location: nil, placeName: nil, note: "新拆出的事件", isHidden: false, isUserCreated: true)
        original.days[0].events.append(split)
        try store.updateTrip(original)

        let fresh = makeTrip(id: original.id, start: base, end: base.addingTimeInterval(2 * 86_400), eventID: original.days[0].events[0].id, title: "旅行", note: "")
        try store.replace(with: ScanSnapshot(version: 1, scannedAt: base.addingTimeInterval(1), accessiblePhotoCount: 4, locatedPhotoCount: 4, inferredHome: nil, trips: [fresh]))
        let saved = try XCTUnwrap(store.snapshot?.trips.first)
        XCTAssertTrue(saved.visibleEvents.contains { $0.id == split.id && $0.note == "新拆出的事件" })
        XCTAssertTrue(saved.days.contains { $0.date.startOfDay == split.startDate.startOfDay })
    }

    func testTripStatisticsUseRouteAndDistinctNames() {
        let base = Date(timeIntervalSince1970: 1_700_000_000)
        let osaka = GeoPoint(latitude: 34.6937, longitude: 135.5023)
        let kyoto = GeoPoint(latitude: 35.0116, longitude: 135.7681)
        let first = MemoryEvent(id: UUID(), title: "大阪", startDate: base, endDate: base.addingTimeInterval(1_000), photoIDs: ["a", "b"], location: osaka, placeName: "大阪城", cityName: "大阪市", countryName: "日本", note: "", isHidden: false)
        let second = MemoryEvent(id: UUID(), title: "京都", startDate: base.addingTimeInterval(2_000), endDate: base.addingTimeInterval(3_000), photoIDs: ["c"], location: kyoto, placeName: "清水寺", cityName: "京都市", countryName: "日本", note: "", isHidden: false)
        let day = TravelDay(id: UUID(), date: base.startOfDay, title: "第一天", events: [first, second])
        let trip = DiscoveredTrip(id: UUID(), title: "关西", startDate: base, endDate: base.addingTimeInterval(3_000), photoIDs: ["a", "b", "c"], days: [day], center: nil, distanceFromHomeMeters: 800_000, isConfirmed: true, isHidden: false, detectedAt: base)
        XCTAssertEqual(trip.placeCount, 2)
        XCTAssertEqual(trip.cityCount, 2)
        XCTAssertGreaterThan(trip.routeDistanceMeters, 30_000)
        XCTAssertEqual(trip.mostPhotographedEvent?.id, first.id)
    }

    func testReconcileUpdatesTripRangeAndPhotoReferences() {
        let base = Date(timeIntervalSince1970: 1_700_000_000)
        let visible = MemoryEvent(id: UUID(), title: "新的事件", startDate: base.addingTimeInterval(86_400), endDate: base.addingTimeInterval(90_000), photoIDs: ["new-a", "new-b"], location: nil, placeName: nil, note: "", isHidden: false)
        let hidden = MemoryEvent(id: UUID(), title: "隐藏事件", startDate: base, endDate: base.addingTimeInterval(1_000), photoIDs: ["old"], location: nil, placeName: nil, note: "", isHidden: true)
        let day = TravelDay(id: UUID(), date: visible.startDate.startOfDay, title: "第二天", events: [hidden, visible])
        var trip = DiscoveredTrip(id: UUID(), title: "旅行", startDate: base, endDate: base.addingTimeInterval(1_000), photoIDs: ["old"], days: [day], center: nil, distanceFromHomeMeters: 100_000, isConfirmed: true, isHidden: false, detectedAt: base, coverPhotoIDOverride: "old")
        trip.reconcileDerivedFields()
        XCTAssertEqual(trip.startDate, visible.startDate)
        XCTAssertEqual(trip.endDate, visible.endDate)
        XCTAssertEqual(trip.photoIDs, ["old", "new-a", "new-b"])
        XCTAssertEqual(trip.photoCount, 2)
        XCTAssertNil(trip.coverPhotoIDOverride)
        XCTAssertEqual(day.photoCount, 2)
    }

    func testHiddenOnlyEventKeepsMatchingReferencesButNotVisibleCount() {
        let base = Date(timeIntervalSince1970: 1_700_000_000)
        let hidden = MemoryEvent(id: UUID(), title: "隐藏", startDate: base, endDate: base.addingTimeInterval(1_000), photoIDs: ["a", "b"], location: nil, placeName: nil, note: "", isHidden: true)
        let day = TravelDay(id: UUID(), date: base.startOfDay, title: "第一天", events: [hidden])
        var trip = DiscoveredTrip(id: UUID(), title: "旅行", startDate: base, endDate: base.addingTimeInterval(1_000), photoIDs: ["a", "b"], days: [day], center: nil, distanceFromHomeMeters: 100_000, isConfirmed: true, isHidden: false, detectedAt: base)
        trip.reconcileDerivedFields()
        XCTAssertEqual(trip.photoIDs, ["a", "b"])
        XCTAssertEqual(trip.photoCount, 0)
        XCTAssertNil(trip.coverPhotoID)
    }

    @MainActor
    func testConfirmedTripSurvivesWhenPhotosAreNoLongerDetected() throws {
        let directory = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString, directoryHint: .isDirectory)
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = TripStore(storageDirectory: directory)
        let base = Date(timeIntervalSince1970: 1_700_000_000)
        var trip = makeTrip(id: UUID(), start: base, end: base.addingTimeInterval(2 * 86_400), eventID: UUID(), title: "保留的旅行", note: "原图删掉也要保留")
        trip.isConfirmed = true
        try store.replace(with: ScanSnapshot(version: 1, scannedAt: base, accessiblePhotoCount: 2, locatedPhotoCount: 2, inferredHome: nil, trips: [trip]))
        try store.replace(with: ScanSnapshot(version: 1, scannedAt: base.addingTimeInterval(100), accessiblePhotoCount: 0, locatedPhotoCount: 0, inferredHome: nil, trips: []))
        let preserved = try XCTUnwrap(store.snapshot?.trips.first)
        XCTAssertEqual(preserved.id, trip.id)
        XCTAssertEqual(preserved.days[0].events[0].note, "原图删掉也要保留")
    }

    @MainActor
    func testRescanMatchesCorrectedTripByPhotoReferences() throws {
        let directory = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString, directoryHint: .isDirectory)
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = TripStore(storageDirectory: directory)
        let base = Date(timeIntervalSince1970: 1_700_000_000)
        var corrected = makeTrip(id: UUID(), start: base, end: base.addingTimeInterval(2 * 86_400), eventID: UUID(), title: "原旅行", note: "")
        corrected.isConfirmed = true
        corrected.title = "我修改过的旅行"
        corrected.startDate = base.addingTimeInterval(20 * 86_400)
        corrected.endDate = base.addingTimeInterval(22 * 86_400)
        try store.replace(with: ScanSnapshot(version: 1, scannedAt: base, accessiblePhotoCount: 2, locatedPhotoCount: 2, inferredHome: nil, trips: [corrected]))

        let fresh = makeTrip(id: UUID(), start: base, end: base.addingTimeInterval(2 * 86_400), eventID: UUID(), title: "自动发现", note: "")
        try store.replace(with: ScanSnapshot(version: 1, scannedAt: base.addingTimeInterval(1), accessiblePhotoCount: 2, locatedPhotoCount: 2, inferredHome: nil, trips: [fresh]))
        XCTAssertEqual(store.snapshot?.trips.count, 1)
        XCTAssertEqual(store.snapshot?.trips.first?.title, "我修改过的旅行")
        XCTAssertTrue(store.snapshot?.trips.first?.isConfirmed == true)
    }

    private func record(_ id: String, _ date: Date, _ point: GeoPoint?) -> PhotoRecord {
        PhotoRecord(id: id, creationDate: date, location: point, pixelWidth: 4032, pixelHeight: 3024, isFavorite: false, isScreenshot: false)
    }

    private func makeTrip(id: UUID, start: Date, end: Date, eventID: UUID, title: String, note: String) -> DiscoveredTrip {
        let event = MemoryEvent(id: eventID, title: "下午漫步", startDate: start.addingTimeInterval(2_000), endDate: start.addingTimeInterval(4_000), photoIDs: ["a", "b"], location: nil, placeName: nil, note: note, isHidden: false)
        let day = TravelDay(id: UUID(), date: start.startOfDay, title: "第一天", events: [event])
        return DiscoveredTrip(id: id, title: title, startDate: start, endDate: end, photoIDs: ["a", "b"], days: [day], center: nil, distanceFromHomeMeters: 100_000, isConfirmed: false, isHidden: false, detectedAt: start)
    }
}
