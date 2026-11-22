package cn.ianzb.hypernavbar.ui.component

import android.os.Build
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.ianzb.hypernavbar.R
import cn.ianzb.hypernavbar.ui.util.pageStartInset
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.glass.ChevronBackward
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 页面顶栏：统一走 HyperOS 4 玻璃顶栏（[GlassTopAppBar]），所有页面（含二级页）共用这一条路径。
 *
 * 设备缺少运行时着色器时不再切换到另一套顶栏实现：玻璃组件自己会把 `backdrop` 视为 null、
 * 退化成实色带（见 miuix 0.9.5 起 `BlurTopAppBar` 收为内部实现），因此这里只控制是否传入 backdrop。
 * 参数取值参照 miuix 官方 demo（`utils/PageUtils.kt` 的 `AdaptiveTopAppBar`）：
 * `defaultWindowInsetsPadding = false` 以免与各页 Scaffold 的内边距叠加。
 *
 * @param color 顶栏带区着色；玻璃可用时带区走官方渐变 brush、忽略该值，只有缺少运行时着色器时
 *   才用它画实色带（因此各页在玻璃路径下传 `Color.Transparent` 是安全的）。
 * @param backdrop 玻璃采样源，由页面脚手架传入；为 null 时顶栏退化为渐变着色带。
 * @param onBack 非空时在左侧渲染返回按钮（胶囊底板 + `MiuixIcons.Glass.ChevronBackward`，自动处理 RTL 镜像）。
 * @param titleAlpha 标题透明度，用于「滚动到一定位置才浮现标题」的页面。
 * @param expandable 是否保留可折叠大标题，`false` 时标题固定（等价旧版 SmallTopAppBar）。
 */
@Composable
fun AppTopAppBar(
    title: String,
    scrollBehavior: ScrollBehavior,
    color: Color = MiuixTheme.colorScheme.surface,
    backdrop: LayerBackdrop? = null,
    onBack: (() -> Unit)? = null,
    titleAlpha: () -> Float = { 1f },
    expandable: Boolean = true,
    useGlassTopBar: Boolean = true,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    // 玻璃带区由滚动偏移（contentOffset + overscrollOffset）驱动：内容不足一屏的页面靠
    // pageScrollModifiers 把弹性回弹位移写进 overscrollOffset，因此上滑回弹时同样有带区和模糊。
    val glass = useGlassTopBar && isGlassTopBarAvailable()
    // 宿主（宽屏 + 侧边导航栏）会把这两个值打开：折叠胶囊占据标题位置时标题淡出，且不再展开大标题。
    val chromeAlpha = LocalAppTopBarTitleAlpha.current
    val compact = LocalAppTopBarCompact.current
    val mergedTitleAlpha: () -> Float = { (titleAlpha() * chromeAlpha()).coerceIn(0f, 1f) }
    // 宽屏侧边导航栏覆盖在页面上：顶栏前景内容（标题/按钮）让出侧栏宽度，背景带仍铺满全宽。
    val centerObstructionStart = LocalAppTopBarCenterObstructionStart.current()
    val backButton: @Composable () -> Unit = {
        CompositionLocalProvider(LocalIsGlassTopBar provides glass) {
            if (onBack != null) {
                BackNavigationButton(
                    onClick = onBack,
                    glass = glass,
                    backdrop = backdrop,
                )
            }
            navigationIcon()
        }
    }
    val scopedActions: @Composable RowScope.() -> Unit = {
        CompositionLocalProvider(LocalIsGlassTopBar provides glass) {
            actions()
        }
    }

    // 玻璃顶栏在没有 RuntimeShader 的设备上会把 backdrop 当成 null、退化成实色带，
    // 所以不再维护一份独立的非玻璃实现（上游 0.9.5 也已把 BlurTopAppBar 收为内部实现）：
    // 这里只按「玻璃是否可用」决定是否传入 backdrop，并在不可用时用页面指定的 color 画带区。
    val surfaceColor = MiuixTheme.colorScheme.surface
    val bandBrush = remember(surfaceColor, color, glass) {
        if (glass) {
            GlassTopAppBarDefaults.bandBrush(surfaceColor)
        } else {
            SolidColor(color)
        }
    }
    GlassTopAppBar(
        title = title,
        // 背景带固定在 y=0（覆盖状态栏），前景内容自己让出状态栏高度。
        contentModifier = Modifier.windowInsetsPadding(appTopBarSafeInsets()).pageStartInset(),
        backdrop = if (glass) backdrop else null,
        // 带区是否模糊完全跟随「背景模糊」开关（backdrop 为 null 即关闭）与着色器可用性。
        progressiveBlur = glass && backdrop != null,
        scrollBehavior = scrollBehavior,
        expandable = expandable && !compact,
        bandBrush = bandBrush,
        bandOverhang = GlassTopAppBarDefaults.BandOverhang,
        backgroundAlpha = { 1f },
        defaultWindowInsetsPadding = false,
        titleAlpha = mergedTitleAlpha,
        centerObstructionStart = centerObstructionStart,
        navigationIcon = backButton,
        actions = scopedActions,
    )
}

