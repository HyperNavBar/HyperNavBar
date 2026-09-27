package cn.ianzb.hypernavbar.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ianzb.hypernavbar.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 重启系统前的二次确认对话框。
 *
 * 目标包含 `system` / `android` / `system_server` 时，重启会触发系统重启，属于破坏性操作，
 * 必须再次确认。
 */
@Composable
fun SystemRestartConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    WindowDialog(
        show = true,
        title = stringResource(R.string.restart_system_confirm_title),
        summary = stringResource(R.string.restart_system_confirm_message),
        onDismissRequest = onDismiss,
    ) {
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
                text = stringResource(R.string.restart_system_confirm_action),
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
