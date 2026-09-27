package cn.ianzb.hypernavbar.ui.util

import androidx.compose.animation.core.SpringSpec
import androidx.compose.ui.unit.IntSize
import top.yukonga.miuix.kmp.anim.folmeSpring

/**
 * 组件出现 / 隐藏使用的 Miuix 标准弹性动画。
 *
 * 参数取自 Miuix 内部标准弹簧（临界阻尼、响应 0.4s），用于展开 / 收起动画。
 * 所有 `AnimatedVisibility` 的显隐动画都应使用该 spec，保持全局动效一致。
 */
val MiuixExpandSpec: SpringSpec<IntSize> = folmeSpring(damping = 1.0f, response = 0.4f)
