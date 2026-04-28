package dev.niz.gemmalauncher

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.material.icons.rounded.PushPin
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
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherApp(
    appSource: () -> List<LauncherEntry>,
    usageStore: LauncherUsageStore,
    termuxBridgeStatus: TermuxBridgeStatus,
    refreshTermuxBridgeStatus: () -> Unit,
    requestTermuxPermission: () -> Unit,
    openLauncherSettings: () -> Unit,
    openTermuxSettings: () -> Unit,
    openTermuxOverlaySettings: () -> Unit,
    controlBackend: (Boolean) -> String,
    launchApp: (LauncherEntry) -> Unit,
    launchNativeAction: (NativeLauncherAction) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val turns = remember {
        mutableStateListOf(
            ChatTurn(
                user = "",
                agent = "Gemma Launcher is ready. Local app and system routing works immediately. Agent responses depend on the local backend.",
                route = "Launcher",
                routeDetail = "Home shell active",
            )
        )
    }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf<OverlaySheet?>(null) }
    var apps by remember { mutableStateOf(emptyList<LauncherEntry>()) }
    var drawerQuery by remember { mutableStateOf("") }
    var drawerSuggestions by remember { mutableStateOf(emptyList<LauncherEntry>()) }
    var usageSnapshot by remember { mutableStateOf(LauncherUsageSnapshot()) }
    var traceVisible by remember { mutableStateOf(true) }
    var lastTraceSummary by remember { mutableStateOf<List<String>>(emptyList()) }
    var backendStatus by remember { mutableStateOf(BackendStatus()) }
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

    fun recordLaunch(entry: LauncherEntry) {
        usageStore.recordLaunch(entry.packageName)
        usageSnapshot = usageStore.snapshot()
    }

    fun recordNativeAction(action: NativeLauncherAction) {
        usageStore.recordNativeAction(action)
        usageSnapshot = usageStore.snapshot()
    }

    fun recordDecision(record: LauncherDecisionRecord) {
        usageStore.recordDecision(record)
        usageSnapshot = usageStore.snapshot()
    }

    fun togglePinned(entry: LauncherEntry) {
        usageStore.togglePinned(entry.packageName)
        usageSnapshot = usageStore.snapshot()
    }

    val pinnedApps = usageSnapshot.pinnedPackages.mapNotNull { pkg ->
        apps.firstOrNull { it.packageName == pkg }
    }
    val dockApps = remember(apps, usageSnapshot) {
        buildDockApps(apps = apps, usage = usageSnapshot)
    }
    val recentActivity = remember(apps, usageSnapshot) {
        buildRecentActivityItems(apps = apps, usage = usageSnapshot)
    }
    val recentDecisions = usageSnapshot.recentDecisions
    val lastDecisionRoute = recentDecisions.firstOrNull()?.route
    val homeSuggestions = remember(input, apps, usageSnapshot) {
        buildHomeInputSuggestions(query = input, apps = apps, usage = usageSnapshot)
    }

    suspend fun refreshBackendLink(refreshWidgetsToo: Boolean = false): BackendStatus {
        val status = fetchBackendStatus()
        backendStatus = status
        if (refreshWidgetsToo) {
            widgets = refreshWidgets(status)
        }
        return status
    }

    fun executeNativeAction(query: String, action: NativeLauncherAction) {
        recordNativeAction(action)
        recordDecision(
            LauncherDecisionRecord(
                query = query,
                route = "System",
                detail = action.label,
            )
        )
        turns.add(
            ChatTurn(
                user = query,
                agent = action.openingMessage,
                route = "Phone",
                routeDetail = "Native system action",
            )
        )
        launchNativeAction(action)
    }

    fun executeAppLaunch(query: String, entry: LauncherEntry) {
        recordLaunch(entry)
        recordDecision(
            LauncherDecisionRecord(
                query = query,
                route = "App",
                detail = entry.label,
            )
        )
        turns.add(
            ChatTurn(
                user = query,
                agent = "Opening ${entry.label}.",
                route = "App",
                routeDetail = entry.label,
            )
        )
        launchApp(entry)
    }

    fun handleLauncherResolution(message: String): Boolean {
        return when (val resolution = resolveHomeIntent(message = message, apps = apps, usage = usageSnapshot)) {
            is HomeIntentResolution.LaunchNativeAction -> {
                executeNativeAction(message, resolution.action)
                true
            }
            is HomeIntentResolution.LaunchApp -> {
                executeAppLaunch(message, resolution.entry)
                true
            }
            is HomeIntentResolution.OpenDrawer -> {
                recordDecision(
                    LauncherDecisionRecord(
                        query = message,
                        route = "Drawer",
                        detail = resolution.query,
                    )
                )
                drawerQuery = resolution.query
                drawerSuggestions = resolution.suggestions
                overlay = OverlaySheet.Apps
                turns.add(
                    ChatTurn(
                        user = message,
                        agent = resolution.message,
                        route = "Drawer",
                        routeDetail = "Ranked app matches",
                    )
                )
                true
            }
            is HomeIntentResolution.SendToAgent -> false
        }
    }

    suspend fun requestBackendStart(restart: Boolean, addTurn: Boolean): BackendStatus {
        val userLabel = if (restart) "Restart Gemma" else "Start Gemma"
        val bridgeMessage = controlBackend(restart)
        refreshTermuxBridgeStatus()
        recordDecision(
            LauncherDecisionRecord(
                query = userLabel,
                route = "Launcher",
                detail = if (restart) "Restart backend" else "Start backend",
            )
        )
        if (addTurn) {
            turns.add(
                ChatTurn(
                    user = userLabel,
                    agent = bridgeMessage,
                    route = "Launcher",
                    routeDetail = "Backend control",
                )
            )
        }
        lastTraceSummary = listOf(
            "launcher: $bridgeMessage",
            "termux: ${termuxBridgeStatus.detail}",
        )
        for (attempt in 1..30) {
            delay(1500)
            val status = refreshBackendLink(refreshWidgetsToo = true)
            if (status.agentReady) {
                return status
            }
        }
        return backendStatus
    }

    fun buildUnavailableReply(status: BackendStatus, attemptedAutoStart: Boolean): BackendReply {
        val detail = status.detail.ifBlank { "Backend unavailable" }
        val actorUnavailable = status.online && !status.actorOnline
        val guidance = when {
            !termuxBridgeStatus.termuxInstalled ->
                "Install Termux to let the launcher manage Gemma."
            !termuxBridgeStatus.runCommandPermissionGranted ->
                "Grant Gemma Launcher the Android permission to run commands in Termux, then retry."
            attemptedAutoStart ->
                "The launcher asked Termux to restore Gemma, but the model is still not ready. Run ./tools/configure_termux_bridge.sh in Termux if needed, then retry."
            else ->
                "Use Start Gemma or Restart Gemma from the agent layer, then retry."
        }
        return BackendReply(
            response = if (actorUnavailable) {
                "Gemma backend is up, but the actor model is not ready. $guidance"
            } else {
                "Gemma backend is offline. $guidance"
            },
            traceSummary = listOf(
                if (actorUnavailable) "launcher: actor unavailable" else "launcher: backend offline",
                "detail: $detail",
                "termux: ${termuxBridgeStatus.detail}",
            )
        )
    }

    LaunchedEffect(Unit) {
        apps = appSource()
        usageSnapshot = usageStore.snapshot()
        backendStatus = fetchBackendStatus()
        if (!backendStatus.agentReady && termuxBridgeStatus.canDispatchCommands) {
            val shouldRestart = backendStatus.online && !backendStatus.actorOnline
            backendStatus = requestBackendStart(restart = shouldRestart, addTurn = false)
        }
        widgets = refreshWidgets(backendStatus)
    }

    fun backendRouteForReply(reply: BackendReply, forcedGemma: Boolean): Pair<String, String> {
        return when (reply.mode) {
            "fast_path" -> {
                if (forcedGemma) {
                    "Native" to "Gemma request via deterministic tool"
                } else {
                    "Native" to "Deterministic tool route"
                }
            }
            else -> {
                if (forcedGemma) {
                    "Gemma" to "Explicit Gemma request"
                } else {
                    "Gemma" to "On-device reasoning"
                }
            }
        }
    }

    fun sendMessage(message: String, forceGemma: Boolean = false) {
        if (message.isBlank() || loading) return
        if (!forceGemma && handleLauncherResolution(message)) return
        val normalizedMessage = if (forceGemma) {
            message.removePrefix("gemma:").trim()
                .removePrefix("ask gemma").trim()
                .ifBlank { message.trim() }
        } else {
            message.trim()
        }
        recordDecision(
            LauncherDecisionRecord(
                query = normalizedMessage,
                route = "Gemma",
                detail = if (forceGemma) "Explicit Gemma route" else "Agent reasoning",
            )
        )
        loading = true
        turns.add(
            ChatTurn(
                user = normalizedMessage,
                agent = "Gemma is thinking...",
                route = "Gemma",
                routeDetail = if (forceGemma) "Explicit Gemma request" else "Agent reasoning",
                pending = true,
            )
        )
        scope.launch {
            var status = if (backendStatus.online) backendStatus else refreshBackendLink()
            var attemptedAutoStart = false
            if (!status.online && termuxBridgeStatus.canDispatchCommands) {
                attemptedAutoStart = true
                status = requestBackendStart(restart = false, addTurn = false)
            }
            if (!status.online) {
                val reply = buildUnavailableReply(status, attemptedAutoStart)
                turns[turns.lastIndex] = ChatTurn(
                    user = normalizedMessage,
                    agent = reply.response,
                    route = "Gemma",
                    routeDetail = "Backend unavailable",
                )
                lastTraceSummary = reply.traceSummary
                loading = false
                return@launch
            }
            var reply = runCatching { backendChat(normalizedMessage) }.getOrElse {
                backendStatus = BackendStatus(
                    checked = true,
                    online = false,
                    actorOnline = false,
                    detail = it.message ?: "Backend unavailable",
                )
                buildUnavailableReply(backendStatus, attemptedAutoStart = false)
            }
            if (reply.response.startsWith("Error: actor engine is offline")) {
                if (termuxBridgeStatus.canDispatchCommands) {
                    status = requestBackendStart(restart = true, addTurn = false)
                    reply = if (status.agentReady) {
                        runCatching { backendChat(normalizedMessage) }.getOrElse {
                            buildUnavailableReply(status, attemptedAutoStart = true)
                        }
                    } else {
                        buildUnavailableReply(status, attemptedAutoStart = true)
                    }
                } else {
                    reply = buildUnavailableReply(
                        BackendStatus(
                            checked = true,
                            online = true,
                            actorOnline = false,
                            detail = "Backend is reachable, but the actor model is not ready yet.",
                        ),
                        attemptedAutoStart = false,
                    )
                }
            }
            val (route, routeDetail) = backendRouteForReply(reply, forceGemma)
            turns[turns.lastIndex] = ChatTurn(
                user = normalizedMessage,
                agent = reply.response,
                route = route,
                routeDetail = routeDetail,
            )
            lastTraceSummary = reply.traceSummary
            loading = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
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
                TopStatusBar(
                    loading = loading,
                    backendStatus = backendStatus,
                    termuxBridgeStatus = termuxBridgeStatus,
                    lastRoute = lastDecisionRoute,
                    onAgent = { overlay = OverlaySheet.Agent },
                    onPhone = { overlay = OverlaySheet.Phone },
                    onDebug = { overlay = OverlaySheet.Debug }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ChatHome(
                    turns = turns,
                    traceVisible = traceVisible,
                    lastTraceSummary = lastTraceSummary,
                    backendStatus = backendStatus,
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (homeSuggestions.isNotEmpty()) {
                    HomeInputSuggestionRow(
                        suggestions = homeSuggestions,
                        onSelect = { suggestion ->
                            val query = input.trim().ifBlank {
                                when (suggestion) {
                                    is HomeInputSuggestion.App -> suggestion.entry.label
                                    is HomeInputSuggestion.Native -> suggestion.action.label
                                }
                            }
                            input = ""
                            when (suggestion) {
                                is HomeInputSuggestion.App -> executeAppLaunch(query, suggestion.entry)
                                is HomeInputSuggestion.Native -> executeNativeAction(query, suggestion.action)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                CommandBar(
                    value = input,
                    loading = loading,
                    backendStatus = backendStatus,
                    onValueChange = { input = it },
                    onSend = {
                        val outgoing = input
                        input = ""
                        sendMessage(outgoing)
                    },
                    onAskGemma = {
                        val outgoing = input
                        input = ""
                        sendMessage(outgoing, forceGemma = true)
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                LauncherDock(
                    dockApps = dockApps,
                    onLaunchApp = { entry ->
                        recordLaunch(entry)
                        launchApp(entry)
                    },
                    onApps = {
                        drawerQuery = ""
                        drawerSuggestions = emptyList()
                        overlay = OverlaySheet.Apps
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
                OverlaySheet.Apps -> AppDrawerSheet(
                    apps = apps,
                    usage = usageSnapshot,
                    pinnedApps = pinnedApps,
                    initialQuery = drawerQuery,
                    suggestedApps = drawerSuggestions,
                    recentApps = usageSnapshot.recentPackages.mapNotNull { pkg ->
                        apps.firstOrNull { it.packageName == pkg }
                    },
                    onTogglePinned = { entry -> togglePinned(entry) },
                    launchApp = { entry ->
                        recordLaunch(entry)
                        launchApp(entry)
                    }
                )
                OverlaySheet.Agent -> AgentSheet(
                    turns = turns,
                    decisions = recentDecisions,
                    backendStatus = backendStatus,
                    termuxBridgeStatus = termuxBridgeStatus,
                    onRefreshBackend = {
                        scope.launch { refreshBackendLink(refreshWidgetsToo = true) }
                    },
                    onStartBackend = {
                        scope.launch { requestBackendStart(restart = false, addTurn = true) }
                    },
                    onRestartBackend = {
                        scope.launch { requestBackendStart(restart = true, addTurn = true) }
                    },
                    onGrantTermuxPermission = { requestTermuxPermission() },
                    onOpenLauncherSettings = { openLauncherSettings() },
                    onOpenTermuxSettings = { openTermuxSettings() },
                    onOpenTermuxOverlaySettings = { openTermuxOverlaySettings() },
                    onRecall = { sendMessage("recall what you know about this project") }
                )
                OverlaySheet.Phone -> PhoneSheet(widgets = widgets, onRefresh = {
                    scope.launch { refreshBackendLink(refreshWidgetsToo = true) }
                }, recentActivity = recentActivity, onQuickAction = { action ->
                    executeNativeAction(action.label, action)
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
private fun TopStatusBar(
    loading: Boolean,
    backendStatus: BackendStatus,
    termuxBridgeStatus: TermuxBridgeStatus,
    lastRoute: String?,
    onAgent: () -> Unit,
    onPhone: () -> Unit,
    onDebug: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xCC08141C))) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Gemma Launcher", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        "Chat-first home shell",
                        color = Color(0xFF7FA4B2),
                        fontSize = 11.sp,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = onAgent) {
                        Icon(Icons.Rounded.Memory, contentDescription = "Agent layer", tint = Color(0xFF6EE7D2))
                    }
                    IconButton(onClick = onPhone) {
                        Icon(Icons.Rounded.Android, contentDescription = "Phone layer", tint = Color(0xFF6EE7D2))
                    }
                    IconButton(onClick = onDebug) {
                        Icon(Icons.Rounded.BugReport, contentDescription = "Debug layer", tint = Color(0xFF6EE7D2))
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (loading) {
                    item {
                        StatusChip("Busy")
                    }
                }
                item {
                    StatusChip(
                        when {
                            !backendStatus.checked -> "Checking Link"
                            backendStatus.online -> "Gemma Online"
                            else -> "Gemma Offline"
                        },
                        containerColor = when {
                            !backendStatus.checked -> Color(0x33405D6C)
                            backendStatus.online -> Color(0x33448F75)
                            else -> Color(0x33644545)
                        }
                    )
                }
                if (backendStatus.checked && backendStatus.online) {
                    item {
                        StatusChip(
                            if (backendStatus.actorOnline) "Actor Ready" else "Actor Down",
                            containerColor = if (backendStatus.actorOnline) Color(0x33306A58) else Color(0x33635B2D)
                        )
                    }
                } else {
                    val bridgeLabel = when {
                        !termuxBridgeStatus.termuxInstalled -> "No Termux"
                        !termuxBridgeStatus.runCommandPermissionGranted -> "Grant Permission"
                        else -> "Bridge Ready"
                    }
                    item {
                        StatusChip(bridgeLabel, containerColor = Color(0x33305A72))
                    }
                }
                if (!lastRoute.isNullOrBlank()) {
                    item {
                        StatusChip("Last: $lastRoute")
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ChatHome(
    turns: List<ChatTurn>,
    traceVisible: Boolean,
    lastTraceSummary: List<String>,
    backendStatus: BackendStatus,
) {
    Card(
        modifier = Modifier.weight(1f),
        colors = CardDefaults.cardColors(containerColor = Color(0xAA08141C))
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Chat Home", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Gemma is the main interaction layer. Search apps, launch actions, or ask for reasoning here.",
                        color = Color(0xFF9FB8C4),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
                StatusChip(
                    if (backendStatus.agentReady) "Gemma Ready" else if (backendStatus.online) "Model Warming" else "Local Only",
                    containerColor = if (backendStatus.agentReady) Color(0x33306A58) else Color(0x33305A72)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 220.dp)
            ) {
                items(turns) { turn ->
                    if (turn.user.isNotBlank()) {
                        MessageBubble(text = turn.user, label = "You", detail = "", isUser = true)
                    }
                    MessageBubble(
                        text = turn.agent,
                        label = turn.route,
                        detail = turn.routeDetail,
                        isUser = false,
                        pending = turn.pending,
                    )
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
}

@Composable
private fun MessageBubble(
    text: String,
    label: String,
    detail: String,
    isUser: Boolean,
    pending: Boolean = false,
) {
    val container = if (isUser) Color(0xFF8CEEDD) else Color(0xFF142734)
    val content = if (isUser) Color(0xFF041017) else Color.White
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .widthIn(max = 440.dp)
                .padding(vertical = 6.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = container)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BubbleRouteChip(label = label, isUser = isUser, pending = pending)
                    if (detail.isNotBlank()) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            detail,
                            color = if (isUser) Color(0xFF16323B) else Color(0xFF7FA4B2),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = text,
                    color = content,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun BubbleRouteChip(label: String, isUser: Boolean, pending: Boolean) {
    val containerColor = when {
        isUser -> Color(0x3316302C)
        pending -> Color(0x335C4B19)
        label == "Gemma" -> Color(0x33306A58)
        label == "Native" || label == "Phone" -> Color(0x33305A72)
        label == "App" -> Color(0x333B4A78)
        else -> Color(0x332D4D5C)
    }
    val textColor = if (isUser) Color(0xFF0B1D22) else Color(0xFFC7D9E3)
    Box(
        modifier = Modifier
            .background(containerColor, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            if (pending) "$label Thinking" else label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LauncherDock(
    dockApps: List<LauncherEntry>,
    onLaunchApp: (LauncherEntry) -> Unit,
    onApps: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xDD08141C))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            dockApps.take(4).forEach { app ->
                DockApp(app = app, onClick = { onLaunchApp(app) }, modifier = Modifier.weight(1f))
            }
            repeat(4 - dockApps.take(4).size) {
                DockPlaceholder(modifier = Modifier.weight(1f))
            }
            DockAction("Apps", Icons.Rounded.Dashboard, onApps, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DockAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0x441A3342))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label, tint = Color(0xFF6EE7D2))
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DockApp(
    app: LauncherEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0x441A3342))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppIcon(app = app, modifier = Modifier.size(34.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(app.label, color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DockPlaceholder(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0x221A3342))
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp))
    }
}

@Composable
private fun CommandBar(
    value: String,
    loading: Boolean,
    backendStatus: BackendStatus,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onAskGemma: () -> Unit,
) {
    val hint = if (backendStatus.checked && !backendStatus.online) {
        "Backend offline. Search apps or launch locally"
    } else {
        "Search apps or ask Gemma"
    }
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xDD08141C))) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(hint) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                enabled = !loading,
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RouteActionButton(
                    label = "Search / Launch",
                    icon = Icons.Rounded.Search,
                    onClick = onSend,
                    enabled = !loading && value.isNotBlank(),
                    modifier = Modifier.weight(1f),
                )
                RouteActionButton(
                    label = "Ask Gemma",
                    icon = Icons.Rounded.Memory,
                    onClick = onAskGemma,
                    enabled = !loading && value.isNotBlank(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RouteActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val container = if (enabled) Color(0x55305A72) else Color(0x22305A72)
    val tint = if (enabled) Color(0xFF6EE7D2) else Color(0xFF5A7380)
    Card(
        modifier = modifier.then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = label, tint = tint)
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, color = if (enabled) Color.White else Color(0xFF7FA4B2), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HomeInputSuggestionRow(
    suggestions: List<HomeInputSuggestion>,
    onSelect: (HomeInputSuggestion) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(suggestions.take(2)) { suggestion ->
            Card(
                modifier = Modifier
                    .widthIn(min = 150.dp, max = 220.dp)
                    .clickable { onSelect(suggestion) },
                colors = CardDefaults.cardColors(containerColor = Color(0x44203846))
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                    when (suggestion) {
                        is HomeInputSuggestion.App -> {
                            Text("App Match", color = Color(0xFF6EE7D2), fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(suggestion.entry.label, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(suggestion.entry.category.label, color = Color(0xFF7FA4B2), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        is HomeInputSuggestion.Native -> {
                            Text("System Action", color = Color(0xFF6EE7D2), fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(suggestion.action.label, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Native launcher path", color = Color(0xFF7FA4B2), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppDrawerSheet(
    apps: List<LauncherEntry>,
    usage: LauncherUsageSnapshot,
    pinnedApps: List<LauncherEntry>,
    initialQuery: String,
    suggestedApps: List<LauncherEntry>,
    recentApps: List<LauncherEntry>,
    onTogglePinned: (LauncherEntry) -> Unit,
    launchApp: (LauncherEntry) -> Unit
) {
    var query by remember(initialQuery) { mutableStateOf(initialQuery) }
    val normalizedQuery = query.trim().lowercase()
    val visibleApps = remember(apps, query, usage) {
        if (normalizedQuery.isBlank()) {
            apps
        } else {
            rankAppsForQuery(normalizedQuery, apps, usage)
        }
    }
    val categorySections = remember(apps, usage) {
        buildCategorySections(apps, usage)
    }
    val rankedSuggestions = remember(suggestedApps, visibleApps, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            emptyList()
        } else {
            (suggestedApps + visibleApps)
                .distinctBy { it.packageName }
                .take(5)
        }
    }
    val bestMatch = rankedSuggestions.firstOrNull()
    val alternativeMatches = rankedSuggestions.drop(1)
    val hiddenPackages = rankedSuggestions.map { it.packageName }.toSet()
    val remainingQueryApps = visibleApps.filterNot { it.packageName in hiddenPackages }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("App Drawer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search apps") },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White)
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (normalizedQuery.isNotBlank() && bestMatch != null) {
            BestMatchCard(
                app = bestMatch,
                pinned = bestMatch.packageName in usage.pinnedPackages,
                onLaunch = { launchApp(bestMatch) },
                onTogglePinned = { onTogglePinned(bestMatch) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (normalizedQuery.isNotBlank() && alternativeMatches.isNotEmpty()) {
            Text("Other Matches", color = Color(0xFF6EE7D2), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRowRecentApps(
                recentApps = alternativeMatches,
                launchApp = launchApp,
                onTogglePinned = onTogglePinned,
                pinnedPackages = usage.pinnedPackages
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (normalizedQuery.isBlank() && pinnedApps.isNotEmpty()) {
            Text("Pinned Apps", color = Color(0xFF6EE7D2), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRowRecentApps(
                recentApps = pinnedApps,
                launchApp = launchApp,
                onTogglePinned = onTogglePinned,
                pinnedPackages = usage.pinnedPackages
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (normalizedQuery.isBlank() && recentApps.isNotEmpty()) {
            Text("Recent Apps", color = Color(0xFF6EE7D2), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRowRecentApps(
                recentApps = recentApps,
                launchApp = launchApp,
                onTogglePinned = onTogglePinned,
                pinnedPackages = usage.pinnedPackages
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.height(420.dp)) {
            if (normalizedQuery.isBlank()) {
                categorySections.forEach { (category, sectionApps) ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        CategorySectionHeader(category)
                    }
                    items(sectionApps, key = { it.packageName }) { app ->
                        AppIconTile(
                            app = app,
                            pinned = app.packageName in usage.pinnedPackages,
                            onClick = { launchApp(app) },
                            onTogglePinned = { onTogglePinned(app) }
                        )
                    }
                }
            } else {
                items(remainingQueryApps, key = { it.packageName }) { app ->
                    AppIconTile(
                        app = app,
                        pinned = app.packageName in usage.pinnedPackages,
                        onClick = { launchApp(app) },
                        onTogglePinned = { onTogglePinned(app) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LazyRowRecentApps(
    recentApps: List<LauncherEntry>,
    launchApp: (LauncherEntry) -> Unit,
    onTogglePinned: (LauncherEntry) -> Unit,
    pinnedPackages: List<String>
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        recentApps.take(4).forEach { app ->
            Card(
                modifier = Modifier.weight(1f).clickable { launchApp(app) },
                colors = CardDefaults.cardColors(containerColor = Color(0x44203846))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = { onTogglePinned(app) }) {
                            Icon(
                                Icons.Rounded.PushPin,
                                contentDescription = "Pin ${app.label}",
                                tint = if (app.packageName in pinnedPackages) Color(0xFF6EE7D2) else Color(0xFF5A7380)
                            )
                        }
                    }
                    AppIcon(app = app, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(app.label, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun BestMatchCard(
    app: LauncherEntry,
    pinned: Boolean,
    onLaunch: () -> Unit,
    onTogglePinned: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onLaunch),
        colors = CardDefaults.cardColors(containerColor = Color(0x44305A4A))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(app = app, modifier = Modifier.size(52.dp))
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Best Match", color = Color(0xFF6EE7D2), fontSize = 11.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(app.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(app.category.label, color = Color(0xFFC7D9E3), fontSize = 12.sp)
            }
            IconButton(onClick = onTogglePinned) {
                Icon(
                    Icons.Rounded.PushPin,
                    contentDescription = "Pin ${app.label}",
                    tint = if (pinned) Color(0xFF6EE7D2) else Color(0xFF5A7380)
                )
            }
        }
    }
}

@Composable
private fun CategorySectionHeader(category: LauncherCategory) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(category.label, color = Color(0xFF6EE7D2), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AppIconTile(
    app: LauncherEntry,
    pinned: Boolean,
    onClick: () -> Unit,
    onTogglePinned: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(8.dp)
            .clickable(onClick = onClick)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppIcon(app = app, modifier = Modifier.size(52.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(app.label, color = Color.White, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(2.dp))
            Text(app.category.label, color = Color(0xFF7FA4B2), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(
            onClick = onTogglePinned,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Icon(
                Icons.Rounded.PushPin,
                contentDescription = "Pin ${app.label}",
                tint = if (pinned) Color(0xFF6EE7D2) else Color(0xFF5A7380)
            )
        }
    }
}

@Composable
private fun AppIcon(app: LauncherEntry, modifier: Modifier = Modifier) {
    if (app.icon != null) {
        AndroidView(
            factory = { context ->
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    clipToOutline = true
                }
            },
            update = { view -> view.setImageDrawable(app.icon) },
            modifier = modifier
                .background(Color(0x332D4D5C), CircleShape)
                .padding(8.dp)
        )
    } else {
        Box(
            modifier = modifier.background(Color(0x332D4D5C), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(app.label.take(1), color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AgentSheet(
    turns: List<ChatTurn>,
    decisions: List<LauncherDecisionRecord>,
    backendStatus: BackendStatus,
    termuxBridgeStatus: TermuxBridgeStatus,
    onRefreshBackend: () -> Unit,
    onStartBackend: () -> Unit,
    onRestartBackend: () -> Unit,
    onGrantTermuxPermission: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
    onOpenTermuxSettings: () -> Unit,
    onOpenTermuxOverlaySettings: () -> Unit,
    onRecall: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Agent Layer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Row {
                TextButton(onClick = onRefreshBackend) { Text("Refresh Link") }
                TextButton(onClick = onRecall) { Text("Recall") }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        BackendStatusCard(
            status = backendStatus,
            termuxBridgeStatus = termuxBridgeStatus,
            onStartBackend = onStartBackend,
            onRestartBackend = onRestartBackend,
            onGrantTermuxPermission = onGrantTermuxPermission,
            onOpenLauncherSettings = onOpenLauncherSettings,
            onOpenTermuxSettings = onOpenTermuxSettings,
            onOpenTermuxOverlaySettings = onOpenTermuxOverlaySettings,
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (decisions.isNotEmpty()) {
            Text("Recent Decisions", color = Color(0xFF6EE7D2), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            decisions.take(4).forEach { decision ->
                DecisionCard(decision = decision)
                Spacer(modifier = Modifier.height(6.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
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
private fun PhoneSheet(
    widgets: List<WidgetState>,
    onRefresh: () -> Unit,
    recentActivity: List<LauncherActivityItem>,
    onQuickAction: (NativeLauncherAction) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Phone Layer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            TextButton(onClick = onRefresh) { Text("Refresh") }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text("Quick Actions", color = Color(0xFF6EE7D2), fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        QuickActionRow(
            actions = HOME_QUICK_ACTIONS,
            onQuickAction = onQuickAction
        )
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
        if (recentActivity.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Recent Activity", color = Color(0xFF6EE7D2), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            recentActivity.take(5).forEach { activity ->
                RecentActivityCard(activity = activity)
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun BackendStatusCard(
    status: BackendStatus,
    termuxBridgeStatus: TermuxBridgeStatus,
    onStartBackend: () -> Unit,
    onRestartBackend: () -> Unit,
    onGrantTermuxPermission: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
    onOpenTermuxSettings: () -> Unit,
    onOpenTermuxOverlaySettings: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0x44203846)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Gemma Link", color = Color.White, fontWeight = FontWeight.SemiBold)
                StatusChip(
                    when {
                        !status.checked -> "Checking Link"
                        status.online -> "Gemma Online"
                        else -> "Gemma Offline"
                    },
                    containerColor = when {
                        !status.checked -> Color(0x33405D6C)
                        status.online -> Color(0x33448F75)
                        else -> Color(0x33644545)
                    }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(status.detail, color = Color(0xFFC7D9E3))
            Spacer(modifier = Modifier.height(8.dp))
            Text(termuxBridgeStatus.detail, color = Color(0xFF7FA4B2), fontSize = 11.sp)
            if (status.checked && status.online) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (status.actorOnline) "Actor Ready" else "Actor Down",
                    color = if (status.actorOnline) Color(0xFF8CEEDD) else Color(0xFFF8B84E),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                status.actorModel?.let { model ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(model, color = Color(0xFF7FA4B2), fontSize = 11.sp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                when {
                    !termuxBridgeStatus.termuxInstalled -> {
                        Text(
                            "Install Termux to let the launcher start Gemma automatically.",
                            color = Color(0xFF7FA4B2),
                            fontSize = 11.sp,
                        )
                    }
                    !termuxBridgeStatus.runCommandPermissionGranted -> {
                        ControlButton(
                            label = "Grant Termux Permission",
                            onClick = onGrantTermuxPermission,
                            modifier = Modifier.weight(1f)
                        )
                        ControlButton(
                            label = "Launcher Settings",
                            onClick = onOpenLauncherSettings,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    else -> {
                        ControlButton(
                            label = if (status.agentReady) "Restart Gemma" else if (status.online) "Recover Model" else "Start Gemma",
                            onClick = if (status.agentReady || status.online) onRestartBackend else onStartBackend,
                            modifier = Modifier.weight(1f)
                        )
                        ControlButton(
                            label = "Termux Settings",
                            onClick = onOpenTermuxSettings,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            if (termuxBridgeStatus.canDispatchCommands) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Bridge setup: 1) grant the Termux run-command permission, 2) enable allow-external-apps = true in Termux. You should not need to browse a directory picker for this flow.",
                    color = Color(0xFF7FA4B2),
                    fontSize = 11.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ControlButton(
                        label = "Termux Settings",
                        onClick = onOpenTermuxSettings,
                        modifier = Modifier.weight(1f)
                    )
                    ControlButton(
                        label = "Termux Overlay",
                        onClick = onOpenTermuxOverlaySettings,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "If Termux flashes a brief popup about drawing over apps, open Termux Overlay and enable it.",
                    color = Color(0xFF7FA4B2),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun ControlButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0x55305A72))
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
private fun StatusChip(
    label: String,
    containerColor: Color = Color(0x332D4D5C),
) {
    Box(
        modifier = Modifier
            .background(containerColor, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = Color(0xFFC7D9E3), fontSize = 11.sp)
    }
}

@Composable
private fun QuickActionRow(
    actions: List<NativeLauncherAction>,
    onQuickAction: (NativeLauncherAction) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(actions) { action ->
            Card(
                modifier = Modifier
                    .widthIn(min = 110.dp, max = 160.dp)
                    .clickable { onQuickAction(action) },
                colors = CardDefaults.cardColors(containerColor = Color(0x44203846))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(action.label, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun RecentActivityCard(activity: LauncherActivityItem) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0x44203846)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(activity.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(2.dp))
                Text(activity.subtitle, color = Color(0xFF7FA4B2), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            StatusChip(activity.kind)
        }
    }
}

@Composable
private fun DecisionCard(decision: LauncherDecisionRecord) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0x44203846)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    decision.detail,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                StatusChip(decision.route)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                decision.query,
                color = Color(0xFF7FA4B2),
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun buildDockApps(apps: List<LauncherEntry>, usage: LauncherUsageSnapshot): List<LauncherEntry> {
    val pinned = usage.pinnedPackages.mapNotNull { pkg ->
        apps.firstOrNull { it.packageName == pkg }
    }
    val recent = usage.recentPackages.mapNotNull { pkg ->
        apps.firstOrNull { it.packageName == pkg && pkg !in usage.pinnedPackages }
    }
    val frequent = apps
        .sortedByDescending { usage.launchCounts[it.packageName] ?: 0 }
        .filterNot { it.packageName in usage.pinnedPackages || it.packageName in usage.recentPackages }

    return buildList {
        pinned.forEach(::add)
        recent.forEach(::add)
        frequent.forEach(::add)
    }.distinctBy { it.packageName }.take(4)
}

private val HOME_QUICK_ACTIONS = listOf(
    NativeLauncherAction.Wifi,
    NativeLauncherAction.Bluetooth,
    NativeLauncherAction.Notifications,
    NativeLauncherAction.Camera,
)

private sealed interface HomeInputSuggestion {
    data class App(val entry: LauncherEntry) : HomeInputSuggestion
    data class Native(val action: NativeLauncherAction) : HomeInputSuggestion
}

private data class LauncherActivityItem(
    val title: String,
    val subtitle: String,
    val kind: String,
)

private fun buildRecentActivityItems(
    apps: List<LauncherEntry>,
    usage: LauncherUsageSnapshot,
): List<LauncherActivityItem> {
    return usage.recentActivityKeys.mapNotNull { key ->
        when {
            key.startsWith("app:") -> {
                val packageName = key.removePrefix("app:")
                val app = apps.firstOrNull { it.packageName == packageName } ?: return@mapNotNull null
                LauncherActivityItem(
                    title = app.label,
                    subtitle = app.category.label,
                    kind = "App"
                )
            }
            key.startsWith("native:") -> {
                val actionName = key.removePrefix("native:")
                val action = runCatching { NativeLauncherAction.valueOf(actionName) }.getOrNull() ?: return@mapNotNull null
                LauncherActivityItem(
                    title = action.label,
                    subtitle = "System action",
                    kind = "System"
                )
            }
            else -> null
        }
    }
}

private fun buildHomeInputSuggestions(
    query: String,
    apps: List<LauncherEntry>,
    usage: LauncherUsageSnapshot,
): List<HomeInputSuggestion> {
    val normalized = query.trim()
    if (normalized.length < 2 || normalized.endsWith("?")) return emptyList()
    if (normalized.split(" ").count { it.isNotBlank() } > 4) return emptyList()

    val nativeActions = rankNativeLauncherActions(normalized).take(1)
    val nativeSuggestions = nativeActions.map { HomeInputSuggestion.Native(it) }

    val hiddenLabels = nativeActions
        .map { action -> normalizeLauncherLabel(action.label) }
        .toSet()

    val appSuggestions = rankAppsForQuery(normalized, apps, usage)
        .filterNot { normalizeLauncherLabel(it.label) in hiddenLabels }
        .take(if (nativeSuggestions.isEmpty()) 2 else 1)
        .map { HomeInputSuggestion.App(it) }

    return (nativeSuggestions + appSuggestions).take(2)
}

private fun normalizeLauncherLabel(value: String): String {
    return value.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")
}
