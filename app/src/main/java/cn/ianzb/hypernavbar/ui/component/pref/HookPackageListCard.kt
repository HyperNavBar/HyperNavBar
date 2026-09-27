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
 * 把逗号 / 分号 / 换行 / 空格分隔的文本解析为去重后的包名列表。
 */
fun parsePackageList(raw: String): List<String> =
    raw.split(',', '，', ';', '；', '\n', ' ', '\t')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

/**
 * 包名列表卡片：点击后弹出多行输入对话框。
 *
 * 输入内容会在页面右上角生成「重启应用」按钮，用于批量重启这些应用。
 */
@Composable
fun HookPackageListCard(
    spec: OptionSpec,
    modifier: Modifier = Modifier,
) {
    val enabled = rememberDependencyEnabled(spec)
    val value = ConfigState.string(spec.key, spec.defaultString)
    var showDialog by remember { mutableStateOf(false) }
    val packages = parsePackageList(value)
    val summary = if (packages.isEmpty()) {
        stringResource(R.string.hook_text_not_set)
    } else {
        stringResource(R.string.hook_package_list_count, packages.size)
    }

    ArrowPreference(
        title = stringResource(spec.titleRes),
        summary = summary,
        onClick = { if (enabled) showDialog = true },
        enabled = enabled,
        modifier = modifier,
    )

    if (showDialog) {
        PackageListDialog(spec = spec, onDismiss = { showDialog = false })
    }
}

@Composable
private fun PackageListDialog(
    spec: OptionSpec,
    onDismiss: () -> Unit,
) {
    val current = ConfigState.string(spec.key, spec.defaultString)
    var input by remember { mutableStateOf(current) }

    WindowDialog(
        show = true,
        title = stringResource(spec.titleRes),
        summary = stringResource(R.string.hook_package_list_dialog_summary),
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = input,
                onValueChange = { input = it },
                label = stringResource(R.string.hook_package_list_placeholder),
                singleLine = false,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            MiuixText(
                text = stringResource(R.string.hook_package_list_hint),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote2,
                modifier = Modifier.padding(top = 8.dp),
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
