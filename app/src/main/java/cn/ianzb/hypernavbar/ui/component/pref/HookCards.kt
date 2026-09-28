package cn.ianzb.hypernavbar.ui.component.pref

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cn.ianzb.hypernavbar.prefs.ConfigState
import cn.ianzb.hypernavbar.prefs.OptionSpec
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

/** 带 switch 的卡片：关时不 hook，开时 hook。 */
@Composable
fun HookSwitchCard(
    spec: OptionSpec,
    modifier: Modifier = Modifier,
) {
    val enabled = rememberDependencyEnabled(spec)
    val checked = ConfigState.bool(spec.key, spec.defaultBoolean)
    SwitchPreference(
        title = stringResource(spec.titleRes),
        summary = spec.summaryRes.takeIf { it != 0 }?.let { stringResource(it) },
        checked = checked,
        onCheckedChange = {
            ConfigState.set(spec.key, it)
        },
        enabled = enabled,
        modifier = modifier,
    )
}

/** 带 checkbox 的卡片。 */
@Composable
fun HookCheckboxCard(
    spec: OptionSpec,
    modifier: Modifier = Modifier,
) {
    val enabled = rememberDependencyEnabled(spec)
    val checked = ConfigState.bool(spec.key, spec.defaultBoolean)
    CheckboxPreference(
        title = stringResource(spec.titleRes),
        summary = spec.summaryRes.takeIf { it != 0 }?.let { stringResource(it) },
        checked = checked,
        onCheckedChange = {
            ConfigState.set(spec.key, it)
        },
        enabled = enabled,
        modifier = modifier,
    )
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
