package cn.ianzb.hypernavbar.ui.util

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    if (gate != null && contentState != null) {
        LaunchedEffect(gate, contentState) {
            snapshotFlow { contentState.canScrollForward || contentState.canScrollBackward }
                .collect { gate.contentScrollable = it }
        }
    }
    val state = topAppBarScrollBehavior.state
    val onOffsetChanged: (Float) -> Unit = remember(state, gate) {
        { offset ->
            state.overscrollOffset = offset
            if (gate != null && !gate.contentScrollable) state.heightOffset = offset
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


val LocalAppPageStartInset = staticCompositionLocalOf<State<Dp>> { mutableStateOf(0.dp) }

@Composable
fun Modifier.pageStartInset(): Modifier {
    val inset = LocalAppPageStartInset.current
    return layout { measurable, constraints ->
        val start = inset.value.roundToPx().coerceIn(0, constraints.maxWidth)
        val placeable = measurable.measure(
            Constraints(
                minWidth = (constraints.minWidth - start).coerceAtLeast(0),
                maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth - start else Constraints.Infinity,
                minHeight = constraints.minHeight,
                maxHeight = constraints.maxHeight,
            ),
        )
        layout(placeable.width + start, placeable.height) { placeable.placeRelative(start, 0) }
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
