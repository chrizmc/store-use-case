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
    private var liveUpdatesStarted = false

    // Bumped on every load() call; a fetch only applies its result if it's still the
    // most recently requested one. Without this, overlapping loads (e.g. the screen's
    // own entry reload racing an SSE-triggered reload) could let a slower, older
    // response overwrite a newer one and leave the list looking stale/incomplete.
    private var loadRequestId = 0

    fun load() {
        val requestId = ++loadRequestId
        viewModelScope.launch {
            loading = true
            try {
                val result = repository.getOrders(Constants.STORE_ID)
                if (requestId == loadRequestId) orders = result
            } finally {
                if (requestId == loadRequestId) loading = false
            }
        }
    }

    // Starts listening for live order/order-item changes pushed from the backend. Only
    // opens one connection per ViewModel instance (which is retained across bottom-nav
    // tab switches) - reopening it on every re-entry caused reconnect churn that could
    // drop an event during the gap between the old connection closing and the new one
    // being established.
    fun startLiveUpdates() {
        if (liveUpdatesStarted) return
        liveUpdatesStarted = true
        eventsClient.start()
    }

    override fun onCleared() {
        eventsClient.stop()
        super.onCleared()
    }
}
