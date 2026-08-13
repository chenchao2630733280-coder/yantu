import SwiftUI

struct DayTimelineView: View {
    @EnvironmentObject private var model: AppModel
    let tripID: UUID
    let dayID: UUID
    @State private var period: DayPeriod = .all
    @State private var showMemoryEditor = false

    private var day: TravelDay? { model.store.snapshot?.trips.first(where: { $0.id == tripID })?.days.first(where: { $0.id == dayID }) }
    private var events: [MemoryEvent] { (day?.events ?? []).filter { !$0.isHidden && period.contains($0.startDate) } }

    var body: some View {
        VStack(spacing: 0) {
            Picker("时段", selection: $period) { ForEach(DayPeriod.allCases) { Text($0.rawValue).tag($0) } }.pickerStyle(.segmented).padding()
            if let day {
                List {
                    Section {
                        VStack(alignment: .leading, spacing: 5) {
                            Text("\(day.title)").font(.title2.bold())
                            Text("\(DateFormatter.dayLabel.string(from: day.date)) · \(day.photoCount) 张照片").font(.caption).foregroundStyle(ShiLvTheme.muted)
                        }.padding(.vertical, 8)
                    }
                    ForEach(Array(events.enumerated()), id: \.element.id) { index, event in
                        NavigationLink { EventDetailView(tripID: tripID, dayID: dayID, eventID: event.id) } label: { EventRow(event: event) }
                            .listRowBackground(ShiLvTheme.paper).listRowSeparator(.hidden)
                        if index < events.count - 1, shouldShowTransition(event, events[index + 1]) {
                            RouteTransitionRow(from: event, to: events[index + 1])
                                .listRowBackground(ShiLvTheme.paper).listRowSeparator(.hidden)
                        }
                    }
                }.listStyle(.plain).scrollContentBackground(.hidden).background(ShiLvTheme.paper)
                    .navigationTitle(DateFormatter.dayLabel.string(from: day.date)).navigationBarTitleDisplayMode(.inline)
                    .safeAreaInset(edge: .bottom) {
                        Button("＋ 记下一句话") { showMemoryEditor = true }
                            .buttonStyle(.borderedProminent).tint(ShiLvTheme.ink).padding(.horizontal).padding(.bottom, 6)
                    }
            } else {
                ContentUnavailableView("找不到这一天", systemImage: "calendar.badge.exclamationmark")
            }
        }.background(ShiLvTheme.paper)
        .sheet(isPresented: $showMemoryEditor) {
            if let first = events.first { QuickMemoryEditor(tripID: tripID, dayID: dayID, event: first) }
        }
    }

    private func shouldShowTransition(_ from: MemoryEvent, _ to: MemoryEvent) -> Bool {
        guard let origin = from.location, let destination = to.location else { return false }
        return origin.distance(to: destination) >= 20_000
    }
}

private enum DayPeriod: String, CaseIterable, Identifiable {
    case all = "全部", morning = "上午", afternoon = "下午", evening = "晚上"
    var id: Self { self }
    func contains(_ date: Date) -> Bool {
        let hour = Calendar.current.component(.hour, from: date)
        switch self { case .all: return true; case .morning: return hour < 12; case .afternoon: return hour >= 12 && hour < 18; case .evening: return hour >= 18 }
    }
}

private struct EventRow: View {
    let event: MemoryEvent
    var body: some View {
        HStack(spacing: 12) {
            VStack { Text(event.startDate, formatter: DateFormatter.timeLabel).font(.caption.bold()); Circle().fill(ShiLvTheme.orange).frame(width: 8, height: 8); Spacer() }.frame(width: 46)
            PhotoThumbnail(id: event.coverPhotoID, height: 118, cornerRadius: 14).frame(width: 118)
            VStack(alignment: .leading, spacing: 6) {
                Text(event.placeName ?? event.title).font(.headline)
                Text("停留约 \(event.duration.durationShortText) · \(event.photoCount) 张照片").font(.caption).foregroundStyle(ShiLvTheme.muted)
                if let summary = event.summary { Text(summary).font(.caption).lineLimit(2) }
                if !event.note.isEmpty { Label(event.note, systemImage: "quote.opening").font(.caption).lineLimit(2) }
            }
        }.padding(.vertical, 7)
    }
}

private struct RouteTransitionRow: View {
    let from: MemoryEvent
    let to: MemoryEvent
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "arrow.right").font(.caption.bold()).foregroundStyle(ShiLvTheme.orange).frame(width: 46)
            VStack(alignment: .leading, spacing: 4) {
                Text("\(period)从\(from.cityName ?? from.placeName ?? from.title)前往\(to.cityName ?? to.placeName ?? to.title)").font(.subheadline.bold())
                if let origin = from.location, let destination = to.location {
                    Text("移动约 \(Int(origin.distance(to: destination) / 1000)) 公里").font(.caption).foregroundStyle(ShiLvTheme.muted)
                }
            }
        }.padding(.vertical, 6).accessibilityElement(children: .combine)
    }

    private var period: String {
        switch Calendar.current.component(.hour, from: to.startDate) {
        case 0..<12: return "上午"
        case 12..<18: return "下午"
        default: return "傍晚"
        }
    }
}

private struct QuickMemoryEditor: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    let tripID: UUID; let dayID: UUID; let event: MemoryEvent
    @State private var note = ""

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                Text("把照片不知道的部分留给以后。").foregroundStyle(ShiLvTheme.muted)
                TextEditor(text: $note).frame(minHeight: 180).padding(8).background(.white).clipShape(RoundedRectangle(cornerRadius: 16))
                Spacer()
            }.padding().background(ShiLvTheme.paper).navigationTitle("记下一句话")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("保存") { save() }.disabled(note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) }
            }
        }.onAppear { note = event.note }
    }

    private func save() {
        guard var trip = model.store.snapshot?.trips.first(where: { $0.id == tripID }), let day = trip.days.firstIndex(where: { $0.id == dayID }), let index = trip.days[day].events.firstIndex(where: { $0.id == event.id }) else { return }
        trip.days[day].events[index].note = String(note.trimmingCharacters(in: .whitespacesAndNewlines).prefix(500))
        model.update(trip); dismiss()
    }
}

private extension TimeInterval {
    var durationShortText: String {
        let minutes = max(1, Int(self / 60))
        return minutes >= 60 ? "\(minutes / 60) 小时 \(minutes % 60) 分" : "\(minutes) 分钟"
    }
}
