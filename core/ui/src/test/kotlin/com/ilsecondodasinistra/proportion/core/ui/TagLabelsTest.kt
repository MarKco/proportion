package com.ilsecondodasinistra.proportion.core.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.ilsecondodasinistra.proportion.core.model.MeasureUnit
import com.ilsecondodasinistra.proportion.core.model.Tag
import com.ilsecondodasinistra.proportion.core.model.UnitCategory
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TagLabelsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `every built in tag key resolves to a label`() {
        Tag.BUILT_IN_KEYS.forEach { key ->
            val res = builtInTagLabelRes(key)
            assertThat(res).isNotNull()
            assertThat(context.getString(res!!)).isNotEmpty()
        }
    }

    @Test
    fun `an unknown key has no label so the caller can fall back`() {
        assertThat(builtInTagLabelRes("not_a_real_key")).isNull()
    }

    @Test
    fun `every unit category has a header`() {
        UnitCategory.entries.forEach { category ->
            assertThat(context.getString(unitCategoryLabelRes(category))).isNotEmpty()
        }
    }

    @Test
    fun `the picker offers every unit exactly once`() {
        val offered = unitsByCategory.values.flatten()

        assertThat(offered).containsExactlyElementsIn(MeasureUnit.entries)
    }
}
