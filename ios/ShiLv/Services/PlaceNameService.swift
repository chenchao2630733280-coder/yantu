import Foundation
@preconcurrency import CoreLocation

actor PlaceNameService {
    private var cache: [String: PlaceName] = [:]
    private var lastRequestAt: Date?

    struct PlaceName: Sendable {
        let shortName: String
        let city: String?
        let country: String?
    }

    func resolve(_ point: GeoPoint) async -> PlaceName? {
        let key = cacheKey(point)
        if let cached = cache[key] { return cached }
        await respectRateLimit()
        let geocoder = CLGeocoder()
        do {
            let marks = try await geocoder.reverseGeocodeLocation(CLLocation(latitude: point.latitude, longitude: point.longitude), preferredLocale: Locale(identifier: "zh_CN"))
            guard let mark = marks.first else { return nil }
            let city = mark.locality ?? mark.subAdministrativeArea ?? mark.administrativeArea
            let short = mark.name ?? mark.subLocality ?? city ?? mark.country ?? "旅途中的一站"
            let value = PlaceName(shortName: short, city: city, country: mark.country)
            cache[key] = value
            lastRequestAt = Date()
            return value
        } catch {
            lastRequestAt = Date()
            return nil
        }
    }

    func locate(_ query: String) async -> GeoPoint? {
        guard !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        await respectRateLimit()
        do {
            let marks = try await CLGeocoder().geocodeAddressString(query, in: nil, preferredLocale: Locale(identifier: "zh_CN"))
            lastRequestAt = Date()
            guard let coordinate = marks.first?.location?.coordinate else { return nil }
            return GeoPoint(latitude: coordinate.latitude, longitude: coordinate.longitude)
        } catch { lastRequestAt = Date(); return nil }
    }

    private func cacheKey(_ point: GeoPoint) -> String {
        "\((point.latitude * 100).rounded() / 100),\((point.longitude * 100).rounded() / 100)"
    }

    private func respectRateLimit() async {
        guard let lastRequestAt else { return }
        let remaining = 250_000_000 - Int64(Date().timeIntervalSince(lastRequestAt) * 1_000_000_000)
        if remaining > 0 { try? await Task.sleep(nanoseconds: UInt64(remaining)) }
    }
}
