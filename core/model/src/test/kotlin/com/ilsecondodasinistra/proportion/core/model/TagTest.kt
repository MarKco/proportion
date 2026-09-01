package com.ilsecondodasinistra.proportion.core.model

import org.junit.Assert.assertThrows
import org.junit.Test

class TagTest {

    @Test
    fun `a built in tag carries a key and no literal name`() {
        Tag(id = "t1", key = "dessert", name = null, isBuiltIn = true)
    }

    @Test
    fun `a user tag carries a literal name and no key`() {
        Tag(id = "t2", key = null, name = "merenda", isBuiltIn = false)
    }

    @Test
    fun `a tag with both a key and a name is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            Tag(id = "t3", key = "dessert", name = "merenda", isBuiltIn = true)
        }
    }

    @Test
    fun `a tag with neither a key nor a name is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            Tag(id = "t4", key = null, name = null, isBuiltIn = false)
        }
    }
}
