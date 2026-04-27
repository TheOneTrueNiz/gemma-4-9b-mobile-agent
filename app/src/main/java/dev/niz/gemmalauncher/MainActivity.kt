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
        val usageStore = LauncherUsageStore(this)
        setContent {
            MaterialTheme {
                LauncherApp(
                    appSource = { loadLaunchableApps() },
                    usageStore = usageStore,
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
    val resolvedLabel = label?.toString() ?: applicationInfo.packageName
    return LauncherEntry(
        label = resolvedLabel,
        packageName = applicationInfo.packageName,
        category = inferLauncherCategory(resolvedLabel, applicationInfo.packageName),
        icon = getIcon(0)
    )
}
