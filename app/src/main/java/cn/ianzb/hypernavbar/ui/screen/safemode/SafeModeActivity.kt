package cn.ianzb.hypernavbar.ui.screen.safemode

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ianzb.hypernavbar.R
import cn.ianzb.hypernavbar.ui.component.AppSectionCard
import cn.ianzb.hypernavbar.ui.component.ScopedAppCard
import cn.ianzb.hypernavbar.ui.screen.subpage.BaseSubPageActivity
import cn.ianzb.hypernavbar.ui.util.LocalSubPageScrollBehavior
import cn.ianzb.hypernavbar.ui.util.pageScrollModifiers
import cn.ianzb.hypernavbar.xposed.SafeModeReader
import cn.ianzb.hypernavbar.xposed.XposedServiceManager
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.time.Duration.Companion.milliseconds
import top.yukonga.miuix.kmp.basic.Text as MiuixText

/**
 * 安全模式二级页面。
 *
 * 列出全部被 hook 的应用，逐项控制其安全模式开关：
 * - 打开：该应用下次启动时跳过全部 hook（原生与 Java）；
 * - 关闭：恢复 hook，并清空崩溃计数与加载时间戳。
 *
 * 进入方式：设置 → 模块 → 安全模式。
 */
class SafeModeActivity : BaseSubPageActivity() {

    override val titleRes: Int = R.string.safe_mode_manage

    @Composable
    override fun SubPageContent(
        isBlurEnabled: Boolean,
        contentPadding: PaddingValues,
    ) = SafeModeContent(contentPadding)
}

@Composable
internal fun SafeModeContent(contentPadding: PaddingValues) {
    LaunchedEffect(Unit) {
        delay(400.milliseconds)
        XposedServiceManager.refreshScope()
        SafeModeReader.refresh()
        while (true) {
            delay(1500.milliseconds)
            XposedServiceManager.refreshScope()
            SafeModeReader.refresh()
        }
    }

    val resources = LocalResources.current
    val scope = XposedServiceManager.scope
    val safeModePackages = SafeModeReader.safeModePackages
    val scrollBehavior = LocalSubPageScrollBehavior.current
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
        item {
            AppSectionCard(topSpace = 12.dp) {
                MiuixText(
                    text = stringResource(R.string.safe_mode_declaration),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote2,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }

        if (scope.isEmpty()) {
            item {
                MiuixText(
                    text = stringResource(R.string.scope_empty),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote2,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                )
            }
        } else {
            items(scope, key = { it }) { packageName ->
                val inSafeMode = packageName in safeModePackages
                val crashes = SafeModeReader.crashCount(packageName)

                val status = if (crashes > 0) resources.getString(R.string.safe_mode_crash_count, crashes) else null

                ScopedAppCard(
                    packageName = packageName,
                    status = status,
                    endActions = {
                        Switch(
                            checked = inSafeMode,
                            onCheckedChange = { checked ->
                                SafeModeReader.setSafeMode(packageName, checked)
                            },
                        )
                    },
                )
            }
        }
    }
}
