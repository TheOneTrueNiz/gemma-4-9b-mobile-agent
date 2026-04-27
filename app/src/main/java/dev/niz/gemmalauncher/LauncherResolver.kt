package dev.niz.gemmalauncher

import kotlin.math.max
import kotlin.math.min

private val APP_LAUNCH_PREFIXES = listOf("open ", "launch ", "start ")
private val APP_DRAWER_PREFIXES = listOf(
    "find app ",
    "find apps ",
    "search app ",
    "search apps ",
    "show apps ",
    "apps "
)
private val NON_LAUNCHER_LEADING_WORDS = setOf(
    "what", "who", "when", "where", "why", "how",
    "tell", "explain", "remember", "recall",
    "battery", "clock", "time", "date", "weather",
    "convert", "calculate", "count", "reverse",
    "uppercase", "lowercase", "search", "research"
)
private val COMMON_PACKAGE_TOKENS = setOf(
    "android", "apps", "app", "com", "google", "launcher", "mobile"
)
private val APP_QUERY_STOPWORDS = setOf("the", "app", "apps", "my", "please")

fun resolveHomeIntent(
    message: String,
    apps: List<LauncherEntry>,
    usage: LauncherUsageSnapshot,
): HomeIntentResolution {
    val raw = message.trim()
    if (raw.isBlank()) {
        return HomeIntentResolution.SendToAgent(message)
    }

    extractDrawerQuery(raw)?.let { query ->
        return resolveDrawerQuery(query, apps, usage)
    }

    extractLaunchQuery(raw)?.let { query ->
        return resolveAppLaunchQuery(query, apps, usage, explicit = true)
    }

    if (looksLikeLauncherQuery(raw)) {
        return resolveAppLaunchQuery(raw, apps, usage, explicit = false)
    }

    return HomeIntentResolution.SendToAgent(raw)
}

fun rankAppsForQuery(
    query: String,
    apps: List<LauncherEntry>,
    usage: LauncherUsageSnapshot,
): List<LauncherEntry> {
    val normalized = normalizeLauncherText(query)
    if (normalized.isBlank()) return apps

    return apps
        .map { entry -> entry to scoreEntry(normalized, entry, usage) }
        .filter { (_, score) -> score > 0 }
        .sortedWith(
            compareByDescending<Pair<LauncherEntry, Int>> { it.second }
                .thenByDescending { usage.launchCounts[it.first.packageName] ?: 0 }
                .thenBy { it.first.label.lowercase() }
        )
        .map { it.first }
}

private fun resolveDrawerQuery(
    query: String,
    apps: List<LauncherEntry>,
    usage: LauncherUsageSnapshot,
): HomeIntentResolution {
    val ranked = rankAppsForQuery(query, apps, usage)
    return HomeIntentResolution.OpenDrawer(
        query = query,
        message = if (ranked.isEmpty()) {
            "I couldn't find installed apps matching \"$query\", but I opened the app drawer search."
        } else {
            "Showing app matches for \"$query\"."
        },
        suggestions = ranked.take(8),
    )
}

private fun resolveAppLaunchQuery(
    query: String,
    apps: List<LauncherEntry>,
    usage: LauncherUsageSnapshot,
    explicit: Boolean,
): HomeIntentResolution {
    val normalized = normalizeLauncherText(query)
    val ranked = rankAppsForQuery(normalized, apps, usage)
    if (ranked.isEmpty()) {
        return if (explicit) {
            HomeIntentResolution.OpenDrawer(
                query = query,
                message = "I couldn't find an installed app matching \"$query\".",
            )
        } else {
            HomeIntentResolution.SendToAgent(query)
        }
    }

    val top = ranked.first()
    val topScore = scoreEntry(normalized, top, usage)
    val runnerUpScore = ranked.getOrNull(1)?.let { scoreEntry(normalized, it, usage) } ?: 0
    val exact = isExactMatch(normalized, top)

    if (!explicit && topScore < 560) {
        return HomeIntentResolution.SendToAgent(query)
    }

    return if (exact || topScore >= 920 || (explicit && topScore - runnerUpScore >= 140)) {
        HomeIntentResolution.LaunchApp(entry = top, query = query)
    } else {
        HomeIntentResolution.OpenDrawer(
            query = query,
            message = "I found a few app matches for \"$query\". Pick one from the app drawer.",
            suggestions = ranked.take(8),
        )
    }
}

