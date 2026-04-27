package dev.niz.gemmalauncher

private val CATEGORY_PRIORITY = listOf(
    LauncherCategory.Communication,
    LauncherCategory.Web,
    LauncherCategory.Media,
    LauncherCategory.Tools,
    LauncherCategory.System,
    LauncherCategory.Travel,
    LauncherCategory.Work,
    LauncherCategory.AI,
    LauncherCategory.Other,
)

fun inferLauncherCategory(label: String, packageName: String): LauncherCategory {
    val text = normalizeCatalogText("$label $packageName")
    return when {
        containsAny(text, "message", "messages", "messaging", "sms", "mail", "gmail", "email", "phone", "dialer", "contacts", "telegram", "whatsapp", "discord") ->
            LauncherCategory.Communication
        containsAny(text, "chrome", "browser", "web", "firefox", "internet", "brave") ->
            LauncherCategory.Web
        containsAny(text, "camera", "gallery", "photos", "music", "spotify", "youtube", "video", "media", "vlc") ->
            LauncherCategory.Media
        containsAny(text, "clock", "calculator", "files", "file", "storage", "notes", "terminal", "termux", "tool", "tools", "utility") ->
            LauncherCategory.Tools
        containsAny(text, "settings", "system", "security", "device", "launcher", "package installer") ->
            LauncherCategory.System
        containsAny(text, "maps", "navigation", "gps", "uber", "lyft", "travel") ->
            LauncherCategory.Travel
        containsAny(text, "calendar", "docs", "drive", "sheet", "slack", "meet", "zoom", "office", "todo", "tasks") ->
            LauncherCategory.Work
        containsAny(text, "gemma", "openai", "chatgpt", "claude", "copilot", "assistant", "ai") ->
            LauncherCategory.AI
        else -> LauncherCategory.Other
    }
}

fun buildCategorySections(
    apps: List<LauncherEntry>,
    usage: LauncherUsageSnapshot,
): List<Pair<LauncherCategory, List<LauncherEntry>>> {
    return CATEGORY_PRIORITY.mapNotNull { category ->
        val entries = apps
            .filter { it.category == category }
            .sortedWith(
                compareByDescending<LauncherEntry> { it.packageName in usage.pinnedPackages }
                    .thenByDescending { usage.launchCounts[it.packageName] ?: 0 }
                    .thenBy { it.label.lowercase() }
            )
        entries.takeIf { it.isNotEmpty() }?.let { category to it }
    }
}

private fun containsAny(text: String, vararg terms: String): Boolean {
    return terms.any { term -> text.contains(term) }
}

private fun normalizeCatalogText(value: String): String {
    return value
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
}
