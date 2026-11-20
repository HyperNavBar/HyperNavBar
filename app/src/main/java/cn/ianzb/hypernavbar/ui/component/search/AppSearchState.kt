package cn.ianzb.hypernavbar.ui.component.search

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp

@Stable
internal class AppSearchState {
    var expanded by mutableStateOf(false)
        private set

    var results by mutableStateOf<(@Composable ColumnScope.(String, () -> Dp) -> Unit)?>(null)

    var scrollOffset: () -> Float = { 0f }

    fun open() {
        if (results != null) expanded = true
    }

    fun close() {
        expanded = false
    }
}

internal val LocalAppSearchState = staticCompositionLocalOf<AppSearchState?> { null }

@Composable
internal fun rememberAppSearchOpen(): (() -> Unit)? = LocalAppSearchState.current?.let { it::open }
