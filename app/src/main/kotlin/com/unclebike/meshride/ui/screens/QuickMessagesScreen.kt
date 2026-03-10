package com.unclebike.meshride.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.unclebike.meshride.R
import com.unclebike.meshride.ui.MeshRideUiState

@Composable
fun QuickMessagesScreen(
    uiState: MeshRideUiState,
    onUpdateMessage: (slot: Int, label: String, text: String) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(R.string.quick_messages), style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onBack) { Text("Back") }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "Configure quick messages for side button actions during rides.", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(12.dp))
        uiState.quickMessages.forEach { msg ->
            var text by remember(msg) { mutableStateOf(msg.text) }
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = "Button ${msg.slot + 1} (${msg.label})", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { if (it.length <= 32) { text = it; onUpdateMessage(msg.slot, msg.label, it) } },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Message (max 32 chars)") },
                    )
                }
            }
        }
    }
}
