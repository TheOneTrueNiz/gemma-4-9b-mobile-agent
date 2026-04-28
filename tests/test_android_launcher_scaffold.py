from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]


class AndroidLauncherScaffoldTests(unittest.TestCase):
    def test_gradle_project_files_exist(self):
        for rel in [
            "settings.gradle.kts",
            "build.gradle.kts",
            "gradle.properties",
            "gradlew",
            "gradlew.bat",
            "gradle/wrapper/gradle-wrapper.jar",
            "gradle/wrapper/gradle-wrapper.properties",
            "app/build.gradle.kts",
            "app/src/main/AndroidManifest.xml",
            "app/src/main/java/dev/niz/gemmalauncher/MainActivity.kt",
            "app/src/main/java/dev/niz/gemmalauncher/LauncherUi.kt",
            "app/src/main/java/dev/niz/gemmalauncher/BackendClient.kt",
            "app/src/main/java/dev/niz/gemmalauncher/LauncherModels.kt",
            "app/src/main/java/dev/niz/gemmalauncher/LauncherCatalog.kt",
            "app/src/main/java/dev/niz/gemmalauncher/LauncherNativeActions.kt",
            "app/src/main/java/dev/niz/gemmalauncher/LauncherResolver.kt",
            "app/src/main/java/dev/niz/gemmalauncher/TermuxBridge.kt",
            "app/src/main/java/dev/niz/gemmalauncher/LauncherUsageStore.kt",
            "app/src/test/java/dev/niz/gemmalauncher/BackendClientTest.kt",
            "app/src/test/java/dev/niz/gemmalauncher/LauncherCatalogTest.kt",
            "app/src/test/java/dev/niz/gemmalauncher/LauncherResolverTest.kt",
            "app/src/test/java/dev/niz/gemmalauncher/TermuxBridgeTest.kt",
            "tools/check_android_launcher_env.sh",
            "tools/bootstrap_android_sdk.sh",
            "tools/build_android_launcher.sh",
            "tools/install_android_launcher.sh",
            "tools/configure_termux_bridge.sh",
            "tools/start_backend_from_launcher.sh",
        ]:
            self.assertTrue((ROOT / rel).exists(), rel)

    def test_manifest_declares_launcher_home_activity(self):
        manifest_path = ROOT / "app/src/main/AndroidManifest.xml"
        root = ET.fromstring(manifest_path.read_text())
        ns = {"android": "http://schemas.android.com/apk/res/android"}

        categories = []
        for category in root.findall(".//category"):
            name = category.attrib.get("{http://schemas.android.com/apk/res/android}name")
            if name:
                categories.append(name)

        self.assertIn("android.intent.category.HOME", categories)
        self.assertIn("android.intent.category.DEFAULT", categories)
        self.assertIn("android.intent.category.LAUNCHER", categories)

        permissions = [
            item.attrib.get("{http://schemas.android.com/apk/res/android}name")
            for item in root.findall("uses-permission")
        ]
        self.assertIn("android.permission.INTERNET", permissions)
        self.assertIn("com.termux.permission.RUN_COMMAND", permissions)

        manifest_text = manifest_path.read_text()
        self.assertIn('<package android:name="com.termux" />', manifest_text)

    def test_launcher_targets_local_backend(self):
        backend_client = (ROOT / "app/src/main/java/dev/niz/gemmalauncher/BackendClient.kt").read_text()
        self.assertIn('private const val BACKEND_BASE_URL = "http://127.0.0.1:1337"', backend_client)
        self.assertIn('private const val BACKEND_CHAT_URL = "$BACKEND_BASE_URL/chat"', backend_client)
        self.assertIn('private const val BACKEND_HEALTH_URL = "$BACKEND_BASE_URL/health"', backend_client)
        self.assertIn("fetchBackendStatus", backend_client)
        self.assertIn("android.intent.category.HOME", (ROOT / "app/src/main/AndroidManifest.xml").read_text())

    def test_launcher_ui_has_app_search_and_dock(self):
        ui = (ROOT / "app/src/main/java/dev/niz/gemmalauncher/LauncherUi.kt").read_text()
        self.assertIn("Search apps", ui)
        self.assertIn("LauncherDock", ui)
        self.assertIn("DockApp", ui)
        self.assertIn("BestMatchCard", ui)
        self.assertIn("CategorySectionHeader", ui)
        self.assertIn("QuickActionRow", ui)
        self.assertIn("RecentActivityCard", ui)
        self.assertIn("DecisionCard", ui)
        self.assertIn("BackendStatusCard", ui)
        self.assertIn("HomeInputSuggestionRow", ui)
        self.assertIn("Pinned Apps", ui)
        self.assertIn("Best Match", ui)
        self.assertIn("Other Matches", ui)
        self.assertIn("App Match", ui)
        self.assertIn("System Action", ui)
        self.assertIn("Gemma Online", ui)
        self.assertIn("Gemma Offline", ui)
        self.assertIn("Actor Ready", ui)
        self.assertIn("Actor Down", ui)
        self.assertIn("Quick Actions", ui)
        self.assertIn("Recent Activity", ui)
        self.assertIn("Recent Decisions", ui)
        self.assertIn("Refresh Link", ui)
        self.assertIn("Start Gemma", ui)
        self.assertIn("Restart Gemma", ui)
        self.assertIn("Grant Termux Permission", ui)
        self.assertIn("Launcher Settings", ui)
        self.assertIn("Termux Settings", ui)
        self.assertIn("Termux Overlay", ui)
        self.assertIn("drawing over apps", ui)
        self.assertIn("Search / Launch", ui)
        self.assertIn("Ask Gemma", ui)
        self.assertIn("Gemma is thinking...", ui)
        self.assertIn("BubbleRouteChip", ui)
        self.assertIn("Explicit Gemma request", ui)
        self.assertIn("Deterministic tool route", ui)
        self.assertIn("allow-external-apps = true", ui)
        self.assertIn("configure_termux_bridge.sh", ui)
        self.assertIn("Last: $lastRoute", ui)
        self.assertIn("Backend offline. Search apps or launch locally", ui)
        self.assertIn("Search apps or ask Gemma", ui)
        self.assertIn("OverlaySheet.Apps", ui)
        self.assertIn("Recent Apps", ui)
        self.assertIn("resolveHomeIntent", ui)
        self.assertIn("rankAppsForQuery", ui)

    def test_launcher_uses_real_json_client_and_icons(self):
        backend_client = (ROOT / "app/src/main/java/dev/niz/gemmalauncher/BackendClient.kt").read_text()
        activity = (ROOT / "app/src/main/java/dev/niz/gemmalauncher/MainActivity.kt").read_text()
        termux_bridge = (ROOT / "app/src/main/java/dev/niz/gemmalauncher/TermuxBridge.kt").read_text()
        self.assertIn("JSONObject", backend_client)
        self.assertIn("getIcon(0)", activity)
        self.assertIn("inferLauncherCategory", activity)
        self.assertIn("launchNativeAction", activity)
        self.assertIn("buildBackendControlIntent", activity)
        self.assertIn("registerForActivityResult", activity)
        self.assertIn("TERMUX_RUN_COMMAND_PERMISSION", activity)
        self.assertIn("ACTION_APPLICATION_DETAILS_SETTINGS", activity)
        self.assertIn("ACTION_MANAGE_OVERLAY_PERMISSION", activity)
        self.assertIn("buildTermuxBridgeStatus", termux_bridge)
        self.assertIn("GEMMA_BACKEND_START_SCRIPT", termux_bridge)
        self.assertIn("com.termux.RUN_COMMAND", termux_bridge)
        self.assertIn("Settings.Panel.ACTION_INTERNET_CONNECTIVITY", activity)
        self.assertIn("MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA", activity)

    def test_launcher_has_resolver_and_usage_store(self):
        catalog = (ROOT / "app/src/main/java/dev/niz/gemmalauncher/LauncherCatalog.kt").read_text()
        native_actions = (ROOT / "app/src/main/java/dev/niz/gemmalauncher/LauncherNativeActions.kt").read_text()
        resolver = (ROOT / "app/src/main/java/dev/niz/gemmalauncher/LauncherResolver.kt").read_text()
        usage_store = (ROOT / "app/src/main/java/dev/niz/gemmalauncher/LauncherUsageStore.kt").read_text()
        self.assertIn('listOf("open ", "launch ", "start ")', resolver)
        self.assertIn('listOf(', resolver)
        self.assertIn('find app ', resolver)
        self.assertIn("fuzzyScore", resolver)
        self.assertIn("aliasTerms", resolver)
        self.assertIn("resolveNativeLauncherAction", resolver)
        self.assertIn("rankNativeLauncherActions", native_actions)
        self.assertIn("LaunchNativeAction", resolver)
        self.assertIn("resolveHomeIntent", resolver)
        self.assertIn("rankAppsForQuery", resolver)
        self.assertIn("inferLauncherCategory", catalog)
        self.assertIn("buildCategorySections", catalog)
        self.assertIn("LauncherCategory", catalog)
        self.assertIn("NativeLauncherAction", native_actions)
        self.assertIn("openingMessage", native_actions)
        self.assertIn("Settings", native_actions)
        self.assertIn("Camera", native_actions)
        self.assertIn("getSharedPreferences", usage_store)
        self.assertIn("recordLaunch", usage_store)
        self.assertIn("recordNativeAction", usage_store)
        self.assertIn("recordDecision", usage_store)
        self.assertIn("recent_activity", usage_store)
        self.assertIn("recent_decisions", usage_store)
        self.assertIn("togglePinned", usage_store)

    def test_wrapper_and_build_scripts_are_wired(self):
        wrapper = (ROOT / "gradle/wrapper/gradle-wrapper.properties").read_text()
        env_script = (ROOT / "tools/check_android_launcher_env.sh").read_text()
        sdk_bootstrap = (ROOT / "tools/bootstrap_android_sdk.sh").read_text()
        build_script = (ROOT / "tools/build_android_launcher.sh").read_text()
        configure_script = (ROOT / "tools/configure_termux_bridge.sh").read_text()
        install_script = (ROOT / "tools/install_android_launcher.sh").read_text()
        start_script = (ROOT / "tools/start_backend_from_launcher.sh").read_text()
        self.assertIn("gradle-8.7-bin.zip", wrapper)
        self.assertIn("distributionSha256Sum", wrapper)
        self.assertIn("JDK 17", env_script)
        self.assertIn("host-native aapt2", env_script)
        self.assertIn("apt-get install aapt", env_script)
        self.assertIn("commandlinetools-linux-14742923_latest.zip", sdk_bootstrap)
        self.assertIn('"platforms;android-34"', sdk_bootstrap)
        self.assertIn('"build-tools;34.0.0"', sdk_bootstrap)
        self.assertIn(':app:assembleDebug', build_script)
        self.assertIn("android.aapt2FromMavenOverride", build_script)
        self.assertIn("allow-external-apps = true", configure_script)
        self.assertIn("termux-reload-settings", configure_script)
        self.assertIn("android.intent.action.VIEW", install_script)
        self.assertIn("application/vnd.android.package-archive", install_script)
        self.assertIn("am start --user 0", install_script)
        self.assertIn("termux-open", install_script)
        self.assertIn("Gemma Launcher", install_script)
        self.assertIn("backend/main.py", start_script)
        self.assertIn("actor_online", start_script)
        self.assertIn("--restart", start_script)
        self.assertIn("127.0.0.1:1337/health", start_script)


if __name__ == "__main__":
    unittest.main()
