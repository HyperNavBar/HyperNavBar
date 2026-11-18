package cn.ianzb.hypernavbar.ui.component.pref

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import cn.ianzb.hypernavbar.R
import cn.ianzb.hypernavbar.hook.rule.SystemVersion
import cn.ianzb.hypernavbar.prefs.ConfigState
import cn.ianzb.hypernavbar.prefs.OptionSpec
import cn.ianzb.hypernavbar.prefs.OsRequirement
import cn.ianzb.hypernavbar.xposed.HookStatusReader
import cn.ianzb.hypernavbar.xposed.XposedServiceManager
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 带 switch 的卡片：关时不 hook，开时 hook。 */
@Composable
fun HookSwitchCard(
    spec: OptionSpec,
    modifier: Modifier = Modifier,
) {
    val enabled = rememberDependencyEnabled(spec)
    val checked = ConfigState.bool(spec.key, spec.defaultBoolean)
    BasicComponent(
        enabled = enabled,
        modifier = modifier,
        onClick = { ConfigState.set(spec.key, !checked) },
        endActions = {
            Switch(checked = checked, onCheckedChange = { ConfigState.set(spec.key, it) }, enabled = enabled)
        },
    ) {
        HookBooleanTitle(spec, enabled, checked)
    }
}

/** 带 checkbox 的卡片。 */
@Composable
fun HookCheckboxCard(
    spec: OptionSpec,
    modifier: Modifier = Modifier,
) {
    val enabled = rememberDependencyEnabled(spec)
    val checked = ConfigState.bool(spec.key, spec.defaultBoolean)
    BasicComponent(
        enabled = enabled,
        modifier = modifier,
        onClick = { ConfigState.set(spec.key, !checked) },
        endActions = {
            Checkbox(state = ToggleableState(checked), onClick = { ConfigState.set(spec.key, !checked) }, enabled = enabled)
        },
    ) {
        HookBooleanTitle(spec, enabled, checked)
    }
}

/** 带 arrow 的卡片：点击进入下级页面。 */
@Composable
fun HookArrowCard(
    spec: OptionSpec,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val enabled = rememberDependencyEnabled(spec)
    ArrowPreference(
        title = stringResource(spec.titleRes),
        summary = spec.summaryRes.takeIf { it != 0 }?.let { stringResource(it) },
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
    )
}


@Composable
private fun HookBooleanTitle(spec: OptionSpec, enabled: Boolean, checked: Boolean) {
    val colors = MiuixTheme.colorScheme
    val unsupported = spec.osRequirement != OsRequirement.NONE &&
        !spec.osRequirement.satisfiedBy(SystemVersion.current())
    val status = when {
        unsupported -> R.string.hook_badge_unavailable
        !enabled || !checked || !XposedServiceManager.isActivated -> null
        else -> when (HookStatusReader.statusOf(spec.statusId)) {
            true -> R.string.hook_badge_applied
            false -> R.string.hook_badge_failed
            null -> null
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(spec.titleRes),
            modifier = Modifier.weight(1f, fill = false),
            style = MiuixTheme.textStyles.headline1,
            color = if (enabled) colors.onBackground else colors.disabledOnSecondaryVariant,
        )
        if (status != null) {
            val tint = when (status) {
                R.string.hook_badge_applied -> colors.primary
                R.string.hook_badge_failed -> colors.error
                else -> colors.onSurfaceVariantSummary
            }
            Spacer(Modifier.width(8.dp))
            Badge(containerColor = tint.copy(alpha = 0.14f), contentColor = tint) {
                Text(stringResource(status), maxLines = 1)
            }
        }
    }
    if (spec.summaryRes != 0) {
        Text(
            text = stringResource(spec.summaryRes),
            modifier = Modifier.padding(top = 2.dp),
            style = MiuixTheme.textStyles.body2,
            color = if (enabled) colors.onSurfaceVariantSummary else colors.disabledOnSecondaryVariant,
        )
    }
}