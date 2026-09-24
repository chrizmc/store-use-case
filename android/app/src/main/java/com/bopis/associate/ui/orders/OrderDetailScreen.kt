package com.bopis.associate.ui.orders

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bopis.associate.data.remote.OrderItem
import com.bopis.associate.data.remote.Shelf
import com.bopis.associate.ui.scan.rememberQrScanLauncher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    viewModel: OrderDetailViewModel,
    onScanForItem: (itemId: String, qrCode: String) -> Unit,
) {
    LaunchedEffect(Unit) { viewModel.load() }
    val order = viewModel.order

    Scaffold(topBar = { TopAppBar(title = { Text("Order Items") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
        ) {
            items(order?.items ?: emptyList()) { item ->
                OrderItemRow(item, viewModel.shelves, onScanForItem)
            }
        }
    }
}

@Composable
private fun OrderItemRow(
    item: OrderItem,
    shelves: List<Shelf>,
    onScanForItem: (String, String) -> Unit,
) {
    val launchScanner = rememberQrScanLauncher { qrCode ->
        if (qrCode != null) onScanForItem(item.id, qrCode)
    }
    var pickerExpanded by remember { mutableStateOf(false) }
    // Same-product shelf is the expected match; fall back to all shelves if none found.
    val pickerOptions = shelves.filter { it.product_id == item.product_id }.ifEmpty { shelves }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(item.product_name ?: item.product_id)
            Text("Status: ${item.status}" + (item.substituted_with_product_name?.let { " \u2192 $it" } ?: ""))
            if (item.status == "pending") {
                Button(onClick = launchScanner) { Text("Scan shelf") }
                TextButton(onClick = { pickerExpanded = true }) { Text("Pick shelf (no camera)") }
                DropdownMenu(expanded = pickerExpanded, onDismissRequest = { pickerExpanded = false }) {
                    pickerOptions.forEach { shelf ->
                        DropdownMenuItem(
                            text = { Text("${shelf.product_name} \u00b7 aisle ${shelf.aisle ?: "-"}") },
                            onClick = {
                                pickerExpanded = false
                                onScanForItem(item.id, shelf.qr_code)
                            },
                        )
                    }
                }
            }
        }
    }
}

