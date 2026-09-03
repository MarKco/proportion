package com.ilsecondodasinistra.proportion.core.ui

import com.google.common.truth.Truth.assertThat
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class IngredientResourceConsistencyTest {

    @Test
    fun `every seeded ingredient key has a matching string resource in every supported language`() {
        val keys = seededKeys()

        assertThat(keys.size).isAtLeast(400)
        assertThat(keys.size).isAtMost(600)

        val englishNames = stringNames(File("src/main/res/values/strings.xml"))
        val italianNames = stringNames(File("src/main/res/values-it/strings.xml"))

        keys.forEach { key ->
            assertThat(englishNames).contains("ingredient_$key")
            assertThat(italianNames).contains("ingredient_$key")
        }
    }

    @Test
    fun `seeded ingredient keys are unique`() {
        val keys = seededKeys()

        assertThat(keys).containsNoDuplicates()
    }

    private fun seededKeys(): List<String> {
        val jsonFile = File("../database/src/main/assets/ingredients.json")
        val parsed = Json.parseToJsonElement(jsonFile.readText()) as JsonArray
        return parsed.map { it.jsonObject.getValue("key").jsonPrimitive.content }
    }

    private fun stringNames(file: File): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("name").nodeValue }.toSet()
    }
}
