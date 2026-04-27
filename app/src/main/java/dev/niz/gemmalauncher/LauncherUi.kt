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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherApp(
    appSource: () -> List<LauncherEntry>,
    usageStore: LauncherUsageStore,
    launchApp: (LauncherEntry) -> Unit,
    launchNativeAction: (NativeLauncherAction) -> Unit,
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
    var drawerQuery by remember { mutableStateOf("") }
    var drawerSuggestions by remember { mutableStateOf(emptyList<LauncherEntry>()) }
    var usageSnapshot by remember { mutableStateOf(LauncherUsageSnapshot()) }
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
        usageSnapshot = usageStore.snapshot()
        widgets = refreshWidgets()
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

    fun handleLauncherResolution(message: String): Boolean {
        return when (val resolution = resolveHomeIntent(message = message, apps = apps, usage = usageSnapshot)) {
            is HomeIntentResolution.LaunchNativeAction -> {
                recordNativeAction(resolution.action)
                recordDecision(
                    LauncherDecisionRecord(
                        query = message,
                        route = "System",
                        detail = resolution.action.label,
                    )
                )
                turns.add(ChatTurn(user = message, agent = resolution.action.openingMessage))
                launchNativeAction(resolution.action)
                true
            }
            is HomeIntentResolution.LaunchApp -> {
                recordLaunch(resolution.entry)
                recordDecision(
                    LauncherDecisionRecord(
                        query = message,
                        route = "App",
                        detail = resolution.entry.label,
                    )
                )
                turns.add(ChatTurn(user = message, agent = "Opening ${resolution.entry.label}."))
                launchApp(resolution.entry)
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
                turns.add(ChatTurn(user = message, agent = resolution.message))
                true
            }
            is HomeIntentResolution.SendToAgent -> false
        }
    }

    fun sendMessage(message: String) {
        if (message.isBlank() || loading) return
        if (handleLauncherResolution(message)) return
        recordDecision(
            LauncherDecisionRecord(
                query = message,
                route = "Gemma",
                detail = "Agent reasoning",
            )
        )
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
                    appCount = apps.size,
                    lastRoute = lastDecisionRoute,
                    onAgent = { overlay = OverlaySheet.Agent },
                    onPhone = { overlay = OverlaySheet.Phone },
                    onDebug = { overlay = OverlaySheet.Debug }
                )
                Spacer(modifier = Modifier.height(10.dp))
                HomeHero(
                    recentActivity = recentActivity,
                    onQuickAction = { action ->
                        recordNativeAction(action)
                        recordDecision(
                            LauncherDecisionRecord(
                                query = action.label,
                                route = "System",
                                detail = action.label,
                            )
                        )
                        turns.add(ChatTurn(user = action.label, agent = action.openingMessage))
                        launchNativeAction(action)
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
                ChatHome(turns = turns, traceVisible = traceVisible, lastTraceSummary = lastTraceSummary)
                Spacer(modifier = Modifier.height(10.dp))
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
                    onRecall = { sendMessage("recall what you know about this project") }
                )
                OverlaySheet.Phone -> PhoneSheet(widgets = widgets, onRefresh = {
                    scope.launch { widgets = refreshWidgets() }
                }, recentActivity = recentActivity, onQuickAction = { action ->
                    recordNativeAction(action)
                    recordDecision(
                        LauncherDecisionRecord(
                            query = action.label,
                            route = "System",
                            detail = action.label,
                        )
                    )
                    turns.add(ChatTurn(user = action.label, agent = action.openingMessage))
                    launchNativeAction(action)
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
    appCount: Int,
    lastRoute: String?,
    onAgent: () -> Unit,
    onPhone: () -> Unit,
    onDebug: () -> Unit,
) {
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
                if (!lastRoute.isNullOrBlank()) {
                    StatusChip("Last: $lastRoute")
                }
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
    }
}

@Composable
private fun HomeHero(
    recentActivity: List<LauncherActivityItem>,
    onQuickAction: (NativeLauncherAction) -> Unit,
) {
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
            Spacer(modifier = Modifier.height(12.dp))
            Text("Quick Actions", color = Color(0xFF6EE7D2), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            QuickActionRow(
                actions = HOME_QUICK_ACTIONS,
                onQuickAction = onQuickAction
            )
            if (recentActivity.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Recent Activity", color = Color(0xFF6EE7D2), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                recentActivity.take(4).forEach { activity ->
                    RecentActivityCard(activity = activity)
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ChatHome(turns: List<ChatTurn>, traceVisible: Boolean, lastTraceSummary: List<String>) {
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
    dockApps: List<LauncherEntry>,
    onLaunchApp: (LauncherEntry) -> Unit,
    onApps: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xDD08141C))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
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
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppIcon(app = app, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(app.label, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                label = { Text("Search apps or ask Gemma") },
                placeholder = { Text("Search apps or ask Gemma") },
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
            label = { Text("Search apps") }
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
    onRecall: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Agent Layer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            TextButton(onClick = onRecall) { Text("Recall") }
        }
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
            Text("Recent Launcher Activity", color = Color(0xFF6EE7D2), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            recentActivity.take(5).forEach { activity ->
                RecentActivityCard(activity = activity)
                Spacer(modifier = Modifier.height(6.dp))
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

@Composable
private fun QuickActionRow(
    actions: List<NativeLauncherAction>,
    onQuickAction: (NativeLauncherAction) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        actions.forEach { action ->
            Card(
                modifier = Modifier.weight(1f).clickable { onQuickAction(action) },
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
