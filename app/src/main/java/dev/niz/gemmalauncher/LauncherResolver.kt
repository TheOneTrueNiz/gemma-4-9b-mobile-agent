package dev.niz.gemmalauncher

private val APP_LAUNCH_PREFIXES = listOf("open ", "launch ", "start ")

fun resolveHomeIntent(
    message: String,
    apps: List<LauncherEntry>,
    usage: LauncherUsageSnapshot,
): HomeIntentResolution {
    val normalized = message.trim()
    if (normalized.isBlank()) {
        return HomeIntentResolution.SendToAgent(message)
    }

    val launchQuery = extractLaunchQuery(normalized)
    if (launchQuery != null) {
        return resolveAppLaunchQuery(launchQuery, apps, usage, explicit = true)
    }

    if (looksLikeLauncherQuery(normalized)) {
        return resolveAppLaunchQuery(normalized, apps, usage, explicit = false)
    }

    return HomeIntentResolution.SendToAgent(normalized)
}

fun rankAppsForQuery(
    query: String,
    apps: List<LauncherEntry>,
    usage: LauncherUsageSnapshot,
): List<LauncherEntry> {
    val normalized = query.trim().lowercase()
    if (normalized.isBlank()) return apps

    return apps
        .map { entry -> entry to scoreEntry(normalized, entry, usage) }
        .filter { (_, score) -> score > 0 }
        .sortedWith(
            compareByDescending<Pair<LauncherEntry, Int>> { it.second }
                .thenBy { it.first.label.lowercase() }
        )
        .map { it.first }
}

private fun resolveAppLaunchQuery(
    query: String,
    apps: List<LauncherEntry>,
    usage: LauncherUsageSnapshot,
    explicit: Boolean,
): HomeIntentResolution {
    val ranked = rankAppsForQuery(query, apps, usage)
    if (ranked.isEmpty()) {
        return HomeIntentResolution.OpenDrawer(
            query = query,
            message = "I couldn't find an installed app matching \"$query\".",
        )
    }

    val top = ranked.first()
    val topScore = scoreEntry(query.lowercase(), top, usage)
    val runnerUpScore = ranked.getOrNull(1)?.let { scoreEntry(query.lowercase(), it, usage) } ?: 0
    val exact = top.label.equals(query, ignoreCase = true) || top.packageName.equals(query, ignoreCase = true)

    return if (exact || topScore >= 900 || (explicit && topScore - runnerUpScore >= 140)) {
        HomeIntentResolution.LaunchApp(entry = top, query = query)
    } else {
        HomeIntentResolution.OpenDrawer(
            query = query,
            message = "I found a few app matches for \"$query\". Pick one from the app drawer.",
            suggestions = ranked.take(6),
        )
    }
}

private fun extractLaunchQuery(message: String): String? {
    val prefix = APP_LAUNCH_PREFIXES.firstOrNull { message.lowercase().startsWith(it) } ?: return null
    return message.drop(prefix.length).trim().takeIf { it.isNotBlank() }
}

private fun looksLikeLauncherQuery(message: String): Boolean {
    if (message.endsWith("?")) return false
    val words = message.split(Regex("\\s+")).filter { it.isNotBlank() }
    return words.size in 1..3
}

private fun scoreEntry(
    query: String,
    entry: LauncherEntry,
    usage: LauncherUsageSnapshot,
): Int {
    val label = entry.label.lowercase()
    val packageName = entry.packageName.lowercase()
    var score = 0

    when {
        label == query -> score += 1000
        label.startsWith(query) -> score += 760
        label.contains(query) -> score += 520
    }

    when {
        packageName == query -> score += 920
        packageName.endsWith(query) -> score += 460
        packageName.contains(query) -> score += 320
    }

    if (entry.packageName in usage.recentPackages) {
        score += 120 - (usage.recentPackages.indexOf(entry.packageName) * 12)
    }

    score += (usage.launchCounts[entry.packageName] ?: 0).coerceAtMost(25) * 6
    return score
}
