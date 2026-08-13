@preconcurrency import Photos
import UIKit
import Combine

@MainActor
final class PhotoLibraryService: NSObject, ObservableObject, PHPhotoLibraryChangeObserver {
    static let shared = PhotoLibraryService()

    @Published private(set) var accessState: PhotoAccessState = .notDetermined
    private let imageManager = PHCachingImageManager()
    private let thumbnailCache = ThumbnailCache()
    private var changeHandler: (() -> Void)?

    override private init() {
        super.init()
        refreshAccessState()
        PHPhotoLibrary.shared().register(self)
    }

    deinit { PHPhotoLibrary.shared().unregisterChangeObserver(self) }

    func requestAccess() async -> PhotoAccessState {
        let status = await PHPhotoLibrary.requestAuthorization(for: .readWrite)
        let state = Self.map(status)
        accessState = state
        return state
    }

    func refreshAccessState() {
        accessState = Self.map(PHPhotoLibrary.authorizationStatus(for: .readWrite))
    }

    func setChangeHandler(_ handler: @escaping () -> Void) {
        changeHandler = handler
    }

    func presentLimitedLibraryPicker() {
        guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let root = scene.keyWindow?.rootViewController else { return }
        PHPhotoLibrary.shared().presentLimitedLibraryPicker(from: root)
    }

    func fetchMetadata(progress: @escaping @MainActor @Sendable (Int, Int) -> Void) async -> [PhotoRecord] {
        guard accessState == .full || accessState == .limited else { return [] }
        let worker = Task.detached(priority: .userInitiated) {
            let options = PHFetchOptions()
            options.predicate = NSPredicate(format: "mediaType == %d", PHAssetMediaType.image.rawValue)
            options.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: true)]
            let result = PHAsset.fetchAssets(with: options)
            var records: [PhotoRecord] = []
            records.reserveCapacity(result.count)

            for index in 0..<result.count {
                if Task.isCancelled { return records }
                autoreleasepool {
                    let asset = result.object(at: index)
                    if let date = asset.creationDate {
                        let point = asset.location.map { GeoPoint(latitude: $0.coordinate.latitude, longitude: $0.coordinate.longitude) }
                        records.append(PhotoRecord(
                            id: asset.localIdentifier,
                            creationDate: date,
                            location: point,
                            pixelWidth: asset.pixelWidth,
                            pixelHeight: asset.pixelHeight,
                            isFavorite: asset.isFavorite,
                            isScreenshot: asset.mediaSubtypes.contains(.photoScreenshot)
                        ))
                    }
                }
                if index % 250 == 0 || index == result.count - 1 {
                    await progress(index + 1, result.count)
                    await Task.yield()
                }
            }
            return records
        }
        return await withTaskCancellationHandler(operation: { await worker.value }, onCancel: { worker.cancel() })
    }

    func requestImage(id: String, targetSize: CGSize, contentMode: PHImageContentMode = .aspectFill) async -> UIImage? {
        let cacheKey = ThumbnailCache.key(id: id, targetSize: targetSize, contentMode: contentMode)
        let cacheGeneration = await thumbnailCache.currentGeneration()
        guard let asset = PHAsset.fetchAssets(withLocalIdentifiers: [id], options: nil).firstObject else { return nil }
        if let data = await thumbnailCache.data(for: cacheKey), let image = UIImage(data: data) { return image }
        let requestState = ImageRequestState()
        let image: UIImage? = await withTaskCancellationHandler(operation: {
            await withCheckedContinuation { continuation in
                let options = PHImageRequestOptions()
                options.deliveryMode = .highQualityFormat
                options.resizeMode = .fast
                options.isNetworkAccessAllowed = true
                let gate = ContinuationGate()
                let requestID = imageManager.requestImage(for: asset, targetSize: targetSize, contentMode: contentMode, options: options) { image, info in
                    let error = info?[PHImageErrorKey] as? Error
                    let cancelled = (info?[PHImageCancelledKey] as? Bool) ?? false
                    let degraded = (info?[PHImageResultIsDegradedKey] as? Bool) ?? false
                    if error != nil || cancelled { if gate.claim() { continuation.resume(returning: nil) } }
                    else if !degraded, gate.claim() { continuation.resume(returning: image) }
                }
                if requestState.register(requestID) { imageManager.cancelImageRequest(requestID) }
            }
        }, onCancel: {
            if let requestID = requestState.requestCancellation() {
                Task { @MainActor in PhotoLibraryService.shared.cancelImageRequest(requestID) }
            }
        })
        guard !Task.isCancelled else { return nil }
        if let image, max(targetSize.width, targetSize.height) <= 1_600, let data = image.jpegData(compressionQuality: 0.82) {
            await thumbnailCache.store(data, for: cacheKey, generation: cacheGeneration)
        }
        return image
    }

    func thumbnailCacheSize() async -> Int64 { await thumbnailCache.size() }
    func clearThumbnailCache() async throws { try await thumbnailCache.clear() }

    private func cancelImageRequest(_ requestID: PHImageRequestID) { imageManager.cancelImageRequest(requestID) }

    nonisolated func photoLibraryDidChange(_ changeInstance: PHChange) {
        Task { @MainActor in self.changeHandler?() }
    }

    private static func map(_ status: PHAuthorizationStatus) -> PhotoAccessState {
        switch status {
        case .authorized: return .full
        case .limited: return .limited
        case .denied: return .denied
        case .restricted: return .restricted
        case .notDetermined: return .notDetermined
        @unknown default: return .restricted
        }
    }
}

