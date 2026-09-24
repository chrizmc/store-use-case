package com.bopis.associate.ui.orders

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bopis.associate.data.BopisRepository
import com.bopis.associate.data.remote.OrderDetail
import kotlinx.coroutines.launch

class OrderDetailViewModel(private val repository: BopisRepository, private val orderId: String) : ViewModel() {
    var order by mutableStateOf<OrderDetail?>(null)
        private set

    fun load() {
        viewModelScope.launch {
            order = repository.getOrder(orderId)
        }
    }
}
