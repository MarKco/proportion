package com.ilsecondodasinistra.proportion.core.ui

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AndroidIngredientNamerTest {

    private val namer = AndroidIngredientNamer(ApplicationProvider.getApplicationContext())

    @Test
    fun `resolves a known key in the default language`() {
        assertThat(namer.name("flour_00")).isEqualTo("Type 00 flour")
    }

    @Test
    @Config(qualifiers = "it")
    fun `resolves a known key in Italian`() {
        assertThat(namer.name("flour_00")).isEqualTo("Farina 00")
    }

    @Test
    fun `an unknown key fails loudly rather than silently`() {
        assertThrows(IllegalStateException::class.java) { namer.name("no_such_key") }
    }

    private fun assertThrows(expected: Class<out Throwable>, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            if (expected.isInstance(t)) return
            throw t
        }
        throw AssertionError("expected $expected to be thrown")
    }
}
