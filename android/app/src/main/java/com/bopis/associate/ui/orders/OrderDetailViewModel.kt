package com.bopis.associate.ui.orders

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bopis.associate.data.BopisRepository
import com.bopis.associate.data.remote.OrderDetail
import com.bopis.associate.data.remote.Shelf
import kotlinx.coroutines.launch

class OrderDetailViewModel(private val repository: BopisRepository, private val orderId: String) : ViewModel() {
    var order by mutableStateOf<OrderDetail?>(null)
        private set

    // Backs the manual "pick a shelf" fallback for scanning (e.g. no usable emulator camera).
    var shelves by mutableStateOf<List<Shelf>>(emptyList())
        private set

    fun load() {
        viewModelScope.launch {
            val loaded = repository.getOrder(orderId)
            order = loaded
            shelves = repository.getShelves(loaded.store_id)
        }
    }
}
