package net.zash.clashpanel.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Global transition animations (1.3.0, same durations as the HarmonyOS app's common/MotionLogic.ets):
 * 150-380 ms, decelerating, no bounce. When off - user switch or system animator scale 0 - every
 * duration collapses to 0 and switches happen instantly.
 */
object Motion {
    const val FAST = 150
    const val BASE = 220
    const val TAB = 300
    const val NAV = 380

    /** Effective duration of [base] under the current motion setting. */
    fun ms(base: Int, on: Boolean): Int = if (on) base else 0

    fun fadeEnter(on: Boolean, base: Int = BASE): EnterTransition =
        fadeIn(tween(ms(base, on), easing = FastOutSlowInEasing))

    fun fadeExit(on: Boolean, base: Int = FAST): ExitTransition =
        fadeOut(tween(ms(base, on), easing = FastOutSlowInEasing))

    /** Cards / banners appearing: fade + a slight rise. */
    fun revealEnter(on: Boolean): EnterTransition =
        fadeIn(tween(ms(BASE, on), easing = FastOutSlowInEasing)) +
            slideInVertically(tween(ms(BASE, on), easing = FastOutSlowInEasing)) { it / 6 }
}

/** Whether transition animations are currently on (user switch && system animator scale > 0). */
val LocalMotion = staticCompositionLocalOf { true }
