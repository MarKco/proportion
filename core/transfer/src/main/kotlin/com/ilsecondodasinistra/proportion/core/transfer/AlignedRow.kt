package com.ilsecondodasinistra.proportion.core.transfer

/**
 * Renders one "- name  quantity" row with the quantity column starting at [width], the length of
 * the longest name in the list being rendered.
 *
 * Shared by [PlainTextFormatter] and [ShoppingListFormatter] so the two formatters agree on one
 * alignment convention instead of each reimplementing the same padding.
 */
internal fun alignedRow(name: String, width: Int, quantityText: String): String =
    "- ${name.padEnd(width)}  $quantityText"
