import SwiftUI

struct TripOverviewView: View {
    @EnvironmentObject private var model: AppModel
    @State var trip: DiscoveredTrip
    @State private var showRename = false
    @State private var draftTitle = ""
    @State private var showCoverPicker = false

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 20) {
                hero
                if !trip.isConfirmed { confirmation }
                Text(trip.summary ?? "照片把一路的时间、地点和停留重新连接了起来。").font(.system(size: 22, design: .serif)).lineSpacing(7)
                stats
                highlights
                if trip.center != nil {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("旅行路线").font(.title2.bold())
                        Text(trip.visibleEvents.prefix(5).map { $0.placeName ?? $0.title }.joined(separator: "  →  ")).font(.subheadline).foregroundStyle(ShiLvTheme.muted)
                        NavigationLink { TripMapView(trip: trip) } label: { Label("打开记忆地图", systemImage: "map").frame(maxWidth: .infinity).padding(13) }.buttonStyle(.bordered).tint(ShiLvTheme.ink)
                    }
                }
                HStack {
                    VStack(alignment: .leading) { Text("旅程故事线").font(.title2.bold()); Text("点开一天，重新走过当时的故事").font(.caption).foregroundStyle(ShiLvTheme.muted) }
                    Spacer()
                    NavigationLink { MemoryCardView(trip: trip) } label: { Text("回忆卡片 ›").font(.subheadline.bold()) }
                }
                ForEach(Array(trip.days.filter { !$0.visibleEvents.isEmpty }.enumerated()), id: \.element.id) { index, day in
                    NavigationLink { DayTimelineView(tripID: trip.id, dayID: day.id) } label: { DayCard(index: index, day: day) }.buttonStyle(.plain)
                }
            }.padding(.horizontal, 20).padding(.bottom, 32)
        }
        .background(ShiLvTheme.paper)
        .navigationTitle(trip.title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button { trip.isFavorite = !trip.favorite; persist() } label: { Image(systemName: trip.favorite ? "heart.fill" : "heart") }.accessibilityLabel(trip.favorite ? "取消收藏" : "收藏旅行")
                Button { showCoverPicker = true } label: { Image(systemName: "photo.badge.checkmark") }.accessibilityLabel("更换旅行封面")
                Button { draftTitle = trip.title; showRename = true } label: { Image(systemName: "ellipsis") }
            }
        }
        .alert("修改旅行名称", isPresented: $showRename) {
            TextField("旅行名称", text: $draftTitle)
            Button("取消", role: .cancel) { }
            Button("保存") { let trimmed = draftTitle.trimmingCharacters(in: .whitespacesAndNewlines); if !trimmed.isEmpty { trip.title = String(trimmed.prefix(40)); persist() } }
        }
        .onReceive(model.store.$snapshot) { snapshot in
            if let updated = snapshot?.trips.first(where: { $0.id == trip.id }) { trip = updated }
        }
        .sheet(isPresented: $showCoverPicker) {
            TripCoverPicker(photoIDs: Array(trip.visibleEvents.flatMap(\.visiblePhotoIDs).prefix(50)), selectedID: trip.coverPhotoID) { id in
                trip.coverPhotoIDOverride = id; persist(); showCoverPicker = false
            }
        }
    }

    private var hero: some View {
        ZStack(alignment: .bottomLeading) {
            PhotoThumbnail(id: trip.coverPhotoID, height: 310, cornerRadius: 0)
            LinearGradient(colors: [.clear, .black.opacity(0.72)], startPoint: .center, endPoint: .bottom)
            VStack(alignment: .leading, spacing: 7) {
                if !trip.isConfirmed { Label("新发现", systemImage: "sparkles").font(.caption.bold()).padding(.horizontal, 10).padding(.vertical, 6).background(ShiLvTheme.orange).clipShape(Capsule()) }
                Text(trip.title).font(.system(size: 34, weight: .bold, design: .serif))
                Text(trip.dateRangeText).font(.subheadline)
            }.foregroundStyle(.white).padding(22)
        }.clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous)).padding(.top, 12)
    }

    private var stats: some View {
        HStack {
            StatCell(value: "\(trip.dayCount)", label: "天")
            StatCell(value: "\(trip.photoCount)", label: "张照片")
            StatCell(value: "\(trip.placeCount)", label: "个地点")
        }.padding(.vertical, 18).shilvCard()
    }

    private var highlights: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("旅程精彩时刻").font(.title2.bold())
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: 10) {
                    ForEach(Array(trip.visibleEvents.compactMap(\.coverPhotoID).prefix(6)), id: \.self) { id in
                        PhotoThumbnail(id: id, height: 160, cornerRadius: 18).frame(width: 128)
                    }
                }
            }.contentMargins(.horizontal, 1)
        }
    }

    private var confirmation: some View {
        VStack(alignment: .leading, spacing: 13) {
            Text("这是你的旅行吗？").font(.headline)
            Text("这些照片远离常驻区域、时间连续，因此被识别为同一段旅程。").font(.subheadline).foregroundStyle(ShiLvTheme.muted)
            if let progress = model.analysisProgress {
                VStack(alignment: .leading, spacing: 7) {
                    ProgressView(value: Double(progress.current), total: Double(progress.total)).tint(ShiLvTheme.orange)
                    Text("正在本机理解事件 · \(progress.current)/\(progress.total)").font(.caption).foregroundStyle(ShiLvTheme.muted)
                }
            } else {
                Button("这是我的旅行") { Task { trip = await model.confirmAndAnalyze(trip) } }.buttonStyle(.borderedProminent).tint(ShiLvTheme.ink).frame(maxWidth: .infinity)
            }
            Button("隐藏这次发现", role: .destructive) { trip.isHidden = true; persist() }.font(.footnote).frame(maxWidth: .infinity)
        }.padding(20).shilvCard()
    }

    private func persist() { model.update(trip) }
}

