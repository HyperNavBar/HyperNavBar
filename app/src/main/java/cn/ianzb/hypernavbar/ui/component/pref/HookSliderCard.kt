package cn.ianzb.hypernavbar.ui.component.pref

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ianzb.hypernavbar.R
import cn.ianzb.hypernavbar.prefs.ConfigState
import cn.ianzb.hypernavbar.prefs.OptionSpec
import cn.ianzb.hypernavbar.ui.util.MiuixExpandSpec
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Text as MiuixText

/**
 * 带 slider 的卡片。
 *
 * - 由 [OptionSpec.masterKey] 的开关控制 slider 是否生效与是否出现（带 Miuix 动画）
 * - 支持整数 / 小数 / 自定义范围（内部定点换算，避免浮点精度丢失）
 * - 当前值与默认值同一行显示，两侧显示最小值 / 最大值
 * - 点击卡片空白弹出居中输入卡片（取消 / 恢复默认 / 确定）
 */
@Composable
fun HookSliderCard(
    spec: OptionSpec,
    modifier: Modifier = Modifier,
) {
    val enabled = rememberDependencyEnabled(spec)
    val masterKey = spec.masterKey
    val masterEnabled = if (masterKey != null) ConfigState.bool(masterKey, false) else true
    val value = ConfigState.float(spec.key, spec.defaultFloat)
    var showDialog by remember { mutableStateOf(false) }
    val unit = if (spec.sliderUnitRes != 0) stringResource(spec.sliderUnitRes) else ""

    Column(modifier = modifier) {
        if (masterKey != null) {
            SwitchPreference(
                title = stringResource(spec.titleRes),
                summary = spec.summaryRes.takeIf { it != 0 }?.let { stringResource(it) },
                checked = masterEnabled,
                onCheckedChange = {
                    ConfigState.set(masterKey, it)
                    if (it) ensureScopeFor(spec)
                },
                enabled = enabled,
            )
        }

        AnimatedVisibility(
            visible = masterEnabled && enabled,
            enter = expandVertically(animationSpec = MiuixExpandSpec),
            exit = shrinkVertically(animationSpec = MiuixExpandSpec),
        ) {
            Column {
                if (masterKey == null && spec.sliderValueLabelRes == 0) {
                    MiuixText(
                        text = stringResource(spec.titleRes),
                        style = MiuixTheme.textStyles.main,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp),
                    )
                }
                // 数值类型说明作为标题居左，当前值显示在右侧、紧邻箭头。
                val valueLabel = if (spec.sliderValueLabelRes != 0) {
                    stringResource(spec.sliderValueLabelRes)
                } else {
                    null
                }
                SliderPreference(
                    value = value,
                    onValueChange = { ConfigState.set(spec.key, roundToDecimals(it, spec.sliderDecimals)) },
                    valueRange = spec.sliderMin..spec.sliderMax,
                    steps = sliderSteps(spec),
                    title = valueLabel,
                    valueText = formatValue(value, spec, unit),
                    onClick = { showDialog = true },
                    enabled = enabled,
                )
            }
        }
    }

    if (showDialog) {
        SliderValueDialog(
            spec = spec,
            unit = unit,
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun SliderValueDialog(
    spec: OptionSpec,
    unit: String,
    onDismiss: () -> Unit,
) {
    val current = ConfigState.float(spec.key, spec.defaultFloat)
    var input by remember { mutableStateOf(formatNumber(current, spec.sliderDecimals)) }
    var isError by remember { mutableStateOf(false) }

    WindowDialog(
        show = true,
        title = stringResource(spec.titleRes),
        summary = stringResource(R.string.hook_slider_dialog_summary),
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiuixText(
                    text = formatValue(spec.sliderMin, spec, unit),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote2,
                )
                Spacer(Modifier.weight(1f))
                MiuixText(
                    text = stringResource(
                        R.string.hook_slider_current_default,
                        formatValue(current, spec, unit),
                        formatValue(spec.defaultFloat, spec, unit),
                    ),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote2,
                )
                Spacer(Modifier.weight(1f))
                MiuixText(
                    text = formatValue(spec.sliderMax, spec, unit),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote2,
                )
            }
            Spacer(Modifier.padding(top = 8.dp))
            TextField(
                value = input,
                onValueChange = {
                    input = it
                    isError = parseInput(it, spec) == null
                },
                label = stringResource(R.string.hook_slider_dialog_input),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (isError) {
                MiuixText(
                    text = stringResource(
                        R.string.hook_slider_dialog_range,
                        formatNumber(spec.sliderMin, spec.sliderDecimals),
                        formatNumber(spec.sliderMax, spec.sliderDecimals),
                    ),
                    color = MiuixTheme.colorScheme.error,
                    style = MiuixTheme.textStyles.footnote2,
                )
            }
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
                    text = stringResource(R.string.hook_slider_restore_default),
                    onClick = {
                        ConfigState.set(spec.key, spec.defaultFloat)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    text = stringResource(R.string.action_confirm),
                    onClick = {
                        parseInput(input, spec)?.let { ConfigState.set(spec.key, it) }
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun sliderSteps(spec: OptionSpec): Int {
    if (spec.sliderStep <= 0f) return 0
    val count = ((spec.sliderMax - spec.sliderMin) / spec.sliderStep).toInt() - 1
    return count.coerceAtLeast(0)
}

private fun roundToDecimals(value: Float, decimals: Int): Float =
    BigDecimal(value.toString()).setScale(decimals, RoundingMode.HALF_UP).toFloat()

private fun formatValue(value: Float, spec: OptionSpec, unit: String): String =
    formatNumber(value, spec.sliderDecimals) + unit

private fun formatNumber(value: Float, decimals: Int): String =
    String.format(Locale.US, "%.${decimals}f", value)

private fun parseInput(input: String, spec: OptionSpec): Float? {
    val parsed = input.trim().toBigDecimalOrNull() ?: return null
    val min = BigDecimal(spec.sliderMin.toString())
    val max = BigDecimal(spec.sliderMax.toString())
    if (parsed < min || parsed > max) return null
    return parsed.setScale(spec.sliderDecimals, RoundingMode.HALF_UP).toFloat()
}
