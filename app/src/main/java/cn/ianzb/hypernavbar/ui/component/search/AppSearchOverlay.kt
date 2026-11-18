package cn.ianzb.hypernavbar.ui.component.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cn.ianzb.hypernavbar.R
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Close
import top.yukonga.miuix.kmp.icon.glass.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** 搜索框右侧为「取消」按钮预留的空间（dp），随展开进度生长。 */
private const val SearchFieldEndSpaceDp = 54f

/** 取消按钮从右侧滑入的距离（dp）。 */
private const val SearchCancelTravelDp = 66f

/** 取消按钮出现/消失时的模糊强度（px）。 */
private const val SearchCancelBlur = 50f

/**
 * 把一段弹簧运动映射到 400ms 的搜索转场（与 miuix 示例 GlassSearchOverlay 相同的曲线）。
 */
private val SearchModeEasing: Easing = run {
    val omega = 2.0 * PI / 0.75f
    val stiffness = (omega * omega).toFloat()
    val damping = (0.98f * 2.0 * omega).toFloat()
    val decay = -damping / 2f
    val frequency = sqrt(-(damping.toDouble() * damping - stiffness.toDouble() * 4)).toFloat() / 2f
    val sineCoefficient = decay / frequency
    Easing { fraction ->
        (
            1.0 + exp((decay * fraction).toDouble()) *
                (-cos((frequency * fraction).toDouble()) + sineCoefficient * sin((frequency * fraction).toDouble()))
            ).toFloat()
    }
}

/**
 * 全局玻璃搜索界面。
 *
 * 写法复刻自 miuix 示例 `GlassPage` 的搜索模式（`GlassSearchOverlay.kt`，Apache-2.0）：
 *
 * - 由底部玻璃底栏的搜索按钮或任意主页面的顶栏搜索按钮唤起（两个入口共用同一份状态，见 [AppSearchState]）；
 * - 进入时搜索框宽度、取消按钮位移/缩放/模糊按同一条弹簧曲线展开，退出时反向收回；
 * - 搜索过程中界面铺满整个窗口并使用页面底色，因此底栏与侧栏被整体遮住（与示例一致）；
 * - 点击空白只收起键盘，返回键 / 取消按钮才退出搜索。
 *
 * @param expanded 是否处于搜索模式。
 * @param onDismissRequest 点击取消或返回键时回调。
 * @param label 搜索框占位文案。
 * @param content 搜索结果区域，参数为当前输入的关键词。
 */
@Composable
fun AppSearchOverlay(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(String) -> Unit,
) {
    val transition = updateTransition(expanded, label = "appSearch")
    val progress = transition.animateFloat(
        transitionSpec = { tween(400, easing = SearchModeEasing) },
        label = "appSearchGeometry",
    ) { if (it) 1f else 0f }
    val cancelAlpha = transition.animateFloat(
        transitionSpec = { tween(150, delayMillis = if (targetState) 100 else 0, easing = LinearEasing) },
        label = "appSearchCancelMaterial",
    ) { if (it) 1f else 0f }

    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val direction = LocalLayoutDirection.current
    val density = LocalDensity.current

    // 搜索框与页面本身一样从状态栏下方开始，展开过程中随上边距一起落位。
    val topInsets = WindowInsets.systemBars
        .add(WindowInsets.captionBar)
        .add(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Top)
    val topInset = topInsets.asPaddingValues().calculateTopPadding()
    val topInsetPx = with(density) { topInset.toPx() }
    val endSpacePx = with(density) { SearchFieldEndSpaceDp.dp.toPx() }
    val cancelTravelPx = with(density) { SearchCancelTravelDp.dp.toPx() }

    val active = expanded || transition.currentState || transition.isRunning
    LaunchedEffect(expanded) {
        query = ""
        if (expanded) {
            focusRequester.requestFocus()
            keyboard?.show()
        } else if (active) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
    BackHandler(enabled = expanded, onBack = onDismissRequest)
    if (!active) return

    val colors = MiuixTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = progress.value }
            .background(colors.surface),
    ) {
        // 空白区域只收键盘，不退出搜索。
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(interactionSource = null, indication = null) { keyboard?.hide() },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(topInsets)
                .imePadding(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GlassTopAppBarDefaults.HorizontalPadding, vertical = 4.dp)
                    .graphicsLayer { translationY = -topInsetPx * (1f - progress.value) },
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    enabled = expanded,
                    singleLine = true,
                    textStyle = MiuixTheme.textStyles.body1.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .layout { measurable, constraints ->
                            // 搜索框右侧随展开进度让出「取消」按钮的位置。
                            val endSpace = (endSpacePx * progress.value).toInt()
                            val width = (constraints.maxWidth - endSpace).coerceAtLeast(0)
                            val placeable = measurable.measure(
                                constraints.copy(minWidth = width, maxWidth = width),
                            )
                            layout(constraints.maxWidth, placeable.height) {
                                placeable.placeRelative(0, 0)
                            }
                        }
                        .height(44.dp)
                        .focusRequester(focusRequester),
                    decorationBox = { field ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(colors.surfaceContainerHigh, CircleShape)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Glass.Search,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = colors.disabledOnSurface,
                            )
                            Box(Modifier.weight(1f)) {
                                if (query.isEmpty()) {
                                    Text(
                                        text = label,
                                        color = colors.disabledOnSurface,
                                        style = MiuixTheme.textStyles.body1,
                                    )
                                }
                                field()
                            }
                            if (query.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        query = ""
                                        focusRequester.requestFocus()
                                    },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Glass.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp * (7f / 6f)),
                                        tint = colors.disabledOnSurface,
                                    )
                                }
                            }
                        }
                    },
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .graphicsLayer {
                            val fraction = progress.value
                            translationX = cancelTravelPx * (1f - fraction) *
                                if (direction == LayoutDirection.Ltr) 1f else -1f
                            scaleX = fraction
                            scaleY = fraction
                            alpha = cancelAlpha.value
                            val blur = SearchCancelBlur * (1f - cancelAlpha.value)
                            renderEffect = if (blur > 0.5f) {
                                BlurEffect(blur, blur, TileMode.Decal)
                            } else {
                                null
                            }
                        },
                ) {
                    IconButton(
                        onClick = onDismissRequest,
                        enabled = expanded,
                        modifier = Modifier
                            .size(44.dp)
                            .background(colors.surfaceContainerHigh, CircleShape),
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Glass.Close,
                            // 取消按钮的无障碍名固定用「取消」，不要复用搜索框占位文案。
                            contentDescription = stringResource(R.string.action_cancel),
                            modifier = Modifier.size(28.dp),
                            tint = colors.onSurface,
                        )
                    }
                }
            }
            content(query)
        }
    }
}
