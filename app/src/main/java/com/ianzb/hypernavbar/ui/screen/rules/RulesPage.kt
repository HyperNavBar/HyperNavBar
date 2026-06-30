package com.ianzb.hypernavbar.ui.screen.rules

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ianzb.hypernavbar.R
import com.ianzb.hypernavbar.RootHelper
import com.ianzb.hypernavbar.rules.RootApplier
import com.ianzb.hypernavbar.rules.RuleCombiner
import com.ianzb.hypernavbar.rules.RuleConfigSource
import com.ianzb.hypernavbar.rules.RuleConverter
import com.ianzb.hypernavbar.rules.RuleFetcher
import com.ianzb.hypernavbar.rules.RuleType
import com.ianzb.hypernavbar.rules.RulesManager
import com.ianzb.hypernavbar.rules.SystemVersionDetector
import com.ianzb.hypernavbar.ui.util.BlurredBar
import com.ianzb.hypernavbar.ui.util.pageScrollModifiers
import com.ianzb.hypernavbar.ui.util.rememberBlurBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog
import kotlin.time.Duration.Companion.milliseconds
import top.yukonga.miuix.kmp.basic.Text as MiuixText

private fun getEmptyJsonTemplate(): String {
    val date = java.text.SimpleDateFormat("yyMMdd", java.util.Locale.US).format(java.util.Date())
    return """{
    "name": "沉浸规则",
    "dataVersion": "$date",
    "modules": "navigation_bar_immersive_application_config_new",
    "modifyApps": "modifyApps",
    "NBIRules": {}
}"""
}

private data class PresetSource(val name: String, val summary: String, val url: String)

private val PRESET_SOURCES = listOf(
    PresetSource("官方规则源", "由小米官方维护的沉浸规则", "https://drive.ianzb.cn/code/HyperNavBarRules/official.json"),
    PresetSource("社区规则源", "由本项目社区维护的沉浸规则", "https://drive.ianzb.cn/code/HyperNavBarRules/custom.json"),
)

private fun formatElapsedTime(context: android.content.Context, timestamp: Long, @Suppress("UNUSED_PARAMETER") tick: Int): String {
    if (timestamp == 0L) return "—"
    val diff = System.currentTimeMillis() - timestamp
    if (diff < 1000) return context.getString(R.string.rules_just_now)
    val seconds = (diff / 1000).toInt()
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        days > 0 -> context.getString(R.string.rules_time_day, days)
        hours > 0 -> context.getString(R.string.rules_time_hour, hours)
        minutes > 0 -> context.getString(R.string.rules_time_minute, minutes)
        else -> context.getString(R.string.rules_time_second, seconds)
    }
}

