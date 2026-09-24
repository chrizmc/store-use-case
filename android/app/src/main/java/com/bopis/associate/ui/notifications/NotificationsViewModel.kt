package com.bopis.associate.ui.notifications

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bopis.associate.data.BopisRepository
import com.bopis.associate.data.remote.AppNotification
import kotlinx.coroutines.launch

class NotificationsViewModel(private val repository: BopisRepository) : ViewModel() {
    var notifications by mutableStateOf<List<AppNotification>>(emptyList())
        private set

    fun load() {
        viewModelScope.launch {
            val result = repository.pull(since = 0, storeId = null)
            notifications = result?.changes?.notifications?.sortedByDescending { it.id } ?: notifications
        }
    }
}
