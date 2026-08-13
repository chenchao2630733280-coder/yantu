import SwiftUI

@main
struct ShiLvApp: App {
    @StateObject private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .task { await model.start() }
                .alert("出了点问题", isPresented: Binding(
                    get: { model.presentedError != nil },
                    set: { if !$0 { model.presentedError = nil } }
                )) {
                    Button("知道了", role: .cancel) { model.presentedError = nil }
                } message: { Text(model.presentedError ?? "") }
        }
    }
}
