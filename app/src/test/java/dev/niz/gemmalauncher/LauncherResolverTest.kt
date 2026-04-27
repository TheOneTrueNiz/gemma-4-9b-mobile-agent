package dev.niz.gemmalauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherResolverTest {
    private val apps = listOf(
        LauncherEntry(label = "Settings", packageName = "com.android.settings"),
        LauncherEntry(label = "Chrome", packageName = "com.android.chrome"),
        LauncherEntry(label = "Camera", packageName = "com.android.camera"),
        LauncherEntry(label = "Maps", packageName = "com.google.android.apps.maps"),
        LauncherEntry(label = "Messages", packageName = "com.google.android.apps.messaging"),
    )

    private val usage = LauncherUsageSnapshot(
        launchCounts = mapOf("com.android.chrome" to 4),
        recentPackages = listOf("com.android.chrome"),
        pinnedPackages = listOf("com.android.settings"),
    )

    @Test
    fun openBrowserAliasLaunchesChrome() {
        val resolution = resolveHomeIntent("open browser", apps, usage)
        assertTrue(resolution is HomeIntentResolution.LaunchApp)
        assertEquals("com.android.chrome", (resolution as HomeIntentResolution.LaunchApp).entry.packageName)
    }

    @Test
    fun openWifiResolvesToNativeAction() {
        val resolution = resolveHomeIntent("open wifi", apps, usage)
        assertTrue(resolution is HomeIntentResolution.LaunchNativeAction)
        assertEquals(NativeLauncherAction.Wifi, (resolution as HomeIntentResolution.LaunchNativeAction).action)
    }

    @Test
    fun showNotificationsResolvesToNativeAction() {
        val resolution = resolveHomeIntent("show notifications", apps, usage)
        assertTrue(resolution is HomeIntentResolution.LaunchNativeAction)
        assertEquals(NativeLauncherAction.Notifications, (resolution as HomeIntentResolution.LaunchNativeAction).action)
    }

    @Test
    fun openCameraUsesNativeIntentPath() {
        val resolution = resolveHomeIntent("open camera", apps, usage)
        assertTrue(resolution is HomeIntentResolution.LaunchNativeAction)
        assertEquals(NativeLauncherAction.Camera, (resolution as HomeIntentResolution.LaunchNativeAction).action)
    }

    @Test
    fun typoStillRanksSettingsFirst() {
        val ranked = rankAppsForQuery("setings", apps, usage)
        assertEquals("com.android.settings", ranked.first().packageName)
    }

    @Test
    fun searchAppsPrefixOpensDrawerWithSuggestions() {
        val resolution = resolveHomeIntent("search apps maps", apps, usage)
        assertTrue(resolution is HomeIntentResolution.OpenDrawer)
        resolution as HomeIntentResolution.OpenDrawer
        assertEquals("maps", resolution.query)
        assertEquals("com.google.android.apps.maps", resolution.suggestions.first().packageName)
    }

    @Test
    fun lowConfidenceBareQueryFallsBackToAgent() {
        val resolution = resolveHomeIntent("battery", apps, usage)
        assertTrue(resolution is HomeIntentResolution.SendToAgent)
        assertEquals("battery", (resolution as HomeIntentResolution.SendToAgent).message)
    }

    @Test
    fun nativeActionRankingSurfacesWifiFromPrefix() {
        val ranked = rankNativeLauncherActions("wi")
        assertEquals(NativeLauncherAction.Wifi, ranked.first())
    }

    @Test
    fun nativeActionRankingSurfacesNotificationsFromPrefix() {
        val ranked = rankNativeLauncherActions("notif")
        assertEquals(NativeLauncherAction.Notifications, ranked.first())
    }
}
