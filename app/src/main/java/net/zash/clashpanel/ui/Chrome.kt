package net.zash.clashpanel.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.zash.clashpanel.MainViewModel
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Frosted page header (HarmonyOS components/GlassHeader.ets): overlays the scrolling content and extends under the
 * status bar; compact (about half the old height). Reports its height so the page can pad its content.
 */
@Composable
fun GlassHeader(modifier: Modifier = Modifier, hPad: Dp = 12.dp, onHeight: (Dp) -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    val density = LocalDensity.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(
        modifier.fillMaxWidth().glass(GlassKind.Top, RectangleShape)
            .onSizeChanged { onHeight(with(density) { it.height.toDp() }) },
    ) {
        Column(Modifier.fillMaxWidth().padding(start = hPad, end = hPad, top = top + 4.dp, bottom = 5.dp), content = content)
        HorizontalDivider(color = LocalPal.current.outlineVariant)
    }
}

/** Initial header height guess (the real value comes from onSizeChanged). */
@Composable
fun headerGuess(rows: Int): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp + (rows * 34).dp + ((rows - 1) * 6).dp

/**
 * Auto-collapsing bottom bar (HarmonyOS Store.navScroll): only finger drags / flings count (programmatic scrolls
 * don't dispatch nested scroll); scrolling down collapses after 24 dp, scrolling back expands after 18 dp,
 * an overscroll at the top always expands.
 */
class NavScrollConnection(private val vm: MainViewModel, private val collapsePx: Float, private val expandPx: Float) : NestedScrollConnection {
    private var acc = 0f
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (!vm.autoHideNav) return Offset.Zero
        val d = -consumed.y // > 0: content moves up (scrolling down)
        if (d == 0f) {
            if (available.y > 0f) { acc = 0f; vm.expandNav() }
            return Offset.Zero
        }
        if ((acc > 0 && d < 0) || (acc < 0 && d > 0)) acc = 0f
        acc += d
        if (acc > collapsePx) { acc = 0f; vm.collapseNav() } else if (acc < -expandPx) { acc = 0f; vm.expandNav() }
        return Offset.Zero
    }
}

@Composable
fun rememberNavScroll(vm: MainViewModel): NavScrollConnection {
    val d = LocalDensity.current
    return remember(vm, d) { with(d) { NavScrollConnection(vm, 24.dp.toPx(), 18.dp.toPx()) } }
}

/** Minimum time the refresh indicator stays visible, so it doesn't just flash. */
private const val MIN_SHOW_MS = 400L

/**
 * Pull to refresh (HarmonyOS 1.1.9): accent-colored indicator drawn below the frosted header (not hidden by it),
 * stays until the requests finish (success or failure); failures toast (MainViewModel.pullRefresh).
 * [page] null = no pull to refresh (settings). Content must be a scrollable container so short content can still be pulled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageFrame(vm: MainViewModel, page: String?, topInset: Dp, content: @Composable () -> Unit) {
    val nav = rememberNavScroll(vm)
    if (page == null) {
        Box(Modifier.fillMaxSize().nestedScroll(nav)) { content() }
        return
    }
    val p = LocalPal.current
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            if (!refreshing) scope.launch {
                refreshing = true
                val t0 = System.currentTimeMillis()
                try { vm.pullRefresh(page) } finally {
                    delay((MIN_SHOW_MS - (System.currentTimeMillis() - t0)).coerceAtLeast(0))
                    refreshing = false
                }
            }
        },
        modifier = Modifier.fillMaxSize().nestedScroll(nav),
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state, isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = topInset),
                containerColor = p.solidCard, color = p.accent,
            )
        },
    ) { content() }
}

/** Empty / loading state that can still be pulled (verticalScroll so the pull gesture reaches the refresh box). */
@Composable
fun ScrollableEmpty(top: Dp, bottom: Dp, text: String) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, top = top, bottom = bottom)) {
        EmptyCard(text)
    }
}

/**
 * Gravity sensor for "follow gravity" light: registered at a low ~100 ms rate only while enabled and the activity
 * is started; light direction is smoothed and quantized to 6° (HarmonyOS Store.updateTilt / onGravity).
 */
@Composable
fun TiltSensor(enabled: Boolean, current: Int, onDeg: (Int) -> Unit) {
    if (!enabled) return
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val cb by rememberUpdatedState(onDeg)
    val start by rememberUpdatedState(current)
    DisposableEffect(owner) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var smooth = start.toFloat()
        var last = start
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val gx = e.values[0]; val gy = e.values[1]
                // "world up" within the screen plane; lying flat falls back to the default top-left light
                var target = 330f
                if (sqrt(gx * gx + gy * gy) > 1.5f) target = (Math.toDegrees(atan2(gx, gy).toDouble()) - 30).toFloat()
                val diff = (((target - smooth) % 360 + 540) % 360) - 180
                smooth = (smooth + diff * 0.35f + 360) % 360
                val q = ((smooth / 6).roundToInt() * 6) % 360
                if (q != last) { last = q; cb(q) }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        var on = false
        fun reg(want: Boolean) {
            if (sm == null || sensor == null || want == on) return
            if (want) sm.registerListener(listener, sensor, 100_000) else sm.unregisterListener(listener)
            on = want
        }
        val obs = LifecycleEventObserver { _, ev ->
            when (ev) {
                Lifecycle.Event.ON_START -> reg(true)
                Lifecycle.Event.ON_STOP -> reg(false)
                else -> {}
            }
        }
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) reg(true)
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); reg(false) }
    }
}

/** Soft separator for consent / legal screens. */
@Composable
fun Bullet(text: String) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        androidx.compose.material3.Text("•", Modifier.width(14.dp), color = p.accent, fontSize = androidx.compose.ui.unit.TextUnit(14f, androidx.compose.ui.unit.TextUnitType.Sp))
        androidx.compose.material3.Text(text, Modifier.weight(1f), color = p.text, fontSize = androidx.compose.ui.unit.TextUnit(14f, androidx.compose.ui.unit.TextUnitType.Sp), lineHeight = androidx.compose.ui.unit.TextUnit(22f, androidx.compose.ui.unit.TextUnitType.Sp))
    }
}

@Suppress("unused")
private fun Modifier.bgDebug() = this.background(androidx.compose.ui.graphics.Color.Transparent)
