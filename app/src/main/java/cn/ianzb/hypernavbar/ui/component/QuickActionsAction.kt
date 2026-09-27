package cn.ianzb.hypernavbar.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cn.ianzb.hypernavbar.R
import cn.ianzb.hypernavbar.ui.component.pref.QuickActionDialog
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 顶栏右上角「重启应用」入口。
 *
 * 统一样式：`Refresh`（重启）图标按钮（`tint = onSurface`）→ [QuickActionDialog]，对指定包批量
 * 「重启」，与 [cn.ianzb.hypernavbar.ui.component.pref.HookOptionsPage]
 * 自动生成的右上角按钮保持一致。供二级页面通过 `topBarActions` 注入；[packages] 为空时不渲染。
 */
@Suppress("unused")
@Composable
fun QuickActionsAction(
    packages: List<String>,
    modifier: Modifier = Modifier,
) {
    if (packages.isEmpty()) return
    var showDialog by remember { mutableStateOf(false) }

    IconButton(onClick = { showDialog = true }, modifier = modifier) {
        Icon(
            imageVector = MiuixIcons.Refresh,
            contentDescription = stringResource(R.string.quick_action_title),
            tint = MiuixTheme.colorScheme.onSurface,
        )
    }

    if (showDialog) {
        QuickActionDialog(
            packages = packages,
            onDismiss = { showDialog = false },
        )
    }
}
