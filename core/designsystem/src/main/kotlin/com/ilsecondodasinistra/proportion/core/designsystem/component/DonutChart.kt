@file:Suppress("MatchingDeclarationName") // Hosts DonutSlice beside the DonutChart it describes.

package com.ilsecondodasinistra.proportion.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ilsecondodasinistra.proportion.core.designsystem.theme.ProPortionMotion

/** One wedge of a [DonutChart]: how big a share it takes, what colour it draws in, and its label. */
data class DonutSlice(val value: Int, val color: Color, val label: String)

private const val FULL_TURN = 360f
private const val START_ANGLE = -90f

/** Proportional sweeps for one turn of the circle. Empty or all-zero input draws nothing. */
fun sweepAngles(values: List<Int>): List<Float> {
    val total = values.sum()
    if (total <= 0) return values.map { 0f }
    return values.map { it * FULL_TURN / total }
}

/**
 * A donut chart with a labelled centre, used on the Home dashboard for recipes per course. It
 * knows nothing about recipes or courses: callers hand it plain slices, keeping this component
 * reusable wherever a proportional breakdown needs a centre label.
 *
 * The donut animates from nothing on first composition, which is the one place in the app where
 * motion carries meaning: the library filling up.
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    centreLabel: String,
    centreCaption: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 160.dp,
    thickness: Dp = 22.dp,
) {
    // The target has to CHANGE for the animation to run: animateFloatAsState seeds its animatable
    // with the target it is first given, so a constant 1f would sit at full sweep from frame one
    // and the arcs would never draw on.
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }

    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(
            durationMillis = ProPortionMotion.CHART_DRAW_MILLIS,
            easing = ProPortionMotion.Emphasised,
        ),
        label = "donut",
    )
    val sweeps = sweepAngles(slices.map { it.value })

    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(diameter)) {
            var start = START_ANGLE
            sweeps.forEachIndexed { index, sweep ->
                drawArc(
                    color = slices[index].color,
                    startAngle = start,
                    sweepAngle = sweep * progress,
                    useCenter = false,
                    style = Stroke(width = thickness.toPx()),
                )
                start += sweep
            }
        }
        Box(contentAlignment = Alignment.Center) {
            Text(text = centreLabel, style = MaterialTheme.typography.headlineMedium)
        }
        Text(
            text = centreCaption,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
