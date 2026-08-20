import UIKit
import NodysseyShell

/// The whole of the Swift in this app.
///
/// `MainActivity` is the Android counterpart and it is 76 lines; this is thirty, and the difference
/// is not that iOS does less — it is that everything `MainActivity` does beyond installing a
/// composition is about intents, and nothing here has any yet. When notifications and Universal Links
/// arrive (step D4 and after), this is where they land.
@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        configurationForConnecting session: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        UISceneConfiguration(name: "Default", sessionRole: session.role)
    }
}

/// Where the window is made, which since iOS 26 is the only place it may be made.
///
/// The pre-scene shape — an app delegate that builds its own `UIWindow` in
/// `didFinishLaunchingWithOptions` — no longer merely warns: `UIKit` traps at launch in
/// `_UIApplicationEvaluateRuntimeIssueForNoSceneLifecycleAdoption` unless `Info.plist` declares a
/// `UIApplicationSceneManifest` and something adopts it. That trap is what the first run of this
/// target did, and it looks exactly like an app that will not start.
///
/// The two-step root is `NodysseyApp.start`'s doing: the app's `User-Agent` has to be the one WebKit
/// sends, WebKit will only say what that is asynchronously, and building the graph around a guess
/// would mean throwing away a `cf_clearance` the moment the real answer arrived. So the window comes
/// up empty for the few milliseconds that takes — which is what a launch screen is for.
class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?

    func scene(
        _ scene: UIScene,
        willConnectTo session: UISceneSession,
        options connectionOptions: UIScene.ConnectionOptions
    ) {
        guard let windowScene = scene as? UIWindowScene else { return }
        let window = UIWindow(windowScene: windowScene)
        self.window = window
        window.rootViewController = UIViewController()
        window.makeKeyAndVisible()

        NodysseyApp.shared.start { controller in
            window.rootViewController = controller
        }
    }
}
