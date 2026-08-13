import SwiftUI
import UIKit

struct DiscoveryView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        Group {
            switch model.accessState {
            case .notDetermined: PhotoPermissionView()
            case .denied, .restricted: model.store.snapshot == nil ? AnyView(DeniedPhotoAccessView()) : AnyView(discoveryContent)
            case .full, .limited: discoveryContent
            }
        }
        .navigationBarHidden(true)
    }

    private var discoveryContent: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 18) {
                header
                if model.accessState == .limited { limitedBanner }
                if model.accessState == .denied || model.accessState == .restricted { revokedBanner }
                scanStatus
                if model.trips.isEmpty && model.scanPhase == .complete { emptyState }
                if let featured = model.trips.filter(\.isConfirmed).sorted(by: { $0.startDate > $1.startDate }).first {
                    Text("最近的旅程").font(.title2.bold()).padding(.top, 2)
                    NavigationLink(value: featured.id) { MemoryHeroCard(trip: featured) }.buttonStyle(.plain)
                }
                if let anniversary = anniversaryTrip {
                    Text("几年前的今天").font(.title2.bold()).padding(.top, 4)
                    NavigationLink(value: anniversary.id) { ReflectionCard(trip: anniversary, icon: "calendar", caption: "照片替你记得这一天") }.buttonStyle(.plain)
                }
                if let random = randomTrip {
                    Text("随机回忆").font(.title2.bold()).padding(.top, 4)
                    NavigationLink(value: random.id) { ReflectionCard(trip: random, icon: "shuffle", caption: "再看一眼走过的地方") }.buttonStyle(.plain)
                }
                if let featured = confirmedTrips.first {
                    Text("为你生成的回忆卡").font(.title2.bold()).padding(.top, 4)
                    NavigationLink { MemoryCardView(trip: featured) } label: { ReflectionCard(trip: featured, icon: "sparkles.rectangle.stack", caption: "把这段旅程保存下来") }.buttonStyle(.plain)
                }
                if !model.trips.isEmpty {
                    Text(model.trips.contains(where: { !$0.isConfirmed }) ? "新发现与其他旅行" : "其他旅行").font(.title2.bold()).padding(.top, 4)
                }
                ForEach(model.trips.filter { $0.id != model.trips.filter(\.isConfirmed).sorted(by: { $0.startDate > $1.startDate }).first?.id }) { trip in
                    NavigationLink(value: trip.id) { TripDiscoveryCard(trip: trip) }.buttonStyle(.plain)
                }
            }.padding(20)
        }
        .background(ShiLvTheme.paper)
        .navigationDestination(for: UUID.self) { id in
            if let trip = model.trips.first(where: { $0.id == id }) {
                if trip.isConfirmed { TripOverviewView(trip: trip) } else { TripDiscoveryView(trip: trip) }
            }
        }
        .refreshable { await model.scanLibrary() }
    }

    private var confirmedTrips: [DiscoveredTrip] { model.trips.filter(\.isConfirmed).sorted { $0.startDate > $1.startDate } }

    private var anniversaryTrip: DiscoveredTrip? {
        let today = Calendar.current.dateComponents([.month, .day], from: Date())
        return confirmedTrips.first { trip in
            trip.days.contains { day in Calendar.current.dateComponents([.month, .day], from: day.date) == today }
        }
    }

    private var randomTrip: DiscoveredTrip? {
        let candidates = confirmedTrips.filter { $0.id != anniversaryTrip?.id && $0.id != confirmedTrips.first?.id }
        guard !candidates.isEmpty else { return nil }
        let day = Calendar.current.ordinality(of: .day, in: .era, for: Date()) ?? 0
        return candidates[day % candidates.count]
    }

    private var header: some View {
        HStack(alignment: .bottom) {
            VStack(alignment: .leading, spacing: 5) {
                Text("拾旅").font(.system(size: 38, weight: .bold, design: .serif))
                Text("让照片，重新变成旅途").foregroundStyle(ShiLvTheme.muted)
            }
            Spacer()
            Button { Task { await model.scanLibrary() } } label: { Image(systemName: "arrow.clockwise").font(.title3).padding(12).background(.white).clipShape(Circle()) }
                .accessibilityLabel("重新扫描照片库")
        }.padding(.top, 14)
    }

    private var limitedBanner: some View {
        HStack(spacing: 12) {
            Image(systemName: "photo.badge.exclamationmark").foregroundStyle(ShiLvTheme.orange)
            VStack(alignment: .leading, spacing: 3) { Text("当前只能读取部分照片").font(.subheadline.bold()); Text("这是 iOS 的隐私范围，不是拾旅的数量限制；选择“所有照片”才能扫描完整相册").font(.caption).foregroundStyle(ShiLvTheme.muted) }
            Spacer(); Button("选择更多") { model.photoLibrary.presentLimitedLibraryPicker() }.font(.caption.bold())
        }.padding(14).background(ShiLvTheme.orange.opacity(0.09)).clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private var revokedBanner: some View {
        HStack(spacing: 12) {
            Image(systemName: "lock.fill").foregroundStyle(ShiLvTheme.orange)
            VStack(alignment: .leading, spacing: 3) { Text("照片访问已关闭").font(.subheadline.bold()); Text("旅行结构和文字仍在，恢复权限即可查看原始照片").font(.caption).foregroundStyle(ShiLvTheme.muted) }
            Spacer(); Button("去设置") { if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) } }.font(.caption.bold())
        }.padding(14).background(ShiLvTheme.orange.opacity(0.09)).clipShape(RoundedRectangle(cornerRadius: 16))
    }

    @ViewBuilder private var scanStatus: some View {
        if model.scanPhase != .idle && model.scanPhase != .complete {
            VStack(alignment: .leading, spacing: 9) {
                HStack { ProgressView(); Text(model.scanPhase.label).font(.subheadline); Spacer() }
                if let value = model.scanPhase.progress { ProgressView(value: value).tint(ShiLvTheme.orange) }
                Text("只读取时间和地点，不下载 iCloud 原图").font(.caption).foregroundStyle(ShiLvTheme.muted)
            }.padding(16).shilvCard()
        } else if let snapshot = model.store.snapshot {
            HStack {
                Image(systemName: "lock.shield.fill").foregroundStyle(ShiLvTheme.green)
                VStack(alignment: .leading) { Text("已在本机扫描 \(snapshot.accessiblePhotoCount) 张照片").font(.subheadline.bold()); Text("发现 \(model.trips.count) 次可能的旅行").font(.caption).foregroundStyle(ShiLvTheme.muted) }
            }.padding(15).frame(maxWidth: .infinity, alignment: .leading).background(ShiLvTheme.green.opacity(0.10)).clipShape(RoundedRectangle(cornerRadius: 16))
        }
    }

    private var emptyState: some View {
        ContentUnavailableView("还没有发现旅行", systemImage: "map", description: Text("旅行检测依赖照片的时间和 GPS。你可以在相机设置中开启定位，或允许拾旅访问更多照片。"))
            .frame(minHeight: 340)
    }
}

