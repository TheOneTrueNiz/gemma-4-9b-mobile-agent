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
            "app/build.gradle.kts",
            "app/src/main/AndroidManifest.xml",
            "app/src/main/java/dev/niz/gemmalauncher/MainActivity.kt",
            "app/src/main/java/dev/niz/gemmalauncher/LauncherUi.kt",
            "app/src/main/java/dev/niz/gemmalauncher/BackendClient.kt",
            "app/src/main/java/dev/niz/gemmalauncher/LauncherModels.kt",
            "app/src/main/java/dev/niz/gemmalauncher/LauncherResolver.kt",
            "app/src/main/java/dev/niz/gemmalauncher/LauncherUsageStore.kt",
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

    def test_launcher_targets_local_backend(self):
        backend_client = (ROOT / "app/src/main/java/dev/niz/gemmalauncher/BackendClient.kt").read_text()
        self.assertIn("http://127.0.0.1:1337/chat", backend_client)
        self.assertIn("android.intent.category.HOME", (ROOT / "app/src/main/AndroidManifest.xml").read_text())

    def test_launcher_ui_has_app_search_and_dock(self):
        ui = (ROOT / "app/src/main/java/dev/niz/gemmalauncher/LauncherUi.kt").read_text()
        self.assertIn("Search apps", ui)
        self.assertIn("LauncherDock", ui)
        self.assertIn("OverlaySheet.Apps", ui)
        self.assertIn("Recent Apps", ui)
        self.assertIn("resolveHomeIntent", ui)
        self.assertIn("rankAppsForQuery", ui)

    def test_launcher_uses_real_json_client_and_icons(self):
        backend_client = (ROOT / "app/src/main/java/dev/niz/gemmalauncher/BackendClient.kt").read_text()
        activity = (ROOT / "app/src/main/java/dev/niz/gemmalauncher/MainActivity.kt").read_text()
        self.assertIn("JSONObject", backend_client)
        self.assertIn("getIcon(0)", activity)

    def test_launcher_has_resolver_and_usage_store(self):
        resolver = (ROOT / "app/src/main/java/dev/niz/gemmalauncher/LauncherResolver.kt").read_text()
        usage_store = (ROOT / "app/src/main/java/dev/niz/gemmalauncher/LauncherUsageStore.kt").read_text()
        self.assertIn('listOf("open ", "launch ", "start ")', resolver)
        self.assertIn("resolveHomeIntent", resolver)
        self.assertIn("rankAppsForQuery", resolver)
        self.assertIn("getSharedPreferences", usage_store)
        self.assertIn("recordLaunch", usage_store)


if __name__ == "__main__":
    unittest.main()
