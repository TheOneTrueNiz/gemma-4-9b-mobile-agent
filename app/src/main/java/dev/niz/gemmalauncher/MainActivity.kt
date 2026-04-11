package dev.niz.gemmalauncher

import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                LauncherApp(
                    appSource = { loadLaunchableApps() },
                    launchApp = { info -> launchApp(info) }
                )
            }
        }
    }

    private fun loadLaunchableApps(): List<LauncherEntry> {
        val launcherApps = getSystemService(LauncherApps::class.java) ?: return emptyList()
        val profiles = launcherApps.profiles ?: return emptyList()
        return profiles.flatMap { user ->
            launcherApps.getActivityList(null, user).map { it.toEntry() }
        }.sortedBy { it.label.lowercase() }
    }

    private fun launchApp(entry: LauncherEntry) {
        val intent = packageManager.getLaunchIntentForPackage(entry.packageName)
        if (intent != null) {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

private fun LauncherActivityInfo.toEntry(): LauncherEntry {
    return LauncherEntry(
        label = label?.toString() ?: applicationInfo.packageName,
        packageName = applicationInfo.packageName
    )
}

data class LauncherEntry(
    val label: String,
    val packageName: String,
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

private enum class OverlaySheet {
    Apps, Agent, Phone, Debug
}

private data class BackendReply(
    val response: String,
    val traceSummary: List<String>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LauncherApp(
    appSource: () -> List<LauncherEntry>,
    launchApp: (LauncherEntry) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val turns = remember {
        mutableStateListOf(
            ChatTurn(
                user = "",
                agent = "Gemma Launcher is online. This home screen is chat-first. Ask me to route you to apps, tools, or device actions."
            )
        )
    }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf<OverlaySheet?>(null) }
    var apps by remember { mutableStateOf(emptyList<LauncherEntry>()) }
    var traceVisible by remember { mutableStateOf(true) }
    var lastTraceSummary by remember { mutableStateOf<List<String>>(emptyList()) }
    var widgets by remember {
        mutableStateOf(
            listOf(
                WidgetState("Battery"),
                WidgetState("UTC"),
                WidgetState("Clipboard"),
                WidgetState("Location")
            )
        )
    }

    LaunchedEffect(Unit) {
        apps = appSource()
        widgets = refreshWidgets()
    }

    fun sendMessage(message: String) {
        if (message.isBlank() || loading) return
        loading = true
        turns.add(ChatTurn(user = message, agent = "Working..."))
        scope.launch {
            val reply = runCatching { backendChat(message) }.getOrElse {
                BackendReply(
                    response = "Connection failed. Start the backend on 127.0.0.1:1337.",
                    traceSummary = listOf("exception: ${it.message}")
                )
            }
            turns[turns.lastIndex] = ChatTurn(user = message, agent = reply.response)
            lastTraceSummary = reply.traceSummary
            loading = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF07131B), Color(0xFF0C2430), Color(0xFF10394A))
                    )
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopStatusBar(loading = loading, appCount = apps.size)
                Spacer(modifier = Modifier.height(10.dp))
                HomeHero()
                Spacer(modifier = Modifier.height(10.dp))
                ChatHome(
                    turns = turns,
                    traceVisible = traceVisible,
                    lastTraceSummary = lastTraceSummary
                )
                Spacer(modifier = Modifier.height(10.dp))
                LauncherDock(
                    onAgent = { overlay = OverlaySheet.Agent },
                    onApps = { overlay = OverlaySheet.Apps },
                    onPhone = { overlay = OverlaySheet.Phone },
                    onDebug = { overlay = OverlaySheet.Debug }
                )
                Spacer(modifier = Modifier.height(10.dp))
                CommandBar(
                    value = input,
                    loading = loading,
                    onValueChange = { input = it },
                    onSend = {
                        val outgoing = input
                        input = ""
                        sendMessage(outgoing)
                    }
                )
            }
        }
    }

    if (overlay != null) {
        ModalBottomSheet(
            onDismissRequest = { overlay = null },
            sheetState = sheetState,
            containerColor = Color(0xFF09141C)
        ) {
            when (overlay) {
                OverlaySheet.Apps -> AppDrawerSheet(apps = apps, launchApp = launchApp)
                OverlaySheet.Agent -> AgentSheet(turns = turns, onRecall = { sendMessage("recall what you know about this project") })
                OverlaySheet.Phone -> PhoneSheet(widgets = widgets, onRefresh = {
                    scope.launch { widgets = refreshWidgets() }
                })
                OverlaySheet.Debug -> DebugSheet(
                    traceVisible = traceVisible,
                    onToggleTrace = { traceVisible = !traceVisible },
                    traceSummary = lastTraceSummary
                )
                null -> Unit
            }
        }
    }
}

@Composable
private fun TopStatusBar(loading: Boolean, appCount: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xCC08141C))) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Gemma Launcher", color = Color.White, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(if (loading) "Busy" else "Idle")
                StatusChip("$appCount apps")
            }
        }
    }
}

