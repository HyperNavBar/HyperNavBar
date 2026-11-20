package cn.ianzb.hypernavbar.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AppArrowPreference(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val colors = MiuixTheme.colorScheme
    BasicComponent(
        title = title,
        modifier = modifier,
        summary = summary,
        onClick = onClick,
        enabled = enabled,
        endActions = {
            Icon(
                imageVector = MiuixIcons.Glass.ChevronForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (enabled) colors.onSurfaceVariantActions else colors.disabledOnSecondaryVariant,
            )
        },
    )
}
