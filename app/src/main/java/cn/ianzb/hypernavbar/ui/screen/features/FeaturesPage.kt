package cn.ianzb.hypernavbar.ui.screen.features

import android.content.Intent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.ianzb.hypernavbar.R
import cn.ianzb.hypernavbar.prefs.ConfigState
import cn.ianzb.hypernavbar.prefs.OptionSpec
import cn.ianzb.hypernavbar.prefs.OptionType
import cn.ianzb.hypernavbar.prefs.OsRequirement
import cn.ianzb.hypernavbar.ui.component.pref.HookOptionsPage
import cn.ianzb.hypernavbar.ui.component.pref.HookSection
import cn.ianzb.hypernavbar.ui.screen.safemode.SafeModeActivity
import cn.ianzb.hypernavbar.ui.screen.scope.ScopeListActivity
import cn.ianzb.hypernavbar.xposed.XposedServiceManager
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text as MiuixText

/**
 * 功能页：Hook 相关功能的统一入口。
 *
 * 目前包含「强制生效」「模块」分区，以及作用域 / 安全模式二级页入口。
 * 规则注入与旧版残留还原为核心机制，默认自动启用，不提供界面开关。
 */
@Composable
fun FeaturesPageView(
    isBlurEnabled: Boolean = true,
    extraBottomPadding: Dp = 0.dp,
) {
    val context = LocalContext.current
    val specs = remember { featureSpecs() }
    val sections = remember(specs) { featureSections(specs) }

    // 开启「手势提示线」时申请 SystemUI 作用域（Hook 仅安装在系统界面进程）
    val gestureEnabled = ConfigState.bool("gesture_handle", false)
    LaunchedEffect(gestureEnabled) {
        if (gestureEnabled) {
            XposedServiceManager.ensureScope(listOf(XposedServiceManager.SYSTEM_UI_PACKAGE))
        }
    }

    HookOptionsPage(
        title = stringResource(R.string.tab_features),
        sections = sections,
        isBlurEnabled = isBlurEnabled,
        extraBottomPadding = extraBottomPadding,
        topContent = { FeatureNotice() },
        // 右上角「重启」入口：按规范打开重启弹窗（弹窗内包含系统界面），
        // 修改手势提示线开关 / 作用域后，用于让 SystemUI 侧 Hook 重新加载。
        customActionPackages = listOf(XposedServiceManager.SYSTEM_UI_PACKAGE),
        onArrowClick = { spec ->
            when (spec.key) {
                "feature_scope" -> context.startActivity(Intent(context, ScopeListActivity::class.java))
                "feature_safemode" -> context.startActivity(Intent(context, SafeModeActivity::class.java))
            }
        },
    )
}

/** 顶部提示：说明 Hook 的系统版本要求（HyperOS 3.3 / 4.x）。 */
@Composable
private fun FeatureNotice() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = MiuixIcons.Info,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            MiuixText(
                text = stringResource(R.string.features_notice),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2,
            )
        }
    }
}

private fun featureSections(specs: List<OptionSpec>): List<HookSection> = listOf(
    HookSection(
        titleRes = R.string.section_force,
        specs = listOf(
            specByKey(specs, "force_master"),
            specByKey(specs, "force_ignore_version"),
            specByKey(specs, "force_bypass_e2e"),
            specByKey(specs, "force_bypass_virtual_display"),
            specByKey(specs, "force_gesture_nav"),
            specByKey(specs, "force_fullscreen"),
            specByKey(specs, "force_show_nav"),
        ),
    ),
    HookSection(
        titleRes = R.string.section_gesture,
        specs = listOf(
            specByKey(specs, "gesture_handle"),
            specByKey(specs, "gesture_handle_motion"),
            specByKey(specs, "gesture_handle_motion_hold"),
            specByKey(specs, "gesture_handle_motion_scale"),
            specByKey(specs, "gesture_handle_motion_range"),
            specByKey(specs, "gesture_handle_motion_return"),
            specByKey(specs, "gesture_handle_touch_reveal"),
            specByKey(specs, "gesture_handle_global_mode"),
            specByKey(specs, "gesture_handle_follow_rules"),
        ),
    ),
    HookSection(
        titleRes = R.string.section_module,
        specs = listOf(
            specByKey(specs, "feature_scope"),
            specByKey(specs, "feature_safemode"),
        ),
    ),
)

private fun specByKey(specs: List<OptionSpec>, key: String): OptionSpec =
    specs.first { it.key == key }

