package com.bopis.associate.ui.assistant

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun AssistantScreen(viewModel: AssistantViewModel) {
    val context = LocalContext.current
    var pendingRecord by remember { mutableStateOf(false) }
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pendingRecord) viewModel.startRecording()
        pendingRecord = false
    }

    fun requestRecording() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.startRecording()
        } else {
            pendingRecord = true
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Ask the Assistant") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = viewModel.question,
                onValueChange = { viewModel.question = it },
                label = { Text("Type a question") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Button(onClick = { viewModel.askText() }, enabled = !viewModel.isBusy) {
                    Text("Ask")
                }
                Spacer(modifier = Modifier.height(1.dp))
                Button(
                    onClick = {
                        if (viewModel.isRecording) viewModel.stopRecordingAndAsk() else requestRecording()
                    },
                    enabled = !viewModel.isBusy,
                ) {
                    Text(if (viewModel.isRecording) "Stop && Ask" else "Record")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (viewModel.isBusy) CircularProgressIndicator()
            viewModel.transcript?.let { Text("Heard: \u201c$it\u201d") }
            viewModel.answer?.let { Text(it) }
        }
    }
}

