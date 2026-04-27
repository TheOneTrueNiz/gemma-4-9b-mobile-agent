package dev.niz.gemmalauncher

import android.graphics.drawable.Drawable

data class LauncherEntry(
    val label: String,
    val packageName: String,
    val icon: Drawable? = null,
)

data class LauncherUsageSnapshot(
    val launchCounts: Map<String, Int> = emptyMap(),
    val recentPackages: List<String> = emptyList(),
    val pinnedPackages: List<String> = emptyList(),
)

data class ChatTurn(
    val user: String,
    val agent: String,
)

data class WidgetState(
    val title: String,
    val value: String = "Waiting",
    val live: Boolean = false,
)

data class BackendReply(
    val response: String,
    val traceSummary: List<String>,
)

enum class OverlaySheet {
    Apps, Agent, Phone, Debug
}

sealed interface HomeIntentResolution {
    data class LaunchApp(
        val entry: LauncherEntry,
        val query: String,
    ) : HomeIntentResolution

    data class OpenDrawer(
        val query: String,
        val message: String,
        val suggestions: List<LauncherEntry> = emptyList(),
    ) : HomeIntentResolution

    data class SendToAgent(
        val message: String,
    ) : HomeIntentResolution
}
