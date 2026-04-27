package dev.niz.gemmalauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LauncherUsageStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun snapshot(): LauncherUsageSnapshot {
        val counts = prefs.getString(KEY_LAUNCH_COUNTS, "{}").orEmpty()
        val recents = prefs.getString(KEY_RECENT_PACKAGES, "[]").orEmpty()
        val pinned = prefs.getString(KEY_PINNED_PACKAGES, "[]").orEmpty()
        return LauncherUsageSnapshot(
            launchCounts = counts.toCountMap(),
            recentPackages = recents.toStringList(),
            pinnedPackages = pinned.toStringList(),
        )
    }

    fun recordLaunch(packageName: String) {
        val snap = snapshot()
        val updatedCounts = snap.launchCounts.toMutableMap().apply {
            this[packageName] = (this[packageName] ?: 0) + 1
        }
        val updatedRecents = buildList {
            add(packageName)
            snap.recentPackages.filterNot { it == packageName }.take(MAX_RECENTS - 1).forEach(::add)
        }

        prefs.edit()
            .putString(KEY_LAUNCH_COUNTS, JSONObject(updatedCounts as Map<*, *>).toString())
            .putString(KEY_RECENT_PACKAGES, JSONArray(updatedRecents).toString())
            .apply()
    }

    fun togglePinned(packageName: String) {
        val snap = snapshot()
        val updatedPinned = if (packageName in snap.pinnedPackages) {
            snap.pinnedPackages.filterNot { it == packageName }
        } else {
            buildList {
                add(packageName)
                snap.pinnedPackages.filterNot { it == packageName }.take(MAX_PINNED - 1).forEach(::add)
            }
        }

        prefs.edit()
            .putString(KEY_PINNED_PACKAGES, JSONArray(updatedPinned).toString())
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "gemma_launcher_usage"
        private const val KEY_LAUNCH_COUNTS = "launch_counts"
        private const val KEY_RECENT_PACKAGES = "recent_packages"
        private const val KEY_PINNED_PACKAGES = "pinned_packages"
        private const val MAX_RECENTS = 8
        private const val MAX_PINNED = 4
    }
}

private fun String.toCountMap(): Map<String, Int> {
    val json = JSONObject(this)
    return buildMap {
        json.keys().forEach { key ->
            put(key, json.optInt(key, 0))
        }
    }
}

private fun String.toStringList(): List<String> {
    val json = JSONArray(this)
    return buildList(json.length()) {
        for (index in 0 until json.length()) {
            add(json.optString(index))
        }
    }
}
