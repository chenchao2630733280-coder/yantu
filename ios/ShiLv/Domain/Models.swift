import Foundation
@preconcurrency import CoreLocation

struct GeoPoint: Codable, Hashable, Sendable {
    let latitude: Double
    let longitude: Double

    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }

    func distance(to other: GeoPoint) -> CLLocationDistance {
        CLLocation(latitude: latitude, longitude: longitude)
            .distance(from: CLLocation(latitude: other.latitude, longitude: other.longitude))
    }
}

struct PhotoRecord: Identifiable, Codable, Hashable, Sendable {
    let id: String
    let creationDate: Date
    let location: GeoPoint?
    let pixelWidth: Int
    let pixelHeight: Int
    let isFavorite: Bool
    let isScreenshot: Bool
}

struct MemoryEvent: Identifiable, Codable, Hashable, Sendable {
    let id: UUID
    var title: String
    var startDate: Date
    var endDate: Date
    var photoIDs: [String]
    var location: GeoPoint?
    var placeName: String?
    var cityName: String? = nil
    var countryName: String? = nil
    var note: String
    var isHidden: Bool
    var summary: String? = nil
    var featuredPhotoIDs: [String]? = nil
    var coverPhotoIDOverride: String? = nil
    var excludedPhotoIDs: [String]? = nil
    var isUserCreated: Bool? = nil

    var photoCount: Int { photoIDs.count }
    var duration: TimeInterval { endDate.timeIntervalSince(startDate) }
    var coverPhotoID: String? { coverPhotoIDOverride ?? featuredPhotoIDs?.first ?? photoIDs.first }
    var visiblePhotoIDs: [String] {
        let preferred = featuredPhotoIDs?.filter(photoIDs.contains) ?? []
        return preferred.isEmpty ? Array(photoIDs.prefix(5)) : preferred
    }
}

struct TravelDay: Identifiable, Codable, Hashable, Sendable {
    let id: UUID
    let date: Date
    var title: String
    var events: [MemoryEvent]

    var visibleEvents: [MemoryEvent] { events.filter { !$0.isHidden }.sorted { $0.startDate < $1.startDate } }
    var photoCount: Int { visibleEvents.reduce(0) { $0 + $1.photoCount } }
    var coverPhotoID: String? { visibleEvents.first?.coverPhotoID }
}

struct DiscoveredTrip: Identifiable, Codable, Hashable, Sendable {
    let id: UUID
    var title: String
    var startDate: Date
    var endDate: Date
    var photoIDs: [String]
    var days: [TravelDay]
    let center: GeoPoint?
    let distanceFromHomeMeters: Double
    var isConfirmed: Bool
    var isHidden: Bool
    let detectedAt: Date
    var summary: String? = nil
    var isFavorite: Bool? = nil
    var coverPhotoIDOverride: String? = nil
    var suppressedEventIDs: [UUID]? = nil

    var photoCount: Int {
        visibleEvents.flatMap(\.photoIDs).reduce(into: [String]()) { result, id in if !result.contains(id) { result.append(id) } }.count
    }
    var dayCount: Int {
        max(1, Calendar.current.dateComponents([.day], from: startDate.startOfDay, to: endDate.startOfDay).day.map { $0 + 1 } ?? 1)
    }
    var eventCount: Int { days.reduce(0) { $0 + $1.events.filter { !$0.isHidden }.count } }
    var visiblePhotoIDs: [String] {
        visibleEvents.flatMap(\.photoIDs).reduce(into: [String]()) { result, id in if !result.contains(id) { result.append(id) } }
    }
    var coverPhotoID: String? {
        if let override = coverPhotoIDOverride, visiblePhotoIDs.contains(override) { return override }
        return days.lazy.compactMap(\.coverPhotoID).first
    }
    var favorite: Bool { isFavorite ?? false }
    var visibleEvents: [MemoryEvent] { days.flatMap(\.events).filter { !$0.isHidden }.sorted { $0.startDate < $1.startDate } }
    var locatedEventCount: Int { visibleEvents.filter { $0.location != nil }.count }
    var placeCount: Int {
        let named = Set(visibleEvents.compactMap(\.placeName))
        return named.isEmpty ? locatedEventCount : named.count
    }
    var cityCount: Int { Set(visibleEvents.compactMap(\.cityName)).count }
    var routeDistanceMeters: Double {
        let points = days.flatMap(\.events).sorted { $0.startDate < $1.startDate }.compactMap(\.location)
        return zip(points, points.dropFirst()).reduce(0) { $0 + $1.0.distance(to: $1.1) }
    }
    var latestPhotoTime: Date { endDate }
    var busiestDay: TravelDay? { days.max { $0.photoCount < $1.photoCount } }
    var mostPhotographedEvent: MemoryEvent? { visibleEvents.max { $0.photoCount < $1.photoCount } }

    mutating func reconcileDerivedFields() {
        days.removeAll { $0.events.isEmpty }
        days.sort { $0.date < $1.date }
        let allEvents = days.flatMap(\.events).sorted { $0.startDate < $1.startDate }
        guard !allEvents.isEmpty else { photoIDs = []; return }
        let rangeEvents = visibleEvents.isEmpty ? allEvents : visibleEvents
        startDate = rangeEvents.map(\.startDate).min() ?? startDate
        endDate = rangeEvents.map(\.endDate).max() ?? endDate
        photoIDs = allEvents.flatMap(\.photoIDs).reduce(into: [String]()) { result, id in
            if !result.contains(id) { result.append(id) }
        }
        if let override = coverPhotoIDOverride, !visiblePhotoIDs.contains(override) { coverPhotoIDOverride = nil }
    }
}

struct ScanSnapshot: Codable, Sendable {
    let version: Int
    let scannedAt: Date
    let accessiblePhotoCount: Int
    let locatedPhotoCount: Int
    let inferredHome: GeoPoint?
    var trips: [DiscoveredTrip]
}

enum PhotoAccessState: Equatable, Sendable {
    case notDetermined
    case full
    case limited
    case denied
    case restricted
}

enum ScanPhase: Equatable, Sendable {
    case idle
    case requestingPermission
    case readingMetadata(current: Int, total: Int)
    case detectingTrips
    case saving
    case complete
    case failed(String)

    var label: String {
        switch self {
        case .idle: return "准备扫描"
        case .requestingPermission: return "等待照片授权"
        case let .readingMetadata(current, total): return "正在读取照片信息 · \(current)/\(total)"
        case .detectingTrips: return "正在还原旅行"
        case .saving: return "正在保存到本机"
        case .complete: return "扫描完成"
        case let .failed(message): return message
        }
    }

    var progress: Double? {
        guard case let .readingMetadata(current, total) = self, total > 0 else { return nil }
        return Double(current) / Double(total)
    }
}

extension Date {
    var startOfDay: Date { Calendar.current.startOfDay(for: self) }
}

extension Array where Element == GeoPoint {
    var centroid: GeoPoint? {
        guard !isEmpty else { return nil }
        return GeoPoint(
            latitude: reduce(0) { $0 + $1.latitude } / Double(count),
            longitude: reduce(0) { $0 + $1.longitude } / Double(count)
        )
    }
}
