package cn.ianzb.hypernavbar.ui.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon

@Composable
internal fun ScopedAppCard(
    packageName: String,
    status: String? = null,
    topSpace: Dp = 0.dp,
    endActions: @Composable RowScope.(appName: String) -> Unit,
) {
    val packageManager = LocalContext.current.packageManager
    val density = LocalDensity.current
    val appInfo = remember(packageName) {
        runCatching { packageManager.getApplicationInfo(packageName, 0) }.getOrNull()
    }
    val appName = remember(packageName, appInfo) {
        appInfo?.let {
            runCatching { packageManager.getApplicationLabel(it).toString() }.getOrNull()
        } ?: packageName
    }
    val version = remember(packageName) {
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }
            .getOrNull().orEmpty()
    }
    val iconSizePx = with(density) { 40.dp.roundToPx() }
    val icon = remember(packageName, appInfo, iconSizePx) {
        appInfo?.let {
            runCatching {
                packageManager.getApplicationIcon(it)
                    .toBitmap(iconSizePx, iconSizePx)
                    .asImageBitmap()
            }.getOrNull()
        }
    }

    val summary = buildString {
        append(packageName)
        if (version.isNotBlank()) append(" · v").append(version)
        if (status != null) append(" · ").append(status)
    }
    AppSectionCard(topSpace = topSpace) {
        BasicComponent(
            title = appName,
            summary = summary,
            startAction = icon?.let { bitmap ->
                {
                    Icon(
                        bitmap = bitmap,
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(40.dp),
                    )
                }
            },
            endActions = { endActions(appName) },
        )
    }
}
