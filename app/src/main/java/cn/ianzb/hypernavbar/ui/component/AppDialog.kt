package cn.ianzb.hypernavbar.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.ianzb.hypernavbar.ui.util.LocalPreferenceBackdrop
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text as MiuixText

/** 在根 Scaffold 的弹层宿主显示对话框，采样底层页面而非弹层自身。 */
@Composable
fun AppDialog(
    show: Boolean,
    title: String?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enableWindowDim: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backdrop = LocalPreferenceBackdrop.current
    GlassDialog(
        onDismissRequest = onDismissRequest,
        show = show,
        backdrop = backdrop,
        modifier = modifier.windowInsetsPadding(AppPageContentWindowInsets),
        enableWindowDim = enableWindowDim,
        renderInRootScaffold = true,
        title = title,
        summary = summary,
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
internal fun AppDialogButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/**
 * 对话框动作按钮。
 *
 * 按钮所在的行是等分宽度的，三按钮并列时「恢复默认」这类较长文案在窄屏/大字体的
 * 手机上会被折成两行（“恢复默 / 认”）。这里固定单行并允许自动缩字号：
 * 放得下时仍是原字号，放不下才按 TextAutoSize 逐级缩小，英文 “Restore Default” 同样不会折行。
 *
 * @param primary 主要动作（蓝色实底），同一对话框最多一个
 * @param destructive 破坏性动作（如重启系统），优先级低于 [primary]
 */
@Composable
fun AppDialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    val textStyle = MiuixTheme.textStyles.button
    // 主题若未指定字号，TextAutoSize 不接受 Unspecified，这里退回到按钮默认的 17.sp。
    val styleFontSize = textStyle.fontSize
    val maxFontSize = if (styleFontSize.type == TextUnitType.Sp) styleFontSize else 17.sp
    GlassDialogButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        role = when {
            primary -> GlassDialogButtonRole.Primary
            destructive -> GlassDialogButtonRole.Destructive
            else -> GlassDialogButtonRole.Normal
        },
    ) {
        MiuixText(
            text = text,
            style = textStyle,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 11.sp,
                maxFontSize = maxFontSize,
            ),
        )
    }
}

