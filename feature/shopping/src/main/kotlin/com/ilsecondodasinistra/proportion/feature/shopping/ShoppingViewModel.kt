package com.ilsecondodasinistra.proportion.feature.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilsecondodasinistra.proportion.core.domain.repository.ShoppingRepository
import com.ilsecondodasinistra.proportion.core.domain.unit.QuantityFormatter
import com.ilsecondodasinistra.proportion.core.model.ShoppingItem
import com.ilsecondodasinistra.proportion.core.transfer.ShoppingListFormatter
import com.ilsecondodasinistra.proportion.core.transfer.ShoppingListStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The single persistent shopping list. Unchecked items sort before checked ones, so what is still
 * needed stays at the top of the list while shopping.
 */
@HiltViewModel
class ShoppingViewModel @Inject constructor(
    private val repository: ShoppingRepository,
    private val formatter: QuantityFormatter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShoppingUiState())
    val uiState: StateFlow<ShoppingUiState> = _uiState.asStateFlow()

    /** Kept alongside the rendered rows so [shareText] can hand the raw items to the formatter. */
    private var currentItems: List<ShoppingItem> = emptyList()

    init {
        viewModelScope.launch {
            repository.observeItems().collect { items ->
                currentItems = items
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        items = items.toRows(),
                        checkedCount = items.count { item -> item.isChecked },
                    )
                }
            }
        }
    }

    fun onCheckedChange(id: String, checked: Boolean) {
        viewModelScope.launch { repository.setChecked(id, checked) }
    }

    fun onClearChecked() {
        viewModelScope.launch { repository.clearChecked() }
    }

    /** Emptying the whole list is destructive, so it asks first. */
    fun onClearAllRequested() {
        _uiState.update { it.copy(confirmClearAll = true) }
    }

    fun onClearAllDismissed() {
        _uiState.update { it.copy(confirmClearAll = false) }
    }

    fun onClearAllConfirmed() {
        viewModelScope.launch {
            repository.clearAll()
            _uiState.update { it.copy(confirmClearAll = false) }
        }
    }

    fun shareText(strings: ShoppingListStrings): String =
        ShoppingListFormatter.format(currentItems, strings, formatter)

    private fun List<ShoppingItem>.toRows(): List<ShoppingRow> =
        sortedBy { item -> if (item.isChecked) 1 else 0 }.map { item ->
            ShoppingRow(
                id = item.id,
                name = item.ingredient.name,
                amountText = item.quantity?.let { formatter.format(it, item.unit).text }.orEmpty(),
                isChecked = item.isChecked,
                sourceCount = item.sourceRecipeIds.size,
            )
        }
}
