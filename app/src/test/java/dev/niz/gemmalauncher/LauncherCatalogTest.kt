package dev.niz.gemmalauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherCatalogTest {
    @Test
    fun inferLauncherCategoryIdentifiesWebApps() {
        assertEquals(
            LauncherCategory.Web,
            inferLauncherCategory("Chrome", "com.android.chrome")
        )
    }

    @Test
    fun inferLauncherCategoryIdentifiesCommunicationApps() {
        assertEquals(
            LauncherCategory.Communication,
            inferLauncherCategory("Messages", "com.google.android.apps.messaging")
        )
    }

    @Test
    fun buildCategorySectionsGroupsAndSortsApps() {
        val apps = listOf(
            LauncherEntry("Camera", "com.android.camera", LauncherCategory.Media),
            LauncherEntry("Chrome", "com.android.chrome", LauncherCategory.Web),
            LauncherEntry("Messages", "com.google.android.apps.messaging", LauncherCategory.Communication),
            LauncherEntry("Settings", "com.android.settings", LauncherCategory.System),
        )
        val usage = LauncherUsageSnapshot(
            launchCounts = mapOf("com.android.chrome" to 5),
            recentPackages = listOf("com.google.android.apps.messaging"),
            pinnedPackages = listOf("com.android.settings"),
        )

        val sections = buildCategorySections(apps, usage)

        assertEquals(LauncherCategory.Communication, sections.first().first)
        assertTrue(sections.any { it.first == LauncherCategory.Web })
        assertTrue(sections.any { it.first == LauncherCategory.System })
    }
}
