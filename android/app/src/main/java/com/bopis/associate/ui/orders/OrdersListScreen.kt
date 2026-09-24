package com.bopis.associate.ui.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bopis.associate.data.remote.OrderSummary

@Composable
fun OrdersListScreen(viewModel: OrdersViewModel, onOrderClick: (String) -> Unit) {
    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Open Orders") },
                // Compose doesn't re-run LaunchedEffect(Unit) when navigating back to this
                // screen, so orders created elsewhere (e.g. the Customer Order Simulator)
                // need an explicit way to reload without restarting the app.
                actions = { TextButton(onClick = { viewModel.load() }) { Text("Refresh") } },
            )
        }
    ) { padding ->
        if (viewModel.loading && viewModel.orders.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                items(viewModel.orders) { order -> OrderRow(order, onOrderClick) }
            }
        }
    }
}

@Composable
private fun OrderRow(order: OrderSummary, onClick: (String) -> Unit) {
    Card(onClick = { onClick(order.id) }, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(order.customer_name)
            Text("${order.pending_count} item(s) pending \u00b7 ${order.status}")
        }
    }
}
