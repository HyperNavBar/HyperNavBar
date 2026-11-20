package cn.ianzb.hypernavbar.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle

/**
 * 分组卡片：可选的 [SmallTitle] + 一张 [Card]。
 *
 * 卡片自己承担「与下一组之间的留白」，统一由 [sectionCardPadding] 给出（左右各 12.dp、
 * 底部 12.dp），因此各页面不要再给卡片加 `padding(horizontal = 12.dp)` / `padding(bottom = 12.dp)`；
 * 分组标题只保留 `SmallTitle` 自带的 `insideMargin`，也不要再补 `padding(top = ...)`。
 *
 * @param title 分组标题，为空时不渲染 `SmallTitle`（例如只有一张卡片的独立卡片组）。
 * @param modifier 作用在「标题 + 卡片」这一整组上。
 * @param topSpace 与上一组之间的额外留白；首组需要与顶栏拉开距离时传入，其余情况保持默认 0。
 * @param colors 卡片配色，需要高亮（如搜索命中的灰闪）时传入。
 */
@Composable
internal fun AppSectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    topSpace: Dp = 0.dp,
    colors: CardColors = CardDefaults.defaultColors(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (!title.isNullOrBlank()) SmallTitle(text = title)
        Card(
            modifier = Modifier.sectionCardPadding(top = topSpace),
            colors = colors,
            content = content,
        )
    }
}

/**
 * 分组卡片的外边距：左右各 12.dp、底部 12.dp（与下一组标题的间距由这里的 bottom 统一负责）。
 *
 * 卡片组不是单个 `Card`（例如横屏下并排多张卡片的 `Row`）时直接用它，
 * 不要再手写 `padding(horizontal = 12.dp)`。
 *
 * @param top 与上一组之间的额外留白。
 */
internal fun Modifier.sectionCardPadding(top: Dp = 0.dp): Modifier =
    padding(start = 12.dp, end = 12.dp, top = top, bottom = 12.dp)
