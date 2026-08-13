import SwiftUI
import Photos
import UIKit
import UniformTypeIdentifiers

struct SettingsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var showReset = false
    @State private var showExporter = false
    @State private var confirmExport = false
    @State private var exportDocument = TripExportDocument(snapshot: nil)
    @AppStorage("shareIncludesMemories") private var shareIncludesMemories = true

    var body: some View {
        List {
            Section {
                HStack(spacing: 15) { Text("旅").font(.system(size: 26, weight: .bold, design: .serif)).foregroundStyle(.white).frame(width: 62, height: 62).background(ShiLvTheme.ink).clipShape(Circle()); VStack(alignment: .leading) { Text("我的拾旅").font(.headline); Text("记忆索引只保存在这台设备").font(.caption).foregroundStyle(ShiLvTheme.muted) } }
            }
            Section("照片与隐私") {
                LabeledContent("照片权限", value: accessLabel)
                if model.accessState == .limited { Button("选择更多照片") { model.photoLibrary.presentLimitedLibraryPicker() } }
                Button("打开系统照片权限") {
                    if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                }
                NavigationLink("照片如何被使用") { PrivacyExplanationView() }
            }
            Section("本机数据") {
                LabeledContent("已扫描", value: "\(model.store.snapshot?.accessiblePhotoCount ?? 0) 张")
                LabeledContent("已发现旅行", value: "\(model.trips.count) 次")
                LabeledContent("缩略图缓存", value: ByteCountFormatter.string(fromByteCount: model.thumbnailCacheSize, countStyle: .file))
                LabeledContent("旅行数据", value: ByteCountFormatter.string(fromByteCount: model.store.storedDataSize, countStyle: .file))
                LabeledContent("总占用", value: ByteCountFormatter.string(fromByteCount: model.thumbnailCacheSize + model.store.storedDataSize, countStyle: .file))
                Button("清理缩略图缓存") { Task { await model.clearThumbnailCache() } }.disabled(model.thumbnailCacheSize == 0)
                Button(model.accessState == .full ? "重新扫描整个照片库" : "重新扫描已授权照片") { Task { await model.scanLibrary() } }
                Button("导出旅行数据") { confirmExport = true }
                    .disabled(model.store.snapshot == nil)
                Button(model.isResettingIndex ? "正在删除……" : "删除本机旅行索引", role: .destructive) { showReset = true }
                    .disabled(model.isResettingIndex)
            }
            Section("分享设置") {
                Toggle("回忆卡包含我的补充记忆", isOn: $shareIncludesMemories)
                Text("关闭后，保存和分享的回忆卡不会带上你写下的话。").font(.caption).foregroundStyle(ShiLvTheme.muted)
            }
            Section("关于") { LabeledContent("版本", value: "1.0.0"); Text("拾旅不会修改或删除系统照片，不会将原图、位置或记忆发送到拾旅服务器，也不使用广告跟踪。确认旅行或纠正地点时，有限坐标或地点词会交给 Apple 系统地理编码服务解析。") }
        }
        .navigationTitle("我")
        .task { await model.refreshStorageUsage() }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in
            model.photoLibrary.refreshAccessState()
            model.refreshAccessState()
        }
        .alert("删除本机旅行索引？", isPresented: $showReset) {
            Button("取消", role: .cancel) { }
            Button("删除", role: .destructive) { Task { await model.resetIndex() } }
        } message: { Text("这不会删除系统照片。旅行确认、名称和补充记忆会从拾旅中移除。") }
        .fileExporter(isPresented: $showExporter, document: exportDocument, contentType: .json, defaultFilename: "拾旅-旅行数据") { result in
            if case let .failure(error) = result { model.presentedError = "导出失败：\(error.localizedDescription)" }
        }
        .alert("导出旅行数据？", isPresented: $confirmExport) {
            Button("取消", role: .cancel) { }
            Button("继续导出") { exportDocument = TripExportDocument(snapshot: model.store.snapshot); showExporter = true }
        } message: {
            Text("导出文件包含旅行时间、地点坐标、照片本机引用和你补充的记忆。请只保存到你信任的位置。文件不包含照片原图。")
        }
    }

    private var accessLabel: String {
        switch model.accessState { case .full: return "所有照片"; case .limited: return "部分照片"; case .denied: return "已拒绝"; case .restricted: return "受限制"; case .notDetermined: return "未设置" }
    }
}

private struct TripExportDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    let data: Data

    init(snapshot: ScanSnapshot?) {
        let encoder = JSONEncoder(); encoder.dateEncodingStrategy = .iso8601; encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        if let snapshot, let encoded = try? encoder.encode(snapshot) { data = encoded }
        else { data = Data("{\"trips\":[]}".utf8) }
    }

    init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }
}

private struct PrivacyExplanationView: View {
    var body: some View {
        List {
            Section("读取") { Label("照片拍摄时间", systemImage: "clock"); Label("照片位置（如果相机曾记录）", systemImage: "location"); Label("照片本机标识和尺寸", systemImage: "number") }
            Section("扫描范围") { Text("选择“所有照片”时，拾旅通过 PhotoKit 枚举完整授权图库，不存在 9 张上限。选择“部分照片”时，iOS 只允许拾旅读取你指定的照片。") }
            Section("不做") { Label("不修改或删除系统照片", systemImage: "hand.raised"); Label("不在后台偷偷扫描", systemImage: "eye.slash"); Label("不把原图、位置或记忆发送到拾旅服务器", systemImage: "icloud.slash") }
            Section("地点名称") { Text("确认旅行或手动纠正地点时，拾旅使用 Apple 系统地理编码服务把有限坐标或地点词转换为可读名称。该服务可能需要网络。") }
            Section("存储") { Text("旅行索引以完整文件保护写入 App 的 Application Support 目录。卸载 App 或使用“删除本机旅行索引”即可清除。") }
        }.navigationTitle("照片使用说明")
    }
}
