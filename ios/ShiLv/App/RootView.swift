import SwiftUI

struct RootView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        TabView {
            NavigationStack { DiscoveryView() }
                .tabItem { Label("回忆", systemImage: "photo.on.rectangle.angled") }
            NavigationStack { AllTripsTimelineView() }
                .tabItem { Label("时间线", systemImage: "calendar") }
            NavigationStack { SettingsView() }
                .tabItem { Label("我", systemImage: "person") }
        }
        .tint(ShiLvTheme.ink)
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { Task { await model.resumeInterruptedScanIfNeeded() } }
            else { model.cancelActiveScan() }
        }
    }
}
