package com.bopis.associate.ui.agent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bopis.associate.ui.assistant.AssistantChatContent
import com.bopis.associate.ui.assistant.AssistantViewModel

// Always-visible corner widget: idle bubble (tap opens the chat window), a Yes/No prompt
// card when a rule fires something that implies the associate should physically move, and a
// mock navigation dialog on "Yes".
@Composable
fun ProactiveAgentOverlay(viewModel: ProactiveAgentViewModel, assistantViewModel: AssistantViewModel) {
    val prompt = viewModel.prompt
    val navigationTarget = viewModel.navigationTarget
    var showChat by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Column(
            modifier = Modifier.align(Alignment.BottomEnd),
            horizontalAlignment = Alignment.End,
        ) {
            if (prompt != null) {
                AgentPromptCard(prompt = prompt, onYes = viewModel::respondYes, onNo = viewModel::respondNo)
                Spacer(modifier = Modifier.height(8.dp))
            }
            AgentBubble(onClick = { if (prompt == null) showChat = true })
        }
    }

    if (navigationTarget != null) {
        MockNavigationDialog(navigationTarget, onDismiss = viewModel::dismissNavigation)
    }

    if (showChat) {
        ChatDialog(assistantViewModel, onDismiss = { showChat = false })
    }
}

@Composable
private fun AgentBubble(onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(48.dp).clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text("\uD83E\uDD16", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun ChatDialog(viewModel: AssistantViewModel, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.width(300.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Assistant", fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = onDismiss) { Text("Close") }
                }
                Spacer(modifier = Modifier.height(8.dp))
                AssistantChatContent(viewModel)
            }
        }
    }
}

@Composable
private fun AgentPromptCard(prompt: AgentPrompt, onYes: () -> Unit, onNo: () -> Unit) {
    Card(modifier = Modifier.width(260.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Assistant", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(promptQuestion(prompt))
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = onNo) { Text("No") }
                Button(onClick = onYes) { Text("Yes") }
            }
        }
    }
}

@Composable
private fun MockNavigationDialog(target: AgentPrompt, onDismiss: () -> Unit) {
    var started by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(
                modifier = Modifier.padding(20.dp).width(260.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("\uD83D\uDDFA\uFE0F", style = MaterialTheme.typography.displayMedium)
                Spacer(modifier = Modifier.height(8.dp))
                when (target) {
                    is AgentPrompt.GuideToShelf -> {
                        Text("Aisle ${target.aisle}", fontWeight = FontWeight.Bold)
                        Text(target.productName)
                    }
                    is AgentPrompt.NavigateToStore -> {
                        Text(target.storeName, fontWeight = FontWeight.Bold)
                        Text(target.storeAddress)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (started) {
                    Text("Navigating\u2026", color = MaterialTheme.colorScheme.primary)
                } else {
                    Button(onClick = { started = true }) { Text("Start Navigation") }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}
