package cn.ianzb.hypernavbar.ui.component.pref

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ianzb.hypernavbar.R
import cn.ianzb.hypernavbar.prefs.ConfigState
import cn.ianzb.hypernavbar.prefs.OptionSpec
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.basic.Text as MiuixText

/**
 * 文本输入卡片：点击后弹出与滑块卡片一致的居中输入对话框。
 */
@Composable
fun HookTextCard(
    spec: OptionSpec,
    modifier: Modifier = Modifier,
) {
    val enabled = rememberDependencyEnabled(spec)
    val value = ConfigState.string(spec.key, spec.defaultString)
    var showDialog by remember { mutableStateOf(false) }
    val notSet = stringResource(R.string.hook_text_not_set)

    ArrowPreference(
        title = stringResource(spec.titleRes),
        summary = value.ifEmpty { notSet },
        onClick = { if (enabled) showDialog = true },
        enabled = enabled,
        modifier = modifier,
    )

    if (showDialog) {
        TextValueDialog(spec = spec, onDismiss = { showDialog = false })
    }
}

@Composable
private fun TextValueDialog(
    spec: OptionSpec,
    onDismiss: () -> Unit,
) {
    val current = ConfigState.string(spec.key, spec.defaultString)
    var input by remember { mutableStateOf(current) }

    WindowDialog(
        show = true,
        title = stringResource(spec.titleRes),
        summary = stringResource(R.string.hook_text_dialog_summary),
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val notSet = stringResource(R.string.hook_text_not_set)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MiuixText(
                    text = stringResource(
                        R.string.hook_text_current,
                        current.ifEmpty { notSet },
                    ),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote2,
                )
                MiuixText(
                    text = stringResource(
                        R.string.hook_text_default,
                        spec.defaultString.ifEmpty { notSet },
                    ),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote2,
                )
            }
            Spacer(Modifier.padding(top = 8.dp))
            TextField(
                value = input,
                onValueChange = { input = it },
                label = stringResource(R.string.hook_text_placeholder),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    text = stringResource(R.string.hook_slider_restore_default),
                    onClick = {
                        ConfigState.set(spec.key, spec.defaultString)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    text = stringResource(R.string.action_confirm),
                    onClick = {
                        ConfigState.set(spec.key, input)
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
