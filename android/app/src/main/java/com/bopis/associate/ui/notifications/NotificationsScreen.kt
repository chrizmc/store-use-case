package com.bopis.associate.ui.notifications

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bopis.associate.data.remote.AppNotification
import kotlinx.serialization.json.JsonPrimitive

@Composable
fun NotificationsScreen(viewModel: NotificationsViewModel) {
    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(topBar = { TopAppBar(title = { Text("Notifications") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
        ) {
            items(viewModel.notifications) { NotificationRow(it) }
        }
    }
}

@Composable
private fun NotificationRow(notification: AppNotification) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            AssistChip(onClick = {}, label = { Text(notification.role) })
            Text(notification.type)
            Text(notification.payload.entries.joinToString(", ") { (key, value) ->
                val display = (value as? JsonPrimitive)?.content ?: value.toString()
                "$key=$display"
            })
        }
    }
}
