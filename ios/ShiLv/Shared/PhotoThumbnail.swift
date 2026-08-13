import SwiftUI
import UIKit

struct PhotoThumbnail: View {
    let id: String?
    var height: CGFloat = 120
    var cornerRadius: CGFloat = 16
    @State private var image: UIImage?
    @State private var finishedLoading = false

    var body: some View {
        Group {
            if let image { Image(uiImage: image).resizable().scaledToFill() }
            else {
                LinearGradient(colors: [ShiLvTheme.line, ShiLvTheme.paper], startPoint: .topLeading, endPoint: .bottomTrailing)
                    .overlay {
                        if finishedLoading {
                            VStack(spacing: 7) {
                                Image(systemName: PhotoLibraryService.shared.accessState == .denied ? "lock.fill" : "photo").font(.title2)
                                Text(PhotoLibraryService.shared.accessState == .denied ? "恢复照片访问权限即可查看" : "这张照片已不在系统相册中")
                                    .font(.caption2).multilineTextAlignment(.center).padding(.horizontal, 12)
                            }.foregroundStyle(ShiLvTheme.muted.opacity(0.72))
                        } else { ProgressView().tint(ShiLvTheme.muted) }
                    }
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: height)
        .clipped()
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        .task(id: id) {
            image = nil; finishedLoading = false
            guard let id else { finishedLoading = true; return }
            let scale = UIScreen.main.scale
            image = await PhotoLibraryService.shared.requestImage(id: id, targetSize: CGSize(width: 500 * scale, height: height * scale))
            guard !Task.isCancelled else { return }
            finishedLoading = true
        }
        .accessibilityLabel(image == nil ? "照片暂时无法显示" : "旅行照片")
        .onChange(of: id) { _, _ in image = nil; finishedLoading = false }
    }
}