private actor ThumbnailCache {
    private let directory: URL
    private var generation = 0

    init(fileManager: FileManager = .default) {
        let root = fileManager.urls(for: .cachesDirectory, in: .userDomainMask).first ?? fileManager.temporaryDirectory
        directory = root.appending(path: "ShiLvThumbnails", directoryHint: .isDirectory)
        try? fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
    }

    static func key(id: String, targetSize: CGSize, contentMode: PHImageContentMode) -> String {
        var hash: UInt64 = 14_695_981_039_346_656_037
        for byte in "\(id)-\(Int(targetSize.width))-\(Int(targetSize.height))-\(contentMode.rawValue)".utf8 {
            hash = (hash ^ UInt64(byte)) &* 1_099_511_628_211
        }
        return String(hash, radix: 16) + ".jpg"
    }

    func data(for key: String) -> Data? { try? Data(contentsOf: directory.appending(path: key)) }

    func currentGeneration() -> Int { generation }

    func store(_ data: Data, for key: String, generation requestGeneration: Int) {
        guard requestGeneration == generation else { return }
        try? data.write(to: directory.appending(path: key), options: [.atomic, .completeFileProtection])
    }

    func size() -> Int64 {
        let fileManager = FileManager.default
        let keys: [URLResourceKey] = [.fileSizeKey, .isRegularFileKey]
        let files = (try? fileManager.contentsOfDirectory(at: directory, includingPropertiesForKeys: keys)) ?? []
        return files.reduce(0) { total, url in
            let values = try? url.resourceValues(forKeys: Set(keys))
            return total + Int64(values?.isRegularFile == true ? values?.fileSize ?? 0 : 0)
        }
    }

    func clear() throws {
        generation += 1
        let fileManager = FileManager.default
        let files = try fileManager.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil)
        for file in files { try fileManager.removeItem(at: file) }
    }
}

private final class ContinuationGate: @unchecked Sendable {
    private let lock = NSLock()
    private var available = true

    func claim() -> Bool {
        lock.lock(); defer { lock.unlock() }
        guard available else { return false }
        available = false
        return true
    }
}

private final class ImageRequestState: @unchecked Sendable {
    private let lock = NSLock()
    private var requestID: PHImageRequestID?
    private var cancellationRequested = false

    func register(_ requestID: PHImageRequestID) -> Bool {
        lock.lock(); defer { lock.unlock() }
        self.requestID = requestID
        return cancellationRequested
    }

    func requestCancellation() -> PHImageRequestID? {
        lock.lock(); defer { lock.unlock() }
        cancellationRequested = true
        return requestID
    }
}

private extension UIWindowScene {
    var keyWindow: UIWindow? { windows.first(where: { $0.isKeyWindow }) }
}
