package com.bopis.associate.ui.orders

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bopis.associate.data.remote.OrderItem
import com.bopis.associate.data.remote.Shelf
import com.bopis.associate.ui.scan.QrScanScreen
import com.bopis.associate.ui.scan.decodeQrFromUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            items(order?.items ?: emptyList(), key = { it.id }) { item ->
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uploadError by remember { mutableStateOf<String?>(null) }
    var showScanner by remember { mutableStateOf(false) }

    if (showScanner) {
        Dialog(
            onDismissRequest = { showScanner = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            QrScanScreen(
                onDecoded = { qrCode ->
                    showScanner = false
                    onScanForItem(item.id, qrCode)
                },
                onCancel = { showScanner = false },
            )
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val decoded = withContext(Dispatchers.IO) { decodeQrFromUri(context, uri) }
            if (decoded != null) {
                uploadError = null
                onScanForItem(item.id, decoded)
            } else {
                uploadError = "No QR code found in that image."
            }
        }
    }
    var pickerExpanded by remember { mutableStateOf(false) }
    // Same-product shelf is the expected match; fall back to all shelves if none found.
    val pickerOptions = shelves.filter { it.product_id == item.product_id }.ifEmpty { shelves }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(item.product_name ?: item.product_id)
            Text("Status: ${item.status}" + (item.substituted_with_product_name?.let { " \u2192 $it" } ?: ""))
            if (item.status == "pending") {
                Button(onClick = { showScanner = true }) { Text("Scan shelf") }
                TextButton(onClick = { pickImage.launch("image/*") }) { Text("Upload QR photo") }
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
                uploadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

