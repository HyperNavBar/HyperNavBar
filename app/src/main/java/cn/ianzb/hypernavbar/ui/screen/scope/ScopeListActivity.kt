package cn.ianzb.hypernavbar.ui.screen.scope

import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ianzb.hypernavbar.R
import cn.ianzb.hypernavbar.prefs.HookBlacklist
import cn.ianzb.hypernavbar.ui.component.AppSectionCard
import cn.ianzb.hypernavbar.ui.component.ScopedAppCard
import cn.ianzb.hypernavbar.ui.component.SectionTitle
import cn.ianzb.hypernavbar.ui.component.SystemRestartConfirmDialog
import cn.ianzb.hypernavbar.ui.screen.subpage.BaseSubPageActivity
import cn.ianzb.hypernavbar.ui.util.LocalSubPageScrollBehavior
import cn.ianzb.hypernavbar.ui.util.pageScrollModifiers
import cn.ianzb.hypernavbar.xposed.AppRestarter
import cn.ianzb.hypernavbar.xposed.SafeModeReader
import cn.ianzb.hypernavbar.xposed.XposedServiceManager
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Close
import top.yukonga.miuix.kmp.icon.glass.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text as MiuixText

/**
 * 作用域列表二级页面。
 *
 * - 页面可见期间轮询刷新作用域，实时反映 LSPosed 中的授权变化。
 * - 每项右侧提供「重启」按钮（需要 Root）。
 * - 被兜底机制自动禁用的应用会标注「安全模式」，并提供一键恢复。
 */
class ScopeListActivity : BaseSubPageActivity() {

    override val titleRes: Int = R.string.home_scope

    @Composable
    override fun SubPageContent(
        isBlurEnabled: Boolean,
        contentPadding: PaddingValues,
    ) = ScopeListContent(contentPadding)
}

