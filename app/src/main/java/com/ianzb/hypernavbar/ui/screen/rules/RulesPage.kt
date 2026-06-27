package com.ianzb.hypernavbar.ui.screen.rules

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ianzb.hypernavbar.R
import com.ianzb.hypernavbar.RootHelper
import com.ianzb.hypernavbar.rules.RootApplier
import com.ianzb.hypernavbar.rules.RuleCombiner
import com.ianzb.hypernavbar.rules.RuleConfigSource
import com.ianzb.hypernavbar.rules.RuleConverter
import com.ianzb.hypernavbar.rules.RuleFetcher
import com.ianzb.hypernavbar.rules.RuleType
import com.ianzb.hypernavbar.rules.RulesManager
import com.ianzb.hypernavbar.ui.util.BlurredBar
import com.ianzb.hypernavbar.ui.util.pageScrollModifiers
import com.ianzb.hypernavbar.ui.util.rememberBlurBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.basic.Text as MiuixText

private const val EMPTY_JSON_TEMPLATE = """{
    "dataVersion": "999999",
    "name": "沉浸规则",
    "modules": "navigation_bar_immersive_application_config_new",
    "modifyApps": "modifyApps",
    "NBIRules": {}
}"""

private data class PresetSource(val name: String, val summary: String, val url: String)

private val PRESET_SOURCES = listOf(
    PresetSource("官方配置", "由小米官方维护的沉浸规则", "https://drive.ianzb.cn/code/MiNavBarImmerse/official.json"),
    PresetSource("社区配置", "由本项目社区维护的沉浸规则", "https://drive.ianzb.cn/code/MiNavBarImmerse/custom.json"),
)

