import SwiftUI

struct AllTripsTimelineView: View {
    @EnvironmentObject private var model: AppModel
    private var grouped: [TripYearGroup] {
        Dictionary(grouping: model.trips.filter(\.isConfirmed)) { Calendar.current.component(.year, from: $0.startDate) }
            .map { TripYearGroup(year: $0.key, trips: $0.value.sorted { $0.startDate > $1.startDate }) }.sorted { $0.year > $1.year }
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 22) {
                VStack(alignment: .leading, spacing: 5) { Text("时间线").font(.system(size: 38, weight: .bold, design: .serif)); Text("照片替你记得每一次出发").foregroundStyle(ShiLvTheme.muted) }.padding(.top, 16)
                if grouped.isEmpty { ContentUnavailableView("还没有旅行", systemImage: "calendar", description: Text("完成照片库扫描后，旅行会按年份出现在这里。")) }
                ForEach(grouped) { group in
                    Text(String(group.year)).font(.system(size: 38, weight: .bold, design: .serif))
                    ForEach(group.trips) { trip in
                        NavigationLink { TripOverviewView(trip: trip) } label: { TimelineTripCard(trip: trip) }.buttonStyle(.plain)
                    }
                }
            }.padding(.horizontal, 20).padding(.bottom, 30)
        }.background(ShiLvTheme.paper).navigationBarHidden(true)
    }
}

private struct TripYearGroup: Identifiable {
    let year: Int
    let trips: [DiscoveredTrip]
    var id: Int { year }
}

private struct TimelineTripCard: View {
    let trip: DiscoveredTrip
    var body: some View {
        HStack(spacing: 14) {
            Circle().fill(ShiLvTheme.orange).frame(width: 10, height: 10)
            PhotoThumbnail(id: trip.coverPhotoID, height: 105, cornerRadius: 15).frame(width: 125)
            VStack(alignment: .leading, spacing: 6) { Text(trip.title).font(.headline); Text(trip.dateRangeText).font(.caption).foregroundStyle(ShiLvTheme.muted); Text("\(trip.dayCount) 天 · \(trip.photoCount) 张照片").font(.caption).foregroundStyle(ShiLvTheme.muted) }
            Spacer()
        }.padding(12).shilvCard()
    }
}