@Composable
internal fun ScopeListContent(contentPadding: PaddingValues) {
    val context = LocalContext.current
    val resources = LocalResources.current
    // 排除名单用可观察状态，点击「排除 / 恢复注入」后立即刷新列表
    var blacklist by remember { mutableStateOf(HookBlacklist.all()) }
    // 模块历史自动申请的作用域数量（用于「取消申请」入口）
    var requestedCount by remember { mutableStateOf(XposedServiceManager.requestedPackages().size) }

    // 进入页面前已在主页刷新过；这里延后刷新，避免与打开动画冲突（动画优先）。
    LaunchedEffect(Unit) {
        delay(400.milliseconds)
        XposedServiceManager.refreshScope()
        SafeModeReader.refresh()
        blacklist = HookBlacklist.all()
        requestedCount = XposedServiceManager.requestedPackages().size
        // 轮询保持列表实时更新（含安全模式状态）。
        while (true) {
            delay(1500.milliseconds)
            XposedServiceManager.refreshScope()
            SafeModeReader.refresh()
            blacklist = HookBlacklist.all()
            requestedCount = XposedServiceManager.requestedPackages().size
        }
    }

    val scope = XposedServiceManager.scope
    val safeModePackages = SafeModeReader.safeModePackages
    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = LocalSubPageScrollBehavior.current
    val excluded = remember(blacklist, scope) { (blacklist - scope.toSet()).sorted() }

    val searchField = rememberTextFieldState()
    val query = searchField.text.toString().trim()
    val packageManager = context.packageManager
    val labels by produceState<Map<String, String>>(emptyMap(), scope, excluded, packageManager) {
        value = withContext(Dispatchers.IO) {
            (scope + excluded).associateWith { packageName ->
                runCatching {
                    packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString()
                }.getOrDefault("")
            }
        }
    }
    fun List<String>.matchingQuery(): List<String> = if (query.isEmpty()) this else filter { packageName ->
        packageName.contains(query, ignoreCase = true) ||
            labels[packageName]?.contains(query, ignoreCase = true) == true
    }
    val visibleScope = remember(scope, query, labels) { scope.matchingQuery() }
    val visibleExcluded = remember(excluded, query, labels) { excluded.matchingQuery() }

    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (scrollBehavior != null) {
                    Modifier.pageScrollModifiers(
                        showTopAppBar = true,
                        topAppBarScrollBehavior = scrollBehavior,
                        contentState = listState,
                    )
                } else {
                    Modifier
                }
            ),
        contentPadding = contentPadding,
    ) {
        if (requestedCount > 0) {
            item {
                AppSectionCard(topSpace = 12.dp) {
                    BasicComponent(
                        title = stringResource(R.string.scope_cancel_requested_title),
                        summary = stringResource(R.string.scope_cancel_requested_summary, requestedCount),
                        endActions = {
                            TextButton(
                                text = stringResource(R.string.scope_cancel_requested_action),
                                onClick = {
                                    val count = requestedCount
                                    XposedServiceManager.cancelRequestedScope()
                                    requestedCount = XposedServiceManager.requestedPackages().size
                                    Toast.makeText(
                                        context,
                                        resources.getString(R.string.scope_cancel_requested_done, count),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                                colors = ButtonDefaults.textButtonColors(textColor = Color(0xFFE5A000)),
                                minWidth = 0.dp,
                            )
                        },
                    )
                }
            }
        }
        item(key = "scope-search") {
            GlassTextField(
                state = searchField,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                label = stringResource(R.string.search_hint),
                useLabelAsPlaceholder = true,
                lineLimits = TextFieldLineLimits.SingleLine,
                leadingIcon = {
                    Icon(MiuixIcons.Glass.Search, null, Modifier.size(28.dp))
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        GlassIconButton(onClick = searchField::clearText, surfaceAlpha = 0f, size = 36.dp) {
                            Icon(MiuixIcons.Glass.Close, stringResource(R.string.search_clear), Modifier.size(28.dp))
                        }
                    }
                },
            )
        }

        if (visibleScope.isEmpty() && visibleExcluded.isEmpty()) {
            item {
                MiuixText(
                    text = stringResource(if (query.isEmpty()) R.string.scope_empty else R.string.search_empty),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote2,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                )
            }
        } else {
            items(visibleScope, key = { it }) { packageName ->
                val inSafeMode = packageName in safeModePackages
                val status = if (inSafeMode) stringResource(R.string.scope_safe_mode) else null

                var showRebootConfirm by remember(packageName) { mutableStateOf(false) }
                val doRestart: () -> Unit = {
                    coroutineScope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            AppRestarter.restart(context, packageName)
                        }
                        if (!ok) {
                            Toast.makeText(
                                context,
                                resources.getString(R.string.scope_restart_need_root),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }

                ScopedAppCard(
                    packageName = packageName,
                    status = status,
                    endActions = { appName ->
                        TextButton(
                            text = stringResource(R.string.scope_exclude),
                            onClick = {
                                XposedServiceManager.removeScope(listOf(packageName))
                                HookBlacklist.add(packageName)
                                blacklist = HookBlacklist.all()
                                SafeModeReader.reset(packageName)
                                Toast.makeText(
                                    context,
                                    resources.getString(R.string.scope_exclude_done, appName),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                            colors = ButtonDefaults.textButtonColors(textColor = Color(0xFFE5A000)),
                            minWidth = 0.dp,
                        )
                        if (inSafeMode) {
                            TextButton(
                                text = stringResource(R.string.scope_safe_mode_reset),
                                onClick = {
                                    SafeModeReader.reset(packageName)
                                    Toast.makeText(
                                        context,
                                        resources.getString(R.string.scope_safe_mode_reset_done, appName),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                                colors = ButtonDefaults.textButtonColors(textColor = Color(0xFFDC3545)),
                                minWidth = 0.dp,
                            )
                        }
                        Button(
                            onClick = {
                                if (AppRestarter.isSystemPackage(packageName)) {
                                    showRebootConfirm = true
                                } else {
                                    doRestart()
                                }
                            },
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            minWidth = 0.dp,
                        ) {
                            MiuixText(text = stringResource(R.string.scope_restart))
                        }
                    },
                )

                if (showRebootConfirm) {
                    SystemRestartConfirmDialog(
                        onConfirm = {
                            showRebootConfirm = false
                            doRestart()
                        },
                        onDismiss = { showRebootConfirm = false },
                    )
                }
            }
        }

        if (visibleExcluded.isNotEmpty()) {
            item {
                SectionTitle(text = stringResource(R.string.scope_excluded_title), followsContent = false)
            }
            items(visibleExcluded, key = { "excluded:$it" }) { pkg ->
                ScopedAppCard(
                    packageName = pkg,
                    endActions = {
                        Button(
                            onClick = {
                                HookBlacklist.remove(pkg)
                                blacklist = HookBlacklist.all()
                                XposedServiceManager.ensureScope(listOf(pkg))
                            },
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            minWidth = 0.dp,
                        ) {
                            MiuixText(text = stringResource(R.string.scope_restore_inject))
                        }
                    },
                )
            }
        }
    }
}