@Composable
fun RulesPageView(
    applyIntervalMinutes: Int = 0,
    extraBottomPadding: Dp = 0.dp,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val title = stringResource(R.string.tab_rules)

    val configs = remember { mutableStateListOf<RuleConfigSource>() }
    var isApplying by remember { mutableStateOf(false) }
    var updatingIds by remember { mutableStateOf(emptySet<String>()) }
    var mergedAppCount by remember { mutableIntStateOf(0) }
    var lastApplyTime by remember { mutableStateOf(0L) }
    var tick by remember { mutableStateOf(0) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }
    var showActionsSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<RuleConfigSource?>(null) }
    var editingConfig by remember { mutableStateOf<RuleConfigSource?>(null) }
    var actionsTarget by remember { mutableStateOf<RuleConfigSource?>(null) }
    var pendingEditConfig by remember { mutableStateOf<RuleConfigSource?>(null) }
    var pendingDeleteConfig by remember { mutableStateOf<RuleConfigSource?>(null) }
    var hasRoot by remember { mutableStateOf(RootHelper.isRootAvailable) }
    var isCustomApplied by remember { mutableStateOf(false) }
    var ruleType by remember { mutableStateOf(RuleType.CLOUD) }
    var urlInput by remember { mutableStateOf("") }
    var jsonInput by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }
    var intervalInput by remember { mutableStateOf("0") }
    var showPresetSheet by remember { mutableStateOf(false) }

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    fun reloadConfigs() {
        val updated = RulesManager.loadAll(context).sortedBy { it.priority }
        configs.clear()
        configs.addAll(updated)
    }

    fun formatElapsedTime(timestamp: Long, @Suppress("UNUSED_PARAMETER") tick: Int): String {
        if (timestamp == 0L) return "—"
        val diff = System.currentTimeMillis() - timestamp
        if (diff < 1000) return context.getString(R.string.rules_just_now)
        val seconds = (diff / 1000).toInt()
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        return when {
            days > 0 -> "${days}天"
            hours > 0 -> "${hours}小时"
            minutes > 0 -> "${minutes}分钟"
            else -> "${seconds}秒"
        }
    }

    suspend fun fetchAndParseConfigs(): MutableMap<String, RuleFetcher.FetchResult> {
        val results = mutableMapOf<String, RuleFetcher.FetchResult>()
        for (config in configs) {
            if (!config.enabled) continue
            val content = when {
                config.type == RuleType.LOCAL -> config.jsonContent
                config.cachedContent.isNotEmpty() -> config.cachedContent
                else -> RuleFetcher.fetch(config).getOrNull()?.rawJson ?: ""
            }
            if (content.isEmpty()) continue
            RuleFetcher.parseJson(content).onSuccess { result ->
                results[config.id] = result
            }
        }
        return results
    }

    suspend fun saveApplyState(time: Long, count: Int, isCustom: Boolean) {
        withContext(NonCancellable + Dispatchers.IO) {
            RulesManager.saveApplyState(context, time, count, isCustom)
        }
        mergedAppCount = count
        lastApplyTime = time
        if (isCustom) isCustomApplied = true
    }

    // Load persisted state
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            reloadConfigs()
        }
        hasRoot = RootHelper.isRootAvailable || RootHelper.checkRoot()
        mergedAppCount = RulesManager.loadAppliedCount(context)
        lastApplyTime = RulesManager.loadLastApplyTime(context)
        isCustomApplied = RulesManager.loadIsCustomApplied(context)
    }

    // 1s ticker: refresh UI + trigger auto-update + auto-apply
    LaunchedEffect(Unit) {
        val intervalMinutes = applyIntervalMinutes
        val applyIntervalMs = intervalMinutes * 60_000L

        while (true) {
            delay(1000.milliseconds)
            tick++

            if (isApplying || updatingIds.isNotEmpty()) continue

            // Auto-update subscriptions
            val current = configs.toList()
            val toUpdate = current.filter {
                it.enabled && it.type == RuleType.CLOUD && it.refreshIntervalMs > 0 &&
                    System.currentTimeMillis() - it.lastRefreshTime >= it.refreshIntervalMs
            }
            if (toUpdate.isNotEmpty()) {
                val ids = toUpdate.map { it.id }.toSet()
                updatingIds = updatingIds + ids
                var anyUpdated = false
                for (cfg in toUpdate) {
                    RuleFetcher.fetch(cfg).onSuccess { result ->
                        RulesManager.updateRefreshTime(context, cfg.id, System.currentTimeMillis(), result.appCount, result.configName, result.rawJson)
                        anyUpdated = true
                    }
                }
                updatingIds = updatingIds - ids
                if (anyUpdated) reloadConfigs()
                continue
            }

            // Auto-apply rules
            if (applyIntervalMs > 0 && lastApplyTime > 0 &&
                System.currentTimeMillis() - lastApplyTime >= applyIntervalMs
            ) {
                isApplying = true
                scope.launch {
                    try {
                        val cachedResults = fetchAndParseConfigs()
                        if (cachedResults.isNotEmpty()) {
                            val mergedJson = RuleCombiner.combine(configs.toList(), cachedResults)
                            val mode = RuleConverter.detectOsMode()
                            val targetContent = RuleConverter.convert(mergedJson, mode)
                            val targetPath = RuleConverter.getTargetPath(mode)
                            val totalApps = RuleCombiner.getTotalAppCount(cachedResults)
                            if (hasRoot) {
                                RootApplier.applyRules(targetContent, targetPath, context.cacheDir)
                                isCustomApplied = RootApplier.isCustomRulesApplied(targetPath)
                            }
                            saveApplyState(System.currentTimeMillis(), totalApps, isCustomApplied)
                        }
                    } finally {
                        isApplying = false
                    }
                }
            }
        }
    }

    fun resetAddState() {
        showAddSheet = false
        ruleType = RuleType.CLOUD
        urlInput = ""
        jsonInput = ""
        noteInput = ""
        intervalInput = "0"
    }

    fun resetEditState() {
        showEditSheet = false
        editingConfig = null
        urlInput = ""
        jsonInput = ""
        noteInput = ""
        intervalInput = "0"
    }

    fun addConfig() {
        scope.launch {
            val jsonContent = jsonInput.trim()
            when (ruleType) {
                RuleType.CLOUD -> {
                    val url = urlInput.trim()
                    if (url.isEmpty()) return@launch
                    RuleFetcher.fetch(RuleConfigSource(id = "", url = url, type = RuleType.CLOUD)).fold(
                        onSuccess = { result ->
                            val intervalMs = (intervalInput.toIntOrNull() ?: 0) * 60_000L
                            val newConfig = RulesManager.add(context, RuleType.CLOUD, url, name = result.configName, appCount = result.appCount)
                            RulesManager.update(context, newConfig.copy(refreshIntervalMs = intervalMs, note = noteInput.trim(), cachedContent = result.rawJson))
                            reloadConfigs()
                            withContext(Dispatchers.Main) { resetAddState() }
                        },
                        onFailure = { e ->
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, e.message ?: "Fetch failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
                RuleType.LOCAL -> {
                    if (jsonContent.isEmpty()) return@launch
                    RuleFetcher.fetch(RuleConfigSource(id = "", jsonContent = jsonContent, type = RuleType.LOCAL)).fold(
                        onSuccess = { result ->
                            val intervalMs = (intervalInput.toIntOrNull() ?: 0) * 60_000L
                            val newConfig = RulesManager.add(context, RuleType.LOCAL, "", jsonContent, result.configName, result.appCount)
                            RulesManager.update(context, newConfig.copy(refreshIntervalMs = intervalMs, note = noteInput.trim(), cachedContent = jsonContent))
                            reloadConfigs()
                            withContext(Dispatchers.Main) { resetAddState() }
                        },
                        onFailure = { e ->
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, e.message ?: "Parse failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }

    fun editConfig() {
        val dialog = editingConfig ?: return
        val updated = dialog.copy(
            url = urlInput.trim().ifEmpty { dialog.url },
            jsonContent = jsonInput.trim().ifEmpty { dialog.jsonContent },
            note = noteInput.trim(),
            refreshIntervalMs = (intervalInput.toIntOrNull() ?: 0) * 60_000L,
        )
        RulesManager.update(context, updated)
        val idx = configs.indexOfFirst { it.id == updated.id }
        if (idx >= 0) configs[idx] = updated
        resetEditState()
    }

    fun deleteConfig(config: RuleConfigSource) {
        RulesManager.remove(context, config.id)
        reloadConfigs()
        showDeleteDialog = null
    }

    fun moveUp(config: RuleConfigSource) {
        RulesManager.moveUp(context, config.id)
        reloadConfigs()
        showActionsSheet = false
    }

    fun moveDown(config: RuleConfigSource) {
        RulesManager.moveDown(context, config.id)
        reloadConfigs()
        showActionsSheet = false
    }

    fun applyRules() {
        isApplying = true
        scope.launch {
            try {
                val cachedResults = fetchAndParseConfigs()
                if (cachedResults.isNotEmpty()) {
                    val mergedJson = RuleCombiner.combine(configs.toList(), cachedResults)
                    val mode = RuleConverter.detectOsMode()
                    val targetContent = RuleConverter.convert(mergedJson, mode)
                    val targetPath = RuleConverter.getTargetPath(mode)
                    val totalApps = RuleCombiner.getTotalAppCount(cachedResults)
                    if (hasRoot) {
                        RootApplier.applyRules(targetContent, targetPath, context.cacheDir)
                        isCustomApplied = RootApplier.isCustomRulesApplied(targetPath)
                    }
                    saveApplyState(System.currentTimeMillis(), totalApps, isCustomApplied)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.rules_update_success, totalApps), Toast.LENGTH_SHORT).show()
                        if (!hasRoot) {
                            Toast.makeText(context, context.getString(R.string.rules_root_required), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } finally {
                isApplying = false
            }
        }
    }

    LaunchedEffect(ruleType) {
        if (ruleType == RuleType.LOCAL && jsonInput.isEmpty()) {
            jsonInput = EMPTY_JSON_TEMPLATE
        }
    }

    val jsonFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { input ->
                    jsonInput = input.bufferedReader().readText()
                }
            } catch (_: Exception) {
                Toast.makeText(context, "File read failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(pendingEditConfig) {
        val cfg = pendingEditConfig ?: return@LaunchedEffect
        urlInput = cfg.url
        jsonInput = cfg.jsonContent.ifEmpty { EMPTY_JSON_TEMPLATE }
        noteInput = cfg.note
        intervalInput = (cfg.refreshIntervalMs / 60_000).toString()
        editingConfig = cfg
        showActionsSheet = false
        showEditSheet = true
        pendingEditConfig = null
    }

    LaunchedEffect(pendingDeleteConfig) {
        val cfg = pendingDeleteConfig ?: return@LaunchedEffect
        pendingDeleteConfig = null
        showDeleteDialog = cfg
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop, blurActive) {
                TopAppBar(
                    title = title,
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(onClick = { showPresetSheet = true }) {
                            Icon(
                                imageVector = MiuixIcons.Backup,
                                contentDescription = stringResource(R.string.rules_preset),
                                tint = MiuixTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = {
                            resetAddState()
                            showAddSheet = true
                        }) {
                            Icon(
                                imageVector = MiuixIcons.Add,
                                contentDescription = stringResource(R.string.rules_add),
                                tint = MiuixTheme.colorScheme.onSurface
                            )
                        }
                    },
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->

        // Preset sources sheet
        WindowBottomSheet(
            title = stringResource(R.string.rules_preset),
            show = showPresetSheet,
            onDismissRequest = { showPresetSheet = false },
            startAction = {
                IconButton(onClick = { showPresetSheet = false }) {
                    Icon(MiuixIcons.Close, stringResource(R.string.rules_cancel), tint = MiuixTheme.colorScheme.onBackground)
                }
            },
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().scrollEndHaptic().overScrollVertical(),
            ) {
                items(PRESET_SOURCES) { preset ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        onClick = {
                            showPresetSheet = false
                            scope.launch {
                                val result = RuleFetcher.fetch(RuleConfigSource(id = "", url = preset.url, type = RuleType.CLOUD)).getOrNull()
                                if (result != null) {
                                    val newConfig = RulesManager.add(context, RuleType.CLOUD, preset.url, name = result.configName, appCount = result.appCount)
                                    RulesManager.updateRefreshTime(context, newConfig.id, System.currentTimeMillis(), result.appCount, result.configName, result.rawJson)
                                    reloadConfigs()
                                    Toast.makeText(context, context.getString(R.string.rules_preset_added, result.configName), Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, context.getString(R.string.rules_preset_failed), Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        showIndication = true,
                    ) {
                        BasicComponent(
                            title = preset.name,
                            summary = preset.summary,
                        )
                    }
                }
                item {
                    Spacer(Modifier.padding(
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                            WindowInsets.captionBar.asPaddingValues().calculateBottomPadding(),
                    ))
                }
            }
        }

        // Add subscription sheet
        WindowBottomSheet(
            title = stringResource(R.string.rules_add),
            show = showAddSheet,
            onDismissRequest = { resetAddState() },
            startAction = {
                IconButton(onClick = { resetAddState() }) {
                    Icon(MiuixIcons.Close, stringResource(R.string.rules_cancel), tint = MiuixTheme.colorScheme.onBackground)
                }
            },
            endAction = {
                IconButton(onClick = { addConfig() }) {
                    Icon(MiuixIcons.Ok, stringResource(R.string.rules_confirm), tint = MiuixTheme.colorScheme.onBackground)
                }
            },
        ) {
            SubscriptionForm(
                ruleType = ruleType,
                onRuleTypeChange = { ruleType = it },
                urlInput = urlInput,
                onUrlChange = { urlInput = it },
                jsonInput = jsonInput,
                onJsonChange = { jsonInput = it },
                noteInput = noteInput,
                onNoteChange = { noteInput = it },
                intervalInput = intervalInput,
                onIntervalChange = { v -> intervalInput = v.filter(Char::isDigit) },
                jsonFilePicker = jsonFilePicker,
                showInterval = true,
            )
        }

        // Edit subscription sheet
        WindowBottomSheet(
            title = stringResource(R.string.rules_edit),
            show = showEditSheet,
            onDismissRequest = { resetEditState() },
            startAction = {
                IconButton(onClick = { resetEditState() }) {
                    Icon(MiuixIcons.Close, stringResource(R.string.rules_cancel), tint = MiuixTheme.colorScheme.onBackground)
                }
            },
            endAction = {
                IconButton(onClick = { editConfig() }) {
                    Icon(MiuixIcons.Ok, stringResource(R.string.rules_confirm), tint = MiuixTheme.colorScheme.onBackground)
                }
            },
        ) {
            val cfg = editingConfig
            if (cfg != null) {
                SubscriptionForm(
                    ruleType = cfg.type,
                    onRuleTypeChange = {},
                    urlInput = urlInput,
                    onUrlChange = { urlInput = it },
                    jsonInput = jsonInput,
                    onJsonChange = { jsonInput = it },
                    noteInput = noteInput,
                    onNoteChange = { noteInput = it },
                    intervalInput = intervalInput,
                    onIntervalChange = { v -> intervalInput = v.filter(Char::isDigit) },
                    jsonFilePicker = jsonFilePicker,
                    showInterval = cfg.type == RuleType.CLOUD,
                )
            }
        }

        // Delete confirmation dialog
        WindowDialog(
            title = stringResource(R.string.rules_delete),
            summary = stringResource(R.string.rules_delete_confirm),
            show = showDeleteDialog != null,
            onDismissRequest = { showDeleteDialog = null }
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(text = stringResource(R.string.rules_cancel), onClick = { showDeleteDialog = null }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                TextButton(text = stringResource(R.string.rules_confirm), onClick = { showDeleteDialog?.let { deleteConfig(it) } }, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary())
            }
        }

        // Actions sheet (edit/move/delete/refresh)
        WindowBottomSheet(
            title = actionsTarget?.name?.ifEmpty { actionsTarget?.url?.ifEmpty { "本地规则" } } ?: "",
            show = showActionsSheet,
            onDismissRequest = { showActionsSheet = false },
            startAction = {
                IconButton(onClick = { showActionsSheet = false }) {
                    Icon(MiuixIcons.Close, stringResource(R.string.rules_cancel), tint = MiuixTheme.colorScheme.onBackground)
                }
            },
            endAction = {
                val cfg = actionsTarget
                if (cfg?.type == RuleType.CLOUD) {
                    IconButton(onClick = {
                        scope.launch {
                            updatingIds = updatingIds + cfg.id
                            RuleFetcher.fetch(cfg).fold(
                                onSuccess = { result ->
                                    RulesManager.updateRefreshTime(context, cfg.id, System.currentTimeMillis(), result.appCount, result.configName, result.rawJson)
                                    reloadConfigs()
                                    actionsTarget = actionsTarget?.copy(
                                        lastRefreshTime = System.currentTimeMillis(),
                                        appCount = result.appCount,
                                        name = result.configName,
                                        cachedContent = result.rawJson,
                                    )
                                },
                                onFailure = { }
                            )
                            updatingIds = updatingIds - cfg.id
                        }
                    }) {
                        Icon(
                            imageVector = MiuixIcons.Refresh,
                            contentDescription = if (cfg.id in updatingIds) stringResource(R.string.rules_refreshing) else stringResource(R.string.rules_refresh_manual),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                }
            },
        ) {
            val cfg = actionsTarget
            if (cfg != null) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().scrollEndHaptic().overScrollVertical(),
                ) {
                    item {
                        Card(modifier = Modifier.padding(vertical = 6.dp)) {
                            ArrowPreference(title = stringResource(R.string.rules_edit), onClick = { pendingEditConfig = cfg })
                            ArrowPreference(title = stringResource(R.string.rules_move_up), onClick = { moveUp(cfg) })
                            ArrowPreference(title = stringResource(R.string.rules_move_down), onClick = { moveDown(cfg) })
                            ArrowPreference(title = stringResource(R.string.rules_delete), onClick = { showActionsSheet = false; pendingDeleteConfig = cfg })
                        }
                    }
                    item {
                        Spacer(Modifier.padding(
                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                                WindowInsets.captionBar.asPaddingValues().calculateBottomPadding(),
                        ))
                    }
                }
            }
        }

        // Main content
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().pageScrollModifiers(showTopAppBar = true, topAppBarScrollBehavior = scrollBehavior),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + extraBottomPadding
                )
            ) {
                // Apply section - always at top
                item {
                    SmallTitle(text = stringResource(R.string.rules_apply), modifier = Modifier.padding(top = 6.dp))
                    Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                        ArrowPreference(
                            title = stringResource(R.string.rules_apply_now),
                            summary = stringResource(R.string.rules_merged_count, mergedAppCount) +
                                " · " + if (isApplying) stringResource(R.string.rules_refreshing) else formatElapsedTime(lastApplyTime, tick),
                            onClick = if (isApplying) null else ({ applyRules() }),
                        )
                    }
                }

                // Subscriptions section
                item {
                    SmallTitle(text = stringResource(R.string.rules_config_count, configs.size), modifier = Modifier.padding(top = 12.dp))
                }

                if (configs.isEmpty()) {
                    item {
                        MiuixText(
                            text = stringResource(R.string.rules_no_configs),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp)
                        )
                    }
                }

                itemsIndexed(configs.toList()) { _, config ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 6.dp),
                        onClick = { actionsTarget = config; showActionsSheet = true },
                        showIndication = true,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            BasicComponent(
                                title = config.name.ifEmpty { config.url.ifEmpty { "本地规则" } },
                                summary = buildString {
                                    append(if (config.type == RuleType.LOCAL) "本地 · " else "云端 · ")
                                    append(stringResource(R.string.rules_app_count, config.appCount))
                                    if (config.note.isNotEmpty()) append(" · ${config.note}")
                                if (config.type == RuleType.CLOUD) {
                                    append(" · ")
                                    append(if (config.id in updatingIds) stringResource(R.string.rules_refreshing) else formatElapsedTime(config.lastRefreshTime, tick))
                                }
                                },
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = config.enabled,
                                onCheckedChange = { enabled ->
                                    val updated = config.copy(enabled = enabled)
                                    RulesManager.update(context, updated)
                                    val idx = configs.indexOfFirst { it.id == config.id }
                                    if (idx >= 0) configs[idx] = updated
                                },
                                modifier = Modifier.padding(end = 12.dp).size(52.dp, 32.dp),
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun SubscriptionForm(
    ruleType: RuleType,
    onRuleTypeChange: (RuleType) -> Unit,
    urlInput: String,
    onUrlChange: (String) -> Unit,
    jsonInput: String,
    onJsonChange: (String) -> Unit,
    noteInput: String,
    onNoteChange: (String) -> Unit,
    intervalInput: String,
    onIntervalChange: (String) -> Unit,
    jsonFilePicker: androidx.activity.result.ActivityResultLauncher<String>,
    showInterval: Boolean,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().scrollEndHaptic().overScrollVertical(),
    ) {
        item {
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = "云端",
                    onClick = { onRuleTypeChange(RuleType.CLOUD) },
                    modifier = Modifier.weight(1f),
                    colors = if (ruleType == RuleType.CLOUD) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
                )
                TextButton(
                    text = "本地",
                    onClick = { onRuleTypeChange(RuleType.LOCAL) },
                    modifier = Modifier.weight(1f),
                    colors = if (ruleType == RuleType.LOCAL) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
                )
            }
        }
        item {
            if (ruleType == RuleType.CLOUD) {
                TextField(
                    modifier = Modifier.padding(vertical = 4.dp),
                    value = urlInput,
                    onValueChange = onUrlChange,
                    label = stringResource(R.string.rules_add_url),
                    singleLine = true,
                )
            } else {
                TextField(
                    modifier = Modifier.padding(vertical = 4.dp).height(180.dp),
                    value = jsonInput,
                    onValueChange = onJsonChange,
                    label = "JSON 配置内容",
                    singleLine = false,
                )
                TextButton(
                    text = "从文件导入",
                    onClick = { jsonFilePicker.launch("application/json") },
                )
            }
        }
        item {
            TextField(
                modifier = Modifier.padding(vertical = 4.dp),
                value = noteInput,
                onValueChange = onNoteChange,
                label = stringResource(R.string.rules_note),
                singleLine = true,
            )
        }
        if (showInterval) {
            item {
                TextField(
                    modifier = Modifier.padding(vertical = 4.dp),
                    value = intervalInput,
                    onValueChange = onIntervalChange,
                    label = stringResource(R.string.rules_add_interval),
                    singleLine = true,
                )
            }
        }
        item {
            Spacer(Modifier.padding(
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                    WindowInsets.captionBar.asPaddingValues().calculateBottomPadding(),
            ))
        }
    }
}
