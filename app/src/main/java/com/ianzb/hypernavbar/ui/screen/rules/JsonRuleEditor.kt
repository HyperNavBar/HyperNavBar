package com.ianzb.hypernavbar.ui.screen.rules

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ianzb.hypernavbar.R
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ColorPicker
import top.yukonga.miuix.kmp.basic.ColorSpace
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog

// ── Option definitions ────────────────────────────────────────────────
private val MODE_VALUES = listOf(-1, 0, 1, 2)
private val SF_SAMPLING_VALUES = listOf(0, 1, 255)
private val DIALOG_POPUP_VALUES = listOf(0, 1, 2)

private const val COLOR_TYPE_DEFAULT = 0
private const val COLOR_TYPE_AUTO = 1
private const val COLOR_TYPE_CUSTOM = 2

private const val EMPTY_JSON = """{
    "dataVersion": "999999",
    "name": "沉浸规则",
    "modules": "navigation_bar_immersive_application_config_new",
    "modifyApps": "modifyApps",
    "NBIRules": {}
}"""

// ── JSON helpers ──────────────────────────────────────────────────────
private fun JSONObject.optIntOrNull(key: String): Int? =
    if (isNull(key)) null else optInt(key, 0)

private fun parseJsonSafe(jsonStr: String): JSONObject {
    return try {
        JSONObject(jsonStr.trim().ifEmpty { EMPTY_JSON })
    } catch (_: Exception) {
        JSONObject(EMPTY_JSON)
    }
}

/**
 * Format NBIRules JSON: 4-space indent, sort by package name A-Z,
 * sort activity rules by activity name A-Z.
 */
fun formatNbiJson(jsonStr: String): String {
    val defaultDate = java.text.SimpleDateFormat("yyMMdd", java.util.Locale.US).format(java.util.Date())
    return try {
        val root = JSONObject(jsonStr.trim().ifEmpty { return jsonStr })
        val nbiRules = root.optJSONObject("NBIRules") ?: return root.toString(4)
        val sortedRoot = JSONObject()
        // Root key order: name, dataVersion, modules, modifyApps, NBIRules
        if (root.has("name")) sortedRoot.put("name", root.optString("name"))
        sortedRoot.put("dataVersion", root.optString("dataVersion", defaultDate))
        sortedRoot.put("modules", root.optString("modules", "navigation_bar_immersive_application_config_new"))
        sortedRoot.put("modifyApps", root.optString("modifyApps", "modifyApps"))
        val sortedNbi = JSONObject()
        nbiRules.keys().asSequence().sorted().forEach { pkg ->
            val app = nbiRules.optJSONObject(pkg) ?: return@forEach
            val sortedApp = JSONObject()
            if (app.has("name")) sortedApp.put("name", app.optString("name"))
            sortedApp.put("enable", app.optBoolean("enable", false))
            if (app.has("enable31")) sortedApp.put("enable31", app.optBoolean("enable31"))
            if (app.has("disableVersionCode") && !app.isNull("disableVersionCode"))
                sortedApp.put("disableVersionCode", app.optLong("disableVersionCode"))
            val activityRules = app.optJSONObject("activityRules")
            if (activityRules != null) {
                val sortedActivities = JSONObject()
                activityRules.keys().asSequence().sorted().forEach { act ->
                    sortedActivities.put(act, activityRules.opt(act))
                }
                sortedApp.put("activityRules", sortedActivities)
            }
            sortedNbi.put(pkg, sortedApp)
        }
        sortedRoot.put("NBIRules", sortedNbi)
        sortedRoot.toString(4)
    } catch (_: Exception) {
        jsonStr
    }
}

/**
 * Determine the color-type index from the JSON color value:
 *   0 = null (default), 1 = sentinel 1 (auto-detect), 2 = ARGB integer.
 */
private fun colorTypeFromValue(color: Any?): Int = when {
    color == null || color == JSONObject.NULL -> COLOR_TYPE_DEFAULT
    color is Int && color == 1 -> COLOR_TYPE_AUTO
    else -> COLOR_TYPE_CUSTOM
}

/**
 * The ARGB int stored in JSON, or 0xFF000000 (black) when no custom
 * color has been chosen yet.
 */
