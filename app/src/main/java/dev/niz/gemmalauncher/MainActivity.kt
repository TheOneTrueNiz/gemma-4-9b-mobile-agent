package dev.niz.gemmalauncher

import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                LauncherApp(
                    appSource = { loadLaunchableApps() },
                    launchApp = { entry -> launchApp(entry) }
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
}

private fun LauncherActivityInfo.toEntry(): LauncherEntry {
    return LauncherEntry(
        label = label?.toString() ?: applicationInfo.packageName,
        packageName = applicationInfo.packageName,
        icon = getIcon(0)
    )
}
