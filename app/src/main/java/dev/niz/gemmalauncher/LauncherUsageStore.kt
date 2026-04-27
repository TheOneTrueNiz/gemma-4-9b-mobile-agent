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
        val recentActivity = prefs.getString(KEY_RECENT_ACTIVITY, "[]").orEmpty()
        val recentDecisions = prefs.getString(KEY_RECENT_DECISIONS, "[]").orEmpty()
        return LauncherUsageSnapshot(
            launchCounts = counts.toCountMap(),
            recentPackages = recents.toStringList(),
            pinnedPackages = pinned.toStringList(),
            recentActivityKeys = recentActivity.toStringList(),
            recentDecisions = recentDecisions.toDecisionList(),
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
        val updatedActivity = buildRecentActivity(
            key = "app:$packageName",
            existing = snap.recentActivityKeys,
        )

        prefs.edit()
            .putString(KEY_LAUNCH_COUNTS, JSONObject(updatedCounts as Map<*, *>).toString())
            .putString(KEY_RECENT_PACKAGES, JSONArray(updatedRecents).toString())
            .putString(KEY_RECENT_ACTIVITY, JSONArray(updatedActivity).toString())
            .apply()
    }

    fun recordNativeAction(action: NativeLauncherAction) {
        val snap = snapshot()
        val updatedActivity = buildRecentActivity(
            key = "native:${action.name}",
            existing = snap.recentActivityKeys,
        )

        prefs.edit()
            .putString(KEY_RECENT_ACTIVITY, JSONArray(updatedActivity).toString())
            .apply()
    }

    fun recordDecision(record: LauncherDecisionRecord) {
        val snap = snapshot()
        val updatedDecisions = buildRecentDecisions(
            record = record,
            existing = snap.recentDecisions,
        )

        prefs.edit()
            .putString(KEY_RECENT_DECISIONS, updatedDecisions.toJsonArray().toString())
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
        private const val KEY_RECENT_ACTIVITY = "recent_activity"
        private const val KEY_RECENT_DECISIONS = "recent_decisions"
        private const val MAX_RECENTS = 8
        private const val MAX_PINNED = 4
        private const val MAX_ACTIVITY = 10
        private const val MAX_DECISIONS = 12

        private fun buildRecentActivity(key: String, existing: List<String>): List<String> {
            return buildList {
                add(key)
                existing.filterNot { it == key }.take(MAX_ACTIVITY - 1).forEach(::add)
            }
        }

        private fun buildRecentDecisions(
            record: LauncherDecisionRecord,
            existing: List<LauncherDecisionRecord>,
        ): List<LauncherDecisionRecord> {
            return buildList {
                add(record)
                existing.take(MAX_DECISIONS - 1).forEach(::add)
            }
        }
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

private fun String.toDecisionList(): List<LauncherDecisionRecord> {
    val json = JSONArray(this)
    return buildList(json.length()) {
        for (index in 0 until json.length()) {
            val item = json.optJSONObject(index) ?: continue
            add(
                LauncherDecisionRecord(
                    query = item.optString("query"),
                    route = item.optString("route"),
                    detail = item.optString("detail"),
                )
            )
        }
    }
}

private fun List<LauncherDecisionRecord>.toJsonArray(): JSONArray {
    return JSONArray().apply {
        forEach { record ->
            put(
                JSONObject()
                    .put("query", record.query)
                    .put("route", record.route)
                    .put("detail", record.detail)
            )
        }
    }
}
