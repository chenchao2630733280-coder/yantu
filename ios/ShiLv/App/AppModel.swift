import SwiftUI
import Photos
import UIKit

@MainActor
final class AppModel: ObservableObject {
    @Published var scanPhase: ScanPhase = .idle
    @Published var selectedTripID: UUID?
    @Published var presentedError: String?
    @Published private(set) var accessState: PhotoAccessState
    @Published private(set) var dataRevision = 0
    @Published var analysisProgress: (current: Int, total: Int)?
    @Published private(set) var thumbnailCacheSize: Int64 = 0
    @Published private(set) var isResettingIndex = false

    let photoLibrary: PhotoLibraryService
    let store: TripStore
    private let detector: TripDetector
    private let placeNames = PlaceNameService()
    private var activeScan: (id: UUID, task: Task<Void, Never>)?
    private var scanRequestedWhileBusy = false
    private var scanNeedsResume = false
    private var libraryChangeTask: Task<Void, Never>?

    init(photoLibrary: PhotoLibraryService = .shared, store: TripStore = TripStore(), detector: TripDetector = TripDetector()) {
        self.photoLibrary = photoLibrary
        self.store = store
        self.detector = detector
        self.accessState = photoLibrary.accessState
        photoLibrary.setChangeHandler { [weak self] in
            guard let self,
                  UIApplication.shared.applicationState == .active,
                  self.photoLibrary.accessState == .full || self.photoLibrary.accessState == .limited else { return }
            self.scheduleLibraryChangeScan()
        }
    }

    private func scheduleLibraryChangeScan() {
        libraryChangeTask?.cancel()
        libraryChangeTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 1_500_000_000)
            guard !Task.isCancelled, let self else { return }
            await self.scanLibrary()
        }
    }

    var trips: [DiscoveredTrip] { store.snapshot?.trips.filter { !$0.isHidden } ?? [] }
    func start() async {
        photoLibrary.refreshAccessState()
        accessState = photoLibrary.accessState
        if (accessState == .full || accessState == .limited), store.snapshot == nil {
            await scanLibrary()
        }
        await refreshStorageUsage()
    }

    func requestAndScan() async {
        guard scanPhase != .requestingPermission else { return }
        scanPhase = .requestingPermission
        let state = await photoLibrary.requestAccess()
        accessState = state
        guard state == .full || state == .limited else {
            scanPhase = .idle
            return
        }
        scanPhase = .idle
        await scanLibrary()
    }

    func scanLibrary() async {
        photoLibrary.refreshAccessState()
        accessState = photoLibrary.accessState
        guard accessState == .full || accessState == .limited else { scanPhase = .idle; return }
        if let active = activeScan {
            scanRequestedWhileBusy = true
            await active.task.value
            if activeScan?.id == active.id { activeScan = nil }
            return
        }
        let scanID = UUID()
        let task = Task { @MainActor [weak self] in
            guard let self else { return }
            await self.performScanLoop()
        }
        activeScan = (scanID, task)
        await task.value
        if activeScan?.id == scanID { activeScan = nil }
    }

    private func performScanLoop() async {
        repeat {
            scanRequestedWhileBusy = false
            await performSingleScan()
        } while scanRequestedWhileBusy && !Task.isCancelled && (accessState == .full || accessState == .limited)
    }

    private func performSingleScan() async {
        do {
            let records = await photoLibrary.fetchMetadata { [weak self] current, total in
                self?.scanPhase = .readingMetadata(current: current, total: total)
            }
            guard !Task.isCancelled else { scanPhase = .idle; return }
            photoLibrary.refreshAccessState()
            accessState = photoLibrary.accessState
            guard accessState == .full || accessState == .limited else { scanPhase = .idle; return }
            scanPhase = .detectingTrips
            let worker = Task.detached(priority: .userInitiated) { [detector] in
                detector.detect(from: records)
            }
            let snapshot = await withTaskCancellationHandler(operation: { await worker.value }, onCancel: { worker.cancel() })
            guard !Task.isCancelled else { scanPhase = .idle; return }
            scanPhase = .saving
            try store.replace(with: snapshot)
            dataRevision += 1
            scanPhase = .complete
        } catch {
            scanPhase = .failed("扫描失败，请稍后重试")
            presentedError = error.localizedDescription
        }
    }

    private var isScanning: Bool {
        switch scanPhase {
        case .requestingPermission, .readingMetadata, .detectingTrips, .saving: return true
        case .idle, .complete, .failed: return false
        }
    }

    func cancelActiveScan() {
        scanNeedsResume = isScanning
        scanRequestedWhileBusy = false
        libraryChangeTask?.cancel()
        activeScan?.task.cancel()
        if isScanning { scanPhase = .idle }
    }

    func resumeInterruptedScanIfNeeded() async {
        photoLibrary.refreshAccessState()
        accessState = photoLibrary.accessState
        guard accessState == .full || accessState == .limited else { scanNeedsResume = false; return }
        guard scanNeedsResume else { return }
        if let active = activeScan {
            await active.task.value
            if activeScan?.id == active.id { activeScan = nil }
        }
        scanNeedsResume = false
        await scanLibrary()
    }

    func update(_ trip: DiscoveredTrip) {
        var reconciled = trip
        reconciled.reconcileDerivedFields()
        do { try store.updateTrip(reconciled); dataRevision += 1 }
        catch { presentedError = "无法保存这次修改：\(error.localizedDescription)" }
    }

    func confirmAndAnalyze(_ trip: DiscoveredTrip) async -> DiscoveredTrip {
        analysisProgress = (0, max(1, trip.eventCount))
        let enriched = await MemoryAnalysisService(photoLibrary: photoLibrary).enrich(trip) { [weak self] current, total in
            self?.analysisProgress = (current, total)
        }
        guard !Task.isCancelled else { analysisProgress = nil; return trip }
        update(enriched)
        analysisProgress = nil
        return enriched
    }

    func resetIndex() async {
        guard !isResettingIndex else { return }
        isResettingIndex = true
        defer { isResettingIndex = false }
        cancelActiveScan()
        scanNeedsResume = false
        if let active = activeScan {
            await active.task.value
            if activeScan?.id == active.id { activeScan = nil }
        }
        do { try store.deleteLocalIndex(); dataRevision += 1; scanPhase = .idle }
        catch { presentedError = "无法删除本机索引：\(error.localizedDescription)" }
    }

    func refreshStorageUsage() async { thumbnailCacheSize = await photoLibrary.thumbnailCacheSize() }

    func clearThumbnailCache() async {
        do { try await photoLibrary.clearThumbnailCache(); await refreshStorageUsage() }
        catch { presentedError = "无法清理缩略图缓存：\(error.localizedDescription)" }
    }

    func resolveLocation(_ name: String) async -> GeoPoint? { await placeNames.locate(name) }

    func refreshAccessState() {
        photoLibrary.refreshAccessState()
        accessState = photoLibrary.accessState
    }
}
