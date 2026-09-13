package cn.ianzb.hypernavbar.ui.util

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

val LocalEnableBlur: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

val LocalIsWideScreen: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/**
 * 顶栏渐进模糊（Progressive Blur）统一参数。
 *
 * 所有页面的顶栏模糊都从这里读取，修改后重新编译即可全局生效。
 */
object TopBarBlurConfig {
    /** 模糊半径（dp），模糊最强处的强度 */
    const val BlurRadius: Float = 15f

    /** 顶栏 surface 着色叠加在模糊之上的透明度（0~1），越大栏越实 */
    const val SurfaceAlpha: Float = 0.3f

    /**
     * 顶部保持满强度模糊的比例（0~1）。
     *
     * 该比例之上为满强度模糊，其下线性渐隐到底边。这样内容在整个顶栏内都被充分模糊，
     * 只在底边平滑过渡到清晰，而不会在顶栏下半部分留下清晰内容。
     */
    const val FullStrengthFraction: Float = 0.55f

    /**
     * 滚动渐显距离（dp）：内容下滑该距离内，模糊从透明渐显到完整。
     * 0 = 顶栏常驻完整模糊（推荐，各页面顶部即可见模糊）。
     */
    val ScrollFadeDistance: Dp = 0.dp

    /** 顶部满强度模糊 → 底边渐隐的渐进遮罩。 */
    val progressive: HazeProgressive = HazeProgressive.Brush(
        Brush.verticalGradient(
            0f to Color.Black,
            FullStrengthFraction to Color.Black,
            1f to Color.Black.copy(alpha = 0f),
        )
    )
}

/**
 * 创建顶栏模糊所需的 [HazeState]；设备不支持运行时着色器时返回 null（不启用模糊）。
 */
@Composable
fun rememberBlurState(): HazeState? {
    val supported = isRuntimeShaderSupported() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    return if (supported) rememberHazeState() else null
}

/** 把内容节点登记为 Haze 的模糊来源（[state] 为 null 时不登记）。 */
fun Modifier.blurSource(state: HazeState?): Modifier =
    if (state != null) this.hazeSource(state) else this

/**
 * Miuix [LayerBackdrop]（用于底栏 / 动态背景等非顶栏模糊，与 Haze 顶栏模糊并存）。
 */
@Composable
fun rememberBlurBackdrop(): LayerBackdrop? {
    if (!isRuntimeShaderSupported() || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

@Composable
@ReadOnlyComposable
fun isInDarkTheme(): Boolean {
    val surface = MiuixTheme.colorScheme.surface
    // Calculate relative luminance to determine if surface is dark
    val luminance = 0.2126f * surface.red + 0.7152f * surface.green + 0.0722f * surface.blue
    return luminance < 0.5f
}

@Composable
fun BlurredBar(
    state: HazeState?,
    blurEnabled: Boolean,
    scrollBehavior: ScrollBehavior? = null,
    content: @Composable () -> Unit,
) {
    val blurActive = blurEnabled && state != null
    val scrollFadePx = with(LocalDensity.current) { TopBarBlurConfig.ScrollFadeDistance.toPx() }
    val surfaceColor = MiuixTheme.colorScheme.surface
    Box {
        if (blurActive) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(
                        if (scrollFadePx > 0f) {
                            Modifier.graphicsLayer {
                                alpha = scrollBehavior?.state
                                    ?.let { (-it.contentOffset / scrollFadePx).coerceIn(0f, 1f) }
                                    ?: 1f
                            }
                        } else {
                            Modifier
                        }
                    )
                    .hazeEffect(state = state) {
                        blurRadius = TopBarBlurConfig.BlurRadius.dp
                        // 关键：模糊层必须不透明。Haze 会先原样绘制一遍来源内容，再叠加模糊副本；
                        // 若模糊层透明，卡片等硬边缘会从模糊层中透出，看起来像「组件盖在模糊之上」。
                        backgroundColor = surfaceColor
                        noiseFactor = 0f
                        // surface 着色叠加在模糊之上，控制顶栏实度。
                        tints = listOf(
                            HazeTint(surfaceColor.copy(alpha = TopBarBlurConfig.SurfaceAlpha))
                        )
                        progressive = TopBarBlurConfig.progressive
                    },
            )
        }
        content()
    }
}

fun Modifier.pageScrollModifiers(
    showTopAppBar: Boolean,
    topAppBarScrollBehavior: ScrollBehavior,
): Modifier = this
    .scrollEndHaptic()
    .overScrollVertical()
    .then(if (showTopAppBar) Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection) else Modifier)

@Composable
fun pageContentPadding(
    innerPadding: PaddingValues,
    outerPadding: PaddingValues,
    isWideScreen: Boolean,
    extraTop: Dp = 0.dp,
    extraStart: Dp = 0.dp,
    extraEnd: Dp = 0.dp,
    extraBottom: Dp = 0.dp,
): PaddingValues {
    val topPadding = innerPadding.calculateTopPadding() + extraTop
    val bottomPadding = if (isWideScreen) {
        outerPadding.calculateBottomPadding() + extraBottom +
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
            WindowInsets.captionBar.asPaddingValues().calculateBottomPadding()
    } else {
        outerPadding.calculateBottomPadding() + extraBottom
    }
    return remember(topPadding, bottomPadding, extraStart, extraEnd) {
        PaddingValues(
            top = topPadding,
            start = extraStart,
            end = extraEnd,
            bottom = bottomPadding,
        )
    }
}

@Composable
fun shouldShowSplitPane(): Boolean {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    return with(density) {
        val widthDp = windowInfo.containerSize.width.toDp()
        val heightDp = windowInfo.containerSize.height.toDp()
        val ratio = heightDp / widthDp
        widthDp >= 840.dp || (widthDp >= 600.dp && ratio < 1.2f)
    }
}

@Composable
fun shouldExpandNavigationRail(): Boolean {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    return with(density) {
        windowInfo.containerSize.width.toDp() >= 1200.dp
    }
}

object ColorBlendToken {
    val Pured_Regular_Light = listOf(
        BlendColorEntry(Color(0x340034F9), BlurBlendMode.Overlay),
        BlendColorEntry(Color(0xB3FFFFFF), BlurBlendMode.HardLight),
    )
    val Overlay_Thin_Light = listOf(
        BlendColorEntry(Color(0x4DA9A9A9), BlurBlendMode.Luminosity),
        BlendColorEntry(Color(0x1A9C9C9C), BlurBlendMode.PlusDarker),
    )
}
