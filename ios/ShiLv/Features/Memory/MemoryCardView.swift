import SwiftUI
import UIKit

struct MemoryCardView: View {
    @EnvironmentObject private var model: AppModel
    private let seedTrip: DiscoveredTrip
    @State private var shareItems: [Any] = []
    @State private var showingShare = false
    @State private var coverImage: UIImage?
    @State private var isFavorite: Bool
    @State private var footprintImages: [String: UIImage] = [:]
    @AppStorage("shareIncludesMemories") private var shareIncludesMemories = true
    private var trip: DiscoveredTrip { model.store.snapshot?.trips.first(where: { $0.id == seedTrip.id }) ?? seedTrip }

    init(trip: DiscoveredTrip) {
        self.seedTrip = trip
        _isFavorite = State(initialValue: trip.favorite)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 22) {
                MemoryPoster(trip: trip, coverImage: coverImage, footprintImages: footprintImages, includesMemories: shareIncludesMemories).padding(.top, 12)
                HStack {
                    Button {
                        isFavorite.toggle()
                        var updated = trip; updated.isFavorite = isFavorite; model.update(updated)
                    } label: { Label(isFavorite ? "已收藏" : "收藏", systemImage: isFavorite ? "heart.fill" : "heart") }.buttonStyle(.bordered)
                    Button { renderAndShare() } label: { Label("保存 / 分享", systemImage: "square.and.arrow.up").frame(maxWidth: .infinity) }.buttonStyle(.borderedProminent).tint(ShiLvTheme.ink)
                }.controlSize(.large)
            }.padding(20)
        }
        .background(Color(red: 0.91, green: 0.92, blue: 0.89))
        .navigationTitle("这次旅行的回忆")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showingShare) { ActivityView(items: shareItems) }
        .task {
            if let id = trip.coverPhotoID {
                coverImage = await PhotoLibraryService.shared.requestImage(id: id, targetSize: CGSize(width: 1170, height: 900))
            }
            for event in trip.visibleEvents.prefix(4) {
                guard let photoID = event.coverPhotoID else { continue }
                footprintImages[photoID] = await PhotoLibraryService.shared.requestImage(id: photoID, targetSize: CGSize(width: 420, height: 320))
            }
        }
        .onReceive(model.store.$snapshot) { snapshot in
            if let current = snapshot?.trips.first(where: { $0.id == seedTrip.id }) { isFavorite = current.favorite }
        }
    }

    private func shareText(for snapshot: DiscoveredTrip, includesMemories: Bool) -> String {
        let base = "\(snapshot.title) · \(snapshot.dateRangeText)\n\(snapshot.dayCount) 天，\(snapshot.photoCount) 张照片，\(snapshot.eventCount) 个记忆事件。"
        guard includesMemories, let note = snapshot.visibleEvents.map(\.note).first(where: { !$0.isEmpty }) else { return base }
        return base + "\n“\(note)”"
    }

    @MainActor
    private func renderAndShare() {
        let snapshot = trip
        let includesMemories = shareIncludesMemories
        let poster = MemoryPoster(trip: snapshot, coverImage: coverImage, footprintImages: footprintImages, includesMemories: includesMemories)
            .frame(width: 390)
            .padding(20)
            .background(Color(red: 0.91, green: 0.92, blue: 0.89))
        let renderer = ImageRenderer(content: poster)
        renderer.scale = 3
        if let image = renderer.uiImage {
            shareItems = [image, shareText(for: snapshot, includesMemories: includesMemories)]
        } else {
            shareItems = [shareText(for: snapshot, includesMemories: includesMemories)]
        }
        showingShare = true
    }
}

