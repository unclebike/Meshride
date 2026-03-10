package com.unclebike.meshride.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.unclebike.meshride.ui.screens.DevicePairingScreen
import com.unclebike.meshride.ui.screens.QuickMessagesScreen
import com.unclebike.meshride.ui.screens.StatusScreen
import com.unclebike.meshride.ui.theme.MeshRideTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SetupActivity : ComponentActivity() {

    private val viewModel: MeshRideViewModel by viewModels()

    private val blePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions handled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestBlePermissions()
        setContent {
            MeshRideTheme {
                val uiState by viewModel.uiState.collectAsState()
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
                    NavHost(
                        navController = navController,
                        startDestination = "status",
                        modifier = Modifier.padding(paddingValues),
                    ) {
                        composable("status") {
                            StatusScreen(
                                uiState = uiState,
                                onNavigateToPairing = { navController.navigate("pairing") },
                                onNavigateToMessages = { navController.navigate("messages") },
                                onDisconnect = { viewModel.disconnect() },
                                onSendQuickMessage = { viewModel.sendQuickMessage(it) },
                            )
                        }
                        composable("pairing") {
                            DevicePairingScreen(
                                uiState = uiState,
                                onStartScan = { viewModel.startScan() },
                                onStopScan = { viewModel.stopScan() },
                                onSelectDevice = { viewModel.connectToDevice(it); navController.popBackStack() },
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable("messages") {
                            QuickMessagesScreen(
                                uiState = uiState,
                                onUpdateMessage = { slot, label, text -> viewModel.updateQuickMessage(slot, label, text) },
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun requestBlePermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        if (permissions.isNotEmpty()) {
            blePermissionLauncher.launch(permissions.toTypedArray())
        }
    }
}
