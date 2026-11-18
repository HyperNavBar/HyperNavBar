package cn.ianzb.hypernavbar.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import cn.ianzb.hypernavbar.ui.util.LocalPreferenceBackdrop
import cn.ianzb.hypernavbar.ui.util.LocalSubPageScrollBehavior
import cn.ianzb.hypernavbar.ui.util.LocalSubPageScrollGate
import cn.ianzb.hypernavbar.ui.util.PageBackdrop
import cn.ianzb.hypernavbar.ui.util.SubPageScrollGate
import cn.ianzb.hypernavbar.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 二级页面模板脚手架：顶栏返回、背景模糊、主题等模块配置统一在此处理。
 *
 * @param topBarActions 顶栏右侧扩展槽（如「重启应用」入口），为空则不显示。
 */
@Composable
fun SubPageScaffold(
    title: String,
    isBlurEnabled: Boolean,
    onBack: () -> Unit,
    topBarActions: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollGate = remember { SubPageScrollGate() }
    val scrollBehavior = MiuixScrollBehavior(canScroll = { scrollGate.contentScrollable })
    val pageBackdrop = if (isBlurEnabled) rememberBlurBackdrop() else null
    val blurActive = pageBackdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = title,
                color = barColor,
                backdrop = pageBackdrop,
                scrollBehavior = scrollBehavior,
                onBack = onBack,
                actions = {
                    topBarActions?.invoke()
                },
            )
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        PageBackdrop(pageBackdrop) {
            CompositionLocalProvider(
                LocalSubPageScrollBehavior provides scrollBehavior,
                LocalSubPageScrollGate provides scrollGate,
                LocalPreferenceBackdrop provides pageBackdrop,
            ) {
                content(innerPadding)
            }
        }
    }
}
