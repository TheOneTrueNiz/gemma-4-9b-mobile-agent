package dev.niz.gemmalauncher

import android.graphics.drawable.Drawable

enum class LauncherCategory(val label: String) {
    Communication("Communication"),
    Web("Web"),
    Media("Media"),
    Tools("Tools"),
    System("System"),
    Travel("Travel"),
    Work("Work"),
    AI("AI"),
    Other("Other"),
}

data class LauncherEntry(
    val label: String,
    val packageName: String,
    val category: LauncherCategory = LauncherCategory.Other,
    val icon: Drawable? = null,
)

data class LauncherUsageSnapshot(
    val launchCounts: Map<String, Int> = emptyMap(),
    val recentPackages: List<String> = emptyList(),
    val pinnedPackages: List<String> = emptyList(),
    val recentActivityKeys: List<String> = emptyList(),
    val recentDecisions: List<LauncherDecisionRecord> = emptyList(),
)

data class LauncherDecisionRecord(
    val query: String,
    val route: String,
    val detail: String,
)

data class ChatTurn(
    val user: String,
    val agent: String,
    val route: String = "Gemma",
    val routeDetail: String = "",
    val pending: Boolean = false,
)

data class WidgetState(
    val title: String,
    val value: String = "Waiting",
    val live: Boolean = false,
)

data class BackendReply(
    val response: String,
    val traceSummary: List<String>,
    val mode: String = "agentic",
)

data class BackendStatus(
    val checked: Boolean = false,
    val online: Boolean = false,
    val actorOnline: Boolean = false,
    val actorModel: String? = null,
    val detail: String = "Checking launcher link.",
) {
    val agentReady: Boolean
        get() = online && actorOnline
}

data class TermuxBridgeStatus(
    val termuxInstalled: Boolean = false,
    val runCommandPermissionGranted: Boolean = false,
    val canDispatchCommands: Boolean = false,
    val detail: String = "Checking Termux bridge.",
)

enum class OverlaySheet {
    Apps, Agent, Phone, Debug
}

sealed interface HomeIntentResolution {
    data class LaunchNativeAction(
        val action: NativeLauncherAction,
        val query: String,
    ) : HomeIntentResolution

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
