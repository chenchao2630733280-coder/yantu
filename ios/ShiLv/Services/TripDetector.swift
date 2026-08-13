import Foundation
@preconcurrency import CoreLocation

struct TripDetectionConfiguration: Sendable {
    var minimumDistanceFromHome: CLLocationDistance = 80_000
    var maximumGapWithinTrip: TimeInterval = 36 * 60 * 60
    var minimumLocatedPhotos: Int = 5
    var minimumTotalPhotos: Int = 12
    var eventTimeGap: TimeInterval = 2 * 60 * 60
    var eventDistanceGap: CLLocationDistance = 3_000
}

struct TripDetector: Sendable {
    let configuration: TripDetectionConfiguration
    private let calendar = Calendar.current

    init(configuration: TripDetectionConfiguration = .init()) {
        self.configuration = configuration
    }

    func detect(from photos: [PhotoRecord], now: Date = Date()) -> ScanSnapshot {
        if Task.isCancelled { return ScanSnapshot(version: 1, scannedAt: now, accessiblePhotoCount: photos.count, locatedPhotoCount: 0, inferredHome: nil, trips: []) }
        let sorted = photos.sorted { $0.creationDate < $1.creationDate }
        let eligible = sorted.filter { $0.isScreenshot != true }
        let located = eligible.filter { $0.location != nil }
        let home = inferHome(from: located)
        guard let home else {
            return ScanSnapshot(version: 1, scannedAt: now, accessiblePhotoCount: sorted.count, locatedPhotoCount: located.count, inferredHome: nil, trips: [])
        }

        let locatedGroups = splitAwayAnchors(located, home: home)
        var trips: [DiscoveredTrip] = []

        for group in locatedGroups where group.count >= configuration.minimumLocatedPhotos {
            if Task.isCancelled { break }
            guard let first = group.first, let last = group.last else { continue }
            let paddedStart = first.creationDate.addingTimeInterval(-6 * 60 * 60)
            let paddedEnd = last.creationDate.addingTimeInterval(6 * 60 * 60)
            let allTripPhotos = eligible.filter { $0.creationDate >= paddedStart && $0.creationDate <= paddedEnd }
            guard allTripPhotos.count >= configuration.minimumTotalPhotos else { continue }
            let uniqueDays = Set(allTripPhotos.map { calendar.startOfDay(for: $0.creationDate) })
            guard uniqueDays.count >= 2 || group.count >= 30 else { continue }

            let points = group.compactMap(\.location)
            let center = points.centroid
            let distance = center?.distance(to: home) ?? configuration.minimumDistanceFromHome
            let days = buildDays(from: allTripPhotos)
            trips.append(DiscoveredTrip(
                id: stableTripID(start: first.creationDate, end: last.creationDate),
                title: defaultTripTitle(start: first.creationDate, end: last.creationDate),
                startDate: first.creationDate,
                endDate: last.creationDate,
                photoIDs: allTripPhotos.map(\.id),
                days: days,
                center: center,
                distanceFromHomeMeters: distance,
                isConfirmed: false,
                isHidden: false,
                detectedAt: now,
                summary: nil,
                isFavorite: false,
                coverPhotoIDOverride: nil
            ))
        }

        return ScanSnapshot(
            version: 1,
            scannedAt: now,
            accessiblePhotoCount: sorted.count,
            locatedPhotoCount: located.count,
            inferredHome: home,
            trips: mergeOverlapping(trips).sorted { $0.startDate > $1.startDate }
        )
    }

    func inferHome(from photos: [PhotoRecord]) -> GeoPoint? {
        let located = photos.compactMap { photo -> (GeoPoint, Date)? in
            guard let location = photo.location else { return nil }
            return (location, photo.creationDate)
        }
        guard located.count >= configuration.minimumLocatedPhotos else { return nil }

        struct Cell: Hashable { let lat: Int; let lon: Int }
        var buckets: [Cell: [(GeoPoint, Date)]] = [:]
        for (point, date) in located {
            let cell = Cell(lat: Int((point.latitude * 5).rounded()), lon: Int((point.longitude * 5).rounded()))
            buckets[cell, default: []].append((point, date))
        }
        let best = buckets.max { lhs, rhs in
            let lhsDays = Set(lhs.value.map { calendar.startOfDay(for: $0.1) }).count
            let rhsDays = Set(rhs.value.map { calendar.startOfDay(for: $0.1) }).count
            return lhsDays == rhsDays ? lhs.value.count < rhs.value.count : lhsDays < rhsDays
        }?.value
        return best?.map(\.0).centroid
    }

    private func splitAwayAnchors(_ locatedPhotos: [PhotoRecord], home: GeoPoint) -> [[PhotoRecord]] {
        var groups: [[PhotoRecord]] = []
        var current: [PhotoRecord] = []
        let sparseTravelGap = max(configuration.maximumGapWithinTrip, 72 * 60 * 60)

        for photo in locatedPhotos {
            guard let point = photo.location else { continue }
            let isAway = point.distance(to: home) >= configuration.minimumDistanceFromHome
            if !isAway {
                if !current.isEmpty { groups.append(current); current = [] }
                continue
            }
            if let previous = current.last, photo.creationDate.timeIntervalSince(previous.creationDate) > sparseTravelGap {
                groups.append(current); current = []
            }
            current.append(photo)
        }
        if !current.isEmpty { groups.append(current) }
        return groups
    }

    private func buildDays(from photos: [PhotoRecord]) -> [TravelDay] {
        let byDay = Dictionary(grouping: photos) { calendar.startOfDay(for: $0.creationDate) }
        return byDay.keys.sorted().enumerated().compactMap { index, date in
            guard let photosForDay = byDay[date] else { return nil }
            let events = buildEvents(from: photosForDay.sorted { $0.creationDate < $1.creationDate })
            return TravelDay(id: stableDayID(date), date: date, title: "第 \(index + 1) 天", events: events)
        }
    }

