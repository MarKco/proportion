package com.ilsecondodasinistra.proportion.core.designsystem.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Only the angle maths is worth asserting here; the drawing itself is verified on the device
 * once the dashboard card that hosts it exists.
 */
class DonutChartTest {

    @Test
    fun `equal values split the circle evenly`() {
        assertThat(sweepAngles(listOf(1, 1, 1, 1)))
            .containsExactly(90f, 90f, 90f, 90f).inOrder()
    }

    @Test
    fun `angles are proportional to the values`() {
        val angles = sweepAngles(listOf(3, 1))

        assertThat(angles[0]).isWithin(0.01f).of(270f)
        assertThat(angles[1]).isWithin(0.01f).of(90f)
    }

    @Test
    fun `no values means no arcs rather than a division by zero`() {
        assertThat(sweepAngles(emptyList())).isEmpty()
        assertThat(sweepAngles(listOf(0, 0))).containsExactly(0f, 0f).inOrder()
    }
}
