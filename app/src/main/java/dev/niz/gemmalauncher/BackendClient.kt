package dev.niz.gemmalauncher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

private const val BACKEND_CHAT_URL = "http://127.0.0.1:1337/chat"

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
    val json = JSONObject(body)

    BackendReply(
        response = json.optString("response", "No response"),
        traceSummary = json.optJSONArray("trace_summary").toStringList()
    )
}

suspend fun refreshWidgets(): List<WidgetState> {
    val prompts = listOf(
        "Battery" to "battery",
        "UTC" to "what time is it in utc",
        "Clipboard" to "get clipboard",
        "Location" to "get location",
    )
    return prompts.map { (title, prompt) ->
        val reply = runCatching { backendChat(prompt) }.getOrNull()
        WidgetState(title = title, value = reply?.response ?: "Unavailable", live = reply != null)
    }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList(length()) {
        for (i in 0 until length()) {
            add(optString(i))
        }
    }
}
