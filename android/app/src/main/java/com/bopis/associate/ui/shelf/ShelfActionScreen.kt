package com.bopis.associate.ui.shelf

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ShelfActionScreen(viewModel: ShelfActionViewModel, onDone: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.load() }
    val shelf = viewModel.shelf
    val substitute = viewModel.suggestedSubstitute

    Scaffold(topBar = { TopAppBar(title = { Text("Shelf") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (shelf == null) {
                Text("Loading shelf...")
                return@Column
            }

            Card(modifier = Modifier.padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(shelf.product_name, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                    Text("Aisle: ${shelf.aisle ?: "-"} \u00b7 Qty: ${shelf.qty} \u00b7 Status: ${shelf.inventory_status}")
                }
            }

            Button(onClick = { viewModel.pickedUp(); onDone() }, modifier = Modifier.fillMaxWidth()) {
                Text("Picked Up")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { viewModel.reportStatus("low") }) { Text("Is Nearly Empty") }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { viewModel.reportStatus("empty") }) { Text("Is Empty") }

            if (substitute != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Suggested substitute (customer opted in):")
                        Text(substitute.name, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        Button(onClick = onDone, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Acknowledge")
                        }
                    }
                }
            } else if (viewModel.shelf?.inventory_status == "empty") {
                Spacer(modifier = Modifier.height(16.dp))
                Text("No substitute available or customer opted out.")
            }
        }
    }
}
