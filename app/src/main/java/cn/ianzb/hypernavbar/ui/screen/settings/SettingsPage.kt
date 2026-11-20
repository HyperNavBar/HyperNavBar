package cn.ianzb.hypernavbar.ui.screen.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.ianzb.hypernavbar.AppSettings
import cn.ianzb.hypernavbar.BottomBarStyle
import cn.ianzb.hypernavbar.LocaleHelper
import cn.ianzb.hypernavbar.R
import cn.ianzb.hypernavbar.RailStyle
import cn.ianzb.hypernavbar.UpdateChecker
import cn.ianzb.hypernavbar.hook.device.DeviceContext
import cn.ianzb.hypernavbar.prefs.ConfigBackup
import cn.ianzb.hypernavbar.prefs.ConfigState
import cn.ianzb.hypernavbar.prefs.OptionRegistry
import cn.ianzb.hypernavbar.prefs.OptionType
import cn.ianzb.hypernavbar.ui.component.AppArrowPreference
import cn.ianzb.hypernavbar.ui.component.AppDialog
import cn.ianzb.hypernavbar.ui.component.AppDialogButton
import cn.ianzb.hypernavbar.ui.component.AppLazyPage
import cn.ianzb.hypernavbar.ui.component.AppSectionCard
import cn.ianzb.hypernavbar.ui.component.CurrentChangelogDialog
import cn.ianzb.hypernavbar.ui.screen.rules.FloatingIdentifyService
import cn.ianzb.hypernavbar.ui.screen.rules.ScreenColorPickerService
import cn.ianzb.hypernavbar.ui.util.LocalPreferenceBackdrop
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text as MiuixText