private fun extractLaunchQuery(message: String): String? {
    val prefix = APP_LAUNCH_PREFIXES.firstOrNull { message.lowercase().startsWith(it) } ?: return null
    return message.drop(prefix.length).trim().takeIf { it.isNotBlank() }
}

private fun extractDrawerQuery(message: String): String? {
    val prefix = APP_DRAWER_PREFIXES.firstOrNull { message.lowercase().startsWith(it) } ?: return null
    return message.drop(prefix.length).trim().takeIf { it.isNotBlank() }
}

private fun looksLikeLauncherQuery(message: String): Boolean {
    if (message.endsWith("?")) return false
    val normalized = normalizeLauncherText(message)
    val words = normalized.split(" ").filter { it.isNotBlank() }
    if (words.isEmpty()) return false
    if (words.first() in NON_LAUNCHER_LEADING_WORDS) return false
    return words.size in 1..4
}

private fun isExactMatch(query: String, entry: LauncherEntry): Boolean {
    val normalizedLabel = normalizeLauncherText(entry.label)
    val normalizedPackage = normalizeLauncherText(entry.packageName)
    val compactQuery = query.replace(" ", "")
    return normalizedLabel == query ||
        normalizedLabel.replace(" ", "") == compactQuery ||
        normalizedPackage == query ||
        aliasTerms(entry).any { alias ->
            alias == query || alias.replace(" ", "") == compactQuery
        }
}

private fun scoreEntry(
    query: String,
    entry: LauncherEntry,
    usage: LauncherUsageSnapshot,
): Int {
    val compactQuery = query.replace(" ", "")
    val label = normalizeLauncherText(entry.label)
    val compactLabel = label.replace(" ", "")
    val packageText = normalizeLauncherText(entry.packageName.replace('.', ' '))
    val packageTail = normalizeLauncherText(entry.packageName.substringAfterLast('.'))
    var score = 0

    score = max(score, directMatchScore(query, label, compactQuery, compactLabel, exact = 1000, prefix = 820, contains = 620))
    score = max(score, directMatchScore(query, packageTail, compactQuery, packageTail.replace(" ", ""), exact = 940, prefix = 660, contains = 460))
    if (packageText.contains(query)) {
        score = max(score, 420)
    }

    aliasTerms(entry).forEach { alias ->
        val aliasScore = directMatchScore(
            query = query,
            candidate = alias,
            compactQuery = compactQuery,
            compactCandidate = alias.replace(" ", ""),
            exact = 960,
            prefix = 760,
            contains = 540
        )
        score = max(score, aliasScore)
    }

    val fuzzyCandidates = buildList {
        add(label)
        add(packageTail)
        addAll(aliasTerms(entry))
    }.distinct()

    val fuzzyBoost = fuzzyCandidates.maxOfOrNull { candidate ->
        fuzzyScore(compactQuery, candidate.replace(" ", ""))
    } ?: 0
    score = max(score, fuzzyBoost)

    if (entry.packageName in usage.pinnedPackages) {
        score += 180
    }
    if (entry.packageName in usage.recentPackages) {
        score += 120 - (usage.recentPackages.indexOf(entry.packageName) * 12)
    }
    score += (usage.launchCounts[entry.packageName] ?: 0).coerceAtMost(25) * 6
    return score
}

private fun directMatchScore(
    query: String,
    candidate: String,
    compactQuery: String,
    compactCandidate: String,
    exact: Int,
    prefix: Int,
    contains: Int,
): Int {
    return when {
        candidate == query || compactCandidate == compactQuery -> exact
        candidate.startsWith(query) || compactCandidate.startsWith(compactQuery) -> prefix
        candidate.contains(query) || compactCandidate.contains(compactQuery) -> contains
        else -> tokenOverlapScore(query, candidate)
    }
}

