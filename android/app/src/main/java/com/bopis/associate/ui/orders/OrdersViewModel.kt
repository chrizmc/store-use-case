package com.bopis.associate.ui.orders

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bopis.associate.Constants
import com.bopis.associate.data.BopisRepository
import com.bopis.associate.data.remote.OrderSummary
import com.bopis.associate.sync.OrderEventsClient
import kotlinx.coroutines.launch

class OrdersViewModel(private val repository: BopisRepository) : ViewModel() {
    var orders by mutableStateOf<List<OrderSummary>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set

    private val eventsClient = OrderEventsClient(viewModelScope) { load() }

    fun load() {
        viewModelScope.launch {
            loading = true
            orders = repository.getOrders(Constants.STORE_ID)
            loading = false
        }
    }

    // Starts listening for live order/order-item changes pushed from the backend;
    // safe to call repeatedly (e.g. every time the screen re-enters composition).
    fun startLiveUpdates() = eventsClient.start()

    override fun onCleared() {
        eventsClient.stop()
        super.onCleared()
    }
}
