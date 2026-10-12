// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
//
// 改编自 miuix 的 PagerGestureUtils.horizontalPagerSwipeOverride（Apache-2.0）。
//
// 与上游的唯一行为差异：当本次手势的按下位置命中「认领横向手势」的子组件（如滑条，
// 见 [Modifier.claimPagerSwipe]）时，本组件不再拦截这一手势的横向拖拽——
// 上游实现于 PointerEventPass.Initial（父级先收到、先消费）拦截，导致子组件在 Main
// 阶段拿到的已是被消费事件、无法拖动；[PagerSwipeGate] 让子组件在按下时同步认领，
// 无需经过重组即可被这里实时读取，因此没有竞态。
package cn.ianzb.hypernavbar.ui.util

import androidx.compose.animation.core.animate
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.pageLeft
import androidx.compose.ui.semantics.pageRight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import top.yukonga.miuix.kmp.utils.springAnimateToPage
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.time.Duration.Companion.milliseconds

/**
 * 横向手势归属开关：位于横向 Pager 内的交互组件（如滑条）在按下时认领本次手势，
 * 使 [pagerSwipeOverride] 在手势期间放行、避免横向拖拽被翻页拦截。
 *
 * 认领发生在指针事件的子组件处理阶段，Pager 侧在事件分发中实时读取（非 Compose 状态），
 * 因此不依赖重组、不受“按下与首个位移位于同一帧批次”的影响。
 */
object PagerSwipeGate {

    @Volatile
    var childOwnsGesture: Boolean = false
        private set

    fun claim() {
        childOwnsGesture = true
    }

    fun release() {
        childOwnsGesture = false
    }

    /** 新手势按下时由 Pager 复位，兜底清理未能正常释放的认领。 */
    fun resetForNewGesture() {
        childOwnsGesture = false
    }
}

/**
 * 让当前组件（如滑条行）在本次手势内独占横向拖拽：按下即认领，抬手 / 取消后释放。
 *
 * 只观察、不消费任何事件，因此不影响组件自身的拖拽、点击处理。
 */
fun Modifier.claimPagerSwipe(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        PagerSwipeGate.claim()
        try {
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.none { it.pressed }) break
            }
        } finally {
            PagerSwipeGate.release()
        }
    }
}

/**
 * 驱动横向拖拽与回弹（与 miuix 的 `horizontalPagerSwipeOverride` 等价，另支持 [PagerSwipeGate] 放行）。
 *
 * 使用方式与上游一致：配合 `HorizontalPager(userScrollEnabled = false,
 * pageNestedScrollConnection = PagerGestureNestedScrollConnection)`。
 */
fun Modifier.pagerSwipeOverride(
    pagerState: PagerState,
    enabled: Boolean = true,
    onIntercepted: (() -> Unit)? = null,
): Modifier = if (!enabled) this else then(PagerSwipeOverrideElement(pagerState, onIntercepted))

private data class PagerSwipeOverrideElement(
    val pagerState: PagerState,
    val onIntercepted: (() -> Unit)?,
) : ModifierNodeElement<PagerSwipeOverrideNode>() {
    override fun create(): PagerSwipeOverrideNode = PagerSwipeOverrideNode(pagerState, onIntercepted)

    override fun update(node: PagerSwipeOverrideNode) {
        node.update(pagerState, onIntercepted)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "pagerSwipeOverride"
        properties["pagerState"] = pagerState
    }
}

private sealed interface PagerDragEvent {
    data class Delta(val value: Float) : PagerDragEvent
    data class End(val velocity: Float) : PagerDragEvent
}