    private func buildEvents(from photos: [PhotoRecord]) -> [MemoryEvent] {
        guard let first = photos.first else { return [] }
        var groups = [[first]]
        for photo in photos.dropFirst() {
            guard let previous = groups.last?.last else { continue }
            let timeGap = photo.creationDate.timeIntervalSince(previous.creationDate)
            let distance = zip(previous.location, photo.location).map { pair in pair.0.distance(to: pair.1) } ?? 0
            if timeGap > configuration.eventTimeGap || distance > configuration.eventDistanceGap {
                groups.append([photo])
            } else {
                groups[groups.count - 1].append(photo)
            }
        }
        return groups.compactMap { group in
            guard let first = group.first, let last = group.last else { return nil }
            let points = group.compactMap(\.location)
            let start = first.creationDate
            return MemoryEvent(
                id: stableEventID(start, firstPhotoID: first.id),
                title: eventTitle(for: start),
                startDate: start,
                endDate: last.creationDate,
                photoIDs: group.map(\.id),
                location: points.centroid,
                placeName: nil,
                note: "",
                isHidden: false,
                summary: nil,
                featuredPhotoIDs: representativePhotoIDs(from: group),
                coverPhotoIDOverride: nil,
                excludedPhotoIDs: nil
            )
        }
    }

    private func representativePhotoIDs(from photos: [PhotoRecord]) -> [String] {
        let candidates = photos.filter { $0.isScreenshot != true }
        let source = candidates.isEmpty ? photos : candidates
        guard source.count > 5 else { return source.map(\.id) }
        var indices = [0, source.count / 4, source.count / 2, source.count * 3 / 4, source.count - 1]
        if let favorite = source.firstIndex(where: \.isFavorite) { indices[2] = favorite }
        return indices.map { source[$0].id }.reduce(into: [String]()) { result, id in
            if !result.contains(id) { result.append(id) }
        }
    }

    private func mergeOverlapping(_ trips: [DiscoveredTrip]) -> [DiscoveredTrip] {
        let sorted = trips.sorted { $0.startDate < $1.startDate }
        guard var current = sorted.first else { return [] }
        var result: [DiscoveredTrip] = []
        for next in sorted.dropFirst() {
            if next.startDate <= current.endDate.addingTimeInterval(12 * 60 * 60) {
                let photoIDs = (current.photoIDs + next.photoIDs).reduce(into: [String]()) { result, id in if !result.contains(id) { result.append(id) } }
                let allDays = mergeDays(current.days + next.days)
                current = DiscoveredTrip(id: current.id, title: current.title, startDate: current.startDate, endDate: max(current.endDate, next.endDate), photoIDs: photoIDs, days: allDays, center: [current.center, next.center].compactMap { $0 }.centroid, distanceFromHomeMeters: max(current.distanceFromHomeMeters, next.distanceFromHomeMeters), isConfirmed: current.isConfirmed, isHidden: false, detectedAt: current.detectedAt, summary: current.summary, isFavorite: current.isFavorite, coverPhotoIDOverride: current.coverPhotoIDOverride, suppressedEventIDs: current.suppressedEventIDs)
            } else {
                result.append(current); current = next
            }
        }
        result.append(current)
        return result
    }

    private func mergeDays(_ days: [TravelDay]) -> [TravelDay] {
        let grouped = Dictionary(grouping: days) { calendar.startOfDay(for: $0.date) }
        return grouped.keys.sorted().map { date in
            let events = (grouped[date] ?? []).flatMap(\.events).sorted { $0.startDate < $1.startDate }
            return TravelDay(id: stableDayID(date), date: date, title: grouped[date]?.first?.title ?? "旅途中的一天", events: events)
        }
    }

    private func defaultTripTitle(start: Date, end: Date) -> String {
        let formatter = DateFormatter(); formatter.locale = Locale(identifier: "zh_CN"); formatter.dateFormat = "M月旅行"
        return formatter.string(from: start)
    }

    private func eventTitle(for date: Date) -> String {
        switch calendar.component(.hour, from: date) {
        case 0..<6: return "夜里的片段"
        case 6..<11: return "上午的记忆"
        case 11..<14: return "午间停留"
        case 14..<18: return "下午的漫步"
        default: return "傍晚与夜晚"
        }
    }

    private func stableTripID(start: Date, end: Date) -> UUID { stableUUID("trip-\(start.timeIntervalSince1970)-\(end.timeIntervalSince1970)") }
    private func stableDayID(_ date: Date) -> UUID { stableUUID("day-\(date.timeIntervalSince1970)") }
    private func stableEventID(_ date: Date, firstPhotoID: String) -> UUID { stableUUID("event-\(date.timeIntervalSince1970)-\(firstPhotoID)") }

    private func stableUUID(_ value: String) -> UUID {
        var bytes = [UInt8](repeating: 0, count: 16)
        for (index, byte) in value.utf8.enumerated() { bytes[index % 16] = bytes[index % 16] &* 31 &+ byte }
        bytes[6] = (bytes[6] & 0x0F) | 0x40; bytes[8] = (bytes[8] & 0x3F) | 0x80
        return UUID(uuid: (bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5], bytes[6], bytes[7], bytes[8], bytes[9], bytes[10], bytes[11], bytes[12], bytes[13], bytes[14], bytes[15]))
    }
}

private func zip<T, U>(_ lhs: T?, _ rhs: U?) -> (T, U)? {
    guard let lhs, let rhs else { return nil }
    return (lhs, rhs)
}