@Composable
fun SettingsPageView(
    currentMode: ColorSchemeMode,
    onModeChange: (ColorSchemeMode) -> Unit,
    bottomBarStyle: BottomBarStyle,
    railStyle: RailStyle,
    onBottomBarChange: (BottomBarStyle) -> Unit,
    onRailStyleChange: (RailStyle) -> Unit,
    isBlurEnabled: Boolean,
    onBlurEnabledChange: (Boolean) -> Unit,
    applyIntervalMinutes: Int,
    onApplyIntervalChange: (Int) -> Unit,
    autoApplyAfterEdit: Boolean,
    onAutoApplyAfterEditChange: (Boolean) -> Unit,
    checkUpdateOnLaunch: Boolean,
    onCheckUpdateOnLaunchChange: (Boolean) -> Unit,
    hideLauncherIcon: Boolean,
    onHideLauncherIconChange: (Boolean) -> Unit,
    onCheckUpdate: () -> Unit,
    isCheckingUpdate: Boolean,
    extraBottomPadding: Dp = 0.dp,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val title = stringResource(R.string.tab_settings)


    var showIntervalDialog by remember { mutableStateOf(false) }
    var intervalInput by remember { mutableStateOf(applyIntervalMinutes.toString()) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            try {
                val settings = AppSettings.load(context)
                val rulesConfigs = cn.ianzb.hypernavbar.rules.RulesManager.loadAll(context)
                val configsArr = cn.ianzb.hypernavbar.rules.RulesManager.exportJson(rulesConfigs)
                val json = JSONObject(settings.copy(rulesConfigsJson = configsArr.toString()).toJson())
                json.put("moduleOptions", JSONObject(ConfigBackup.exportJson(moduleOptionTypes().keys)))
                requireNotNull(context.contentResolver.openOutputStream(it)).use { output ->
                    output.write(json.toString(2).toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, context.getString(R.string.export_success), Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, context.getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val json = requireNotNull(context.contentResolver.openInputStream(it))
                    .bufferedReader().use { reader -> reader.readText() }
                val backup = JSONObject(json)
                val moduleValues = backup.optJSONObject("moduleOptions")
                val values = moduleOptionTypes().mapNotNull { (key, type) ->
                    val raw = moduleValues?.opt(key)
                    val value = when (type) {
                        OptionType.SWITCH, OptionType.CHECKBOX -> raw as? Boolean
                        OptionType.SLIDER -> (raw as? Number)?.toFloat()?.takeIf { it.isFinite() }
                        else -> raw as? String
                    }
                    if (value != null) key to value else null
                }
                AppSettings.importFromJson(context, json)
                values.forEach { (key, value) -> ConfigState.set(key, value) }
                Toast.makeText(context, context.getString(R.string.import_success), Toast.LENGTH_SHORT).show()
                activity?.recreate()
            } catch (_: Exception) {
                Toast.makeText(context, context.getString(R.string.import_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Interval dialog
    var showChangelog by rememberSaveable { mutableStateOf(false) }
    if (showChangelog) CurrentChangelogDialog(onDismiss = { showChangelog = false })

    AppDialog(
        show = showIntervalDialog,
        title = stringResource(R.string.rules_apply_interval),
        summary = stringResource(R.string.rules_apply_interval_summary),
        onDismissRequest = { showIntervalDialog = false },
        content = {
            val dismissState = LocalDismissState.current
            TextField(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                value = intervalInput,
                onValueChange = { intervalInput = it.filter(Char::isDigit) },
                label = stringResource(R.string.rules_apply_interval),
                singleLine = true,
            )
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AppDialogButton(
                    text = stringResource(R.string.rules_cancel),
                    onClick = { dismissState?.invoke() },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(20.dp))
                AppDialogButton(
                    text = stringResource(R.string.rules_confirm),
                    onClick = {
                        onApplyIntervalChange(intervalInput.toIntOrNull() ?: 0)
                        dismissState?.invoke()
                    },
                    modifier = Modifier.weight(1f),
                    primary = true,
                )
            }
        },
    )

    AppLazyPage(
        title = title,
        isBlurEnabled = isBlurEnabled,
        extraBottomPadding = extraBottomPadding,
    ) {
    item {
            Column {
                ModuleSettings()

                AppSectionCard(title = stringResource(R.string.settings_features)) {
                    AppArrowPreference(
                        title = stringResource(R.string.rules_apply_interval),
                        summary = if (applyIntervalMinutes > 0) stringResource(R.string.rules_apply_interval_value, applyIntervalMinutes) else stringResource(R.string.rules_apply_interval_off),
                        onClick = {
                            intervalInput = applyIntervalMinutes.toString()
                            showIntervalDialog = true
                        }
                    )
                    SwitchPreference(
                        title = stringResource(R.string.auto_apply_after_edit),
                        summary = stringResource(R.string.auto_apply_after_edit_summary),
                        checked = autoApplyAfterEdit,
                        onCheckedChange = onAutoApplyAfterEditChange,
                    )
                }

                AppSectionCard(title = stringResource(R.string.settings_interface)) {
                    Column {
                        val modes = listOf(
                            stringResource(R.string.theme_system),
                            stringResource(R.string.theme_light),
                            stringResource(R.string.theme_dark),
                            stringResource(R.string.theme_monet_system),
                            stringResource(R.string.theme_monet_light),
                            stringResource(R.string.theme_monet_dark)
                        )
                        val modesEnum = listOf(
                            ColorSchemeMode.System,
                            ColorSchemeMode.Light,
                            ColorSchemeMode.Dark,
                            ColorSchemeMode.MonetSystem,
                            ColorSchemeMode.MonetLight,
                            ColorSchemeMode.MonetDark
                        )
                        val currentIndex = modesEnum.indexOf(currentMode).takeIf { it >= 0 } ?: 0

                        GlassDropdownPreference(
                            backdrop = LocalPreferenceBackdrop.current,
                            title = stringResource(R.string.theme_mode),
                            items = modes,
                            selectedIndex = currentIndex,
                            onSelectedIndexChange = { onModeChange(modesEnum[it]) },
                        )

                        GlassDropdownPreference(
                            title = stringResource(R.string.bottom_bar_style),
                            summary = stringResource(R.string.bottom_bar_style_summary),
                            items = listOf(
                                stringResource(R.string.bottom_bar_style_hyperos),
                                stringResource(R.string.bottom_bar_style_ios),
                            ),
                            selectedIndex = bottomBarStyle.ordinal,
                            onSelectedIndexChange = { onBottomBarChange(BottomBarStyle.entries[it]) },
                            backdrop = LocalPreferenceBackdrop.current,
                            renderInRootScaffold = true,
                        )

                        GlassDropdownPreference(
                            title = stringResource(R.string.rail_style),
                            summary = stringResource(R.string.rail_style_summary),
                            items = listOf(
                                stringResource(R.string.rail_style_none),
                                stringResource(R.string.rail_style_top),
                                stringResource(R.string.rail_style_left),
                            ),
                            selectedIndex = railStyle.ordinal,
                            onSelectedIndexChange = { onRailStyleChange(RailStyle.entries[it]) },
                            backdrop = LocalPreferenceBackdrop.current,
                            renderInRootScaffold = true,
                        )
                        SwitchPreference(
                            title = stringResource(R.string.blur_enabled),
                            summary = stringResource(R.string.blur_enabled_summary),
                            checked = isBlurEnabled,
                            onCheckedChange = onBlurEnabledChange
                        )

                        SwitchPreference(
                            title = stringResource(R.string.hide_launcher_icon),
                            summary = stringResource(R.string.hide_launcher_icon_summary),
                            checked = hideLauncherIcon,
                            onCheckedChange = onHideLauncherIconChange
                        )
                    }
                }

                AppSectionCard(title = stringResource(R.string.settings_language)) {
                    val languageNames = listOf(
                        stringResource(R.string.language_default),
                        stringResource(R.string.language_zh_cn),
                        stringResource(R.string.language_en)
                    )
                    val languageValues = listOf(
                        LocaleHelper.Language.SYSTEM,
                        LocaleHelper.Language.ZH_CN,
                        LocaleHelper.Language.EN
                    )
                    val savedLanguage = LocaleHelper.getSavedLanguage(context)
                    val langCurrentIndex = languageValues.indexOf(savedLanguage)
                        .takeIf { it >= 0 } ?: 0

                    GlassDropdownPreference(
                        backdrop = LocalPreferenceBackdrop.current,
                        title = stringResource(R.string.settings_language),
                        items = languageNames,
                        selectedIndex = langCurrentIndex,
                        onSelectedIndexChange = {
                            val wasFloatingRunning = FloatingIdentifyService.isRunning
                            val wasColorPickerRunning = ScreenColorPickerService.isRunning
                            if (wasFloatingRunning) {
                                context.stopService(Intent(context, FloatingIdentifyService::class.java))
                            }
                            if (wasColorPickerRunning) {
                                context.stopService(Intent(context, ScreenColorPickerService::class.java))
                            }
                            LocaleHelper.setLanguage(context, languageValues[it])
                            activity?.recreate()
                            // 语言切换后重启悬浮窗和取色器
                            if (wasFloatingRunning) {
                                context.startService(Intent(context, FloatingIdentifyService::class.java))
                            }
                            if (wasColorPickerRunning) {
                                context.startService(Intent(context, ScreenColorPickerService::class.java))
                            }
                        },
                    )
                }

                AppSectionCard(title = stringResource(R.string.settings_update)) {
                    Column {
                        SwitchPreference(
                            title = stringResource(R.string.check_update_on_launch),
                            summary = stringResource(R.string.check_update_on_launch_summary),
                            checked = checkUpdateOnLaunch,
                            onCheckedChange = onCheckUpdateOnLaunchChange,
                        )

                        AppArrowPreference(
                            title = stringResource(R.string.check_update),
                            summary = if (isCheckingUpdate) {
                                stringResource(R.string.check_update_checking)
                            } else {
                                stringResource(R.string.check_update_summary, UpdateChecker.currentVersion(context))
                            },
                            onClick = onCheckUpdate,
                        )

                        AppArrowPreference(
                            title = stringResource(R.string.current_changelog),
                            onClick = { showChangelog = true },
                        )
                    }
                }

                AppSectionCard(title = stringResource(R.string.settings_data)) {
                    Column {
                        AppArrowPreference(
                            title = stringResource(R.string.export_settings),
                            summary = stringResource(R.string.export_settings_summary),
                            onClick = {
                                exportLauncher.launch("HyperNavBar_settings.json")
                            }
                        )

                        AppArrowPreference(
                            title = stringResource(R.string.import_settings),
                            summary = stringResource(R.string.import_settings_summary),
                            onClick = { importLauncher.launch("application/json") }
                        )
                    }
                }

                MiuixText(
                    text = stringResource(R.string.copyright),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun moduleOptionTypes(): Map<String, OptionType> = buildMap {
    put(DeviceContext.KEY_DEVICE_TYPE, OptionType.TEXT)
    OptionRegistry.all().forEach { spec ->
        if (spec.type != OptionType.ARROW) {
            put(spec.key, spec.type)
            spec.masterKey?.let { put(it, OptionType.SWITCH) }
        }
    }
}
