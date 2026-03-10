package com.unclebike.meshride.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.unclebike.meshride.R
import com.unclebike.meshride.ui.MeshRideUiState
import com.unclebike.meshride.ui.ScannedDevice

@Composable
fun DevicePairingScreen(
    uiState: MeshRideUiState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onSelectDevice: (ScannedDevice) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(R.string.scan_devices), style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onBack) { Text("Back") }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (uiState.isScanning) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                Text(text = stringResource(R.string.scanning_ellipsis), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onStopScan) { Text(stringResource(R.string.stop_scan)) }
            }
        } else {
            Button(onClick = onStartScan) { Text(stringResource(R.string.scan_devices)) }
        }
        Spacer(modifier = Modifier.height(12.dp))
        uiState.scanResults.forEach { device ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onSelectDevice(device) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(text = device.name, style = MaterialTheme.typography.titleMedium)
                        Text(text = device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    Text(text = "${device.rssi} dBm", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (uiState.scanResults.isEmpty() && !uiState.isScanning) {
            Text(text = "No devices found. Make sure your Meshtastic radio is powered on and in range.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