/** 功能页全部配置项（App 启动时注册，供全局搜索使用）。 */
internal fun featureSpecs(): List<OptionSpec> = listOf(
    OptionSpec(
        key = "force_master",
        type = OptionType.SWITCH,
        titleRes = R.string.force_master_title,
        summaryRes = R.string.force_master_summary,
        defaultBoolean = false,
        // 「强制生效」系列 Hook 只安装在沉浸支持版本（HyperOS 3.3 / 4.0）
        osRequirement = OsRequirement.IMMERSIVE,
    ),
    OptionSpec(
        key = "force_ignore_version",
        type = OptionType.SWITCH,
        titleRes = R.string.force_ignore_version_title,
        summaryRes = R.string.force_ignore_version_summary,
        defaultBoolean = true,
        dependsOn = "force_master",
    ),
    OptionSpec(
        key = "force_bypass_e2e",
        type = OptionType.SWITCH,
        titleRes = R.string.force_bypass_e2e_title,
        summaryRes = R.string.force_bypass_e2e_summary,
        defaultBoolean = true,
        dependsOn = "force_master",
    ),
    OptionSpec(
        key = "force_bypass_virtual_display",
        type = OptionType.SWITCH,
        titleRes = R.string.force_bypass_virtual_display_title,
        summaryRes = R.string.force_bypass_virtual_display_summary,
        defaultBoolean = true,
        dependsOn = "force_master",
    ),
    OptionSpec(
        key = "force_gesture_nav",
        type = OptionType.SWITCH,
        titleRes = R.string.force_gesture_nav_title,
        summaryRes = R.string.force_gesture_nav_summary,
        defaultBoolean = true,
        dependsOn = "force_master",
    ),
    OptionSpec(
        key = "force_fullscreen",
        type = OptionType.SWITCH,
        titleRes = R.string.force_fullscreen_title,
        summaryRes = R.string.force_fullscreen_summary,
        defaultBoolean = true,
        dependsOn = "force_master",
    ),
    OptionSpec(
        key = "force_show_nav",
        type = OptionType.SWITCH,
        titleRes = R.string.force_show_nav_title,
        summaryRes = R.string.force_show_nav_summary,
        defaultBoolean = true,
        dependsOn = "force_master",
    ),
    OptionSpec(
        key = "gesture_handle",
        type = OptionType.SWITCH,
        titleRes = R.string.gesture_handle_title,
        summaryRes = R.string.gesture_handle_summary,
        defaultBoolean = false,
        // 手势提示线 Hook 仅 HyperOS 4.0 提供
        osRequirement = OsRequirement.OS_4,
    ),
    OptionSpec(
        key = "gesture_handle_motion",
        type = OptionType.SWITCH,
        titleRes = R.string.gesture_handle_motion_title,
        summaryRes = R.string.gesture_handle_motion_summary,
        defaultBoolean = true,
        dependsOn = "gesture_handle",
    ),
    OptionSpec(
        key = "gesture_handle_touch_reveal",
        type = OptionType.SWITCH,
        titleRes = R.string.gesture_handle_touch_reveal_title,
        summaryRes = R.string.gesture_handle_touch_reveal_summary,
        defaultBoolean = true,
        dependsOn = "gesture_handle",
    ),
    OptionSpec(
        key = "gesture_handle_motion_hold",
        type = OptionType.SLIDER,
        titleRes = R.string.gesture_motion_hold_title,
        summaryRes = R.string.gesture_motion_hold_summary,
        defaultFloat = 260f,
        dependsOn = "gesture_handle_motion",
        sliderMin = 0f,
        sliderMax = 800f,
        sliderStep = 20f,
        sliderDecimals = 0,
        sliderUnitRes = R.string.gesture_unit_ms,
        sliderValueLabelRes = R.string.gesture_motion_hold_title,
    ),
    OptionSpec(
        key = "gesture_handle_motion_scale",
        type = OptionType.SLIDER,
        titleRes = R.string.gesture_motion_scale_title,
        summaryRes = R.string.gesture_motion_scale_summary,
        defaultFloat = 50f,
        dependsOn = "gesture_handle_motion",
        sliderMin = 0f,
        sliderMax = 100f,
        sliderStep = 5f,
        sliderDecimals = 0,
        sliderUnitRes = R.string.gesture_unit_percent,
        sliderValueLabelRes = R.string.gesture_motion_scale_title,
    ),
    OptionSpec(
        key = "gesture_handle_motion_range",
        type = OptionType.SLIDER,
        titleRes = R.string.gesture_motion_range_title,
        summaryRes = R.string.gesture_motion_range_summary,
        defaultFloat = 24f,
        dependsOn = "gesture_handle_motion",
        sliderMin = 0f,
        sliderMax = 48f,
        sliderStep = 2f,
        sliderDecimals = 0,
        sliderUnitRes = R.string.gesture_unit_dp,
        sliderValueLabelRes = R.string.gesture_motion_range_title,
    ),
    OptionSpec(
        key = "gesture_handle_motion_return",
        type = OptionType.SLIDER,
        titleRes = R.string.gesture_motion_return_title,
        summaryRes = R.string.gesture_motion_return_summary,
        defaultFloat = 100f,
        dependsOn = "gesture_handle_motion",
        sliderMin = 50f,
        sliderMax = 200f,
        sliderStep = 10f,
        sliderDecimals = 0,
        sliderUnitRes = R.string.gesture_unit_percent,
        sliderValueLabelRes = R.string.gesture_motion_return_title,
    ),
    OptionSpec(
        key = "gesture_handle_global_mode",
        type = OptionType.DROPDOWN,
        titleRes = R.string.gesture_handle_global_mode_title,
        summaryRes = R.string.gesture_handle_global_mode_summary,
        defaultString = "alwaysShow",
        entryResIds = listOf(
            R.string.gesture_mode_always_show,
            R.string.gesture_mode_auto_hide,
            R.string.gesture_mode_always_hide,
        ),
        entryValues = listOf("alwaysShow", "autoHide", "alwaysHide"),
        dependsOn = "gesture_handle",
    ),
    OptionSpec(
        key = "gesture_handle_follow_rules",
        type = OptionType.DROPDOWN,
        titleRes = R.string.gesture_handle_follow_rules_title,
        summaryRes = R.string.gesture_handle_follow_rules_summary,
        defaultString = "follow",
        entryResIds = listOf(
            R.string.gesture_follow_on,
            R.string.gesture_follow_off,
        ),
        entryValues = listOf("follow", "off"),
        dependsOn = "gesture_handle",
    ),
    OptionSpec(
        key = "feature_scope",
        type = OptionType.ARROW,
        titleRes = R.string.features_scope_title,
    ),
    OptionSpec(
        key = "feature_safemode",
        type = OptionType.ARROW,
        titleRes = R.string.features_safemode_title,
    ),
)
