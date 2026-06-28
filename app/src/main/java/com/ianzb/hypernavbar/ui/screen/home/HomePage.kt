package com.ianzb.hypernavbar.ui.screen.home

import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ianzb.hypernavbar.R
import com.ianzb.hypernavbar.rules.RulesManager
import com.ianzb.hypernavbar.ui.util.BlurredBar
import com.ianzb.hypernavbar.ui.util.isInDarkTheme
import com.ianzb.hypernavbar.ui.util.pageScrollModifiers
import com.ianzb.hypernavbar.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import java.io.BufferedReader
import java.io.InputStreamReader
import top.yukonga.miuix.kmp.basic.Text as MiuixText

@Composable
fun HomePageView(
    hasRoot: Boolean,
    rootChecked: Boolean,
    onRetryRootCheck: () -> Unit,
    refreshKey: Int = 0,
    extraBottomPadding: Dp = 0.dp,
) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    val title = stringResource(R.string.tab_home)
    val deviceModel = Build.MODEL.ifEmpty { "Unknown" }
    val deviceName = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        ?: deviceModel
    val systemVersion = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
    val loadingText = stringResource(R.string.home_loading)
    val unknownText = stringResource(R.string.home_unknown)
    var hyperOSVersion by remember { mutableStateOf(loadingText) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec("getprop ro.miui.ui.version.name")
                val result = BufferedReader(InputStreamReader(process.inputStream)).readLine()
                hyperOSVersion = result ?: unknownText
            } catch (_: Exception) {
                hyperOSVersion = unknownText
            }
        }
    }
    val moduleVersion = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
    } catch (_: Exception) { "1.0" }
    var subCount by remember { mutableStateOf(0) }
    var mergedCount by remember { mutableStateOf(0) }
    LaunchedEffect(rootChecked) {
        if (rootChecked && !hasRoot) {
            Toast.makeText(context, context.getString(R.string.home_status_no_root), Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(refreshKey, rootChecked) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            subCount = RulesManager.loadAll(context).size
            mergedCount = RulesManager.loadAppliedCount(context)
        }
    }

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop, blurActive) {
                TopAppBar(
                    title = title,
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(
            modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .pageScrollModifiers(
                        showTopAppBar = true,
                        topAppBarScrollBehavior = scrollBehavior,
                    ),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + extraBottomPadding
                )
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(top = 12.dp)
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val darkTheme = isInDarkTheme()
                        val dynamicColor = MiuixTheme.isDynamicColor
                        val statusColor = if (hasRoot) {
                            when {
                                dynamicColor -> MiuixTheme.colorScheme.secondaryContainer
                                darkTheme -> Color(0xFF1A3825)
                                else -> Color(0xFFDFFAE4)
                            }
                        } else {
                            when {
                                dynamicColor -> MiuixTheme.colorScheme.errorContainer
                                darkTheme -> Color(0xFF3D1C1C)
                                else -> Color(0xFFFDE8E8)
                            }
                        }
                        val iconTint = if (hasRoot) {
                            if (dynamicColor) MiuixTheme.colorScheme.primary.copy(alpha = 0.8f) else Color(0xFF36D167)
                        } else {
                            if (dynamicColor) MiuixTheme.colorScheme.error.copy(alpha = 0.8f) else Color(0xFFDC3545)
                        }
                        val titleText = if (hasRoot)
                            stringResource(R.string.home_status_active)
                        else
                            stringResource(R.string.home_status_no_root)
                        val versionText = if (rootChecked) {
                            if (hasRoot)
                                stringResource(R.string.home_status_root_ok_version)
                            else
                                stringResource(R.string.home_status_no_root_version)
                        } else {
                            stringResource(R.string.home_status_active_summary)
                        }

                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            colors = CardDefaults.defaultColors(color = statusColor),
                            onClick = {
                                if (!hasRoot) {
                                    onRetryRootCheck()
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.home_status_root_ok),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            showIndication = true,
                            pressFeedbackType = PressFeedbackType.Tilt
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .offset(38.dp, 45.dp),
                                    contentAlignment = Alignment.BottomEnd
                                ) {
                                    Icon(
                                        modifier = Modifier.size(170.dp),
                                        imageVector = if (hasRoot)
                                            Icons.Rounded.CheckCircleOutline
                                        else
                                            Icons.Rounded.ErrorOutline,
                                        tint = iconTint,
                                        contentDescription = null
                                    )
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(all = 16.dp)
                                ) {
                                    MiuixText(
                                        modifier = Modifier.fillMaxWidth(),
                                        text = titleText,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    MiuixText(
                                        modifier = Modifier.fillMaxWidth(),
                                        text = versionText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                insideMargin = PaddingValues(16.dp),
                                showIndication = true,
                                pressFeedbackType = PressFeedbackType.Tilt
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    MiuixText(
                                        modifier = Modifier.fillMaxWidth(),
                                        text = stringResource(R.string.home_app_count),
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 15.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    )
                                    MiuixText(
                                        modifier = Modifier.fillMaxWidth(),
                                        text = mergedCount.toString(),
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MiuixTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                insideMargin = PaddingValues(16.dp),
                                showIndication = true,
                                pressFeedbackType = PressFeedbackType.Tilt
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    MiuixText(
                                        modifier = Modifier.fillMaxWidth(),
                                        text = stringResource(R.string.home_rule_count),
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 15.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    )
                                    MiuixText(
                                        modifier = Modifier.fillMaxWidth(),
                                        text = subCount.toString(),
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MiuixTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    SmallTitle(
                        text = stringResource(R.string.home_device_info),
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Card(
                        modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)
                    ) {
                        Column {
                            BasicComponent(
                                title = stringResource(R.string.home_device_name),
                                summary = deviceName,
                            )
                            BasicComponent(
                                title = stringResource(R.string.home_device_model),
                                summary = deviceModel,
                            )
                            BasicComponent(
                                title = stringResource(R.string.home_hyperos_version),
                                summary = hyperOSVersion,
                            )
                            BasicComponent(
                                title = stringResource(R.string.home_android_version),
                                summary = systemVersion
                            )
                            BasicComponent(
                                title = stringResource(R.string.home_module_version),
                                summary = moduleVersion
                            )
                        }
                    }
                }
            }
        }
    }
}