private fun argbFromValue(color: Any?): Int = when (color) {
    is Int -> color
    else -> 0xFF000000.toInt()
}

// ── Main editor (three stacked sheets) ────────────────────────────────
@Composable
fun JsonRuleEditorSheet(
    show: Boolean,
    jsonInput: String,
    onJsonChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return

    // --- work-copy of the JSON, rebuilt whenever we confirm an edit ---
    var editingJsonStr by remember { mutableStateOf(jsonInput) }   // stable: only changes on ✓ or editor open
    var stagingJsonStr by remember { mutableStateOf(jsonInput) }    // volatile: changes during edits, reset per sheet
    var editVersion by remember { mutableIntStateOf(0) }

    // Reset editingJsonStr to jsonInput every time the editor opens
    LaunchedEffect(show) {
        if (show) {
            editingJsonStr = jsonInput
            stagingJsonStr = jsonInput
            editVersion = 0
        }
    }

    val root = remember(stagingJsonStr, editVersion) { parseJsonSafe(stagingJsonStr) }
    val nbiRules = remember(root, editVersion) {
        root.optJSONObject("NBIRules") ?: JSONObject().also { root.put("NBIRules", it) }
    }

    // --- navigation ---
    var showApps by remember { mutableStateOf(true) }
    var showActivities by remember { mutableStateOf(false) }
    var showFields by remember { mutableStateOf(false) }
    var selectedPackage by remember { mutableStateOf("") }
    var selectedActivity by remember { mutableStateOf("") }

    // --- undo backups ---
    var activitiesBackup by remember { mutableStateOf("") }
    var fieldBackup by remember { mutableStateOf("") }

    // --- dialogs ---
    var showAddAppDialog by remember { mutableStateOf(false) }
    var showAddActivityDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf("") } // pkg or "pkg::activity"

    // --- search ---
    var searchQuery by remember { mutableStateOf("") }
    var showFloatingIdentify by remember { mutableStateOf(false) }

    // --- add-app fields ---
    var newPkg by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }

    // --- add-activity fields ---
    var newActName by remember { mutableStateOf("") }

    /** Return a mutable copy of the activity JSON. Null if not found. */
    fun getActivityJson(): JSONObject? {
        val app = nbiRules.optJSONObject(selectedPackage) ?: return null
        val rules = app.optJSONObject("activityRules") ?: return null
        return rules.optJSONObject(selectedActivity)
    }

    /** Write back to the working JSON without propagating to parent. */
    fun saveRoot() {
        stagingJsonStr = root.toString(2)
        editVersion++
    }

    fun deleteApp(pkg: String) {
        nbiRules.remove(pkg)
        saveRoot()
    }

    fun deleteActivity(pkg: String, activity: String) {
        val app = nbiRules.optJSONObject(pkg) ?: return
        val rules = app.optJSONObject("activityRules") ?: return
        rules.remove(activity)
        if (rules.length() == 0) app.remove("activityRules")
        saveRoot()
    }

    fun updateActivityField(key: String, value: Any?) {
        val act = getActivityJson() ?: return
        if (value == null) {
            act.remove(key)
        } else {
            act.put(key, value)
        }
        saveRoot()
    }

    // ── Sheet 3: Field editor (topmost) ──────────────────────────────
    WindowBottomSheet(
        title = stringResource(R.string.editor_title_fields),
        show = showFields,
        onDismissRequest = {
            searchQuery = ""
            stagingJsonStr = fieldBackup
            showFields = false
        },
        startAction = {
            IconButton(onClick = {
                searchQuery = ""
                stagingJsonStr = fieldBackup
                showFields = false
            }) {
                Icon(
                    MiuixIcons.Close,
                    contentDescription = stringResource(R.string.editor_back),
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
        },
        endAction = {
            IconButton(onClick = {
                searchQuery = ""
                editingJsonStr = stagingJsonStr
                showFields = false
            }) {
                Icon(
                    MiuixIcons.Ok,
                    contentDescription = stringResource(R.string.editor_save),
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
        },
    ) {
        val currentAct = getActivityJson() ?: JSONObject()
        val act = currentAct

        val mode = act.optIntOrNull("mode") ?: -1
        val modeIdx = MODE_VALUES.indexOf(mode).coerceAtLeast(0)

        val color = act.opt("color")
        val colorType = colorTypeFromValue(color)
        val argb = argbFromValue(color)

        val sfMode = act.optIntOrNull("sf_sampling_mode") ?: 0
        val sfIdx = SF_SAMPLING_VALUES.indexOf(sfMode).coerceAtLeast(0)

        val dialogMode = act.optIntOrNull("dialogMode") ?: 1
        val dialogIdx = DIALOG_POPUP_VALUES.indexOf(dialogMode).coerceAtLeast(0)

        val popupModeVal = act.optIntOrNull("popupMode") ?: 1
        val popupIdx = DIALOG_POPUP_VALUES.indexOf(popupModeVal).coerceAtLeast(0)

        val appNavDisabled = act.optInt("appNavColorDisabled", 0) == 1

        LazyColumn(
            modifier = Modifier.fillMaxWidth().scrollEndHaptic().overScrollVertical(),
        ) {
            item {
                SmallTitle(
                    text = "$selectedPackage / $selectedActivity",
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            // ── mode spinner ──
            item {
                val modeLabels = listOf(
                    stringResource(R.string.editor_mode_auto),
                    stringResource(R.string.editor_mode_disabled),
                    stringResource(R.string.editor_mode_color_pick),
                    stringResource(R.string.editor_mode_float),
                )
                val modeItems = modeLabels.mapIndexed { i, text ->
                    DropdownItem(text = text, summary = MODE_VALUES[i].toString())
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                ) {
                    WindowDropdownPreference(
                        title = stringResource(R.string.editor_mode_title),
                        summary = stringResource(R.string.editor_mode_summary),
                        entry = DropdownEntry(modeItems.mapIndexed { i, item ->
                            item.copy(
                                selected = i == modeIdx,
                                onClick = { updateActivityField("mode", MODE_VALUES[i]) },
                            )
                        }),
                    )
                }
            }

            // ── color ──
            item {
                var pickerColor by remember(argb) { mutableStateOf(Color(argb)) }
                val aVal = (argb ushr 24) and 0xFF
                val rVal = (argb ushr 16) and 0xFF
                val gVal = (argb ushr 8) and 0xFF
                val bVal = argb and 0xFF
                var rgbaInput by remember(argb) { mutableStateOf("$rVal, $gVal, $bVal, $aVal") }
                var hexInput by remember(argb) {
                    mutableStateOf("#${rVal.toString(16).padStart(2, '0')}${gVal.toString(16).padStart(2, '0')}${bVal.toString(16).padStart(2, '0')}${aVal.toString(16).padStart(2, '0')}".uppercase())
                }
                var showColorPicker by remember(colorType) { mutableStateOf(colorType == COLOR_TYPE_CUSTOM) }

                /** Sync both inputs and picker from ARGB int */
                fun syncFromArgb(newArgb: Int) {
                    pickerColor = Color(newArgb)
                    updateActivityField("color", newArgb)
                    val r = (newArgb ushr 16) and 0xFF
                    val g = (newArgb ushr 8) and 0xFF
                    val b = newArgb and 0xFF
                    val a = (newArgb ushr 24) and 0xFF
                    rgbaInput = "$r, $g, $b, $a"
                    hexInput = "#${r.toString(16).padStart(2, '0')}${g.toString(16).padStart(2, '0')}${b.toString(16).padStart(2, '0')}${a.toString(16).padStart(2, '0')}".uppercase()
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                ) {
                    Column {
                        val colorTypeLabels = listOf(
                            stringResource(R.string.editor_color_default),
                            stringResource(R.string.editor_color_auto),
                            stringResource(R.string.editor_color_custom),
                        )
                        val colorTypeSummaries = listOf(
                            "null",
                            "1",
                            stringResource(R.string.editor_color_custom_value),
                        )
                        val colorTypeItems = colorTypeLabels.mapIndexed { i, text ->
                            DropdownItem(text = text, summary = colorTypeSummaries[i])
                        }
                        WindowDropdownPreference(
                            title = stringResource(R.string.editor_color_title),
                            summary = stringResource(R.string.editor_color_summary),
                            entry = DropdownEntry(colorTypeItems.mapIndexed { i, item ->
                                item.copy(
                                    selected = i == colorType,
                                    onClick = {
                                        when (i) {
                                            COLOR_TYPE_DEFAULT -> updateActivityField("color", JSONObject.NULL)
                                            COLOR_TYPE_AUTO -> updateActivityField("color", 1)
                                            COLOR_TYPE_CUSTOM -> updateActivityField("color", 0xFF000000.toInt())
                                        }
                                        showColorPicker = (i == COLOR_TYPE_CUSTOM)
                                    },
                                )
                            }),
                        )
                        // Custom color editor - animated appearance
                        AnimatedVisibility(
                            visible = showColorPicker && colorType == COLOR_TYPE_CUSTOM,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            Column {
                                // Color preview + ARGB display
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(pickerColor),
                                    )
                                    Column(modifier = Modifier.padding(start = 12.dp)) {
                                        Text(
                                            text = "ARGB: $argb",
                                            style = MiuixTheme.textStyles.body2,
                                            color = MiuixTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = "R: $rVal  G: $gVal  B: $bVal  A: $aVal",
                                            style = MiuixTheme.textStyles.footnote2,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                    }
                                }
                                // RGBA input field
                                TextField(
                                    value = rgbaInput,
                                    onValueChange = { v ->
                                        rgbaInput = v
                                        val parts = v.split(",").map { it.trim() }
                                        if (parts.size == 4) {
                                            val nr = parts[0].toIntOrNull()?.coerceIn(0, 255) ?: return@TextField
                                            val ng = parts[1].toIntOrNull()?.coerceIn(0, 255) ?: return@TextField
                                            val nb = parts[2].toIntOrNull()?.coerceIn(0, 255) ?: return@TextField
                                            val na = parts[3].toIntOrNull()?.coerceIn(0, 255) ?: return@TextField
                                            val newArgb = (na shl 24) or (nr shl 16) or (ng shl 8) or nb
                                            syncFromArgb(newArgb)
                                        }
                                    },
                                    label = stringResource(R.string.editor_rgba_hint),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                )
                                // Hex input field
                                TextField(
                                    value = hexInput,
                                    onValueChange = { v ->
                                        hexInput = v
                                        val trimmed = v.trim()
                                        if (trimmed.startsWith("#") && (trimmed.length == 7 || trimmed.length == 9)) {
                                            try {
                                                val hex = trimmed.substring(1)
                                                val r = hex.substring(0, 2).toInt(16)
                                                val g = hex.substring(2, 4).toInt(16)
                                                val b = hex.substring(4, 6).toInt(16)
                                                val a = if (hex.length == 8) hex.substring(6, 8).toInt(16) else 255
                                                val newArgb = (a shl 24) or (r shl 16) or (g shl 8) or b
                                                syncFromArgb(newArgb)
                                            } catch (_: Exception) {}
                                        }
                                    },
                                    label = stringResource(R.string.editor_hex_hint),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                )
                                // ColorPicker
                                ColorPicker(
                                    color = pickerColor,
                                    onColorChanged = { newColor ->
                                        syncFromArgb(newColor.toArgb())
                                    },
                                    colorSpace = ColorSpace.HSV,
                                    showPreview = false,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }

            // ── sf_sampling_mode spinner ──
            item {
                val sfLabels = listOf(
                    stringResource(R.string.editor_mode_auto),
                    stringResource(R.string.editor_sf_force_enable),
                    stringResource(R.string.editor_sf_force_disable),
                )
                val sfItems = sfLabels.mapIndexed { i, text ->
                    DropdownItem(text = text, summary = SF_SAMPLING_VALUES[i].toString())
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                ) {
                    WindowDropdownPreference(
                        title = stringResource(R.string.editor_sf_title),
                        summary = stringResource(R.string.editor_sf_summary),
                        entry = DropdownEntry(sfItems.mapIndexed { i, item ->
                            item.copy(
                                selected = i == sfIdx,
                                onClick = { updateActivityField("sf_sampling_mode", SF_SAMPLING_VALUES[i]) },
                            )
                        }),
                    )
                }
            }

            // ── dialogMode spinner ──
            item {
                val dialogLabels = listOf(
                    stringResource(R.string.editor_mode_disabled),
                    stringResource(R.string.editor_dialog_view_sampling),
                    stringResource(R.string.editor_dialog_sf_sampling),
                )
                val dialogItems = dialogLabels.mapIndexed { i, text ->
                    DropdownItem(text = text, summary = DIALOG_POPUP_VALUES[i].toString())
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                ) {
                    WindowDropdownPreference(
                        title = stringResource(R.string.editor_dialog_title),
                        summary = stringResource(R.string.editor_dialog_summary),
                        entry = DropdownEntry(dialogItems.mapIndexed { i, item ->
                            item.copy(
                                selected = i == dialogIdx,
                                onClick = { updateActivityField("dialogMode", DIALOG_POPUP_VALUES[i]) },
                            )
                        }),
                    )
                }
            }

            // ── popupMode spinner ──
            item {
                val popupLabels = listOf(
                    stringResource(R.string.editor_mode_disabled),
                    stringResource(R.string.editor_dialog_view_sampling),
                    stringResource(R.string.editor_dialog_sf_sampling),
                )
                val popupItems = popupLabels.mapIndexed { i, text ->
                    DropdownItem(text = text, summary = DIALOG_POPUP_VALUES[i].toString())
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                ) {
                    WindowDropdownPreference(
                        title = stringResource(R.string.editor_popup_title),
                        summary = stringResource(R.string.editor_popup_summary),
                        entry = DropdownEntry(popupItems.mapIndexed { i, item ->
                            item.copy(
                                selected = i == popupIdx,
                                onClick = { updateActivityField("popupMode", DIALOG_POPUP_VALUES[i]) },
                            )
                        }),
                    )
                }
            }

            // ── appNavColorDisabled ──
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                ) {
                    SwitchPreference(
                        checked = appNavDisabled,
                        onCheckedChange = { checked ->
                            updateActivityField("appNavColorDisabled", if (checked) 1 else 0)
                        },
                        title = stringResource(R.string.editor_disable_app_nav_color),
                        summary = stringResource(R.string.editor_disable_app_nav_color_summary),
                    )
                }
            }

            // ── delete activity ──
            item {
                TextButton(
                    text = stringResource(R.string.editor_delete_activity),
                    onClick = {
                        showDeleteConfirm = "$selectedPackage::$selectedActivity"
                    },
                    modifier = Modifier.padding(vertical = 6.dp),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }

            item {
                Spacer(
                    Modifier.padding(
                        bottom = WindowInsets.navigationBars.asPaddingValues()
                            .calculateBottomPadding() +
                            WindowInsets.captionBar.asPaddingValues()
                                .calculateBottomPadding(),
                    )
                )
            }
        }
    }

    // ── Sheet 2: Activity list ────────────────────────────────────────
    WindowBottomSheet(
        title = stringResource(R.string.editor_title_activities),
        show = showActivities,
        onDismissRequest = {
            searchQuery = ""
            stagingJsonStr = activitiesBackup
            showActivities = false
        },
        startAction = {
            IconButton(onClick = {
                searchQuery = ""
                stagingJsonStr = activitiesBackup
                showActivities = false
            }) {
                Icon(
                    MiuixIcons.Close,
                    contentDescription = stringResource(R.string.editor_back),
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
        },
        endAction = {
            IconButton(onClick = {
                searchQuery = ""
                editingJsonStr = stagingJsonStr
                showActivities = false
            }) {
                Icon(
                    MiuixIcons.Ok,
                    contentDescription = stringResource(R.string.editor_save),
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
        },
    ) {
        val appForSelected = nbiRules.optJSONObject(selectedPackage)
        val activityRules = appForSelected?.optJSONObject("activityRules") ?: JSONObject()
        val actKeys = activityRules.keys().asSequence().toList().sorted()

        val filteredActKeys = remember(searchQuery, actKeys, stagingJsonStr, editVersion) {
            if (searchQuery.isEmpty()) actKeys
            else actKeys.filter { it.lowercase().contains(searchQuery.lowercase()) }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().scrollEndHaptic().overScrollVertical(),
        ) {
            item {
                SmallTitle(
                    text = selectedPackage,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            // Search bar
            item {
                SearchBar(
                    modifier = Modifier.padding(bottom = 6.dp),
                    inputField = {
                        InputField(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onSearch = { },
                            expanded = false,
                            onExpandedChange = { },
                            label = stringResource(R.string.editor_search_activities),
                        )
                    },
                    expanded = false,
                    onExpandedChange = { },
                    content = { },
                )
            }
            // App name editor
            item {
                val appName = appForSelected?.optString("name", "") ?: ""
                var isEditingName by remember { mutableStateOf(false) }
                var nameField by remember(appName) { mutableStateOf(appName) }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    onClick = { isEditingName = true },
                    showIndication = true,
                ) {
                    if (isEditingName) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            TextField(
                                value = nameField,
                                onValueChange = { nameField = it },
                                label = stringResource(R.string.editor_app_name),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(
                                    text = stringResource(R.string.rules_cancel),
                                    onClick = { isEditingName = false },
                                    enabled = true,
                                )
                                Spacer(Modifier.width(12.dp))
                                TextButton(
                                    text = stringResource(R.string.editor_save),
                                    onClick = {
                                        appForSelected?.put("name", nameField.trim())
                                        saveRoot()
                                        isEditingName = false
                                    },
                                    colors = ButtonDefaults.textButtonColorsPrimary(),
                                    enabled = true,
                                )
                            }
                        }
                    } else {
                        BasicComponent(
                            title = stringResource(R.string.editor_app_name),
                            summary = if (appName.isNotEmpty()) "$appName${stringResource(R.string.editor_tap_edit)}" else stringResource(R.string.editor_not_set_tap_edit),
                        )
                    }
                }
            }
            item {
                TextButton(
                    text = stringResource(R.string.editor_add_activity),
                    onClick = {
                        newActName = ""
                        showAddActivityDialog = true
                    },
                    modifier = Modifier.padding(vertical = 6.dp),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }

            if (filteredActKeys.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.editor_no_search_results),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }

            items(filteredActKeys) { actName ->
                val actJson = activityRules.optJSONObject(actName)
                val mode = actJson?.optIntOrNull("mode")
                val modeLabels = listOf(
                    stringResource(R.string.editor_mode_auto),
                    stringResource(R.string.editor_mode_disabled),
                    stringResource(R.string.editor_mode_color_pick),
                    stringResource(R.string.editor_mode_float),
                )
                val modeLabel = mode?.let { m ->
                    modeLabels.getOrNull(MODE_VALUES.indexOf(m))
                } ?: "—"
                val color = actJson?.opt("color")
                val colorLabel = when (colorTypeFromValue(color)) {
                    COLOR_TYPE_AUTO -> stringResource(R.string.editor_color_auto_short)
                    COLOR_TYPE_CUSTOM -> stringResource(R.string.editor_color_hex_short, (argbFromValue(color) and 0xFFFFFF).toString(16).padStart(6, '0').uppercase())
                    else -> stringResource(R.string.editor_color_default_short)
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    onClick = {
                        searchQuery = ""
                        selectedActivity = actName
                        fieldBackup = stagingJsonStr
                        showFields = true
                    },
                    showIndication = true,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicComponent(
                            title = actName,
                            summary = "$modeLabel · $colorLabel",
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            showDeleteConfirm = "$selectedPackage::$actName"
                        }) {
                            Icon(
                                MiuixIcons.Delete,
                                contentDescription = stringResource(R.string.rules_delete),
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            if (filteredActKeys.isEmpty() && actKeys.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.editor_no_activities),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }

            item {
                Spacer(
                    Modifier.padding(
                        bottom = WindowInsets.navigationBars.asPaddingValues()
                            .calculateBottomPadding() +
                            WindowInsets.captionBar.asPaddingValues()
                                .calculateBottomPadding(),
                    )
                )
            }
        }
    }

    // ── Sheet 1: App list (bottommost) ─────────────────────────────────
    WindowBottomSheet(
        title = stringResource(R.string.editor_title_apps),
        show = showApps,
        onDismissRequest = {
            // Swipe dismiss — do NOT propagate to parent
            onDismiss()
        },
        startAction = {
            IconButton(onClick = {
                // × button — do NOT propagate to parent
                onDismiss()
            }) {
                Icon(
                    MiuixIcons.Close,
                    contentDescription = stringResource(R.string.rules_cancel),
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
        },
        endAction = {
            IconButton(onClick = {
                // ✓ button — save formatted JSON and close
                editingJsonStr = stagingJsonStr
                onJsonChange(formatNbiJson(editingJsonStr))
                onDismiss()
            }) {
                Icon(
                    MiuixIcons.Ok,
                    contentDescription = stringResource(R.string.editor_save),
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
        },
    ) {
        val pkgKeys = nbiRules.keys().asSequence().toList().sorted()

        // Filtered list for search
        val filteredPkgKeys = remember(searchQuery, pkgKeys, stagingJsonStr, editVersion) {
            if (searchQuery.isEmpty()) pkgKeys
            else pkgKeys.filter { pkg ->
                val name = nbiRules.optJSONObject(pkg)?.optString("name", "") ?: ""
                pkg.lowercase().contains(searchQuery.lowercase()) || name.lowercase().contains(searchQuery.lowercase())
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().scrollEndHaptic().overScrollVertical(),
        ) {
            // Search bar
            item {
                SearchBar(
                    modifier = Modifier.padding(bottom = 6.dp),
                    inputField = {
                        InputField(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onSearch = { },
                            expanded = false,
                            onExpandedChange = { },
                            label = stringResource(R.string.editor_search_apps),
                        )
                    },
                    expanded = false,
                    onExpandedChange = { },
                    content = { },
                )
            }
            // Rule name editor
            item {
                val ruleName = root.optString("name", "")
                var isEditingRuleName by remember { mutableStateOf(false) }
                var ruleNameField by remember(ruleName) { mutableStateOf(ruleName) }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    onClick = { isEditingRuleName = true },
                    showIndication = true,
                ) {
                    if (isEditingRuleName) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            TextField(
                                value = ruleNameField,
                                onValueChange = { ruleNameField = it },
                                label = stringResource(R.string.editor_rule_name),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(
                                    text = stringResource(R.string.rules_cancel),
                                    onClick = { isEditingRuleName = false },
                                    enabled = true,
                                )
                                Spacer(Modifier.width(12.dp))
                                TextButton(
                                    text = stringResource(R.string.editor_save),
                                    onClick = {
                                        root.put("name", ruleNameField.trim())
                                        saveRoot()
                                        isEditingRuleName = false
                                    },
                                    colors = ButtonDefaults.textButtonColorsPrimary(),
                                    enabled = true,
                                )
                            }
                        }
                    } else {
                        BasicComponent(
                            title = stringResource(R.string.editor_rule_name),
                            summary = if (ruleName.isNotEmpty()) "$ruleName${stringResource(R.string.editor_tap_edit)}" else stringResource(R.string.editor_not_set_tap_edit),
                        )
                    }
                }
            }
            item {
                SmallTitle(
                    text = stringResource(R.string.editor_app_list_count, nbiRules.length()),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        text = stringResource(R.string.editor_add_app),
                        onClick = {
                            newPkg = ""
                            newName = ""
                            showAddAppDialog = true
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                    IconButton(onClick = { showFloatingIdentify = true }) {
                        Icon(
                            MiuixIcons.Scan,
                            contentDescription = stringResource(R.string.editor_floating_identify),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                }
            }

            if (filteredPkgKeys.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.editor_no_search_results),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }

            items(filteredPkgKeys) { pkg ->
                val app = nbiRules.optJSONObject(pkg)
                val name = app?.optString("name", "") ?: ""
                val actCount = app?.optJSONObject("activityRules")?.length() ?: 0

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    onClick = {
                        searchQuery = ""
                        selectedPackage = pkg
                        activitiesBackup = stagingJsonStr
                        showActivities = true
                    },
                    showIndication = true,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicComponent(
                            title = pkg,
                            summary = buildString {
                                if (name.isNotEmpty()) append(name).append(" · ")
                                append(stringResource(R.string.editor_activity_count, actCount))
                            },
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { showDeleteConfirm = pkg }) {
                            Icon(
                                MiuixIcons.Delete,
                                contentDescription = stringResource(R.string.rules_delete),
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            item {
                Spacer(
                    Modifier.padding(
                        bottom = WindowInsets.navigationBars.asPaddingValues()
                            .calculateBottomPadding() +
                            WindowInsets.captionBar.asPaddingValues()
                                .calculateBottomPadding(),
                    )
                )
            }
        }
    }

    // ── Add App dialog ─────────────────────────────────────────────────
    WindowDialog(
        title = stringResource(R.string.editor_add_app),
        show = showAddAppDialog,
        onDismissRequest = { showAddAppDialog = false },
    ) {
        val dismissState = LocalDismissState.current
        TextField(
            modifier = Modifier.padding(vertical = 4.dp),
            value = newPkg,
            onValueChange = { newPkg = it },
            label = stringResource(R.string.editor_package_name),
            singleLine = true,
        )
        TextField(
            modifier = Modifier.padding(vertical = 4.dp),
            value = newName,
            onValueChange = { newName = it },
            label = stringResource(R.string.editor_app_name_optional),
            singleLine = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(
                text = stringResource(R.string.rules_cancel),
                onClick = { dismissState?.invoke() },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(20.dp))
            TextButton(
                text = stringResource(R.string.rules_confirm),
                onClick = {
                    val pkg = newPkg.trim()
                    if (pkg.isEmpty()) return@TextButton
                    if (!nbiRules.has(pkg)) {
                        val newApp = JSONObject()
                        newApp.put("name", newName.trim())
                        newApp.put("enable", true)
                        newApp.put("activityRules", JSONObject())
                        nbiRules.put(pkg, newApp)
                        saveRoot()
                    }
                    dismissState?.invoke()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }

    // ── Add Activity dialog ────────────────────────────────────────────
    WindowDialog(
        title = stringResource(R.string.editor_add_activity),
        show = showAddActivityDialog,
        onDismissRequest = { showAddActivityDialog = false },
    ) {
        val dismissState = LocalDismissState.current
        TextField(
            modifier = Modifier.padding(vertical = 4.dp),
            value = newActName,
            onValueChange = { newActName = it },
            label = stringResource(R.string.editor_activity_name_hint),
            singleLine = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(
                text = stringResource(R.string.rules_cancel),
                onClick = { dismissState?.invoke() },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(20.dp))
            TextButton(
                text = stringResource(R.string.rules_confirm),
                onClick = {
                    val actName = newActName.trim()
                    if (actName.isEmpty()) return@TextButton
                    val app = nbiRules.optJSONObject(selectedPackage)
                    val rules = app?.optJSONObject("activityRules")
                        ?: JSONObject().also { app?.put("activityRules", it) }
                    if (!rules.has(actName)) {
                        val newAct = JSONObject()
                        newAct.put("mode", 1)
                        newAct.put("color", 1)
                        newAct.put("sf_sampling_mode", 0)
                        newAct.put("dialogMode", 1)
                        newAct.put("popupMode", 1)
                        newAct.put("appNavColorDisabled", 0)
                        rules.put(actName, newAct)
                        saveRoot()
                    }
                    dismissState?.invoke()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }

    // ── Delete confirmation ────────────────────────────────────────────
    WindowDialog(
        title = stringResource(R.string.editor_confirm_delete),
        summary = stringResource(R.string.editor_confirm_delete_message),
        show = showDeleteConfirm.isNotEmpty(),
        onDismissRequest = { showDeleteConfirm = "" },
    ) {
        val dismissState = LocalDismissState.current
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(
                text = stringResource(R.string.rules_cancel),
                onClick = { dismissState?.invoke() },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(20.dp))
            TextButton(
                text = stringResource(R.string.rules_delete),
                onClick = {
                    val target = showDeleteConfirm
                    if (target.contains("::")) {
                        val parts = target.split("::")
                        deleteActivity(parts[0], parts[1])
                        if (showFields) {
                            showFields = false
                        }
                    } else {
                        deleteApp(target)
                        if ((showActivities || showFields) && selectedPackage == target) {
                            showActivities = false
                            showFields = false
                            selectedPackage = ""
                            selectedActivity = ""
                        }
                    }
                    dismissState?.invoke()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
    }
}
