package dev.niz.gemmalauncher

import android.graphics.drawable.Drawable

data class LauncherEntry(
    val label: String,
    val packageName: String,
    val icon: Drawable? = null,
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
