package dev.niz.gemmalauncher

import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val usageStore = LauncherUsageStore(this)
        setContent {
            MaterialTheme {
                LauncherApp(
                    appSource = { loadLaunchableApps() },
                    usageStore = usageStore,
                    launchApp = { entry -> launchApp(entry) },
                    launchNativeAction = { action -> launchNativeAction(action) }
                )
            }
        }
    }

    private fun loadLaunchableApps(): List<LauncherEntry> {
        val launcherApps = getSystemService(LauncherApps::class.java) ?: return emptyList()
        val profiles = launcherApps.profiles ?: return emptyList()
        return profiles
            .flatMap { user -> launcherApps.getActivityList(null, user).map { it.toEntry() } }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun launchApp(entry: LauncherEntry) {
        val intent = packageManager.getLaunchIntentForPackage(entry.packageName) ?: return
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun launchNativeAction(action: NativeLauncherAction) {
        val intent = when (action) {
            NativeLauncherAction.Settings -> Intent(Settings.ACTION_SETTINGS)
            NativeLauncherAction.Notifications -> Intent("android.settings.ALL_APPS_NOTIFICATION_SETTINGS")
            NativeLauncherAction.Wifi -> Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            NativeLauncherAction.Internet -> Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            NativeLauncherAction.Bluetooth -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            NativeLauncherAction.Display -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            NativeLauncherAction.Sound -> Intent(Settings.Panel.ACTION_VOLUME)
            NativeLauncherAction.Battery -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            NativeLauncherAction.Camera -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        }
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun LauncherActivityInfo.toEntry(): LauncherEntry {
    val resolvedLabel = label?.toString() ?: applicationInfo.packageName
    return LauncherEntry(
        label = resolvedLabel,
        packageName = applicationInfo.packageName,
        category = inferLauncherCategory(resolvedLabel, applicationInfo.packageName),
        icon = getIcon(0)
    )
}
