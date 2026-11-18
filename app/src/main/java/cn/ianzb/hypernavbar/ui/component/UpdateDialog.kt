package cn.ianzb.hypernavbar.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ianzb.hypernavbar.R
import cn.ianzb.hypernavbar.UpdateChecker

@Composable
fun UpdateDialog(
    info: UpdateChecker.UpdateInfo,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AppDialog(
        show = true,
        title = stringResource(R.string.update_available_title),
        summary = stringResource(
            R.string.update_available_summary,
            info.currentVersion,
            info.latestVersion,
        ),
        onDismissRequest = onDismissRequest,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (info.releaseNotes.isNotBlank()) {
                UpdateMarkdown(info.releaseNotes)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AppDialogButton(
                    text = stringResource(R.string.update_later),
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                AppDialogButton(
                    text = stringResource(R.string.update_now),
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    primary = true,
                )
            }
        }
    }
}
