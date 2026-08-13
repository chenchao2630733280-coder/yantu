import Foundation
@preconcurrency import Vision

@MainActor
struct MemoryAnalysisService {
    private let photoLibrary: PhotoLibraryService
    private let placeNames: PlaceNameService

    init(photoLibrary: PhotoLibraryService, placeNames: PlaceNameService = PlaceNameService()) {
        self.photoLibrary = photoLibrary
        self.placeNames = placeNames
    }

    @MainActor
    func enrich(_ trip: DiscoveredTrip, progress: @escaping (Int, Int) -> Void) async -> DiscoveredTrip {
        var enriched = trip
        let total = trip.days.reduce(0) { $0 + $1.events.count }
        var completed = 0
        let deepAnalysisBudget = 100

        if let center = trip.center, let place = await placeNames.resolve(center) {
            let destination = place.city ?? place.shortName
            enriched.title = "\(destination)之旅"
        }

        for dayIndex in enriched.days.indices {
            for eventIndex in enriched.days[dayIndex].events.indices {
                if Task.isCancelled { return trip }
                let event = enriched.days[dayIndex].events[eventIndex]
                if completed < deepAnalysisBudget,
                   let photoID = event.coverPhotoID,
                   let image = await photoLibrary.requestImage(id: photoID, targetSize: CGSize(width: 640, height: 640), contentMode: .aspectFit),
                   let cgImage = image.cgImage {
                    let labels = await Self.classify(ReadOnlyCGImage(value: cgImage))
                    enriched.days[dayIndex].events[eventIndex].title = EventSemantic.bestTitle(for: labels, date: event.startDate)
                }
                if let point = event.location, let place = await placeNames.resolve(point) {
                    enriched.days[dayIndex].events[eventIndex].placeName = place.shortName
                    enriched.days[dayIndex].events[eventIndex].cityName = place.city
                    enriched.days[dayIndex].events[eventIndex].countryName = place.country
                }
                let finalEvent = enriched.days[dayIndex].events[eventIndex]
                enriched.days[dayIndex].events[eventIndex].summary = eventSummary(finalEvent)
                completed += 1
                progress(completed, total)
            }
            enriched.days[dayIndex].title = dayTitle(enriched.days[dayIndex])
        }
        if Task.isCancelled { return trip }
        enriched.summary = tripSummary(enriched)
        enriched.isConfirmed = true
        return enriched
    }

    private func eventSummary(_ event: MemoryEvent) -> String {
        let place = event.placeName ?? event.title
        let period: String
        switch Calendar.current.component(.hour, from: event.startDate) {
        case 5..<11: period = "清晨"
        case 11..<14: period = "午间"
        case 14..<18: period = "下午"
        default: period = "傍晚"
        }
        let activity = event.title == place ? "这一段停留" : "\(event.title)的片段"
        return "\(period)来到\(place)，照片留下了\(activity)。"
    }

    private func tripSummary(_ trip: DiscoveredTrip) -> String {
        let places = trip.visibleEvents.compactMap(\.placeName).reduce(into: [String]()) { values, name in
            if !values.contains(name) { values.append(name) }
        }
        guard !places.isEmpty else { return "这是一段由照片重新整理出来的旅程。" }
        return "这是一段沿着\(places.prefix(3).joined(separator: "、"))慢慢展开的旅行。"
    }

    private nonisolated static func classify(_ imageBox: ReadOnlyCGImage) async -> [String] {
        return await Task.detached(priority: .userInitiated) {
            await withCheckedContinuation { continuation in
                let gate = AnalysisContinuationGate()
                let request = VNClassifyImageRequest { request, error in
                    guard error == nil, let observations = request.results as? [VNClassificationObservation] else {
                        if gate.claim() { continuation.resume(returning: []) }; return
                    }
                    if gate.claim() { continuation.resume(returning: observations.filter { $0.confidence >= 0.12 }.prefix(6).map { $0.identifier.lowercased() }) }
                }
                request.usesCPUOnly = false
                do { try VNImageRequestHandler(cgImage: imageBox.value, orientation: .up).perform([request]) }
                catch { if gate.claim() { continuation.resume(returning: []) } }
            }
        }.value
    }

    private func dayTitle(_ day: TravelDay) -> String {
        let unique = day.events.map(\.title).reduce(into: [String]()) { values, title in if !values.contains(title) { values.append(title) } }
        if unique.count >= 2 { return "\(unique[0])与\(unique[1])" }
        return unique.first ?? day.title
    }
}

private struct ReadOnlyCGImage: @unchecked Sendable {
    let value: CGImage
}

private final class AnalysisContinuationGate: @unchecked Sendable {
    private let lock = NSLock()
    private var available = true
    func claim() -> Bool {
        lock.lock(); defer { lock.unlock() }
        guard available else { return false }
        available = false
        return true
    }
}

private enum EventSemantic {
    static func bestTitle(for labels: [String], date: Date) -> String {
        let joined = labels.joined(separator: " ")
        let hour = Calendar.current.component(.hour, from: date)
        if contains(joined, ["food", "dish", "meal", "restaurant", "cuisine", "drink", "coffee", "dessert"]) { return hour < 11 ? "旅途早餐" : hour < 17 ? "当地味道" : "晚餐时光" }
        if contains(joined, ["train", "aircraft", "airport", "vehicle", "bus", "station", "subway", "boat"]) { return "在路上" }
        if contains(joined, ["beach", "ocean", "sea", "lake", "river", "waterfall"]) { return "水边的记忆" }
        if contains(joined, ["mountain", "forest", "tree", "garden", "park", "flower", "nature"]) { return "走进自然" }
        if contains(joined, ["temple", "church", "shrine", "palace", "castle", "monument", "historic"]) { return "古迹与建筑" }
        if contains(joined, ["street", "city", "building", "market", "shop", "town", "architecture"]) { return "城市漫步" }
        if contains(joined, ["animal", "dog", "cat", "deer", "bird", "zoo"]) { return "意外的相遇" }
        if contains(joined, ["night", "skyline", "sunset", "sunrise", "light"]) { return hour >= 17 ? "城市入夜" : "追着光走" }
        switch hour { case 0..<6: return "夜里的片段"; case 6..<11: return "清晨出发"; case 11..<14: return "午间停留"; case 14..<18: return "下午漫步"; default: return "傍晚时分" }
    }

    private static func contains(_ text: String, _ terms: [String]) -> Bool { terms.contains { text.contains($0) } }
}
