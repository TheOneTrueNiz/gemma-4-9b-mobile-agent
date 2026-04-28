package dev.niz.gemmalauncher

import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private var termuxBridgeStatus by mutableStateOf(TermuxBridgeStatus())
    private val backendStopHandler = Handler(Looper.getMainLooper())
    private val backendStopRunnable = Runnable {
        dispatchBackendControl(BackendControlAction.Stop, refreshStatusAfter = false)
    }

    private val runCommandPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            termuxBridgeStatus = buildTermuxBridgeStatus(
                termuxInstalled = isPackageInstalled(TERMUX_PACKAGE_NAME),
                runCommandPermissionGranted = granted,
                detailOverride = if (granted) {
                    "Termux Run Command access granted."
                } else {
                    "Grant Termux Run Command access so the launcher can start Gemma automatically."
                }
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val usageStore = LauncherUsageStore(this)
        refreshTermuxBridgeStatus()
        setContent {
            MaterialTheme {
                LauncherApp(
                    appSource = { loadLaunchableApps() },
                    usageStore = usageStore,
                    termuxBridgeStatus = termuxBridgeStatus,
                    refreshTermuxBridgeStatus = { refreshTermuxBridgeStatus() },
                    requestTermuxPermission = { requestTermuxRunCommandPermission() },
                    openLauncherSettings = { openLauncherSettings() },
                    openTermuxSettings = { openTermuxSettings() },
                    openTermuxOverlaySettings = { openTermuxOverlaySettings() },
                    controlBackend = { restart ->
                        dispatchBackendControl(
                            if (restart) BackendControlAction.Restart else BackendControlAction.Start
                        )
                    },
                    launchApp = { entry -> launchApp(entry) },
                    launchNativeAction = { action -> launchNativeAction(action) }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        backendStopHandler.removeCallbacks(backendStopRunnable)
        maybeStartBackendForLauncher()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            backendStopHandler.removeCallbacks(backendStopRunnable)
            backendStopHandler.postDelayed(backendStopRunnable, BACKEND_STOP_DELAY_MS)
        }
    }

    override fun onDestroy() {
        backendStopHandler.removeCallbacks(backendStopRunnable)
        if (isFinishing) {
            dispatchBackendControl(BackendControlAction.Stop, refreshStatusAfter = false)
        }
        super.onDestroy()
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

    private fun refreshTermuxBridgeStatus(detailOverride: String? = null) {
        val termuxInstalled = isPackageInstalled(TERMUX_PACKAGE_NAME)
        val permissionGranted = termuxInstalled &&
            checkSelfPermission(TERMUX_RUN_COMMAND_PERMISSION) == PackageManager.PERMISSION_GRANTED
        termuxBridgeStatus = buildTermuxBridgeStatus(
            termuxInstalled = termuxInstalled,
            runCommandPermissionGranted = permissionGranted,
            detailOverride = detailOverride,
        )
    }

    private fun requestTermuxRunCommandPermission() {
        if (!isPackageInstalled(TERMUX_PACKAGE_NAME)) {
            refreshTermuxBridgeStatus("Termux is not installed.")
            return
        }
        runCommandPermissionLauncher.launch(TERMUX_RUN_COMMAND_PERMISSION)
    }

    private fun openLauncherSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun openTermuxSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", TERMUX_PACKAGE_NAME, null)
        )
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun openTermuxOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$TERMUX_PACKAGE_NAME")
        )
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun maybeStartBackendForLauncher() {
        refreshTermuxBridgeStatus()
        if (termuxBridgeStatus.canDispatchCommands) {
            dispatchBackendControl(BackendControlAction.Start, refreshStatusAfter = false)
        }
    }

    private fun dispatchBackendControl(
        action: BackendControlAction,
        refreshStatusAfter: Boolean = true,
    ): String {
        refreshTermuxBridgeStatus()
        if (!termuxBridgeStatus.termuxInstalled) {
            return termuxBridgeStatus.detail
        }
        if (!termuxBridgeStatus.runCommandPermissionGranted) {
            return termuxBridgeStatus.detail
        }

        return try {
            val component = startService(buildBackendControlIntent(action))
            val detail = if (component != null) {
                when (action) {
                    BackendControlAction.Start -> "Sent start request to Termux."
                    BackendControlAction.Restart -> "Sent restart request to Termux."
                    BackendControlAction.Stop -> "Sent stop request to Termux."
                }
            } else {
                "Termux did not accept the backend control request."
            }
            if (refreshStatusAfter) {
                refreshTermuxBridgeStatus(detail)
            }
            detail
        } catch (error: SecurityException) {
            val detail = "Launcher cannot control Termux yet: ${error.message ?: "permission denied"}"
            if (refreshStatusAfter) {
                refreshTermuxBridgeStatus(detail)
            }
            detail
        } catch (error: Exception) {
            val detail = "Failed to send backend control request: ${error.message ?: "unknown error"}"
            if (refreshStatusAfter) {
                refreshTermuxBridgeStatus(detail)
            }
            detail
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }
}

private const val BACKEND_STOP_DELAY_MS = 15_000L

private fun LauncherActivityInfo.toEntry(): LauncherEntry {
    val resolvedLabel = label?.toString() ?: applicationInfo.packageName
    return LauncherEntry(
        label = resolvedLabel,
        packageName = applicationInfo.packageName,
        category = inferLauncherCategory(resolvedLabel, applicationInfo.packageName),
        icon = getIcon(0)
    )
}
