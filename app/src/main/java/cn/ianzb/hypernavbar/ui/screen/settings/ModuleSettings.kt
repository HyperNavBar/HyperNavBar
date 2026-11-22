package cn.ianzb.hypernavbar.ui.screen.settings

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import cn.ianzb.hypernavbar.R
import cn.ianzb.hypernavbar.hook.device.DeviceContext
import cn.ianzb.hypernavbar.hook.device.DeviceType
import cn.ianzb.hypernavbar.hook.dexkit.DexKitCacheManager
import cn.ianzb.hypernavbar.hook.prefs.HookKeys
import cn.ianzb.hypernavbar.hook.rule.SystemVersion
import cn.ianzb.hypernavbar.prefs.ConfigState
import cn.ianzb.hypernavbar.prefs.HookBlacklist
import cn.ianzb.hypernavbar.prefs.PrefsStore
import cn.ianzb.hypernavbar.rules.RuleCombiner
import cn.ianzb.hypernavbar.rules.RuleConverter
import cn.ianzb.hypernavbar.rules.RuleFetcher
import cn.ianzb.hypernavbar.rules.RulesManager
import cn.ianzb.hypernavbar.ui.component.AppArrowPreference
import cn.ianzb.hypernavbar.ui.component.AppCardRow
import cn.ianzb.hypernavbar.ui.screen.subpage.AppSubPage
import cn.ianzb.hypernavbar.ui.screen.subpage.rememberOpenAppSubPage
import cn.ianzb.hypernavbar.ui.util.LocalPreferenceBackdrop
import cn.ianzb.hypernavbar.xposed.RootHelper
import cn.ianzb.hypernavbar.xposed.SafeModeReader
import cn.ianzb.hypernavbar.xposed.XposedServiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference

@Composable
internal fun rememberModuleSettingsRows(): Array<AppCardRow?> {
    val context = LocalContext.current
    val openSafeMode = rememberOpenAppSubPage(AppSubPage.SafeMode)
    val openScope = rememberOpenAppSubPage(AppSubPage.Scope)
    val scope = rememberCoroutineScope()
    val safeModeCount = SafeModeReader.safeModePackages.size
    val systemVersion = remember { SystemVersion.current() }
    var clearingCache by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { SafeModeReader.refresh() }

    return arrayOf(
        AppCardRow(R.string.settings_device_type) {
            val deviceValues = listOf(DeviceType.OVERRIDE_AUTO) + DeviceType.entries.map { it.key }
            val deviceLabels = listOf(
                stringResource(R.string.device_type_default, stringResource(deviceLabel(DeviceContext.detected.type))),
            ) + DeviceType.entries.map { stringResource(deviceLabel(it)) }
            val device = ConfigState.string(DeviceContext.KEY_DEVICE_TYPE, DeviceType.OVERRIDE_AUTO)
            GlassDropdownPreference(
                title = stringResource(R.string.settings_device_type),
                summary = stringResource(R.string.settings_device_type_summary),
                items = deviceLabels,
                selectedIndex = deviceValues.indexOf(device).coerceAtLeast(0),
                onSelectedIndexChange = { ConfigState.set(DeviceContext.KEY_DEVICE_TYPE, deviceValues[it]) },
                backdrop = LocalPreferenceBackdrop.current,
            )
        },
        if (safeModeCount > 0) AppCardRow(R.string.safe_mode_active_title) {
            BasicComponent(title = stringResource(R.string.safe_mode_active_title))
        } else null,
        AppCardRow(R.string.safe_mode_manage) {
            AppArrowPreference(
                title = stringResource(R.string.safe_mode_manage),
                onClick = openSafeMode,
            )
        },
        AppCardRow(R.string.features_scope_title) {
            AppArrowPreference(
                title = stringResource(R.string.features_scope_title),
                onClick = openScope,
            )
        },
        AppCardRow(R.string.module_scope_request) {
            AppArrowPreference(
                title = stringResource(R.string.module_scope_request),
                enabled = XposedServiceManager.isActivated && systemVersion.immersiveSupported,
                onClick = {
                    scope.launch {
                        val packages = withContext(Dispatchers.IO) {
                            val requested = mutableListOf("system")
                            if (systemVersion.isOs4 && PrefsStore.getBoolean(HookKeys.GESTURE_HANDLE, false)) {
                                requested += XposedServiceManager.SYSTEM_UI_PACKAGE
                            }
                            if (PrefsStore.getBoolean(HookKeys.MASTER, false)) {
                                val configs = RulesManager.loadAll(context)
                                val cached = configs.mapNotNull { config ->
                                    RuleFetcher.parseJson(config.cachedContent).getOrNull()?.let { config.id to it }
                                }.toMap()
                                requested += RuleConverter.scopeRequestPackages(
                                    RuleCombiner.combine(configs, cached),
                                    masterEnabled = true,
                                )
                            }
                            HookBlacklist.filter(requested.distinct())
                        }
                        if (packages.isNotEmpty()) {
                            XposedServiceManager.ensureScope(packages) { ok, message ->
                                scope.launch {
                                    val text = if (ok) context.getString(R.string.module_scope_request_success)
                                    else context.getString(R.string.module_scope_request_failed, message.orEmpty())
                                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                },
            )
        },
        AppCardRow(R.string.module_clear_dexkit) {
            AppArrowPreference(
                title = stringResource(R.string.module_clear_dexkit),
                enabled = XposedServiceManager.isRootAvailable && XposedServiceManager.scope.isNotEmpty() && !clearingCache,
                onClick = {
                    clearingCache = true
                    scope.launch {
                        val success = try {
                            withContext(Dispatchers.IO) {
                                val targets = XposedServiceManager.scope.filter { packageName.matches(it) }
                                RootHelper.deleteDexKitCache(targets, DexKitCacheManager.CACHE_DIR)
                            }
                        } finally {
                            clearingCache = false
                        }
                        Toast.makeText(
                            context,
                            if (success) R.string.module_clear_dexkit_success else R.string.module_clear_dexkit_failed,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            )
        },
    )
}

private val packageName = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")

private fun deviceLabel(type: DeviceType): Int = when (type) {
    DeviceType.PHONE -> R.string.device_type_phone
    DeviceType.PAD -> R.string.device_type_pad
    DeviceType.FOLD -> R.string.device_type_fold
}