@SuppressLint("LocalContext")
@Composable
fun RulesPageView(
    applyIntervalMinutes: Int = 0,
    extraBottomPadding: Dp = 0.dp,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val title = stringResource(R.string.tab_rules)
    val rulesRootRequiredText = stringResource(R.string.rules_root_required)
    val exportSuccessText = stringResource(R.string.export_success)
    val exportFailedText = stringResource(R.string.export_failed)
    val rulesPresetFailedText = stringResource(R.string.rules_preset_failed)

    val configs = remember { mutableStateListOf<RuleConfigSource>() }
    var isApplying by remember { mutableStateOf(false) }
    var updatingIds by remember { mutableStateOf(emptySet<String>()) }
    var mergedAppCount by remember { mutableIntStateOf(0) }
    var lastApplyTime by remember { mutableLongStateOf(0L) }
    var tick by remember { mutableIntStateOf(0) }
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
    var intervalInput by remember { mutableStateOf("0") }
    var showPresetSheet by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            toastMessage = null
        }
    }
    var isSaving by remember { mutableStateOf(false) }
    var showJsonEditor by remember { mutableStateOf(false) }

    // Drag-to-reorder state
    var draggedConfigId by remember { mutableStateOf<String?>(null) }
    var draggedOriginalIndex by remember { mutableStateOf(-1) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    fun reloadConfigs() {
        val updated = RulesManager.loadAll(context).sortedBy { it.priority }
        configs.clear()
        configs.addAll(updated)
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
    LaunchedEffect(applyIntervalMinutes) {
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
            val applyIntervalMs = applyIntervalMinutes * 60_000L
            if (applyIntervalMs > 0 && lastApplyTime > 0 &&
                System.currentTimeMillis() - lastApplyTime >= applyIntervalMs
            ) {
                isApplying = true
                scope.launch {
                    try {
                        val cachedResults = fetchAndParseConfigs()
                        if (cachedResults.isNotEmpty()) {
                            val mergedJson = RuleCombiner.combine(configs.toList(), cachedResults)
                            val mode = SystemVersionDetector.getEffectiveOsMode(context)?.let { osmode ->
                                try {
                                    RuleConverter.OsMode.valueOf(osmode.name)
                                } catch (_: Exception) {
                                    RuleConverter.detectOsMode()
                                }
                            } ?: RuleConverter.detectOsMode()
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
        intervalInput = "0"
    }

    fun resetEditState() {
        showEditSheet = false
        editingConfig = null
        urlInput = ""
        jsonInput = ""
        intervalInput = "0"
    }

    fun addConfig() {
        if (isSaving) return
        isSaving = true

        scope.launch {
            try {
                when (ruleType) {
                    RuleType.CLOUD -> {
                        val url = urlInput.trim()
                        if (url.isEmpty()) {
                            return@launch
                        }

                        RuleFetcher.fetch(RuleConfigSource(id = "", url = url, type = RuleType.CLOUD))
                            .fold(
                                onSuccess = { result ->
                                    val intervalMs = (intervalInput.toIntOrNull() ?: 0) * 60_000L
                                    val newConfig = RulesManager.add(
                                        context,
                                        RuleType.CLOUD,
                                        url,
                                        name = result.configName,
                                        appCount = result.appCount
                                    )
                                    RulesManager.update(
                                        context,
                                        newConfig.copy(
                                            refreshIntervalMs = intervalMs,
                                            cachedContent = result.rawJson
                                        )
                                    )
                                    reloadConfigs()
                                    withContext(Dispatchers.Main) {
                                        resetAddState()
                                    }
                                },
                                onFailure = { e ->
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            context,
                                            "Fetch failed: ${e.message ?: "unknown error"}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                    }

                    RuleType.LOCAL -> {
                        val jsonContent = formatNbiJson(jsonInput.trim())
                        if (jsonContent.isEmpty()) {
                            return@launch
                        }

                        RuleFetcher.fetch(RuleConfigSource(id = "", jsonContent = jsonContent, type = RuleType.LOCAL))
                            .fold(
                                onSuccess = { result ->
                                    val intervalMs = (intervalInput.toIntOrNull() ?: 0) * 60_000L
                                    val newConfig = RulesManager.add(
                                        context,
                                        RuleType.LOCAL,
                                        "",
                                        jsonContent,
                                        result.configName,
                                        result.appCount
                                    )
                                    RulesManager.update(
                                        context,
                                        newConfig.copy(
                                            refreshIntervalMs = intervalMs,
                                            cachedContent = jsonContent
                                        )
                                    )
                                    reloadConfigs()
                                    withContext(Dispatchers.Main) {
                                        jsonInput = jsonContent
                                        resetAddState()
                                    }
                                },
                                onFailure = { e ->
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            context,
                                            "Parse failed: ${e.message ?: "unknown error"}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                    }
                }
            } finally {
                isSaving = false
            }
        }
    }

    fun editConfig() {
        if (isSaving) return
        isSaving = true

        scope.launch {
            try {
                val dialog = editingConfig ?: return@launch

                val formattedJson = formatNbiJson(jsonInput.trim())
                val updated = if (dialog.type == RuleType.CLOUD) {
                    dialog.copy(
                        url = urlInput.trim().ifEmpty { dialog.url },
                        cachedContent = formattedJson.ifEmpty { dialog.cachedContent },
                        refreshIntervalMs = (intervalInput.toIntOrNull() ?: 0) * 60_000L,
                    )
                } else {
                    dialog.copy(
                        url = urlInput.trim().ifEmpty { dialog.url },
                        jsonContent = formattedJson.ifEmpty { dialog.jsonContent },
                        refreshIntervalMs = (intervalInput.toIntOrNull() ?: 0) * 60_000L,
                    )
                }

                withContext(Dispatchers.IO) {
                    RulesManager.update(context, updated)
                }

                withContext(Dispatchers.Main) {
                    val idx = configs.indexOfFirst { it.id == updated.id }
                    if (idx >= 0) {
                        configs[idx] = updated
                    }
                    jsonInput = formattedJson
                    resetEditState()
                }
            } finally {
                isSaving = false
            }
        }
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
                    val mode = SystemVersionDetector.getEffectiveOsMode(context)?.let { osmode ->
                        try {
                            RuleConverter.OsMode.valueOf(osmode.name)
                        } catch (_: Exception) {
                            RuleConverter.detectOsMode()
                        }
                    } ?: RuleConverter.detectOsMode()
                    val targetContent = RuleConverter.convert(mergedJson, mode)
                    val targetPath = RuleConverter.getTargetPath(mode)
                    val totalApps = RuleCombiner.getTotalAppCount(cachedResults)
                    if (hasRoot) {
                        RootApplier.applyRules(targetContent, targetPath, context.cacheDir)
                        isCustomApplied = RootApplier.isCustomRulesApplied(targetPath)
                    }
                    saveApplyState(System.currentTimeMillis(), totalApps, isCustomApplied)
                    withContext(Dispatchers.Main) {
                        @Suppress("LocalContext")
                        val updateSuccessMsg = context.getString(R.string.rules_update_success, totalApps)
                        Toast.makeText(context, updateSuccessMsg, Toast.LENGTH_SHORT).show()
                        if (!hasRoot) {
                            Toast.makeText(context, rulesRootRequiredText, Toast.LENGTH_SHORT).show()
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
            jsonInput = getEmptyJsonTemplate()
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

    val jsonFileSaver = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { output ->
                    output.write(formatNbiJson(jsonInput).toByteArray())
                }
                Toast.makeText(context, exportSuccessText, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, exportFailedText, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(pendingEditConfig) {
        val cfg = pendingEditConfig ?: return@LaunchedEffect
        urlInput = cfg.url
        jsonInput = when (cfg.type) {
            RuleType.LOCAL -> cfg.jsonContent.ifEmpty { getEmptyJsonTemplate() }
            RuleType.CLOUD -> cfg.cachedContent.ifEmpty { getEmptyJsonTemplate() }
        }
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
                modifier = Modifier
                    .fillMaxWidth()
                    .scrollEndHaptic()
                    .overScrollVertical(),
            ) {
                items(PRESET_SOURCES) { preset ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = {
                            showPresetSheet = false
                            scope.launch {
                                val result = RuleFetcher.fetch(RuleConfigSource(id = "", url = preset.url, type = RuleType.CLOUD)).getOrNull()
                                if (result != null) {
                                    val newConfig = RulesManager.add(context, RuleType.CLOUD, preset.url, name = result.configName, appCount = result.appCount)
                                    RulesManager.updateRefreshTime(context, newConfig.id, System.currentTimeMillis(), result.appCount, result.configName, result.rawJson)
                                    reloadConfigs()
                                    @Suppress("LocalContext")
                                    val presetAddedMsg = context.getString(R.string.rules_preset_added, result.configName)
                                    Toast.makeText(context, presetAddedMsg, Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, rulesPresetFailedText, Toast.LENGTH_SHORT).show()
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
                    Spacer(
                        Modifier.padding(
                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                                    WindowInsets.captionBar.asPaddingValues().calculateBottomPadding(),
                        )
                    )
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
                IconButton(
                    onClick = { addConfig() },
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        InfiniteProgressIndicator()
                    } else {
                        Icon(MiuixIcons.Ok, stringResource(R.string.rules_confirm))
                    }
                }
            }
        ) {
            SubscriptionForm(
                ruleType = ruleType,
                onRuleTypeChange = { ruleType = it },
                urlInput = urlInput,
                onUrlChange = { urlInput = it },
                jsonInput = jsonInput,
                onJsonChange = { jsonInput = it },
                intervalInput = intervalInput,
                onIntervalChange = { v -> intervalInput = v.filter(Char::isDigit) },
                jsonFilePicker = jsonFilePicker,
                showInterval = true,
                enabled = !isSaving,
                onOpenEditor = { showJsonEditor = true },
                onExport = {
                    val name = try {
                        JSONObject(jsonInput.trim()).optString("name", "nbi_rules")
                    } catch (_: Exception) {
                        "nbi_rules"
                    }
                    jsonFileSaver.launch("${name}.json")
                },
                onExportCloud = {
                    scope.launch {
                        val results = fetchAndParseConfigs()
                        val merged = RuleCombiner.combine(configs, results)
                        jsonInput = merged.toString(4)
                        val name = try {
                            merged.optString("name", "nbi_rules")
                        } catch (_: Exception) {
                            "nbi_rules"
                        }
                        jsonFileSaver.launch("${name}.json")
                    }
                },
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
                IconButton(
                    onClick = { editConfig() },
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        InfiniteProgressIndicator()
                    } else {
                        Icon(MiuixIcons.Ok, stringResource(R.string.rules_confirm))
                    }
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
                    intervalInput = intervalInput,
                    onIntervalChange = { v -> intervalInput = v.filter(Char::isDigit) },
                    jsonFilePicker = jsonFilePicker,
                    showInterval = cfg.type == RuleType.CLOUD,
                    showTypeSelector = false,
                    isEditMode = true,
                    enabled = !isSaving,
                    onOpenEditor = { showJsonEditor = true },
                    onExport = {
                        val name = try {
                            JSONObject(jsonInput.trim()).optString("name", "nbi_rules")
                        } catch (_: Exception) {
                            "nbi_rules"
                        }
                        jsonFileSaver.launch("${name}.json")
                    },
                    onExportCloud = {
                        scope.launch {
                            val results = fetchAndParseConfigs()
                            val merged = RuleCombiner.combine(configs, results)
                            jsonInput = merged.toString(4)
                            val name = try {
                                merged.optString("name", "nbi_rules")
                            } catch (_: Exception) {
                                "nbi_rules"
                            }
                            jsonFileSaver.launch("${name}.json")
                        }
                    },
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
            title = actionsTarget?.name?.ifEmpty { actionsTarget?.url?.ifEmpty { stringResource(R.string.rules_local_rule) } } ?: "",
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
                    IconButton(
                        onClick = {
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
                        },
                        enabled = cfg.id !in updatingIds,
                    ) {
                        if (cfg.id in updatingIds) {
                            InfiniteProgressIndicator()
                        } else {
                            Icon(
                                imageVector = MiuixIcons.Refresh,
                                contentDescription = stringResource(R.string.rules_refresh_manual),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    }
                }
            },
        ) {
            val cfg = actionsTarget
            if (cfg != null) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .scrollEndHaptic()
                        .overScrollVertical(),
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
                        Spacer(
                            Modifier.padding(
                                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                                        WindowInsets.captionBar.asPaddingValues().calculateBottomPadding(),
                            )
                        )
                    }
                }
            }
        }

        // Visual JSON rule editor sheet
        JsonRuleEditorSheet(
            show = showJsonEditor,
            jsonInput = jsonInput,
            onJsonChange = { jsonInput = it },
            onDismiss = { showJsonEditor = false },
        )

        // Main content
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .pageScrollModifiers(showTopAppBar = true, topAppBarScrollBehavior = scrollBehavior),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + extraBottomPadding
                )
            ) {
                // Apply section - always at top
                item {
                    SmallTitle(text = stringResource(R.string.rules_apply), modifier = Modifier.padding(top = 6.dp))
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 6.dp)
                    ) {
                        BasicComponent(
                            title = stringResource(R.string.rules_apply_now),
                            summary = stringResource(R.string.rules_merged_count, mergedAppCount) +
                                    " · " + if (isApplying) stringResource(R.string.rules_refreshing) else formatElapsedTime(context, lastApplyTime, tick),
                            onClick = if (isApplying) null else ({ applyRules() }),
                            endActions = {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .padding(end = 8.dp), contentAlignment = Alignment.Center
                                ) {
                                    if (isApplying) {
                                        InfiniteProgressIndicator()
                                    } else {
                                        Icon(
                                            imageVector = MiuixIcons.Refresh,
                                            contentDescription = stringResource(R.string.rules_apply_now),
                                            tint = MiuixTheme.colorScheme.onBackground,
                                        )
                                    }
                                }
                            },
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

                itemsIndexed(configs.toList(), key = { _, c -> c.id }) { index, config ->
                    val density = LocalDensity.current
                    val itemHeightPx = with(density) { 72.dp.toPx() }

                    val isDragged = draggedConfigId == config.id

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 6.dp)
                            .zIndex(if (isDragged) 1f else 0f)
                            .graphicsLayer {
                                if (isDragged) {
                                    translationY = dragOffsetY
                                }
                            }
                            .pointerInput(config.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggedConfigId = config.id
                                        draggedOriginalIndex = index
                                        dragOffsetY = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetY += dragAmount.y

                                        // Swap with neighbor when offset crosses a full item height
                                        val id = draggedConfigId ?: return@detectDragGesturesAfterLongPress
                                        val currentIdx = configs.indexOfFirst { it.id == id }
                                        if (currentIdx < 0) return@detectDragGesturesAfterLongPress

                                        val steps = (dragOffsetY / itemHeightPx).toInt()
                                        if (steps != 0) {
                                            val targetIdx = (currentIdx + steps).coerceIn(0, configs.size - 1)
                                            if (targetIdx != currentIdx) {
                                                val mover = configs[currentIdx]
                                                configs.removeAt(currentIdx)
                                                configs.add(targetIdx, mover)
                                                dragOffsetY -= steps * itemHeightPx
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        val id = draggedConfigId
                                        val orig = draggedOriginalIndex
                                        if (id != null && orig >= 0) {
                                            val fGapSteps = (dragOffsetY / itemHeightPx).toInt()
                                            if (fGapSteps != 0) {
                                                val currentIdx = configs.indexOfFirst { it.id == id }
                                                val targetIdx = (currentIdx + fGapSteps).coerceIn(0, configs.size - 1)
                                                if (targetIdx != currentIdx) {
                                                    val mover = configs[currentIdx]
                                                    configs.removeAt(currentIdx)
                                                    configs.add(targetIdx, mover)
                                                }
                                            }
                                            val reordered = configs.mapIndexed { i, c -> c.copy(priority = i) }
                                            RulesManager.saveAll(context, reordered)
                                            for (i in configs.indices) { configs[i] = reordered[i] }
                                        }
                                        draggedConfigId = null
                                        draggedOriginalIndex = -1
                                        dragOffsetY = 0f
                                    },
                                    onDragCancel = {
                                        draggedConfigId = null
                                        draggedOriginalIndex = -1
                                        dragOffsetY = 0f
                                    },
                                )
                            },
                        onClick = { actionsTarget = config; showActionsSheet = true },
                        showIndication = true,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BasicComponent(
                                title = "${index + 1}. ${config.name.ifEmpty { config.url.ifEmpty { stringResource(R.string.rules_local_rule) } }}",
                                summary = buildString {
                                    append(if (config.type == RuleType.LOCAL) stringResource(R.string.rules_local_prefix) else stringResource(R.string.rules_cloud_prefix))
                                    // Show dataVersion
                                    val content = config.cachedContent.ifEmpty { config.jsonContent }
                                    if (content.isNotEmpty()) {
                                        try {
                                            val json = JSONObject(content)
                                            val version = json.optString("dataVersion", "")
                                            if (version.isNotEmpty()) append(" $version ·")
                                        } catch (_: Exception) {
                                        }
                                    }
                                    append(" ${stringResource(R.string.rules_app_count, config.appCount)}")
                                    if (config.type == RuleType.CLOUD) {
                                        append(" · ")
                                        append(if (config.id in updatingIds) stringResource(R.string.rules_refreshing) else formatElapsedTime(context, config.lastRefreshTime, tick))
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
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(52.dp, 32.dp),
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
    intervalInput: String,
    onIntervalChange: (String) -> Unit,
    jsonFilePicker: androidx.activity.result.ActivityResultLauncher<String>,
    showInterval: Boolean,
    showTypeSelector: Boolean = true,
    isEditMode: Boolean = false,
    enabled: Boolean = true,
    onOpenEditor: () -> Unit = {},
    onExport: () -> Unit = {},
    onExportCloud: () -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .scrollEndHaptic()
            .overScrollVertical(),
    ) {
        if (showTypeSelector) {
            item {
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        text = stringResource(R.string.rules_cloud),
                        onClick = { onRuleTypeChange(RuleType.CLOUD) },
                        modifier = Modifier.weight(1f),
                        colors = if (ruleType == RuleType.CLOUD) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
                        enabled = enabled,
                    )
                    TextButton(
                        text = stringResource(R.string.rules_local),
                        onClick = { onRuleTypeChange(RuleType.LOCAL) },
                        modifier = Modifier.weight(1f),
                        colors = if (ruleType == RuleType.LOCAL) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
                        enabled = enabled,
                    )
                }
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
                    enabled = enabled,
                )
                if (isEditMode) {
                    // Edit mode: show visual edit for cloud cache
                    TextButton(
                        text = stringResource(R.string.rules_visual_edit),
                        onClick = onOpenEditor,
                        enabled = enabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            } else {
                TextField(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .height(180.dp),
                    value = jsonInput,
                    onValueChange = onJsonChange,
                    label = stringResource(R.string.rules_json_content),
                    singleLine = false,
                    enabled = enabled,
                )
                if (isEditMode) {
                    // Edit mode: visual edit alone, import+export together
                    TextButton(
                        text = stringResource(R.string.rules_visual_edit),
                        onClick = onOpenEditor,
                        enabled = enabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TextButton(
                            text = stringResource(R.string.rules_import_file),
                            onClick = { jsonFilePicker.launch("application/json") },
                            enabled = enabled,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            text = stringResource(R.string.rules_export_file),
                            onClick = onExport,
                            enabled = enabled,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    // Add mode: visual edit + export together, import alone
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TextButton(
                            text = stringResource(R.string.rules_visual_edit),
                            onClick = onOpenEditor,
                            enabled = enabled,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                        TextButton(
                            text = stringResource(R.string.rules_export_file),
                            onClick = onExport,
                            enabled = enabled,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    TextButton(
                        text = stringResource(R.string.rules_import_file),
                        onClick = { jsonFilePicker.launch("application/json") },
                        enabled = enabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    )
                }
            }
        }
        if (showInterval) {
            item {
                TextField(
                    modifier = Modifier.padding(vertical = 4.dp),
                    value = intervalInput,
                    onValueChange = onIntervalChange,
                    label = stringResource(R.string.rules_add_interval),
                    singleLine = true,
                    enabled = enabled,
                )
            }
        }
        item {
            Spacer(
                Modifier.padding(
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                            WindowInsets.captionBar.asPaddingValues().calculateBottomPadding(),
                )
            )
        }
    }
}
