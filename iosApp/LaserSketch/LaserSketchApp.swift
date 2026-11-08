import SwiftUI
import LaserSketchUI

@main
struct LaserSketchApp: App {
    var body: some Scene {
        WindowGroup {
            SharedApplication().ignoresSafeArea()
        }
    }
}

private struct SharedApplication: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