private fun tokenOverlapScore(query: String, candidate: String): Int {
    val queryTokens = query.split(" ").filter { it.isNotBlank() && it !in APP_QUERY_STOPWORDS }
    if (queryTokens.isEmpty()) return 0
    val candidateTokens = candidate.split(" ").filter { it.isNotBlank() }.toSet()
    val overlap = queryTokens.count { token ->
        candidateTokens.any { candidateToken ->
            candidateToken == token || candidateToken.startsWith(token) || candidateToken.contains(token)
        }
    }
    return if (overlap == 0) 0 else 180 + (overlap * 60)
}

private fun fuzzyScore(query: String, candidate: String): Int {
    if (query.length < 4 || candidate.isBlank()) return 0
    val distance = boundedLevenshtein(query, candidate, maxDistance = 2) ?: return 0
    return when (distance) {
        0 -> 900
        1 -> 700
        2 -> 560
        else -> 0
    }
}

private fun boundedLevenshtein(a: String, b: String, maxDistance: Int): Int? {
    if (kotlin.math.abs(a.length - b.length) > maxDistance) return null
    var previous = IntArray(b.length + 1) { it }
    var current = IntArray(b.length + 1)

    for (i in a.indices) {
        current[0] = i + 1
        var rowMin = current[0]
        for (j in b.indices) {
            val substitution = if (a[i] == b[j]) 0 else 1
            current[j + 1] = min(
                min(current[j] + 1, previous[j + 1] + 1),
                previous[j] + substitution
            )
            rowMin = min(rowMin, current[j + 1])
        }
        if (rowMin > maxDistance) return null
        val swap = previous
        previous = current
        current = swap
    }

    return previous[b.length].takeIf { it <= maxDistance }
}

private fun aliasTerms(entry: LauncherEntry): List<String> {
    val label = normalizeLauncherText(entry.label)
    val packageName = entry.packageName.lowercase()
    val packageTokens = packageName
        .split('.', '-', '_')
        .map { normalizeLauncherText(it) }
        .filter { it.isNotBlank() && it !in COMMON_PACKAGE_TOKENS }
    val aliases = linkedSetOf<String>()

    aliases += label
    aliases += label.split(" ").filter { it.isNotBlank() }
    aliases += packageTokens
    aliases += normalizeLauncherText(entry.packageName.substringAfterLast('.'))

    fun addKnownAliases(vararg values: String) {
        values.map(::normalizeLauncherText).filter { it.isNotBlank() }.forEach { aliases += it }
    }

    if ("chrome" in packageName || "browser" in label) addKnownAliases("browser", "web", "internet", "chrome")
    if ("settings" in packageName || "settings" in label) addKnownAliases("settings", "preferences", "system settings")
    if ("camera" in packageName || "camera" in label) addKnownAliases("camera", "cam", "photo")
    if ("maps" in packageName || "maps" in label) addKnownAliases("maps", "navigation", "gps")
    if ("message" in packageName || "messaging" in packageName || "messages" in label) addKnownAliases("messages", "sms", "texts")
    if ("dialer" in packageName || "phone" in packageName || "phone" in label) addKnownAliases("phone", "dialer", "calls")
    if ("gallery" in packageName || "photos" in packageName || "gallery" in label || "photos" in label) addKnownAliases("gallery", "photos", "pictures")
    if ("file" in packageName || "documentsui" in packageName || "files" in label) addKnownAliases("files", "file manager", "storage")
    if ("gmail" in packageName || "mail" in packageName || "mail" in label) addKnownAliases("gmail", "mail", "email")
    if ("play" in packageName && "store" in packageName) addKnownAliases("play store", "store", "app store")

    return aliases.toList()
}

private fun normalizeLauncherText(value: String): String {
    return value
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}