@Composable
internal fun appTopBarSafeInsets(): WindowInsets =
    WindowInsets.systemBars
        .union(WindowInsets.captionBar)
        .union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Top)

/**
 * 二级页面顶栏（带返回按钮），其余与 [AppTopAppBar] 一致。
 */
@Composable
fun AppSubPageTopAppBar(
    title: String,
    scrollBehavior: ScrollBehavior,
    onBack: () -> Unit,
    color: Color = MiuixTheme.colorScheme.surface,
    backdrop: LayerBackdrop? = null,
    expandable: Boolean = true,
    useGlassTopBar: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    AppTopAppBar(
        title = title,
        scrollBehavior = scrollBehavior,
        color = color,
        backdrop = backdrop,
        onBack = onBack,
        expandable = expandable,
        useGlassTopBar = useGlassTopBar,
        actions = actions,
    )
}

/**
 * 当前是否处于玻璃顶栏的图标插槽内；仅由 [AppTopAppBar] 提供，供 [AppTopAppBarIconAction] 判断按钮样式。
 */
val LocalIsGlassTopBar: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/**
 * 顶栏标题的额外透明度倍数：宽屏侧栏折叠时顶部胶囊占据标题位置，由宿主把该值降到 0 让标题淡出，
 * 展开成左侧面板后再浮现（对应 miuix example 的 `chromeTitleAlpha`）。
 */
val LocalAppTopBarTitleAlpha: ProvidableCompositionLocal<() -> Float> = staticCompositionLocalOf { { 1f } }

/**
 * 顶栏是否使用紧凑形态（不展开大标题）：宽屏下由宿主设为 true，
 * 否则下移后的大标题会顶到顶部折叠胶囊上（对应 miuix example 的 `expandable = !isWideScreen`）。
 */
val LocalAppTopBarCompact: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

internal val LocalAppTopBarCenterObstructionStart = compositionLocalOf { { Dp.Unspecified } }

/**
 * 顶栏动作图标按钮：处于玻璃顶栏内时用 [GlassIconButton]（自动继承顶栏的 style/material/尺寸），
 * 否则退回普通 [IconButton]，让页面无需关心当前是否启用玻璃态。
 */
@Composable
fun AppTopAppBarIconAction(onClick: () -> Unit, content: @Composable () -> Unit) {
    if (LocalIsGlassTopBar.current) {
        GlassIconButton(onClick = onClick, content = content)
    } else {
        IconButton(onClick = onClick, content = content)
    }
}

/**
 * 返回按钮：与 miuix 官方示例 `component/BackNavigationIcon.kt` 完全一致——玻璃顶栏内直接用组件库的
 * [GlassIconButton]（胶囊底板、描边、投影都由它渲染），其余情况用普通 [IconButton]；
 * RTL 下水平镜像箭头。
 *
 * 胶囊是否可见完全由组件库决定：静止时顶栏 `materialProgress ≈ 0`，只显示箭头；
 * 内容滚到顶栏下方或上滑回弹时浮出玻璃胶囊。二级页与「第三方许可证与致谢」页同一条路径，
 * 因此两处的返回按钮表现完全一致。
 */
@Composable
private fun BackNavigationButton(
    onClick: () -> Unit,
    glass: Boolean,
    backdrop: LayerBackdrop? = null,
) {
    val tint = if (glass) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onBackground
    val content: @Composable () -> Unit = {
        Icon(
            imageVector = if (glass) MiuixIcons.Glass.ChevronBackward else MiuixIcons.Back,
            contentDescription = stringResource(R.string.action_back),
            tint = tint,
            modifier = Modifier.size(if (glass) 28.dp else 24.dp),
        )
    }
    if (glass) {
        GlassIconButton(
            onClick = onClick,
            backdrop = backdrop,
            content = content,
        )
    } else {
        IconButton(onClick = onClick, content = content)
    }
}

/**
 * 当前环境是否走 OS4 玻璃顶栏：只看运行时着色器支持（Android 13+）。
 *
 * 不再要求「模糊已开启」——关闭模糊时同样走 [GlassTopAppBar]，只是不带 progressiveBlur，
 * 由官方 bandBrush 给出与 demo 一致的「不透明 → 透明」渐变带。
 */
internal fun isGlassTopBarAvailable(): Boolean =
    isRuntimeShaderSupported() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

@Composable
fun AppTopBarAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    glassSurface: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (enabled && (LocalIsGlassTopBar.current || glassSurface && isGlassTopBarAvailable())) {
        GlassIconButton(onClick = onClick, modifier = modifier, content = content)
    } else {
        IconButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
    }
}
