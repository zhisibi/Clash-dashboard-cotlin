package net.zash.clashpanel.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/*
 * Edge bounce (1.2.2, HarmonyOS EdgeEffect.Spring {alwaysEnabled: true}).
 *
 * Compose (foundation 1.7) only dispatches overscroll when the container can scroll
 * (ScrollingLogic.shouldDispatchOverscroll = canScrollForward || canScrollBackward), so content shorter than its
 * container gets no stretch / glow at all. These helpers add a spring rubber-band for exactly that case:
 * - while the container CAN scroll nothing changes (the platform stretch on 12+ / glow below 12 is shown);
 * - while it CANNOT scroll, a drag at an edge moves the content with growing resistance and a release (or a fling)
 *   springs it back.
 * The connection never reports the drag as consumed, so pull-to-refresh, the bottom-bar auto-collapse and a
 * bottom sheet's own drag still see the same deltas as before. [top] = false leaves the top edge to a parent that
 * already gives feedback there (pull-to-refresh, a bottom sheet that drags down).
 */
class EdgeBounce internal constructor(
    private val state: ScrollableState,
    private val vertical: Boolean,
    private val scope: CoroutineScope,
    private val limitPx: Float,
) : NestedScrollConnection {
    /** Current rubber-band displacement in px (+ = content moved down / right). */
    var offset by mutableFloatStateOf(0f)
        private set
    var top = true
    var bottom = true
    private var job: Job? = null

    private fun idle() = !state.canScrollForward && !state.canScrollBackward
    private fun Offset.main() = if (vertical) y else x
    private fun Velocity.main() = if (vertical) y else x
    private fun vec(v: Float) = if (vertical) Offset(0f, v) else Offset(v, 0f)
    private fun allowed(d: Float) = (d > 0f && top) || (d < 0f && bottom)

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val d = available.main()
        if (offset == 0f || d == 0f || source != NestedScrollSource.UserInput) return Offset.Zero
        // finger reversed while stretched: take the band back first (1:1)
        if ((offset > 0f && d < 0f) || (offset < 0f && d > 0f)) {
            job?.cancel()
            val next = if (offset > 0f) maxOf(0f, offset + d) else minOf(0f, offset + d)
            val used = next - offset
            offset = next
            return vec(used)
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        val d = available.main()
        if (d == 0f || source != NestedScrollSource.UserInput || !idle() || !allowed(d)) return Offset.Zero
        job?.cancel()
        val resist = (1f - abs(offset) / limitPx).coerceIn(0.05f, 1f) * 0.5f
        offset = (offset + d * resist).coerceIn(-limitPx, limitPx)
        return Offset.Zero // observed, not consumed (see above)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val v = available.main()
        // release: spring back; a fling outwards on short content gives a small bounce of its own
        val kick = if (idle() && allowed(v) && abs(v) > 150f) (v * 0.35f).coerceIn(-2600f, 2600f) else 0f
        if (offset != 0f || kick != 0f) release(kick)
        return Velocity.Zero
    }

    private fun release(velocity: Float) {
        job?.cancel()
        job = scope.launch {
            animate(offset, 0f, velocity, spring(dampingRatio = 0.82f, stiffness = 420f)) { value, _ ->
                offset = value.coerceIn(-limitPx, limitPx)
            }
        }
    }
}

@Composable
fun rememberEdgeBounce(state: ScrollableState, orientation: Orientation = Orientation.Vertical, top: Boolean = true, bottom: Boolean = true): EdgeBounce {
    val scope = rememberCoroutineScope()
    val limit = with(LocalDensity.current) { 140.dp.toPx() }
    val b = remember(state, orientation, scope, limit) { EdgeBounce(state, orientation == Orientation.Vertical, scope, limit) }
    b.top = top; b.bottom = bottom
    return b
}

/**
 * Drop-in for verticalScroll(state) / horizontalScroll(state): same scrolling, plus a spring bounce at both edges
 * when the content fits. The band moves the content inside the viewport (the scroll's own touch handling stays put).
 */
@Composable
fun Modifier.bounceScroll(state: ScrollState, orientation: Orientation = Orientation.Vertical, top: Boolean = true, bottom: Boolean = true): Modifier {
    val b = rememberEdgeBounce(state, orientation, top, bottom)
    val scrolled = if (orientation == Orientation.Vertical) nestedScroll(b).verticalScroll(state) else nestedScroll(b).horizontalScroll(state)
    return scrolled.graphicsLayer { if (orientation == Orientation.Vertical) translationY = b.offset else translationX = b.offset }
}

/** For LazyColumn / LazyVerticalGrid: put on the container's modifier with its state. Bounces when the items fit. */
@Composable
fun Modifier.edgeBounce(state: ScrollableState, top: Boolean = true, bottom: Boolean = true): Modifier {
    val b = rememberEdgeBounce(state, Orientation.Vertical, top, bottom)
    return nestedScroll(b).graphicsLayer { translationY = b.offset }
}
