import SwiftUI

enum ShiLvTheme {
    static let ink = Color(red: 0.09, green: 0.10, blue: 0.09)
    static let paper = Color(red: 0.96, green: 0.965, blue: 0.95)
    static let muted = Color(red: 0.45, green: 0.47, blue: 0.44)
    static let orange = Color(red: 0.95, green: 0.36, blue: 0.13)
    static let green = Color(red: 0.40, green: 0.52, blue: 0.31)
    static let line = Color(red: 0.88, green: 0.89, blue: 0.86)
}

struct CardModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            .shadow(color: .black.opacity(0.045), radius: 14, y: 6)
    }
}

extension View {
    func shilvCard() -> some View { modifier(CardModifier()) }
}

extension DateFormatter {
    static let tripRange: DateFormatter = { let f = DateFormatter(); f.locale = Locale(identifier: "zh_CN"); f.dateFormat = "yyyy.MM.dd"; return f }()
    static let dayLabel: DateFormatter = { let f = DateFormatter(); f.locale = Locale(identifier: "zh_CN"); f.dateFormat = "M月d日 EEEE"; return f }()
    static let timeLabel: DateFormatter = { let f = DateFormatter(); f.locale = Locale(identifier: "zh_CN"); f.dateFormat = "HH:mm"; return f }()
}

extension DiscoveredTrip {
    var dateRangeText: String { "\(DateFormatter.tripRange.string(from: startDate)) – \(DateFormatter.tripRange.string(from: endDate))" }
}
