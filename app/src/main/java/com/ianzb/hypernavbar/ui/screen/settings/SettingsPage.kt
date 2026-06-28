package com.ianzb.hypernavbar.ui.screen.settings

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ianzb.hypernavbar.AppSettings
import com.ianzb.hypernavbar.LocaleHelper
import com.ianzb.hypernavbar.R
import com.ianzb.hypernavbar.ui.util.BlurredBar
import com.ianzb.hypernavbar.ui.util.pageScrollModifiers
import com.ianzb.hypernavbar.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import java.io.BufferedReader
import java.io.InputStreamReader
import top.yukonga.miuix.kmp.basic.Text as MiuixText

@Composable
fun SettingsPageView(
    currentMode: ColorSchemeMode,
    onModeChange: (ColorSchemeMode) -> Unit,
    isFloatingNavbar: Boolean,
    onFloatingNavbarChange: (Boolean) -> Unit,
    isLiquidGlass: Boolean,
    onLiquidGlassChange: (Boolean) -> Unit,
    applyIntervalMinutes: Int,
    onApplyIntervalChange: (Int) -> Unit,
    extraBottomPadding: Dp = 0.dp,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scrollBehavior = MiuixScrollBehavior()
    val title = stringResource(R.string.tab_settings)

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    var showIntervalDialog by remember { mutableStateOf(false) }
    var intervalInput by remember { mutableStateOf(applyIntervalMinutes.toString()) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            try {
                val settings = AppSettings.load(context)
                val rulesConfigs = com.ianzb.hypernavbar.rules.RulesManager.loadAll(context)
                val configsArr = com.ianzb.hypernavbar.rules.RulesManager.exportJson(rulesConfigs)
                val json = settings.copy(rulesConfigsJson = configsArr.toString()).toJson()
                context.contentResolver.openOutputStream(it)?.use { output ->
                    output.write(json.toByteArray())
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
                val inputStream = context.contentResolver.openInputStream(it)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val json = reader.readText()
                reader.close()
                inputStream?.close()
                AppSettings.importFromJson(context, json)
                Toast.makeText(context, context.getString(R.string.import_success), Toast.LENGTH_SHORT).show()
                activity?.recreate()
            } catch (_: Exception) {
                Toast.makeText(context, context.getString(R.string.import_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Interval dialog
    WindowDialog(
        title = stringResource(R.string.rules_apply_interval),
        summary = stringResource(R.string.rules_apply_interval_summary),
        show = showIntervalDialog,
        onDismissRequest = { showIntervalDialog = false }
    ) {
        TextField(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            value = intervalInput,
            onValueChange = { intervalInput = it.filter(Char::isDigit) },
            label = stringResource(R.string.rules_apply_interval),
            singleLine = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            TextButton(
                text = stringResource(R.string.rules_cancel),
                onClick = { showIntervalDialog = false; intervalInput = applyIntervalMinutes.toString() },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            TextButton(
                text = stringResource(R.string.rules_confirm),
                onClick = {
                    onApplyIntervalChange(intervalInput.toIntOrNull() ?: 0)
                    showIntervalDialog = false
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop, blurActive) {
                TopAppBar(
                    title = title,
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(
            modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .pageScrollModifiers(
                        showTopAppBar = true,
                        topAppBarScrollBehavior = scrollBehavior,
                    ),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + extraBottomPadding
                )
            ) {
                item {
                    Column {
                        SmallTitle(text = stringResource(R.string.settings_features))
                        Card(
                            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)
                        ) {
                            ArrowPreference(
                                title = stringResource(R.string.rules_apply_interval),
                                summary = if (applyIntervalMinutes > 0) stringResource(R.string.rules_apply_interval_value, applyIntervalMinutes) else stringResource(R.string.rules_apply_interval_off),
                                onClick = {
                                    intervalInput = applyIntervalMinutes.toString()
                                    showIntervalDialog = true
                                }
                            )
                        }

                        SmallTitle(text = stringResource(R.string.settings_interface))
                        Card(
                            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)
                        ) {
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
                                var expanded by remember { mutableStateOf(false) }
                                val currentIndex = modesEnum.indexOf(currentMode).takeIf { it >= 0 } ?: 0

                                WindowDropdownPreference(
                                    title = stringResource(R.string.theme_mode),
                                    summary = modes[currentIndex],
                                    items = modes,
                                    selectedIndex = currentIndex,
                                    onSelectedIndexChange = { onModeChange(modesEnum[it]) },
                                    onExpandedChange = { expanded = it }
                                )

                                SwitchPreference(
                                    title = stringResource(R.string.floating_navbar),
                                    summary = stringResource(R.string.floating_navbar_summary),
                                    checked = isFloatingNavbar,
                                    onCheckedChange = onFloatingNavbarChange
                                )

                                AnimatedVisibility(
                                    visible = isFloatingNavbar,
                                    enter = expandVertically(),
                                    exit = shrinkVertically(),
                                ) {
                                    SwitchPreference(
                                        title = stringResource(R.string.liquid_glass),
                                        summary = stringResource(R.string.liquid_glass_summary),
                                        checked = isLiquidGlass,
                                        onCheckedChange = onLiquidGlassChange
                                    )
                                }
                            }
                        }

                        SmallTitle(text = stringResource(R.string.settings_language))
                        Card(
                            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)
                        ) {
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
                            var langExpanded by remember { mutableStateOf(false) }

                            WindowDropdownPreference(
                                title = stringResource(R.string.settings_language),
                                summary = languageNames[langCurrentIndex],
                                items = languageNames,
                                selectedIndex = langCurrentIndex,
                                onSelectedIndexChange = {
                                    LocaleHelper.setLanguage(context, languageValues[it])
                                    activity?.recreate()
                                },
                                onExpandedChange = { langExpanded = it }
                            )
                        }

                        SmallTitle(text = stringResource(R.string.settings_data))
                        Card(
                            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)
                        ) {
                            Column {
                                ArrowPreference(
                                    title = stringResource(R.string.export_settings),
                                    summary = stringResource(R.string.export_settings_summary),
                                    onClick = {
                                        exportLauncher.launch("HyperNavBar_settings.json")
                                    }
                                )

                                ArrowPreference(
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
    }
}
