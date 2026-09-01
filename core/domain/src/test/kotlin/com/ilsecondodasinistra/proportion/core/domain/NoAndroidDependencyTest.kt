package com.ilsecondodasinistra.proportion.core.domain

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * The scaling domain must stay pure Kotlin: that is what keeps its tests instant and makes the
 * rules reusable outside Android. This is an architectural invariant, not a style preference.
 */
class NoAndroidDependencyTest {

    @Test
    fun `domain sources never import android or androidx`() {
        val offenders = File("src/main/kotlin").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                file.readLines().any { line ->
                    line.startsWith("import android.") || line.startsWith("import androidx.")
                }
            }
            .map { it.path }
            .toList()

        assertThat(offenders).isEmpty()
    }
}
