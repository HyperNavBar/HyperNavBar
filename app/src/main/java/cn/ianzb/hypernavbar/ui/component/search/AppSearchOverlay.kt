package cn.ianzb.hypernavbar.ui.component.search

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.ianzb.hypernavbar.ui.component.appTopBarSafeInsets
import cn.ianzb.hypernavbar.ui.util.pageStartInset
import top.yukonga.miuix.kmp.glass.GlassSearchOverlay
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Close
import top.yukonga.miuix.kmp.icon.glass.Search

@Composable
fun AppSearchOverlay(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    blurEnabled: Boolean = true,
    scrollOffset: () -> Float = { 0f },
    searchTopPadding: () -> Dp = { 0.dp },
    content: @Composable ColumnScope.(String) -> Unit,
) {
    GlassSearchOverlay(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        label = label,
        modifier = modifier,
        blurEnabled = blurEnabled,
        searchIcon = MiuixIcons.Glass.Search,
        closeIcon = MiuixIcons.Glass.Close,
        scrollOffset = scrollOffset,
        searchTopPadding = searchTopPadding,
        topInsets = appTopBarSafeInsets(),
        horizontalInsetsModifier = Modifier.windowInsetsPadding(
            WindowInsets.displayCutout.union(WindowInsets.navigationBars).only(WindowInsetsSides.Horizontal),
        ).pageStartInset(),
        content = content,
    )
}