@Composable
private fun HomeHero() {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xAA0E212C))) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Home Screen", color = Color(0xFF6EE7D2), fontSize = 11.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text("Agent-first Android launcher", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Human intent goes into chat. The agent mediates reasoning, tools, and app launches before touching the phone.",
                color = Color(0xFFC7D9E3),
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun ChatHome(turns: List<ChatTurn>, traceVisible: Boolean, lastTraceSummary: List<String>) {
    Card(
        modifier = Modifier.weight(1f),
        colors = CardDefaults.cardColors(containerColor = Color(0xAA08141C))
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            items(turns) { turn ->
                if (turn.user.isNotBlank()) {
                    MessageBubble(text = turn.user, isUser = true)
                }
                MessageBubble(text = turn.agent, isUser = false)
            }
            if (traceVisible && lastTraceSummary.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x663A2C11)),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Trace Summary", color = Color(0xFFF8B84E), fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            lastTraceSummary.take(4).forEach {
                                Text(it, color = Color(0xFFF7E7C0), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(text: String, isUser: Boolean) {
    val container = if (isUser) Color(0xFF8CEEDD) else Color(0xFF142734)
    val content = if (isUser) Color(0xFF041017) else Color.White
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.padding(vertical = 6.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = container)
        ) {
            Text(
                text = text,
                color = content,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun LauncherDock(
    onAgent: () -> Unit,
    onApps: () -> Unit,
    onPhone: () -> Unit,
    onDebug: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xDD08141C))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DockAction("Agent", Icons.Rounded.Memory, onAgent, Modifier.weight(1f))
            DockAction("Apps", Icons.Rounded.Dashboard, onApps, Modifier.weight(1f))
            DockAction("Phone", Icons.Rounded.Android, onPhone, Modifier.weight(1f))
            DockAction("Debug", Icons.Rounded.BugReport, onDebug, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DockAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0x441A3342))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label, tint = Color(0xFF6EE7D2))
            Spacer(modifier = Modifier.height(6.dp))
            Text(label, color = Color.White, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CommandBar(
    value: String,
    loading: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xDD08141C))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                label = { Text("Talk to Gemma") },
                enabled = !loading,
                singleLine = true
            )
            IconButton(onClick = onSend, enabled = !loading && value.isNotBlank()) {
                Icon(Icons.Rounded.Search, contentDescription = "Send", tint = Color(0xFF6EE7D2))
            }
        }
    }
}

@Composable
private fun AppDrawerSheet(apps: List<LauncherEntry>, launchApp: (LauncherEntry) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("App Drawer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Spacer(modifier = Modifier.height(12.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.height(420.dp)) {
            items(apps) { app ->
                Column(
                    modifier = Modifier
                        .padding(8.dp)
                        .clickable { launchApp(app) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color(0x332D4D5C), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(app.label.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(app.label, color = Color.White, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun AgentSheet(turns: List<ChatTurn>, onRecall: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Agent Layer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            TextButton(onClick = onRecall) { Text("Recall") }
        }
        Spacer(modifier = Modifier.height(12.dp))
        turns.takeLast(4).reversed().forEach { turn ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0x44203846)), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (turn.user.isNotBlank()) {
                        Text(turn.user, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(turn.agent, color = Color(0xFFC7D9E3))
                }
            }
        }
    }
}

@Composable
private fun PhoneSheet(widgets: List<WidgetState>, onRefresh: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Phone Layer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            TextButton(onClick = onRefresh) { Text("Refresh") }
        }
        Spacer(modifier = Modifier.height(12.dp))
        widgets.forEach { widget ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0x44203846)), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(widget.title, color = Color(0xFF6EE7D2), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(widget.value, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun DebugSheet(traceVisible: Boolean, onToggleTrace: () -> Unit, traceSummary: List<String>) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Operator Deck", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            TextButton(onClick = onToggleTrace) { Text(if (traceVisible) "Hide Trace" else "Show Trace") }
        }
        Spacer(modifier = Modifier.height(12.dp))
        traceSummary.ifEmpty { listOf("No trace data yet.") }.forEach {
            Text(it, color = Color(0xFFC7D9E3), modifier = Modifier.padding(bottom = 6.dp))
        }
    }
}

@Composable
private fun StatusChip(label: String) {
    Box(
        modifier = Modifier
            .background(Color(0x332D4D5C), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = Color(0xFFC7D9E3), fontSize = 11.sp)
    }
}

private suspend fun refreshWidgets(): List<WidgetState> {
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

private suspend fun backendChat(message: String): BackendReply = withContext(Dispatchers.IO) {
    val url = URL("http://127.0.0.1:1337/chat")
    val connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        connectTimeout = 4000
        readTimeout = 20000
        setRequestProperty("Content-Type", "application/json")
    }
    val payload = """{"message":${message.jsonString()},"history":[]}"""
    OutputStreamWriter(connection.outputStream).use { it.write(payload) }
    val body = BufferedReader(connection.inputStream.reader()).use { it.readText() }
    val response = body.extractJsonString("response") ?: "No response"
    val traceSummary = body.extractJsonStringArray("trace_summary")
    BackendReply(response = response, traceSummary = traceSummary)
}

private fun String.jsonString(): String {
    return buildString(length + 2) {
        append('"')
        for (c in this@jsonString) {
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
        append('"')
    }
}

private fun String.extractJsonString(key: String): String? {
    val pattern = """"$key"\s*:\s*"((?:\\.|[^"])*)"""".toRegex()
    val match = pattern.find(this) ?: return null
    return match.groupValues[1]
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
}

private fun String.extractJsonStringArray(key: String): List<String> {
    val start = indexOf("\"$key\"")
    if (start == -1) return emptyList()
    val open = indexOf('[', start)
    val close = indexOf(']', open)
    if (open == -1 || close == -1) return emptyList()
    val slice = substring(open + 1, close)
    return """"((?:\\.|[^"])*)"""".toRegex().findAll(slice).map {
        it.groupValues[1]
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }.toList()
}
