package cn.ianzb.hypernavbar.ui.util

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

val LocalEnableBlur: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

val LocalIsWideScreen: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/** 二级页面脚手架提供的滚动行为，供内容页 LazyColumn 绑定顶栏收起。 */
val LocalSubPageScrollBehavior: ProvidableCompositionLocal<ScrollBehavior?> =
    staticCompositionLocalOf { null }

class SubPageScrollGate {
    var contentScrollable by mutableStateOf(false)
}

val LocalSubPageScrollGate = staticCompositionLocalOf<SubPageScrollGate?> { null }

@Composable
internal fun rememberTopBarBackdrop(blurActive: Boolean): LayerBackdrop? =
    if (blurActive) rememberBlurBackdrop() else null

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
fun Modifier.pageScrollModifiers(
    showTopAppBar: Boolean,
    topAppBarScrollBehavior: ScrollBehavior,
    contentState: LazyListState? = null,
): Modifier {
    val gate = LocalSubPageScrollGate.current
    val state = topAppBarScrollBehavior.state
    if (gate != null && contentState != null) {
        LaunchedEffect(gate, contentState, state) {
            fun canClassify() = !contentState.isScrollInProgress &&
                state.heightOffset == 0f && state.overscrollOffset == 0f
            fun canScroll() = contentState.canScrollForward || contentState.canScrollBackward

            snapshotFlow { if (canClassify()) canScroll() else null }
                .collectLatest { scrollable ->
                    if (scrollable == null) return@collectLatest
                    // 等展开布局稳定后再判断，避免接近一屏的列表在手势中途切换滚动行为。
                    withFrameNanos { }
                    if (canClassify()) gate.contentScrollable = canScroll()
                }
        }
    }
    val onOffsetChanged: (Float) -> Unit = remember(state, gate) {
        { offset ->
            state.overscrollOffset = offset
            if (gate != null && !gate.contentScrollable) state.heightOffset = offset.coerceAtMost(0f)
        }
    }
    return scrollEndHaptic()
        .overScrollVertical(onOffsetChanged = if (showTopAppBar) onOffsetChanged else null)
        .then(if (showTopAppBar) Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection) else Modifier)
}

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

val LocalPreferenceBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }


val LocalAppPageStartInset: ProvidableCompositionLocal<() -> Dp> = staticCompositionLocalOf { { 0.dp } }

/**
 * 把 [LocalAppPageStartInset] 应用到内容容器的起始边。
 *
 * 只给滚动容器/内容列使用，不要加到页面根布局或背景图层上。
 */
@Composable
fun Modifier.pageStartInset(): Modifier = padding(PaddingValues(0.dp).withPageStartInset())

/** 在测量阶段随侧栏让出起始边距。 */
@Composable
internal fun PaddingValues.withPageStartInset(): PaddingValues {
    val base = this
    val inset = LocalAppPageStartInset.current
    return remember(base, inset) {
        object : PaddingValues by base {
            override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
                base.calculateLeftPadding(layoutDirection) + if (layoutDirection == LayoutDirection.Ltr) inset() else 0.dp

            override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
                base.calculateRightPadding(layoutDirection) + if (layoutDirection == LayoutDirection.Rtl) inset() else 0.dp
        }
    }
}
@Composable
fun PageBackdrop(backdrop: LayerBackdrop?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPreferenceBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize().then(if (backdrop == null) Modifier else Modifier.layerBackdrop(backdrop))) {
            content()
        }
    }
}
