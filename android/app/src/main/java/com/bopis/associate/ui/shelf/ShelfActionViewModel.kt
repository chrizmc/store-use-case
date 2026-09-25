package com.bopis.associate.ui.shelf

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bopis.associate.data.ActionResult
import com.bopis.associate.data.BopisRepository
import com.bopis.associate.data.remote.Shelf
import com.bopis.associate.data.remote.SubstituteCandidate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// orderId/itemId are null when the associate scans a shelf outside of an order context
// (ad-hoc shelf check); when present, "Is Empty" also flags the order item so the
// suggest_substitute_on_empty rule (keyed on order_items changes) can fire.
class ShelfActionViewModel(
    private val repository: BopisRepository,
    private val qrCode: String,
    private val orderId: String?,
    private val itemId: String?,
) : ViewModel() {
    var shelf by mutableStateOf<Shelf?>(null)
        private set
    var lastResult by mutableStateOf<ActionResult?>(null)
        private set
    var suggestedSubstitute by mutableStateOf<SubstituteCandidate?>(null)
        private set

    fun load() {
        viewModelScope.launch {
            shelf = repository.getShelf(qrCode)
        }
    }

    fun pickedUp() {
        viewModelScope.launch {
            if (orderId != null && itemId != null) {
                lastResult = repository.pickItem(orderId, itemId)
            }
        }
    }

    fun reportStatus(status: String) {
        viewModelScope.launch {
            lastResult = repository.reportShelf(qrCode, status)
            load()

            if (status == "empty" && orderId != null && itemId != null) {
                repository.markUnavailable(orderId, itemId)
                suggestedSubstitute = pollForSubstitute(orderId, itemId)
            }
        }
    }

    // The rule engine resolves the substitute asynchronously (it reacts to the order_items
    // NOTIFY event, then checks the customer's opt-in and the substitute's own stock via a
    // Neo4j query before writing substituted_with_product_id) - it isn't guaranteed to have
    // finished by the time our own request completes. Poll briefly rather than giving up
    // after a single check, which previously caused a false "no substitute" result.
    private suspend fun pollForSubstitute(orderId: String, itemId: String): SubstituteCandidate? {
        repeat(6) { attempt ->
            val order = repository.getOrder(orderId)
            val substituteId = order.items.firstOrNull { it.id == itemId }?.substituted_with_product_id
            if (substituteId != null) {
                return repository.getSubstitutes(orderId, itemId)
                    .firstOrNull { it.product_id == substituteId }
            }
            if (attempt < 5) delay(400)
        }
        return null
    }
}
