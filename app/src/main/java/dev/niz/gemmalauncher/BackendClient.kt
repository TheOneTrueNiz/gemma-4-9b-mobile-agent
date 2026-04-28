package dev.niz.gemmalauncher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

private const val BACKEND_BASE_URL = "http://127.0.0.1:1337"
private const val BACKEND_CHAT_URL = "$BACKEND_BASE_URL/chat"
private const val BACKEND_HEALTH_URL = "$BACKEND_BASE_URL/health"

suspend fun fetchBackendStatus(): BackendStatus = withContext(Dispatchers.IO) {
    runCatching {
        val connection = (URL(BACKEND_HEALTH_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 2000
            readTimeout = 4000
        }
        val body = BufferedReader(connection.inputStream.reader()).use { it.readText() }
        parseBackendStatus(body)
    }.getOrElse { error ->
        BackendStatus(
            checked = true,
            online = false,
            actorOnline = false,
            detail = error.message ?: "Backend unavailable",
        )
    }
}

suspend fun backendChat(message: String): BackendReply = withContext(Dispatchers.IO) {
    val connection = (URL(BACKEND_CHAT_URL).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        connectTimeout = 4000
        readTimeout = 20000
        setRequestProperty("Content-Type", "application/json")
    }

    val payload = JSONObject()
        .put("message", message)
        .put("history", JSONArray())
        .toString()

    OutputStreamWriter(connection.outputStream).use { it.write(payload) }
    val body = BufferedReader(connection.inputStream.reader()).use { it.readText() }
    parseBackendReply(body)
}

suspend fun refreshWidgets(status: BackendStatus? = null): List<WidgetState> {
    val prompts = listOf(
        "Battery" to "battery",
        "UTC" to "what time is it in utc",
        "Clipboard" to "get clipboard",
        "Location" to "get location",
    )
    val backendStatus = status ?: fetchBackendStatus()
    if (!backendStatus.online) {
        return prompts.map { (title, _) ->
            WidgetState(title = title, value = "Backend offline", live = false)
        }
    }
    return prompts.map { (title, prompt) ->
        val reply = runCatching { backendChat(prompt) }.getOrNull()
        WidgetState(title = title, value = reply?.response ?: "Unavailable", live = reply != null)
    }
}

fun parseBackendStatus(body: String): BackendStatus {
    val online = extractJsonStringField(body, "status") == "ok"
    val actorOnline = extractJsonBooleanField(body, "actor_online")
    val actorModel = extractJsonStringField(body, "actor_model")
    val detail = when {
        !online -> "Backend reported an unhealthy state."
        actorOnline -> "Agent runtime is reachable."
        else -> "Backend is reachable, but the actor model is not ready yet."
    }
    return BackendStatus(
        checked = true,
        online = online,
        actorOnline = actorOnline,
        actorModel = actorModel,
        detail = detail,
    )
}

fun parseBackendReply(body: String): BackendReply {
    val json = JSONObject(body)
    return BackendReply(
        response = json.optString("response", "No response"),
        traceSummary = json.optJSONArray("trace_summary").toStringList(),
        mode = json.optString("mode", "agentic"),
    )
}

private fun extractJsonStringField(body: String, key: String): String? {
    val pattern = Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"")
    return pattern.find(body)?.groupValues?.getOrNull(1)
}

private fun extractJsonBooleanField(body: String, key: String): Boolean {
    val pattern = Regex("\"$key\"\\s*:\\s*(true|false)")
    return pattern.find(body)?.groupValues?.getOrNull(1)?.toBoolean() ?: false
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList(length()) {
        for (i in 0 until length()) {
            add(optString(i))
        }
    }
}
