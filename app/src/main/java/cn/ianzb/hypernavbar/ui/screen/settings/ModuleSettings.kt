package cn.ianzb.hypernavbar.ui.screen.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import cn.ianzb.hypernavbar.ui.component.SectionTitle
import cn.ianzb.hypernavbar.ui.screen.safemode.SafeModeActivity
import cn.ianzb.hypernavbar.ui.screen.scope.ScopeListActivity
import cn.ianzb.hypernavbar.ui.util.LocalPreferenceBackdrop
import cn.ianzb.hypernavbar.xposed.RootHelper
import cn.ianzb.hypernavbar.xposed.SafeModeReader
import cn.ianzb.hypernavbar.xposed.XposedServiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
internal fun ModuleSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val safeModeCount = SafeModeReader.safeModePackages.size
    val systemVersion = remember { SystemVersion.current() }
    var clearingCache by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { SafeModeReader.refresh() }

    SectionTitle(text = stringResource(R.string.settings_module))
    Card(Modifier.padding(horizontal = 12.dp)) {
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
        if (safeModeCount > 0) {
            BasicComponent(
                title = stringResource(R.string.safe_mode_active_title),
                summary = stringResource(R.string.safe_mode_manage_summary_active, safeModeCount),
            )
        }
        ArrowPreference(
            title = stringResource(R.string.safe_mode_manage),
            summary = if (safeModeCount > 0) {
                stringResource(R.string.safe_mode_manage_summary_active, safeModeCount)
            } else {
                stringResource(R.string.safe_mode_manage_summary_none)
            },
            onClick = { context.startActivity(Intent(context, SafeModeActivity::class.java)) },
        )
        ArrowPreference(
            title = stringResource(R.string.features_scope_title),
            onClick = { context.startActivity(Intent(context, ScopeListActivity::class.java)) },
        )
        ArrowPreference(
            title = stringResource(R.string.module_scope_request),
            summary = stringResource(R.string.module_scope_request_summary),
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
        ArrowPreference(
            title = stringResource(R.string.module_clear_dexkit),
            summary = stringResource(R.string.module_clear_dexkit_summary),
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
    }
}

private val packageName = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")

private fun deviceLabel(type: DeviceType): Int = when (type) {
    DeviceType.PHONE -> R.string.device_type_phone
    DeviceType.PAD -> R.string.device_type_pad
    DeviceType.FOLD -> R.string.device_type_fold
}
