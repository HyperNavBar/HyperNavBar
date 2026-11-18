package cn.ianzb.hypernavbar.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.SmallTitle

/** Adds section spacing above the native miuix title when it follows page content. */
@Composable
internal fun SectionTitle(text: String, followsContent: Boolean = false) {
    SmallTitle(
        text = text,
        modifier = if (followsContent) Modifier.padding(top = 12.dp) else Modifier,
    )
}
