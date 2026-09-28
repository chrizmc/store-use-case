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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bopis.associate.data.remote.AppNotification
import kotlinx.serialization.json.JsonPrimitive

// The associate both reports issues up to the manager (e.g. low-stock scans) and receives
// suggestions back (substitutes, other-store availability, loyalty nudges) - split those two
// directions into separate tabs instead of one mixed list.
private val TAB_TITLES = listOf("Received", "Sent")

@Composable
fun NotificationsScreen(viewModel: NotificationsViewModel) {
    LaunchedEffect(Unit) { viewModel.load() }
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(topBar = { TopAppBar(title = { Text("Alerts") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                TAB_TITLES.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }
            // "associate" role = alerts received by this app; "manager"/"customer" role =
            // alerts this associate sent out (e.g. reporting a shelf as empty/low, or
            // notifying the customer their order is ready to collect).
            val filtered = viewModel.notifications.filter {
                if (selectedTab == 0) it.role == "associate" else it.role != "associate"
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
            ) {
                items(filtered) { NotificationRow(it) }
            }
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
