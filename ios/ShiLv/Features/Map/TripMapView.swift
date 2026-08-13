import SwiftUI
@preconcurrency import MapKit

struct TripMapView: View {
    @EnvironmentObject private var model: AppModel
    private let seedTrip: DiscoveredTrip
    @State private var selectedEventID: UUID?
    @State private var selectedDayID: UUID?
    @State private var position: MapCameraPosition

    private var events: [MemoryEvent] {
        let days = selectedDayID.flatMap { id in trip.days.first(where: { $0.id == id }).map { [$0] } } ?? trip.days
        return days.flatMap(\.events).filter { !$0.isHidden && $0.location != nil }
    }
    private var markerEvents: [MemoryEvent] {
        let important = events.sorted { $0.photoCount > $1.photoCount }.prefix(12)
        return important.sorted { $0.startDate < $1.startDate }
    }
    private var trip: DiscoveredTrip { model.store.snapshot?.trips.first(where: { $0.id == seedTrip.id }) ?? seedTrip }

    init(trip: DiscoveredTrip, initialEventID: UUID? = nil) {
        self.seedTrip = trip
        _selectedEventID = State(initialValue: initialEventID)
        _selectedDayID = State(initialValue: initialEventID.flatMap { eventID in trip.days.first(where: { $0.events.contains(where: { $0.id == eventID }) })?.id })
        if let center = trip.center {
            _position = State(initialValue: .region(MKCoordinateRegion(center: center.coordinate, span: MKCoordinateSpan(latitudeDelta: 1.4, longitudeDelta: 1.4))))
        } else { _position = State(initialValue: .automatic) }
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            Map(position: $position) {
                ForEach(markerEvents) { event in
                    if let location = event.location {
                        Annotation(event.placeName ?? event.title, coordinate: location.coordinate) {
                            Button { selectedEventID = event.id } label: { PhotoThumbnail(id: event.coverPhotoID, height: 48, cornerRadius: 24) }
                                .frame(width: 48).clipShape(Circle()).overlay(Circle().stroke(.white, lineWidth: 3)).shadow(radius: 4)
                                .accessibilityLabel("地图地点，\(event.placeName ?? event.title)，\(event.photoCount)张照片")
                        }
                    }
                }
                if events.count > 1 {
                    MapPolyline(coordinates: events.compactMap { $0.location?.coordinate }).stroke(ShiLvTheme.orange.opacity(0.75), lineWidth: 3)
                }
            }.mapStyle(.standard(elevation: .realistic)).ignoresSafeArea(edges: .bottom)
            VStack { dayPicker; Spacer() }.padding(.top, 8)
            if let event = events.first(where: { $0.id == selectedEventID }) {
                NavigationLink { mapEventDestination(event) } label: {
                    HStack(spacing: 13) {
                        PhotoThumbnail(id: event.coverPhotoID, height: 88, cornerRadius: 14).frame(width: 105)
                        VStack(alignment: .leading, spacing: 6) {
                            Text(event.startDate.formatted(date: .abbreviated, time: .shortened)).font(.caption).foregroundStyle(ShiLvTheme.orange)
                            Text(event.placeName ?? event.title).font(.headline)
                            Text("停留 \(event.duration.durationMapText) · \(event.photoCount) 张照片").font(.caption).foregroundStyle(ShiLvTheme.muted)
                            HStack(spacing: 4) {
                                ForEach(event.visiblePhotoIDs.prefix(4), id: \.self) { id in
                                    PhotoThumbnail(id: id, height: 34, cornerRadius: 6).frame(width: 42)
                                }
                            }
                        }
                        Spacer(); Image(systemName: "chevron.right")
                    }.padding(14).shilvCard().padding()
                }.buttonStyle(.plain)
            } else {
                Text("点一个位置，回到那段记忆").font(.caption).padding(.horizontal, 16).padding(.vertical, 9).background(.regularMaterial).clipShape(Capsule()).padding(.bottom, 20)
            }
        }.navigationTitle(trip.title).navigationBarTitleDisplayMode(.inline)
        .onAppear { focusSelectedEvent() }
        .onChange(of: selectedEventID) { _, _ in focusSelectedEvent() }
    }

    private var dayPicker: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                Button("全部") { selectedDayID = nil }.buttonStyle(.borderedProminent).tint(selectedDayID == nil ? ShiLvTheme.ink : ShiLvTheme.muted)
                ForEach(Array(trip.days.enumerated()), id: \.element.id) { index, day in
                    Button("Day \(index + 1)") { withAnimation(.easeInOut(duration: 0.25)) { selectedDayID = day.id; selectedEventID = nil } }
                        .buttonStyle(.borderedProminent).tint(selectedDayID == day.id ? ShiLvTheme.ink : ShiLvTheme.muted)
                }
            }.padding(.horizontal)
        }
    }

    @ViewBuilder private func mapEventDestination(_ event: MemoryEvent) -> some View {
        if let day = trip.days.first(where: { $0.events.contains(where: { $0.id == event.id }) }) {
            EventDetailView(tripID: trip.id, dayID: day.id, eventID: event.id)
        }
    }

    private func focusSelectedEvent() {
        guard let event = events.first(where: { $0.id == selectedEventID }), let location = event.location else { return }
        withAnimation(.easeInOut(duration: 0.35)) {
            position = .region(MKCoordinateRegion(center: location.coordinate, span: MKCoordinateSpan(latitudeDelta: 0.08, longitudeDelta: 0.08)))
        }
    }
}

private extension TimeInterval {
    var durationMapText: String { let minutes = max(1, Int(self / 60)); return minutes >= 60 ? "\(minutes / 60).\((minutes % 60) / 6) 小时" : "\(minutes) 分钟" }
}
