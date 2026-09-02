package com.ilsecondodasinistra.proportion.feature.cook.navigation

import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.domain.scale.AvailableAmount
import com.ilsecondodasinistra.proportion.core.domain.scale.ScaleConstraint
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import java.util.Base64
import org.junit.Test

/**
 * [MeasureUnit] is a plain `enum class` with no `@Serializable` of its own — these tests prove
 * kotlinx.serialization's default enum handling actually carries it through the route encoding,
 * for both places it shows up in [ScaleConstraint]: directly on `ByIngredient`, and nested inside
 * `AvailableAmount` on `ByAvailability`.
 */
class CookRouteKeyTest {

    @Test
    fun `a ByIngredient constraint round-trips through the route encoding, its unit included`() {
        val original = ScaleConstraint.ByIngredient(lineId = "line-uova", qty = 3.0, unit = MeasureUnit.EGG)

        val decoded = original.encodeForRoute().decodeConstraint()

        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun `a ByAvailability constraint round-trips, every nested unit included`() {
        val original = ScaleConstraint.ByAvailability(
            have = listOf(
                AvailableAmount(lineId = "line-farina", qty = 400.0, unit = MeasureUnit.GRAM),
                AvailableAmount(lineId = "line-uova", qty = 3.0, unit = MeasureUnit.EGG),
            ),
        )

        val decoded = original.encodeForRoute().decodeConstraint()

        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun `decodeConstraint returns null rather than throwing for a string that is not valid base64`() {
        assertThat("not valid base64 or json!!".decodeConstraint()).isNull()
    }

    @Test
    fun `decodeConstraint returns null rather than throwing for base64 that is not a constraint`() {
        val notAConstraint = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"not":"a constraint"}""".toByteArray())

        assertThat(notAConstraint.decodeConstraint()).isNull()
    }
}
