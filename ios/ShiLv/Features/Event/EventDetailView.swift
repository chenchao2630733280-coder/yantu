import SwiftUI

struct EventDetailView: View {
    @EnvironmentObject private var model: AppModel
    let tripID: UUID; let dayID: UUID; let eventID: UUID
    @State private var event: MemoryEvent?
    @State private var note = ""
    @State private var title = ""
    @State private var editing = false
    @State private var editingFacts = false
    @State private var draftPlace = ""
    @State private var draftStart = Date()
    @State private var draftEnd = Date()
    @State private var showAllPhotos = false
    @State private var selectedPhotoID: String?
    @State private var confirmRemovePhoto = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            if let event {
                VStack(alignment: .leading, spacing: 18) {
                    photoGallery(event)
                    VStack(alignment: .leading, spacing: 8) {
                        Text("\(DateFormatter.timeLabel.string(from: event.startDate)) – \(DateFormatter.timeLabel.string(from: event.endDate))").font(.caption.bold()).foregroundStyle(ShiLvTheme.orange)
                        Text(event.placeName ?? event.title).font(.title.bold())
                        if event.cityName != nil || event.countryName != nil {
                            Text([event.cityName, event.countryName].compactMap { $0 }.joined(separator: " · ")).font(.subheadline).foregroundStyle(ShiLvTheme.muted)
                        }
                        Text("停留约 \(durationText(event.duration)) · \(event.photoCount) 张照片").foregroundStyle(ShiLvTheme.muted)
                        if let summary = event.summary { Text(summary).font(.system(size: 18, design: .serif)).lineSpacing(5).padding(.top, 7) }
                    }
                    .padding(.horizontal, 20)
                    if !showAllPhotos && event.photoCount > event.visiblePhotoIDs.count {
                        Button("查看全部 \(event.photoCount) 张") { showAllPhotos = true; selectedPhotoID = event.visiblePhotoIDs.first }.font(.subheadline.bold()).padding(.horizontal, 20)
                    }
                    VStack(alignment: .leading, spacing: 12) {
                        Text("这一段记忆").font(.headline)
                        Text(event.note.isEmpty ? "照片记住了你去过哪里。写下一句话，留下照片不知道的部分。" : "“\(event.note)”")
                            .font(.system(size: 20, design: .serif)).lineSpacing(6)
                        if editing {
                            TextField("事件名称", text: $title).textFieldStyle(.roundedBorder)
                            TextEditor(text: $note).frame(minHeight: 120).padding(8).background(ShiLvTheme.paper).clipShape(RoundedRectangle(cornerRadius: 12))
                            HStack { Button("取消") { editing = false }; Spacer(); Button("保存记忆") { save() }.buttonStyle(.borderedProminent).tint(ShiLvTheme.ink) }
                        } else {
                            Button(event.note.isEmpty ? "＋ 记下一句话" : "修改这段记忆") { title = event.title; note = event.note; editing = true }.buttonStyle(.bordered)
                        }
                    }.padding(18).shilvCard()
                    .padding(.horizontal, 20)
                    factActions(event)
                }.padding(.bottom, 30)
            }
        }
        .background(ShiLvTheme.paper).navigationBarTitleDisplayMode(.inline).onAppear { load() }
        .sheet(isPresented: $editingFacts) { factEditor }
        .confirmationDialog("处理这个事件", isPresented: $confirmRemovePhoto, titleVisibility: .visible) {
            if let selectedPhotoID, event?.photoIDs.count ?? 0 > 1 {
                Button("移除当前照片", role: .destructive) { removePhoto(selectedPhotoID) }
                if canSplit { Button("从当前照片开始新事件") { splitAtCurrentPhoto() } }
            }
            Button("隐藏这个事件", role: .destructive) { hide() }
            Button("删除这一段记录", role: .destructive) { deleteEvent() }
            Button("取消", role: .cancel) { }
        }
    }

    private func photoGallery(_ event: MemoryEvent) -> some View {
        let ids = showAllPhotos ? event.photoIDs : event.visiblePhotoIDs
        return ZStack(alignment: .topTrailing) {
            TabView(selection: $selectedPhotoID) {
                ForEach(ids, id: \.self) { id in
                    PhotoThumbnail(id: id, height: 430, cornerRadius: 0).tag(Optional(id))
                        .accessibilityHint("左右滑动查看这个事件的照片")
                }
            }.frame(height: 430).tabViewStyle(.page(indexDisplayMode: .never))
            Text("\((ids.firstIndex(of: selectedPhotoID ?? ids.first ?? "") ?? 0) + 1) / \(ids.count)")
                .font(.caption.bold()).foregroundStyle(.white).padding(.horizontal, 11).padding(.vertical, 7)
                .background(.black.opacity(0.55)).clipShape(Capsule()).padding(16)
        }
    }

    private func factActions(_ event: MemoryEvent) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("纠正这段记忆").font(.headline)
            HStack {
                Button("地点不对") { prepareFacts(event) }.buttonStyle(.bordered)
                Button("时间不对") { prepareFacts(event) }.buttonStyle(.bordered)
            }
            if hasNextEvent { Button("与下一事件合并") { mergeWithNext() }.buttonStyle(.bordered) }
            if event.location != nil, let trip = model.store.snapshot?.trips.first(where: { $0.id == tripID }) {
                NavigationLink { TripMapView(trip: trip, initialEventID: event.id) } label: { Label("在记忆地图中查看", systemImage: "map") }.buttonStyle(.bordered)
            }
            if let selectedPhotoID {
                Button("设为代表照片") { mutate { $0.coverPhotoIDOverride = selectedPhotoID }; load() }.buttonStyle(.bordered)
                Button(event.featuredPhotoIDs?.contains(selectedPhotoID) == true ? "取消精选当前照片" : "精选当前照片") { toggleFeatured(selectedPhotoID) }.buttonStyle(.bordered)
            }
            Button("更多", systemImage: "ellipsis") { confirmRemovePhoto = true }.font(.subheadline)
        }.padding(18).shilvCard().padding(.horizontal, 20)
    }

    private var factEditor: some View {
        NavigationStack {
            Form {
                Section("地点") { TextField("地点未知", text: $draftPlace) }
                Section("时间") {
                    DatePicker("开始", selection: $draftStart)
                    DatePicker("结束", selection: $draftEnd, in: draftStart...)
                }
            }
            .navigationTitle("纠正事实")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("取消") { editingFacts = false } }
                ToolbarItem(placement: .confirmationAction) { Button("保存") { Task { await saveFacts() } } }
            }
        }.presentationDetents([.medium, .large])
    }

    private func load() {
        event = locate(); note = event?.note ?? ""; title = event?.title ?? ""
        if selectedPhotoID == nil { selectedPhotoID = event?.visiblePhotoIDs.first }
    }
    private func locate() -> MemoryEvent? { model.store.snapshot?.trips.first(where: { $0.id == tripID })?.days.first(where: { $0.id == dayID })?.events.first(where: { $0.id == eventID }) }
    private func save() {
        mutate {
            let trimmedTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
            $0.title = trimmedTitle.isEmpty ? $0.title : String(trimmedTitle.prefix(60))
            $0.note = String(note.trimmingCharacters(in: .whitespacesAndNewlines).prefix(500))
        }
        editing = false; load()
    }
    private func hide() {
        guard var trip = model.store.snapshot?.trips.first(where: { $0.id == tripID }), let dayIndex = trip.days.firstIndex(where: { $0.id == dayID }), let eventIndex = trip.days[dayIndex].events.firstIndex(where: { $0.id == eventID }) else { return }
        trip.days[dayIndex].events[eventIndex].isHidden = true
        trip.suppressedEventIDs = Array(Set((trip.suppressedEventIDs ?? []) + [eventID]))
        model.update(trip); dismiss()
    }
    private func prepareFacts(_ event: MemoryEvent) {
        draftPlace = event.placeName ?? ""; draftStart = event.startDate; draftEnd = event.endDate; editingFacts = true
    }
    private func saveFacts() async {
        guard var trip = model.store.snapshot?.trips.first(where: { $0.id == tripID }), let sourceDay = trip.days.firstIndex(where: { $0.id == dayID }), let sourceEvent = trip.days[sourceDay].events.firstIndex(where: { $0.id == eventID }) else { return }
        let trimmed = String(draftPlace.trimmingCharacters(in: .whitespacesAndNewlines).prefix(100))
        var edited = trip.days[sourceDay].events.remove(at: sourceEvent)
        let sourceDate = trip.days[sourceDay].date.startOfDay
        if !trimmed.isEmpty, trimmed != edited.placeName { edited.location = await model.resolveLocation(trimmed) ?? edited.location }
        edited.placeName = trimmed.isEmpty ? nil : trimmed
        if trimmed != event?.placeName { edited.cityName = nil; edited.countryName = nil }
        edited.startDate = draftStart; edited.endDate = max(draftStart, draftEnd)
        let targetDate = edited.startDate.startOfDay
        if let targetDay = trip.days.firstIndex(where: { $0.date.startOfDay == targetDate }) {
            trip.days[targetDay].events.append(edited); trip.days[targetDay].events.sort { $0.startDate < $1.startDate }
        } else {
            trip.days.append(TravelDay(id: UUID(), date: targetDate, title: "补充的一天", events: [edited])); trip.days.sort { $0.date < $1.date }
        }
        model.update(trip); editingFacts = false
        if targetDate != sourceDate { dismiss() } else { load() }
    }
    private var hasNextEvent: Bool {
        guard let events = model.store.snapshot?.trips.first(where: { $0.id == tripID })?.days.first(where: { $0.id == dayID })?.events,
              let index = events.firstIndex(where: { $0.id == eventID }) else { return false }
        return index < events.count - 1
    }
    private var canSplit: Bool {
        guard let event, let selectedPhotoID, let index = event.photoIDs.firstIndex(of: selectedPhotoID) else { return false }
        return index > 0 && index < event.photoIDs.count
    }
    private func mergeWithNext() {
        guard var trip = model.store.snapshot?.trips.first(where: { $0.id == tripID }), let dayIndex = trip.days.firstIndex(where: { $0.id == dayID }), let index = trip.days[dayIndex].events.firstIndex(where: { $0.id == eventID }), index < trip.days[dayIndex].events.count - 1 else { return }
        let next = trip.days[dayIndex].events[index + 1]
        trip.suppressedEventIDs = Array(Set((trip.suppressedEventIDs ?? []) + [next.id]))
        trip.days[dayIndex].events[index].endDate = max(trip.days[dayIndex].events[index].endDate, next.endDate)
        let existingPhotoIDs = trip.days[dayIndex].events[index].photoIDs
        trip.days[dayIndex].events[index].photoIDs += next.photoIDs.filter { !existingPhotoIDs.contains($0) }
        trip.days[dayIndex].events[index].excludedPhotoIDs?.removeAll { next.photoIDs.contains($0) }
        let selected = (trip.days[dayIndex].events[index].featuredPhotoIDs ?? []) + (next.featuredPhotoIDs ?? [])
        trip.days[dayIndex].events[index].featuredPhotoIDs = Array(selected.prefix(5))
        if !next.note.isEmpty { trip.days[dayIndex].events[index].note = [trip.days[dayIndex].events[index].note, next.note].filter { !$0.isEmpty }.joined(separator: "；") }
        trip.days[dayIndex].events.remove(at: index + 1)
        model.update(trip); load()
    }
    private func removePhoto(_ id: String) {
        mutate { value in
            value.photoIDs.removeAll { $0 == id }
            value.excludedPhotoIDs = Array(Set((value.excludedPhotoIDs ?? []) + [id]))
            value.featuredPhotoIDs?.removeAll { $0 == id }
            if value.coverPhotoIDOverride == id { value.coverPhotoIDOverride = nil }
        }
        selectedPhotoID = locate()?.visiblePhotoIDs.first; load()
    }
    private func splitAtCurrentPhoto() {
        guard var trip = model.store.snapshot?.trips.first(where: { $0.id == tripID }), let dayIndex = trip.days.firstIndex(where: { $0.id == dayID }), let eventIndex = trip.days[dayIndex].events.firstIndex(where: { $0.id == eventID }), let selectedPhotoID, let splitIndex = trip.days[dayIndex].events[eventIndex].photoIDs.firstIndex(of: selectedPhotoID), splitIndex > 0 else { return }
        var first = trip.days[dayIndex].events[eventIndex]
        let firstIDs = Array(first.photoIDs[..<splitIndex])
        let secondIDs = Array(first.photoIDs[splitIndex...])
        let fraction = Double(splitIndex) / Double(first.photoIDs.count)
        let splitDate = first.startDate.addingTimeInterval(max(60, first.duration * fraction))
        first.photoIDs = firstIDs
        first.endDate = splitDate.addingTimeInterval(-1)
        first.excludedPhotoIDs = Array(Set((first.excludedPhotoIDs ?? []) + secondIDs))
        first.featuredPhotoIDs = first.featuredPhotoIDs?.filter(firstIDs.contains)
        if !firstIDs.contains(first.coverPhotoIDOverride ?? "") { first.coverPhotoIDOverride = nil }
        let second = MemoryEvent(
            id: UUID(), title: "\(first.title) · 后半段", startDate: splitDate, endDate: max(splitDate, event?.endDate ?? splitDate),
            photoIDs: secondIDs, location: first.location, placeName: first.placeName, cityName: first.cityName, countryName: first.countryName,
            note: "", isHidden: false, summary: first.summary, featuredPhotoIDs: Array(secondIDs.prefix(5)), coverPhotoIDOverride: secondIDs.first,
            excludedPhotoIDs: firstIDs, isUserCreated: true
        )
        trip.days[dayIndex].events[eventIndex] = first
        trip.days[dayIndex].events.insert(second, at: eventIndex + 1)
        model.update(trip); load()
    }
    private func deleteEvent() {
        guard var trip = model.store.snapshot?.trips.first(where: { $0.id == tripID }), let dayIndex = trip.days.firstIndex(where: { $0.id == dayID }), let eventIndex = trip.days[dayIndex].events.firstIndex(where: { $0.id == eventID }) else { return }
        let removed = trip.days[dayIndex].events.remove(at: eventIndex)
        if removed.isUserCreated != true { trip.suppressedEventIDs = Array(Set((trip.suppressedEventIDs ?? []) + [removed.id])) }
        model.update(trip); dismiss()
    }
    private func toggleFeatured(_ id: String) {
        if let featured = event?.featuredPhotoIDs, featured.count == 1, featured.contains(id) {
            model.presentedError = "每个事件至少保留一张精选照片"
            return
        }
        mutate { value in
            var featured = value.featuredPhotoIDs ?? value.visiblePhotoIDs
            if featured.contains(id) { featured.removeAll { $0 == id } }
            else { featured = Array(([id] + featured.filter { $0 != id }).prefix(5)) }
            value.featuredPhotoIDs = featured
        }
        load()
    }
    private func mutate(_ update: (inout MemoryEvent) -> Void) {
        guard var trip = model.store.snapshot?.trips.first(where: { $0.id == tripID }), let dayIndex = trip.days.firstIndex(where: { $0.id == dayID }), let eventIndex = trip.days[dayIndex].events.firstIndex(where: { $0.id == eventID }) else { return }
        update(&trip.days[dayIndex].events[eventIndex]); model.update(trip)
    }
    private func durationText(_ duration: TimeInterval) -> String { let minutes = max(1, Int(duration / 60)); return minutes >= 60 ? "\(minutes / 60) 小时 \(minutes % 60) 分" : "\(minutes) 分钟" }
}
