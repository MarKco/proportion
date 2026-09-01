package com.ilsecondodasinistra.proportion.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * Motion is used where it carries meaning: numbers counting to their new value when the scale
 * changes, donut arcs drawing on the dashboard, a warning badge arriving. Durations live here so
 * those moments stay consistent.
 */
object ProPortionMotion {
    const val QUANTITY_COUNT_MILLIS = 420
    const val CHART_DRAW_MILLIS = 700
    const val BADGE_ENTER_MILLIS = 220

    val Emphasised: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0.2f, 1f)
}
