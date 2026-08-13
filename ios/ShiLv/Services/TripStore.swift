import Foundation
import Combine

@MainActor
final class TripStore: ObservableObject {
    @Published private(set) var snapshot: ScanSnapshot?
    private let fileURL: URL

    init(fileManager: FileManager = .default, storageDirectory: URL? = nil) {
        let root = storageDirectory
            ?? fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? fileManager.temporaryDirectory
        let directory = storageDirectory == nil ? root.appending(path: "ShiLv", directoryHint: .isDirectory) : root
        try? fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        fileURL = directory.appending(path: "travel-index-v1.json")
        load()
    }

    func replace(with newSnapshot: ScanSnapshot) throws {
        let merged = mergeUserEdits(old: snapshot, new: newSnapshot)
        try persist(merged)
    }

    func updateTrip(_ trip: DiscoveredTrip) throws {
        guard var value = snapshot, let index = value.trips.firstIndex(where: { $0.id == trip.id }) else { return }
        var reconciled = trip
        reconciled.reconcileDerivedFields()
        value.trips[index] = reconciled
        try persist(value)
    }

    func deleteLocalIndex() throws {
        if FileManager.default.fileExists(atPath: fileURL.path) { try FileManager.default.removeItem(at: fileURL) }
        snapshot = nil
    }