private struct MemoryHeroCard: View {
    let trip: DiscoveredTrip
    var body: some View {
        ZStack(alignment: .bottomLeading) {
            PhotoThumbnail(id: trip.coverPhotoID, height: 430, cornerRadius: 26)
            LinearGradient(colors: [.clear, .black.opacity(0.82)], startPoint: .center, endPoint: .bottom)
                .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
            VStack(alignment: .leading, spacing: 9) {
                Text("\(Calendar.current.component(.month, from: trip.startDate)) 月，你去了").font(.subheadline)
                Text("\(trip.title) \(trip.dayCount) 天").font(.system(size: 32, weight: .bold, design: .serif))
                Text("\(trip.photoCount) 张照片 · \(trip.placeCount) 个地点").font(.subheadline)
                Text("查看旅程  →").font(.subheadline.bold()).padding(.top, 4)
            }.foregroundStyle(.white).padding(24)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("最近的旅程，\(trip.title)，\(trip.dayCount)天")
    }
}

private struct ReflectionCard: View {
    let trip: DiscoveredTrip
    let icon: String
    let caption: String
    var body: some View {
        HStack(spacing: 14) {
            PhotoThumbnail(id: trip.coverPhotoID, height: 120, cornerRadius: 18).frame(width: 138)
            VStack(alignment: .leading, spacing: 7) {
                Label(caption, systemImage: icon).font(.caption).foregroundStyle(ShiLvTheme.orange)
                Text(trip.title).font(.headline)
                Text(trip.dateRangeText).font(.caption).foregroundStyle(ShiLvTheme.muted)
                Text(trip.summary ?? "重新走进这段旅程").font(.caption).lineLimit(2)
            }
            Spacer()
        }.padding(12).shilvCard()
    }
}

private struct TripDiscoveryCard: View {
    let trip: DiscoveredTrip
    var body: some View {
        HStack(spacing: 15) {
            PhotoThumbnail(id: trip.coverPhotoID, height: 112, cornerRadius: 16).frame(width: 132)
            VStack(alignment: .leading, spacing: 7) {
                if !trip.isConfirmed { Label("新发现", systemImage: "sparkles").font(.caption.bold()).foregroundStyle(ShiLvTheme.orange) }
                Text(trip.title).font(.headline)
                Text(trip.dateRangeText).font(.caption).foregroundStyle(ShiLvTheme.muted)
                Text("\(trip.dayCount) 天 · \(trip.photoCount) 张 · \(trip.eventCount) 个事件").font(.caption).foregroundStyle(ShiLvTheme.muted)
            }
            Spacer(); Image(systemName: "chevron.right").font(.caption.bold()).foregroundStyle(ShiLvTheme.muted)
        }.padding(12).shilvCard()
    }
}

private struct DeniedPhotoAccessView: View {
    var body: some View {
        ContentUnavailableView {
            Label("无法访问照片", systemImage: "photo.badge.exclamationmark")
        } description: {
            Text("请前往系统设置，将“照片”权限改为“所有照片”或“部分照片”。")
        } actions: {
            Button("打开系统设置") {
                if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
            }.buttonStyle(.borderedProminent).tint(ShiLvTheme.ink)
        }.background(ShiLvTheme.paper)
    }
}
