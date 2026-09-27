package cn.ianzb.hypernavbar.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import cn.ianzb.hypernavbar.R
import cn.ianzb.hypernavbar.ui.util.BlurredBar
import cn.ianzb.hypernavbar.ui.util.LocalSubPageScrollBehavior
import cn.ianzb.hypernavbar.ui.util.blurSource
import cn.ianzb.hypernavbar.ui.util.rememberBlurState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 二级页面模板脚手架：顶栏返回、背景模糊、主题等模块配置统一在此处理。
 *
 * @param topBarActions 顶栏右侧扩展槽（如「重启应用」入口），为空则不显示。
 */
@Composable
fun SubPageScaffold(
    title: String,
    isBlurEnabled: Boolean,
    onBack: () -> Unit,
    topBarActions: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val hazeState = rememberBlurState()
    val blurActive = isBlurEnabled && hazeState != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(hazeState, blurActive, scrollBehavior) {
                TopAppBar(
                    title = title,
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.action_back),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    },
                    actions = {
                        topBarActions?.invoke()
                    },
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(
            modifier = Modifier.blurSource(if (isBlurEnabled) hazeState else null)
        ) {
            CompositionLocalProvider(LocalSubPageScrollBehavior provides scrollBehavior) {
                content(innerPadding)
            }
        }
    }
}
