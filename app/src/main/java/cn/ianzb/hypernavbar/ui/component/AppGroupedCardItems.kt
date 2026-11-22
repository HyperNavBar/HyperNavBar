// Adapted from the miuix example, copyright 2026 compose-miuix-ui contributors.
// SPDX-License-Identifier: Apache-2.0

package cn.ianzb.hypernavbar.ui.component

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.LocalContentColor

internal class AppCardRow(@param:StringRes val key: Int, val content: @Composable () -> Unit)

internal fun LazyListScope.appGroupedCardItems(@StringRes title: Int, vararg rows: AppCardRow?) {
    val visibleRows = rows.filterNotNull()
    if (visibleRows.isEmpty()) return
    item(key = "group:$title") { SmallTitle(stringResource(title)) }
    visibleRows.forEachIndexed { index, row ->
        item(key = "group:$title:${row.key}") {
            val isLast = index == visibleRows.lastIndex
            val radius = CardDefaults.CornerRadius
            val topRadius = if (index == 0) radius else 0.dp
            val bottomRadius = if (isLast) radius else 0.dp
            val colors = CardDefaults.defaultColors()
            val color = colors.color
            val fill = if (topRadius == 0.dp && bottomRadius == 0.dp) {
                Modifier.background(color)
            } else {
                Modifier.squircleSurface(color, topRadius, topRadius, bottomRadius, bottomRadius)
            }
            val seam = if (!isLast && color.alpha == 1f) {
                Modifier.drawBehind {
                    // Prevent seams between rows when the glass backdrop is downsampled.
                    drawRect(color, Offset(0f, size.height), Size(size.width, 2f))
                }
            } else {
                Modifier
            }
            CompositionLocalProvider(LocalContentColor provides colors.contentColor) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = if (isLast) 12.dp else 0.dp)
                        .then(seam)
                        .then(fill),
                ) {
                    row.content()
                }
            }
        }
    }
}
