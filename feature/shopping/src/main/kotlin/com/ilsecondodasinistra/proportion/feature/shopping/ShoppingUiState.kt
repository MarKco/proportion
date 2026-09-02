package com.ilsecondodasinistra.proportion.feature.shopping

/** One line of the shopping list, ready to render: no arithmetic or formatting left to do. */
data class ShoppingRow(
    val id: String,
    val name: String,
    val amountText: String,
    val isChecked: Boolean,
    val sourceCount: Int,
)

data class ShoppingUiState(
    val isLoading: Boolean = true,
    val items: List<ShoppingRow> = emptyList(),
    val checkedCount: Int = 0,
    val confirmClearAll: Boolean = false,
) {
    val isEmpty: Boolean get() = !isLoading && items.isEmpty()
}
