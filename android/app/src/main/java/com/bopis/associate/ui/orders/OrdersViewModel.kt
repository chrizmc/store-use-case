package com.bopis.associate.ui.orders

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bopis.associate.Constants
import com.bopis.associate.data.BopisRepository
import com.bopis.associate.data.remote.OrderSummary
import kotlinx.coroutines.launch

class OrdersViewModel(private val repository: BopisRepository) : ViewModel() {
    var orders by mutableStateOf<List<OrderSummary>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set

    fun load() {
        viewModelScope.launch {
            loading = true
            orders = repository.getOrders(Constants.STORE_ID)
            loading = false
        }
    }
}