struct MemoryPoster: View {
    let trip: DiscoveredTrip
    let coverImage: UIImage?
    let footprintImages: [String: UIImage]
    let includesMemories: Bool
    var body: some View {
        VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 9) {
                Text("TRIP MEMORY · \(Calendar.current.component(.year, from: trip.startDate))").font(.caption2.bold()).tracking(2).foregroundStyle(ShiLvTheme.muted)
                Text(trip.title).font(.system(size: 34, weight: .bold, design: .serif))
                Text(trip.dateRangeText).font(.caption).foregroundStyle(ShiLvTheme.muted)
            }.frame(maxWidth: .infinity, alignment: .leading).padding(25)
            Group {
                if let coverImage { Image(uiImage: coverImage).resizable().scaledToFill() }
                else { LinearGradient(colors: [ShiLvTheme.line, ShiLvTheme.paper], startPoint: .topLeading, endPoint: .bottomTrailing) }
            }.frame(maxWidth: .infinity).frame(height: 300).clipped()
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 1), count: 3), spacing: 1) {
                PosterStat(value: "\(trip.photoCount)", label: "照片")
                PosterStat(value: "\(trip.dayCount)", label: "旅行天数")
                PosterStat(value: "\(trip.cityCount)", label: "城市")
                PosterStat(value: "\(trip.placeCount)", label: "地点")
                PosterStat(value: String(format: "%.1f km", trip.routeDistanceMeters / 1000), label: "移动距离")
                PosterStat(value: DateFormatter.timeLabel.string(from: trip.endDate), label: "最后一张")
            }.background(ShiLvTheme.line)
            VStack(alignment: .leading, spacing: 8) {
                if let event = trip.mostPhotographedEvent { Text("\(event.placeName ?? event.title) · 照片最多的地方") }
                if let day = trip.busiestDay { Text("\(DateFormatter.dayLabel.string(from: day.date)) · 拍照最多的一天") }
                Text("\(DateFormatter.timeLabel.string(from: trip.latestPhotoTime)) · 最晚拍照时间")
            }.font(.caption).foregroundStyle(ShiLvTheme.muted).frame(maxWidth: .infinity, alignment: .leading).padding(25)
            VStack(alignment: .leading, spacing: 12) {
                Text("旅行足迹").font(.headline)
                HStack(spacing: 8) {
                    ForEach(Array(trip.visibleEvents.prefix(4))) { event in
                        VStack(alignment: .leading, spacing: 4) {
                            if let id = event.coverPhotoID, let image = footprintImages[id] {
                                Image(uiImage: image).resizable().scaledToFill().frame(height: 68).clipped().clipShape(RoundedRectangle(cornerRadius: 8))
                            } else { RoundedRectangle(cornerRadius: 8).fill(ShiLvTheme.line).frame(height: 68) }
                            Text(event.placeName ?? event.title).font(.system(size: 9)).lineLimit(1)
                        }.frame(maxWidth: .infinity).accessibilityElement(children: .combine).accessibilityLabel("旅行足迹，\(event.placeName ?? event.title)")
                    }
                }
                Text("旅行回顾").font(.headline).padding(.top, 4)
                Text(story)
                    .font(.system(size: 18, design: .serif)).lineSpacing(6)
                Text("拾旅 · 让照片重新变成旅途").font(.caption2).foregroundStyle(ShiLvTheme.muted).frame(maxWidth: .infinity).padding(.top, 8)
            }.padding(25)
        }.background(.white).clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous)).shadow(color: .black.opacity(0.06), radius: 18, y: 8)
    }

    private var story: String {
        if let summary = trip.summary, !summary.isEmpty {
            let note = includesMemories ? trip.visibleEvents.map(\.note).first(where: { !$0.isEmpty }).map { " 你还记得：“\($0)”" } ?? "" : ""
            return summary + note + " \(trip.dayCount) 天以后，你离开了这段旅程，但 \(trip.photoCount) 张照片把它留了下来。"
        }
        let eventTitles = trip.days.flatMap(\.events).filter { !$0.isHidden }.map(\.title)
        let notes = includesMemories ? trip.days.flatMap(\.events).map(\.note).filter { !$0.isEmpty } : []
        let route = Array(eventTitles.prefix(4)).joined(separator: "、")
        let noteText = notes.first.map { "你还记得：“\($0)”" } ?? "你留下的每一句话，会让它越来越像真正的回忆。"
        return "这是一段从 \(DateFormatter.dayLabel.string(from: trip.startDate)) 开始的旅程。照片记住了\(route.isEmpty ? "沿途的光景" : route)。\(noteText)"
    }
}

private struct ActivityView: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) { }
}

private struct PosterStat: View {
    let value: String; let label: String
    var body: some View { VStack(alignment: .leading, spacing: 4) { Text(value).font(.headline).foregroundStyle(ShiLvTheme.orange); Text(label).font(.caption2).foregroundStyle(ShiLvTheme.muted) }.frame(maxWidth: .infinity, alignment: .leading).padding(15).background(.white) }
}
