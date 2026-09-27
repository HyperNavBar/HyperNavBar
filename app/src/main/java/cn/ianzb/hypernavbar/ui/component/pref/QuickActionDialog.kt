package cn.ianzb.hypernavbar.ui.component.pref

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ianzb.hypernavbar.R
import cn.ianzb.hypernavbar.ui.component.SystemRestartConfirmDialog
import cn.ianzb.hypernavbar.xposed.AppRestarter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.basic.Text as MiuixText

/**
 * 包名列表重启对话框：勾选后批量「重启」。
 *
 * - 每行右侧为勾选组件，默认全选；
 * - 底部左侧为「全选 / 全不选」普通按钮，右侧为「重启」主按钮（无勾选时禁用）。
 *
 * 由 [HookOptionsPage] 在检测到 `PACKAGE_LIST` 选项或传入自定义包名时，通过右上角按钮弹出。
 */
@Composable
fun QuickActionDialog(
    packages: List<String>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selected by remember(packages) { mutableStateOf(packages.toSet()) }
    val allSelected = packages.isNotEmpty() && selected.size == packages.size

    // 目标包含系统进程时，重启会触发系统重启，需二次确认。
    var pendingRestart by remember { mutableStateOf<List<String>?>(null) }
    val requestRestart: (List<String>) -> Unit = { targets ->
        when {
            targets.isEmpty() -> Unit
            targets.any { AppRestarter.isSystemPackage(it) } -> pendingRestart = targets
            else -> coroutineScope.launch { restartPackages(context, targets) }
        }
    }

    WindowDialog(
        show = true,
        title = stringResource(R.string.quick_action_title),
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    packages.forEach { packageName ->
                        val label = remember(packageName) { resolveLabel(context, packageName) }
                        CheckboxPreference(
                            title = label,
                            summary = packageName.takeIf { it != label },
                            checked = packageName in selected,
                            onCheckedChange = { checked ->
                                selected = if (checked) selected + packageName else selected - packageName
                            },
                            checkboxLocation = CheckboxLocation.End,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    text = stringResource(
                        if (allSelected) R.string.quick_action_deselect_all
                        else R.string.quick_action_select_all,
                    ),
                    onClick = {
                        selected = if (allSelected) emptySet() else packages.toSet()
                    },
                    colors = ButtonDefaults.textButtonColors(),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { requestRestart(selected.toList()) },
                    enabled = selected.isNotEmpty(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                ) {
                    MiuixText(text = stringResource(R.string.quick_action_restart))
                }
            }
        }
    }

    pendingRestart?.let { targets ->
        SystemRestartConfirmDialog(
            onConfirm = {
                pendingRestart = null
                coroutineScope.launch { restartPackages(context, targets) }
            },
            onDismiss = { pendingRestart = null },
        )
    }
}

/** 解析应用显示名，取不到时回退包名。 */
private fun resolveLabel(context: Context, packageName: String): String = runCatching {
    val info = context.packageManager.getApplicationInfo(packageName, 0)
    context.packageManager.getApplicationLabel(info).toString()
}.getOrDefault(packageName)

private suspend fun restartPackages(context: Context, packages: List<String>) {
    if (packages.isEmpty()) return
    val results = packages.map { packageName ->
        withContext(Dispatchers.IO) {
            AppRestarter.restart(context, packageName)
        }
    }
    val message = if (results.all { it }) {
        R.string.quick_action_restart_success
    } else {
        R.string.scope_restart_need_root
    }
    Toast.makeText(context, context.getString(message), Toast.LENGTH_SHORT).show()
}
