package com.unclebike.meshride.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.unclebike.meshride.R
import com.unclebike.meshride.data.ConnectionState
import com.unclebike.meshride.ui.MeshRideUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatusScreen(
    uiState: MeshRideUiState,
    onNavigateToPairing: () -> Unit,
    onNavigateToMessages: () -> Unit,
    onDisconnect: () -> Unit,
    onSendQuickMessage: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text(text = stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(12.dp))

        // Connection Status
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusColor = when (uiState.connectionState) {
                        ConnectionState.CONNECTED -> Color(0xFF4CAF50)
                        ConnectionState.CONNECTING, ConnectionState.RECONNECTING, ConnectionState.SCANNING -> Color(0xFFFF9800)
                        ConnectionState.DISCONNECTED -> Color(0xFF9E9E9E)
                    }
                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(statusColor))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (uiState.connectionState) {
                            ConnectionState.CONNECTED -> stringResource(R.string.connected)
                            ConnectionState.CONNECTING -> stringResource(R.string.connecting_ellipsis)
                            ConnectionState.SCANNING -> stringResource(R.string.scanning_ellipsis)
                            ConnectionState.RECONNECTING -> stringResource(R.string.reconnecting_ellipsis)
                            ConnectionState.DISCONNECTED -> stringResource(R.string.disconnected)
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (uiState.pairedDeviceName != null) {
                    Text(text = "${stringResource(R.string.paired_device)}: ${uiState.pairedDeviceName}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
                Text(text = "${stringResource(R.string.nodes_on_mesh)}: ${uiState.nodeCount}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (uiState.connectionState == ConnectionState.DISCONNECTED) {
                        Button(onClick = onNavigateToPairing) { Text(stringResource(R.string.connect)) }
                    } else {
                        OutlinedButton(onClick = onDisconnect) { Text(stringResource(R.string.disconnect)) }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Quick Messages
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = stringResource(R.string.quick_messages), style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = onNavigateToMessages) { Text("Edit") }
                }
                uiState.quickMessages.forEach { msg ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = msg.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Button(onClick = { onSendQuickMessage(msg.slot) }, enabled = uiState.connectionState == ConnectionState.CONNECTED) { Text("Send") }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Recent Messages
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = stringResource(R.string.last_message), style = MaterialTheme.typography.titleMedium)
                if (uiState.messages.isEmpty()) {
                    Text(text = stringResource(R.string.no_messages), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                } else {
                    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    uiState.messages.take(5).forEach { msg ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(text = "[${timeFormat.format(Date(msg.timestamp))}]", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "${msg.senderShortName}: ${msg.messageText}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Node List
        if (uiState.nodes.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = "${stringResource(R.string.nodes_on_mesh)} (${uiState.nodes.size})", style = MaterialTheme.typography.titleMedium)
                    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    uiState.nodes.forEach { node ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = node.longName, style = MaterialTheme.typography.bodySmall)
                            Text(text = timeFormat.format(Date(node.lastHeard)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }
}
