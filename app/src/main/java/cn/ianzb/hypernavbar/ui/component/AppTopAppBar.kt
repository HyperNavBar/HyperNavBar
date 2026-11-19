package cn.ianzb.hypernavbar.ui.component

import android.os.Build
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ianzb.hypernavbar.R
import cn.ianzb.hypernavbar.ui.util.LocalAppPageStartInset
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

val LocalCollapsedGlassRail = staticCompositionLocalOf<Float?> { null }

private val LocalGlassTopBar = staticCompositionLocalOf { false }

@Composable
fun AppTopAppBar(
    title: String,
    scrollBehavior: ScrollBehavior,
    color: Color,
    backdrop: LayerBackdrop?,
    expandable: Boolean = true,
    titleAlpha: () -> Float = { 1f },
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val collapsedRail = LocalCollapsedGlassRail.current
    val startInset = LocalAppPageStartInset.current.value
    val surface = MiuixTheme.colorScheme.surface
    val glass = isGlassTopBarAvailable()
    val bandBrush = remember(glass, surface, color) {
        if (glass) GlassTopAppBarDefaults.bandBrush(surface) else SolidColor(color)
    }
    GlassTopAppBar(
        title = title,
        isContentScrolled = scrollBehavior.state.contentOffset + scrollBehavior.state.overscrollOffset < 0f,
        contentModifier = Modifier.windowInsetsPadding(appTopBarSafeInsets()).padding(start = startInset),
        backdrop = backdrop.takeIf { glass },
        progressiveBlur = glass && backdrop != null,
        scrollBehavior = scrollBehavior,
        expandable = expandable && collapsedRail == null,
        bandBrush = bandBrush,
        defaultWindowInsetsPadding = false,
        titleAlpha = { titleAlpha() * (1f - (collapsedRail ?: 0f)) },
        navigationIcon = {
            if (onBack != null) {
                CompositionLocalProvider(LocalGlassTopBar provides glass) {
                    AppTopBarAction(onClick = onBack) {
                        Icon(
                            imageVector = if (glass) MiuixIcons.Glass.ChevronBackward else MiuixIcons.Back,
                            contentDescription = stringResource(R.string.action_back),
                            modifier = Modifier.size(if (glass) 28.dp else 24.dp),
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        actions = {
            CompositionLocalProvider(LocalGlassTopBar provides glass) { actions() }
        },
    )
}

@Composable
fun AppTopBarAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    glassSurface: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (enabled && (LocalGlassTopBar.current || glassSurface && isGlassTopBarAvailable())) {
        GlassIconButton(onClick = onClick, modifier = modifier, content = content)
    } else {
        IconButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
    }
}

private fun isGlassTopBarAvailable(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && isRuntimeShaderSupported()

@Composable
internal fun appTopBarSafeInsets(): WindowInsets =
    WindowInsets.systemBars
        .union(WindowInsets.captionBar)
        .union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Top)
