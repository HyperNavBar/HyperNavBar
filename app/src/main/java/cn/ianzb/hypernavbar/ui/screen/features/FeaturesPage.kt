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
import cn.ianzb.hypernavbar.prefs.HookBlacklist
import cn.ianzb.hypernavbar.prefs.OptionSpec
import cn.ianzb.hypernavbar.prefs.OptionType
import cn.ianzb.hypernavbar.rules.HookRulePublisher
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

    // 开启「强制生效」后，为已发布规则的应用申请作用域（框架 Hook 仅对作用域内进程生效）。
    val activated = XposedServiceManager.isActivated
    val forceMaster = ConfigState.bool("force_master", false)
    LaunchedEffect(forceMaster, activated) {
        if (forceMaster && activated) {
            val packages = HookBlacklist.filter(HookRulePublisher.ruledPackages())
            if (packages.isNotEmpty()) XposedServiceManager.ensureScope(packages)
        }
    }

    HookOptionsPage(
        title = stringResource(R.string.tab_features),
        sections = sections,
        isBlurEnabled = isBlurEnabled,
        extraBottomPadding = extraBottomPadding,
        topContent = { FeatureNotice() },
        onArrowClick = { spec ->
            when (spec.key) {
                "feature_scope" -> context.startActivity(Intent(context, ScopeListActivity::class.java))
                "feature_safemode" -> context.startActivity(Intent(context, SafeModeActivity::class.java))
            }
        },
    )
}

/** 顶部提示：说明 Hook 的系统版本要求（HyperOS 4.0+）。 */
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
        titleRes = R.string.section_module,
        specs = listOf(
            specByKey(specs, "feature_scope"),
            specByKey(specs, "feature_safemode"),
        ),
    ),
)

private fun specByKey(specs: List<OptionSpec>, key: String): OptionSpec =
    specs.first { it.key == key }

/** 功能页全部配置项（App 启动时注册，供全局搜索与作用域申请使用）。 */
internal fun featureSpecs(): List<OptionSpec> = listOf(
    OptionSpec(
        key = "force_master",
        type = OptionType.SWITCH,
        titleRes = R.string.force_master_title,
        summaryRes = R.string.force_master_summary,
        defaultBoolean = false,
        targetPackages = listOf("system"),
    ),
    OptionSpec(
        key = "force_ignore_version",
        type = OptionType.SWITCH,
        titleRes = R.string.force_ignore_version_title,
        summaryRes = R.string.force_ignore_version_summary,
        defaultBoolean = true,
        dependsOn = "force_master",
        targetPackages = listOf("system"),
    ),
    OptionSpec(
        key = "force_bypass_e2e",
        type = OptionType.SWITCH,
        titleRes = R.string.force_bypass_e2e_title,
        summaryRes = R.string.force_bypass_e2e_summary,
        defaultBoolean = true,
        dependsOn = "force_master",
        targetPackages = listOf("system"),
    ),
    OptionSpec(
        key = "force_bypass_virtual_display",
        type = OptionType.SWITCH,
        titleRes = R.string.force_bypass_virtual_display_title,
        summaryRes = R.string.force_bypass_virtual_display_summary,
        defaultBoolean = true,
        dependsOn = "force_master",
        targetPackages = listOf("system"),
    ),
    OptionSpec(
        key = "force_gesture_nav",
        type = OptionType.SWITCH,
        titleRes = R.string.force_gesture_nav_title,
        summaryRes = R.string.force_gesture_nav_summary,
        defaultBoolean = true,
        dependsOn = "force_master",
        targetPackages = listOf("system"),
    ),
    OptionSpec(
        key = "force_fullscreen",
        type = OptionType.SWITCH,
        titleRes = R.string.force_fullscreen_title,
        summaryRes = R.string.force_fullscreen_summary,
        defaultBoolean = true,
        dependsOn = "force_master",
        targetPackages = listOf("system"),
    ),
    OptionSpec(
        key = "force_show_nav",
        type = OptionType.SWITCH,
        titleRes = R.string.force_show_nav_title,
        summaryRes = R.string.force_show_nav_summary,
        defaultBoolean = true,
        dependsOn = "force_master",
        targetPackages = listOf("system"),
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
