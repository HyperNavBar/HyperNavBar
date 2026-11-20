package cn.ianzb.hypernavbar.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import cn.ianzb.hypernavbar.ui.util.LocalSubPageScrollBehavior
import cn.ianzb.hypernavbar.ui.util.LocalSubPageScrollGate
import cn.ianzb.hypernavbar.ui.util.SubPageScrollGate
import cn.ianzb.hypernavbar.ui.util.withPageStartInset
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior

/**
 * 二级页面模板脚手架：顶栏返回、背景模糊、主题等模块配置统一在此处理。
 *
 * 顶栏与「第三方许可证与致谢」页走同一条路径（[AppPageScaffold] 默认的 miuix 玻璃顶栏
 * [top.yukonga.miuix.kmp.glass.GlassTopAppBar]），不额外自定义任何绘制。
 *
 * 二级页内容常常不足一屏。此时列表没有滚动余量，`canScroll` 闸门会关闭顶栏的折叠消费，
 * 上滑手势改由 `overScrollVertical` 处理：内容弹性上移后回弹，回弹位移写入
 * `TopAppBarState.overscrollOffset`，玻璃带区与渐变模糊在回弹过程中出现（与 demo 一致）。
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
    // MiuixScrollBehavior 内部已是 remember，随主体一起保存，直接调用即可。
    val scrollGate = remember { SubPageScrollGate() }
    val scrollBehavior = MiuixScrollBehavior(canScroll = { scrollGate.contentScrollable })

    AppPageScaffold(
        title = title,
        isBlurEnabled = isBlurEnabled,
        scrollBehavior = scrollBehavior,
        onBack = onBack,
        actions = {
            topBarActions?.invoke()
        },
    ) { innerPadding ->
        val direction = LocalLayoutDirection.current
        val systemBottom = WindowInsets.navigationBars.union(WindowInsets.captionBar)
            .asPaddingValues().calculateBottomPadding()
        CompositionLocalProvider(
            LocalSubPageScrollBehavior provides scrollBehavior,
            LocalSubPageScrollGate provides scrollGate,
        ) {
            content(
                PaddingValues(
                    start = innerPadding.calculateStartPadding(direction),
                    top = innerPadding.calculateTopPadding(),
                    end = innerPadding.calculateEndPadding(direction),
                    bottom = maxOf(innerPadding.calculateBottomPadding(), systemBottom),
                ).withPageStartInset(),
            )
        }
    }
}
