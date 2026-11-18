package cn.ianzb.hypernavbar.ui.component.search

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

@Stable
internal class AppSearchState {
    var expanded by mutableStateOf(false)
        private set

    var results by mutableStateOf<(@Composable ColumnScope.(String) -> Unit)?>(null)

    fun open() {
        if (results != null) expanded = true
    }

    fun close() {
        expanded = false
    }
}

internal val LocalAppSearchState = staticCompositionLocalOf<AppSearchState?> { null }
