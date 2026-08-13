import SwiftUI

struct PhotoPermissionView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        VStack(spacing: 26) {
            Spacer()
            ZStack {
                Circle().fill(ShiLvTheme.orange.opacity(0.12)).frame(width: 112, height: 112)
                Image(systemName: "photo.stack").font(.system(size: 42)).foregroundStyle(ShiLvTheme.orange)
            }
            VStack(spacing: 12) {
                Text("让照片，重新变成旅途").font(.system(size: 30, weight: .bold, design: .serif))
                Text("拾旅会读取照片中的时间和地点，自动发现旅行，并整理成旅行故事。")
                    .font(.body).foregroundStyle(ShiLvTheme.muted).multilineTextAlignment(.center).lineSpacing(5)
            }
            VStack(alignment: .leading, spacing: 14) {
                Label("你的照片不会被复制到拾旅", systemImage: "lock.shield.fill")
                Label("地点名称由 Apple 系统服务按需解析", systemImage: "map")
                Label("全部照片：扫描整个照片库，发现完整旅程", systemImage: "checkmark.circle.fill")
                Label("部分照片：只分析你允许的照片", systemImage: "circle.lefthalf.filled")
                Label("随时可在系统设置中更改权限", systemImage: "gearshape")
            }.font(.subheadline).foregroundStyle(ShiLvTheme.ink)
            Button {
                Task { await model.requestAndScan() }
            } label: {
                Text("开始发现我的旅行").frame(maxWidth: .infinity).padding(.vertical, 16)
            }
            .buttonStyle(.borderedProminent).tint(ShiLvTheme.ink).clipShape(RoundedRectangle(cornerRadius: 16))
            Text("你可以先退出，准备好后再回来；拾旅不会在后台读取照片。")
                .font(.footnote).foregroundStyle(ShiLvTheme.muted).multilineTextAlignment(.center)
            Spacer()
        }
        .padding(28)
        .background(ShiLvTheme.paper.ignoresSafeArea())
    }
}