private struct TripCoverPicker: View {
    let photoIDs: [String]
    let selectedID: String?
    let select: (String) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                    ForEach(photoIDs, id: \.self) { id in
                        Button { select(id) } label: {
                            PhotoThumbnail(id: id, height: 120, cornerRadius: 12)
                                .overlay(alignment: .topTrailing) {
                                    if id == selectedID { Image(systemName: "checkmark.circle.fill").foregroundStyle(.white, ShiLvTheme.orange).padding(7) }
                                }
                        }.buttonStyle(.plain).accessibilityLabel(id == selectedID ? "当前旅行封面" : "设为旅行封面").accessibilityHint("双击选择这张照片作为旅行首页大图")
                    }
                }.padding()
            }.background(ShiLvTheme.paper).navigationTitle("选择旅行封面")
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } } }
        }
    }
}

private struct StatCell: View {
    let value: String; let label: String
    var body: some View { VStack(spacing: 3) { Text(value).font(.title3.bold()); Text(label).font(.caption).foregroundStyle(ShiLvTheme.muted) }.frame(maxWidth: .infinity) }
}

private struct DayCard: View {
    let index: Int; let day: TravelDay
    var body: some View {
        HStack(spacing: 15) {
            VStack { Text("Day").font(.caption.bold()); Text("\(index + 1)").font(.title.bold()); Text(day.date, formatter: DateFormatter.dayLabel).font(.system(size: 9)).lineLimit(1) }
                .foregroundStyle(index % 2 == 0 ? ShiLvTheme.orange : ShiLvTheme.green).frame(width: 70)
            PhotoThumbnail(id: day.coverPhotoID, height: 90, cornerRadius: 14).frame(width: 105)
            VStack(alignment: .leading, spacing: 6) { Text(day.title).font(.headline); Text("\(day.visibleEvents.count) 个事件 · \(day.photoCount) 张照片").font(.caption).foregroundStyle(ShiLvTheme.muted); Text(day.visibleEvents.prefix(2).map(\.title).joined(separator: " · ")).font(.caption).lineLimit(2) }
            Spacer(); Image(systemName: "chevron.right").font(.caption).foregroundStyle(ShiLvTheme.muted)
        }.padding(13).shilvCard()
    }
}
