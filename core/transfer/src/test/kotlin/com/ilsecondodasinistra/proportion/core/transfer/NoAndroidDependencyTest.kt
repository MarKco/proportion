package com.ilsecondodasinistra.proportion.core.transfer

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/** The wire format must be testable and reusable without an Android runtime. */
class NoAndroidDependencyTest {

    @Test
    fun `transfer sources never import android or androidx`() {
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
