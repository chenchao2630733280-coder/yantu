import SwiftUI

struct TripDiscoveryView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State var trip: DiscoveredTrip
    @State private var isConfirming = false
    @State private var showOverview = false
    @State private var analysisTask: Task<Void, Never>?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                ZStack(alignment: .bottomLeading) {
                    PhotoThumbnail(id: trip.coverPhotoID, height: 430, cornerRadius: 0)
                    LinearGradient(colors: [.clear, .black.opacity(0.78)], startPoint: .center, endPoint: .bottom)
                    VStack(alignment: .leading, spacing: 8) {
                        Label("发现一段旅程", systemImage: "sparkles").font(.caption.bold())
                        Text(trip.title).font(.system(size: 38, weight: .bold, design: .serif))
                        Text(trip.dateRangeText).font(.subheadline)
                    }.foregroundStyle(.white).padding(24)
                }.clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))

                HStack { DiscoveryStat(value: "\(trip.dayCount)", label: "天"); DiscoveryStat(value: "\(trip.photoCount)", label: "张照片"); DiscoveryStat(value: "\(trip.placeCount)", label: "个地点") }
                if trip.cityCount > 0 { Text("途经 \(trip.cityCount) 个城市").font(.subheadline).foregroundStyle(ShiLvTheme.muted) }

                VStack(alignment: .leading, spacing: 12) {
                    Text("我们根据照片的时间和地点发现，这可能是一次旅行。").font(.system(size: 20, design: .serif)).lineSpacing(5)
                    if trip.locatedEventCount > 1 {
                        Text(trip.visibleEvents.prefix(4).map { $0.placeName ?? $0.title }.joined(separator: "  →  "))
                            .font(.subheadline.bold()).foregroundStyle(ShiLvTheme.orange).lineLimit(2)
                    }
                }

                if isConfirming {
                    VStack(alignment: .leading, spacing: 13) {
                        if let progress = model.analysisProgress {
                            ProgressView(value: Double(progress.current), total: Double(max(1, progress.total))).tint(ShiLvTheme.orange)
                            Text(statusText(progress.current, progress.total)).font(.headline)
                        } else {
                            ProgressView().tint(ShiLvTheme.orange)
                            Text("正在准备整理旅程……").font(.headline)
                        }
                        Text("原始照片始终留在系统相册").font(.caption).foregroundStyle(ShiLvTheme.muted)
                    }.padding(20).shilvCard()
                } else {
                    Button("这是我的旅行") {
                        isConfirming = true
                        analysisTask = Task { @MainActor in
                            let analyzed = await model.confirmAndAnalyze(trip)
                            guard !Task.isCancelled else { isConfirming = false; return }
                            trip = analyzed; isConfirming = false; analysisTask = nil; showOverview = true
                        }
                    }.buttonStyle(.borderedProminent).tint(ShiLvTheme.ink).controlSize(.large).frame(maxWidth: .infinity)
                    Button("可能不是这次旅行", role: .destructive) { trip.isHidden = true; model.update(trip); dismiss() }
                        .font(.footnote).frame(maxWidth: .infinity)
                }
            }.padding(.horizontal, 20).padding(.bottom, 35)
        }
        .background(ShiLvTheme.paper)
        .navigationBarTitleDisplayMode(.inline)
        .navigationDestination(isPresented: $showOverview) { TripOverviewView(trip: trip) }
        .onDisappear { if isConfirming && !showOverview { analysisTask?.cancel() } }
    }

    private func statusText(_ current: Int, _ total: Int) -> String {
        let progress = total > 0 ? Double(current) / Double(total) : 0
        if progress < 0.25 { return "正在整理照片……" }
        if progress < 0.55 { return "正在识别你去过的地方……" }
        if progress < 0.85 { return "正在还原旅程……" }
        return "正在生成旅行故事……"
    }
}

private struct DiscoveryStat: View {
    let value: String; let label: String
    var body: some View { VStack(spacing: 4) { Text(value).font(.title2.bold()); Text(label).font(.caption).foregroundStyle(ShiLvTheme.muted) }.frame(maxWidth: .infinity) }
}