    var storedDataSize: Int64 {
        Int64((try? fileURL.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0)
    }

    private func load() {
        guard let data = try? Data(contentsOf: fileURL), let decoded = try? JSONDecoder.shilv.decode(ScanSnapshot.self, from: data) else { return }
        snapshot = decoded
    }

    private func persist(_ value: ScanSnapshot) throws {
        let data = try JSONEncoder.shilv.encode(value)
        try data.write(to: fileURL, options: [.atomic, .completeFileProtection])
        snapshot = value
    }

    private func mergeUserEdits(old: ScanSnapshot?, new: ScanSnapshot) -> ScanSnapshot {
        guard let old else { return new }
        let previousTrips = Dictionary(uniqueKeysWithValues: old.trips.map { ($0.id, $0) })
        var merged = new
        merged.trips = new.trips.map { fresh in
            let previous = previousTrips[fresh.id] ?? old.trips.max(by: {
                matchScore($0, fresh) < matchScore($1, fresh)
            }).flatMap { matchScore($0, fresh) >= 0.5 ? $0 : nil }
            guard let previous else { return fresh }
            var result = fresh
            result.title = previous.title
            result.isConfirmed = previous.isConfirmed
            result.isHidden = previous.isHidden
            result.summary = previous.summary
            result.isFavorite = previous.isFavorite
            result.coverPhotoIDOverride = previous.coverPhotoIDOverride
            result.suppressedEventIDs = previous.suppressedEventIDs
            let oldEvents = Dictionary(uniqueKeysWithValues: previous.days.flatMap(\.events).map { ($0.id, $0) })
            result.days = fresh.days.map { day in
                var updated = day
                if let oldDay = previous.days.min(by: {
                    abs($0.date.timeIntervalSince(day.date)) < abs($1.date.timeIntervalSince(day.date))
                }), abs(oldDay.date.timeIntervalSince(day.date)) < 12 * 60 * 60 {
                    updated.title = oldDay.title
                }
                updated.events = day.events.map { event in
                    let oldEvent = oldEvents[event.id] ?? previous.days.flatMap(\.events).min(by: {
                        abs($0.startDate.timeIntervalSince(event.startDate)) < abs($1.startDate.timeIntervalSince(event.startDate))
                    }).flatMap { abs($0.startDate.timeIntervalSince(event.startDate)) <= 30 * 60 ? $0 : nil }
                    guard let oldEvent else { return event }
                    var value = event
                    value.title = oldEvent.title; value.placeName = oldEvent.placeName
                    value.cityName = oldEvent.cityName; value.countryName = oldEvent.countryName
                    value.note = oldEvent.note; value.isHidden = oldEvent.isHidden
                    value.startDate = oldEvent.startDate; value.endDate = oldEvent.endDate
                    value.location = oldEvent.location
                    value.summary = oldEvent.summary
                    value.featuredPhotoIDs = oldEvent.featuredPhotoIDs
                    value.coverPhotoIDOverride = oldEvent.coverPhotoIDOverride
                    value.excludedPhotoIDs = oldEvent.excludedPhotoIDs
                    value.isUserCreated = oldEvent.isUserCreated
                    let existingPhotoIDs = value.photoIDs
                    value.photoIDs += oldEvent.photoIDs.filter { !existingPhotoIDs.contains($0) }
                    if let excluded = oldEvent.excludedPhotoIDs {
                        value.photoIDs.removeAll { excluded.contains($0) }
                        value.featuredPhotoIDs?.removeAll { excluded.contains($0) }
                    }
                    return value
                }
                let customEvents = previous.days.flatMap(\.events).filter { $0.isUserCreated == true && $0.startDate.startOfDay == day.date.startOfDay }
                let existingIDs = Set(updated.events.map(\.id))
                updated.events += customEvents.filter { !existingIDs.contains($0.id) }
                if let suppressed = previous.suppressedEventIDs { updated.events.removeAll { suppressed.contains($0.id) } }
                return updated
            }
            let existingEventIDs = Set(result.days.flatMap(\.events).map(\.id))
            let unmatchedCustomEvents = previous.days.flatMap(\.events).filter { $0.isUserCreated == true && !existingEventIDs.contains($0.id) }
            for custom in unmatchedCustomEvents {
                if let dayIndex = result.days.firstIndex(where: { $0.date.startOfDay == custom.startDate.startOfDay }) {
                    result.days[dayIndex].events.append(custom)
                } else {
                    result.days.append(TravelDay(id: UUID(), date: custom.startDate.startOfDay, title: "补充的一天", events: [custom]))
                }
            }
            if let suppressed = previous.suppressedEventIDs {
                for index in result.days.indices { result.days[index].events.removeAll { suppressed.contains($0.id) } }
            }
            result.days = normalizeEventDays(result.days)
            result.reconcileDerivedFields()
            return result
        }
        let matchedOldIDs = Set(merged.trips.compactMap { fresh in
            old.trips.max(by: { matchScore($0, fresh) < matchScore($1, fresh) }).flatMap { matchScore($0, fresh) >= 0.5 ? $0.id : nil }
        })
        let preservedMemories = old.trips.filter { $0.isConfirmed && !matchedOldIDs.contains($0.id) }
        merged.trips += preservedMemories
        merged.trips.sort { $0.startDate > $1.startDate }
        return merged
    }

    private func normalizeEventDays(_ days: [TravelDay]) -> [TravelDay] {
        let allEvents = days.flatMap(\.events)
        let grouped = Dictionary(grouping: allEvents) { $0.startDate.startOfDay }
        return grouped.keys.sorted().map { date in
            let existing = days.first(where: { $0.date.startOfDay == date })
            return TravelDay(
                id: existing?.id ?? UUID(),
                date: date,
                title: existing?.title ?? "补充的一天",
                events: (grouped[date] ?? []).sorted { $0.startDate < $1.startDate }
            )
        }
    }

    private func overlapRatio(_ lhs: DiscoveredTrip, _ rhs: DiscoveredTrip) -> Double {
        let intersectionStart = max(lhs.startDate, rhs.startDate)
        let intersectionEnd = min(lhs.endDate, rhs.endDate)
        guard intersectionEnd > intersectionStart else { return 0 }
        let intersection = intersectionEnd.timeIntervalSince(intersectionStart)
        let smallerDuration = min(lhs.endDate.timeIntervalSince(lhs.startDate), rhs.endDate.timeIntervalSince(rhs.startDate))
        return smallerDuration > 0 ? intersection / smallerDuration : 0
    }

    private func photoOverlapRatio(_ lhs: DiscoveredTrip, _ rhs: DiscoveredTrip) -> Double {
        let left = Set(lhs.photoIDs), right = Set(rhs.photoIDs)
        guard !left.isEmpty, !right.isEmpty else { return 0 }
        return Double(left.intersection(right).count) / Double(min(left.count, right.count))
    }

    private func matchScore(_ lhs: DiscoveredTrip, _ rhs: DiscoveredTrip) -> Double {
        if lhs.id == rhs.id { return 1 }
        return max(overlapRatio(lhs, rhs), photoOverlapRatio(lhs, rhs))
    }
}

private extension JSONEncoder {
    static var shilv: JSONEncoder { let encoder = JSONEncoder(); encoder.dateEncodingStrategy = .iso8601; encoder.outputFormatting = [.sortedKeys]; return encoder }
}
private extension JSONDecoder {
    static var shilv: JSONDecoder { let decoder = JSONDecoder(); decoder.dateDecodingStrategy = .iso8601; return decoder }
}