private class PagerSwipeOverrideNode(
    var pagerState: PagerState,
    var onIntercepted: (() -> Unit)?,
) : DelegatingNode(),
    CompositionLocalConsumerModifierNode,
    SemanticsModifierNode,
    PointerInputModifierNode {

    private var motionJob: Job? = null
    private var nonTouchEvents: Channel<PagerDragEvent>? = null
    private var wheelEndJob: Job? = null

    private val pointerNode = delegate(
        SuspendingPointerInputModifierNode {
            val velocityTracker = VelocityTracker()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                // 新手势：复位上一次的认领，随后子组件可在本次按下中重新认领
                PagerSwipeGate.resetForNewGesture()
                val state = pagerState
                val scrollSign = scrollSign()
                finishNonTouchInput()
                val events = startMotion(state)

                var pointerId = down.id
                var accumulated = Offset.Zero
                var dragging = false
                var ended = false
                velocityTracker.resetTracking()
                velocityTracker.addPointerInputChange(down)
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.fastFirstOrNull { it.id == pointerId } ?: break
                        if (change.isConsumed) break
                        // 子组件（滑条）已认领本次手势：本手势不翻页，横向拖拽让给子组件
                        if (PagerSwipeGate.childOwnsGesture) break
                        if (!change.pressed) {
                            val replacement = event.changes.fastFirstOrNull { it.pressed }
                            if (replacement != null) {
                                pointerId = replacement.id
                                velocityTracker.resetTracking()
                                velocityTracker.addPointerInputChange(replacement)
                                continue
                            }
                            velocityTracker.addPointerInputChange(change)
                            val velocity = if (dragging) {
                                change.consume()
                                velocityTracker.calculateVelocity(
                                    Velocity(viewConfiguration.maximumFlingVelocity, viewConfiguration.maximumFlingVelocity),
                                ).x * scrollSign
                            } else {
                                0f
                            }
                            events.trySend(PagerDragEvent.End(velocity))
                            ended = true
                            break
                        }

                        velocityTracker.addPointerInputChange(change)
                        val delta = change.position - change.previousPosition
                        accumulated += delta
                        if (!dragging) {
                            val x = abs(accumulated.x)
                            val y = abs(accumulated.y)
                            if (x > viewConfiguration.touchSlop && x > y) {
                                dragging = true
                                onIntercepted?.invoke()
                                change.consume()
                                // Keep the first drag frame's movement beyond slop.
                                val overSlop = accumulated.x - sign(accumulated.x) * viewConfiguration.touchSlop
                                events.trySend(PagerDragEvent.Delta(overSlop * scrollSign))
                            } else if (y > viewConfiguration.touchSlop) {
                                // Yield to the child; finally settles the pager.
                                break
                            }
                        } else {
                            change.consume()
                            events.trySend(PagerDragEvent.Delta(delta.x * scrollSign))
                        }
                    }
                } finally {
                    if (!ended) events.trySend(PagerDragEvent.End(0f))
                    events.close()
                }
            }
        },
    )

    private fun scrollSign(): Float = if (
        (currentValueOf(LocalLayoutDirection) == LayoutDirection.Rtl) xor pagerState.layoutInfo.reverseLayout
    ) {
        1f
    } else {
        -1f
    }

    private fun startMotion(state: PagerState = pagerState): Channel<PagerDragEvent> {
        val minimumVelocity = with(currentValueOf(LocalDensity)) { 400.dp.toPx() }
        val events = Channel<PagerDragEvent>(Channel.UNLIMITED)
        motionJob?.cancel()
        motionJob = coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                state.scroll(MutatePriority.UserInput) {
                    // Keep drag and settling under one mutation so new input cancels them together.
                    for (event in events) {
                        when (event) {
                            is PagerDragEvent.Delta -> scrollBy(event.value)

                            is PagerDragEvent.End -> {
                                val position = state.currentPage + state.currentPageOffsetFraction
                                val target = when {
                                    event.velocity > minimumVelocity -> ceil(position).toInt()
                                    event.velocity < -minimumVelocity -> floor(position).toInt()
                                    else -> position.roundToInt()
                                }
                                state.animateToPageBySpring(this, target, event.velocity)
                                break
                            }
                        }
                    }
                }
            } finally {
                events.cancel()
            }
        }
        return events
    }

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        pointerNode.onPointerEvent(pointerEvent, pass, bounds)
        if (pass != PointerEventPass.Main) return
        // Handle wheel/trackpad input after children, since native pager input is disabled.
        val isWheel = pointerEvent.type == PointerEventType.Scroll
        val isPan = pointerEvent.type == PointerEventType.PanMove
        if (pointerEvent.type == PointerEventType.PanEnd) {
            finishNonTouchInput()
            return
        }
        if (!isWheel && !isPan) return
        var delta = Offset.Zero
        for (change in pointerEvent.changes) {
            if (change.isConsumed) return
            delta += if (isWheel) change.scrollDelta else change.panOffset
        }
        if (delta.x == 0f || abs(delta.x) <= abs(delta.y)) return
        val scale = if (isWheel) with(currentValueOf(LocalDensity)) { 48.dp.toPx() } else 1f
        val events = if (motionJob?.isActive == true) nonTouchEvents else null
        val activeEvents = events ?: startMotion().also { nonTouchEvents = it }
        activeEvents.trySend(PagerDragEvent.Delta(-delta.x * scale * scrollSign()))
        pointerEvent.changes.forEach { it.consume() }
        if (isWheel) {
            wheelEndJob?.cancel()
            wheelEndJob = coroutineScope.launch {
                delay(120.milliseconds)
                finishNonTouchInput()
            }
        }
    }

    override fun onCancelPointerInput() {
        pointerNode.onCancelPointerInput()
        finishNonTouchInput()
    }

    private fun finishNonTouchInput() {
        wheelEndJob?.cancel()
        wheelEndJob = null
        nonTouchEvents?.let {
            it.trySend(PagerDragEvent.End(0f))
            it.close()
        }
        nonTouchEvents = null
    }

    override fun onDetach() {
        finishNonTouchInput()
        motionJob?.cancel()
        motionJob = null
    }

    override fun SemanticsPropertyReceiver.applySemantics() {
        pageLeft { navigateBy(scrollSign().toInt()) }
        pageRight { navigateBy(-scrollSign().toInt()) }
    }

    private fun navigateBy(pages: Int): Boolean {
        val target = pagerState.currentPage + pages
        if (target !in 0 until pagerState.pageCount) return false
        finishNonTouchInput()
        motionJob?.cancel()
        motionJob = coroutineScope.launch { pagerState.springAnimateToPage(target) }
        return true
    }

    fun update(state: PagerState, onIntercepted: (() -> Unit)?) {
        this.onIntercepted = onIntercepted
        if (pagerState != state) {
            finishNonTouchInput()
            motionJob?.cancel()
            pagerState = state
            pointerNode.resetPointerInputHandler()
        }
    }
}

/** 与 miuix 内部实现一致的带初速度分页动画（[PagerNavigationSpringSpec]）。 */
private suspend fun PagerState.animateToPageBySpring(
    scrollScope: ScrollScope,
    target: Int,
    initialVelocity: Float = 0f,
) = with(scrollScope) {
    if (pageCount == 0) return@with
    val destination = target.coerceIn(0, pageCount - 1)
    val pageSize = layoutInfo.pageSize + layoutInfo.pageSpacing
    updateTargetPage(destination)
    if (pageSize > 0) {
        val distance = (destination - currentPage - currentPageOffsetFraction) * pageSize
        var previousValue = 0f
        animate(
            initialValue = 0f,
            targetValue = distance,
            initialVelocity = initialVelocity,
            animationSpec = PagerNavigationSpringSpec,
        ) { currentValue, _ ->
            previousValue += scrollBy(currentValue - previousValue)
        }
    }
    // Complete within this mutation so cancellation cannot snap a newer gesture to an old target.
    if (pageCount > 0) updateCurrentPage(destination.coerceAtMost(pageCount - 1))
}
