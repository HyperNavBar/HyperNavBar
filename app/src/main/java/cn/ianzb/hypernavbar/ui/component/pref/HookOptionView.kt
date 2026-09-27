package cn.ianzb.hypernavbar.ui.component.pref

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cn.ianzb.hypernavbar.prefs.OptionSpec
import cn.ianzb.hypernavbar.prefs.OptionType

/** 根据 [OptionSpec.type] 渲染对应组件。 */
@Composable
fun HookOptionView(
    spec: OptionSpec,
    modifier: Modifier = Modifier,
    onArrowClick: () -> Unit = {},
) {
    when (spec.type) {
        OptionType.SWITCH -> HookSwitchCard(spec, modifier)
        OptionType.CHECKBOX -> HookCheckboxCard(spec, modifier)
        OptionType.ARROW -> HookArrowCard(spec, modifier, onArrowClick)
        OptionType.DROPDOWN -> HookDropdownCard(spec, modifier)
        OptionType.RADIO -> HookRadioCard(spec, modifier)
        OptionType.SLIDER -> HookSliderCard(spec, modifier)
        OptionType.TEXT -> HookTextCard(spec, modifier)
        OptionType.PACKAGE_LIST -> HookPackageListCard(spec, modifier)
        OptionType.SPINNER -> HookDropdownCard(spec, modifier)
    }
}
