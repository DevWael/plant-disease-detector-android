package com.bbioon.plantdisease.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bbioon.plantdisease.R
import com.bbioon.plantdisease.data.model.ScanRecord
import com.bbioon.plantdisease.data.remote.ThermalPrinterManager
import com.bbioon.plantdisease.ui.theme.*
import com.bbioon.plantdisease.util.PrintReceiptBuilder
import kotlinx.coroutines.launch

private enum class PrintState {
    SCANNING, SELECT_DEVICE, CONNECTING, PRINTING, SUCCESS, FAILED, NO_PERMISSION
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterBottomSheet(
    scan: ScanRecord,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val printer = remember { ThermalPrinterManager(context) }

    var printState by remember { mutableStateOf(PrintState.SCANNING) }
    var allDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var errorMessage by remember { mutableStateOf("") }

    // Permission handling
    val hasPermission = remember { mutableStateOf(hasBluetoothPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermission.value = results.values.all { it }
        if (hasPermission.value) {
            printState = PrintState.SCANNING
            startScan(scope, printer, onDevices = { allDevices = it }, onDone = { printState = PrintState.SELECT_DEVICE })
        } else {
            printState = PrintState.NO_PERMISSION
        }
    }

    // Load devices on first launch
    LaunchedEffect(Unit) {
        if (hasPermission.value) {
            startScan(scope, printer, onDevices = { allDevices = it }, onDone = { printState = PrintState.SELECT_DEVICE })
        } else {
            requestBluetoothPermissions(permissionLauncher)
        }
    }

    // Clean up on dismiss
    DisposableEffect(Unit) {
        onDispose { printer.disconnect() }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Title
            Text(
                text = stringResource(R.string.print_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Spacer(Modifier.height(16.dp))

            when (printState) {
                PrintState.NO_PERMISSION -> {
                    Icon(
                        Icons.Default.BluetoothDisabled,
                        contentDescription = null,
                        tint = Warning,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.print_bluetooth_required),
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    ) {
                        Text(stringResource(R.string.print_open_settings))
                    }
                }

                PrintState.SCANNING -> {
                    CircularProgressIndicator(
                        color = Primary,
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 3.dp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.print_scanning),
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                    )
                }

                PrintState.SELECT_DEVICE -> {
                    if (allDevices.isEmpty()) {
                        Icon(
                            Icons.Default.PrintDisabled,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.print_no_paired),
                            color = TextMuted,
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    printState = PrintState.SCANNING
                                    startScan(scope, printer, onDevices = { allDevices = it }, onDone = { printState = PrintState.SELECT_DEVICE })
                                },
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(stringResource(R.string.print_retry))
                            }
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                },
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(stringResource(R.string.print_open_settings))
                            }
                        }
                    } else {
                        Text(
                            stringResource(R.string.print_select_printer),
                            color = TextSecondary,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            allDevices.forEach { device ->
                                DeviceRow(device) {
                                    printState = PrintState.CONNECTING
                                    scope.launch {
                                        val connectResult = printer.connect(device)
                                        if (connectResult.isSuccess) {
                                            printState = PrintState.PRINTING
                                            try {
                                                PrintReceiptBuilder.printScanReceipt(printer, scan, context)
                                                printState = PrintState.SUCCESS
                                            } catch (e: Exception) {
                                                errorMessage = e.message ?: "Unknown error"
                                                printState = PrintState.FAILED
                                            }
                                        } else {
                                            errorMessage = connectResult.exceptionOrNull()?.message ?: "Connection failed"
                                            printState = PrintState.FAILED
                                        }
                                        printer.disconnect()
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                printState = PrintState.SCANNING
                                startScan(scope, printer, onDevices = { allDevices = it }, onDone = { printState = PrintState.SELECT_DEVICE })
                            },
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.print_rescan), color = Primary)
                        }
                    }
                }

                PrintState.CONNECTING -> {
                    CircularProgressIndicator(
                        color = Primary,
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 3.dp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.print_connecting),
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                    )
                }

                PrintState.PRINTING -> {
                    CircularProgressIndicator(
                        color = Primary,
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 3.dp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.print_printing),
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                    )
                }

                PrintState.SUCCESS -> {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Healthy,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.print_success),
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    ) {
                        Text(stringResource(R.string.ok))
                    }
                }

                PrintState.FAILED -> {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = Danger,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.print_failed),
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                    )
                    if (errorMessage.isNotBlank()) {
                        Text(
                            errorMessage,
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { printState = PrintState.SELECT_DEVICE },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    ) {
                        Text(stringResource(R.string.print_retry))
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun DeviceRow(device: BluetoothDevice, onClick: () -> Unit) {
    val name = try { device.name ?: "Unknown Device" } catch (_: SecurityException) { "Unknown Device" }
    val address = device.address ?: ""

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(InputBg)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.Print,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(name, color = TextPrimary, fontWeight = FontWeight.Medium)
                Text(address, fontSize = 12.sp, color = TextMuted)
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextMuted,
            )
        }
    }
}

private fun hasBluetoothPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
}

private fun requestBluetoothPermissions(
    launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        launcher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
            )
        )
    } else {
        launcher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        )
    }
}

@SuppressLint("MissingPermission")
private fun startScan(
    scope: kotlinx.coroutines.CoroutineScope,
    printer: ThermalPrinterManager,
    onDevices: (List<BluetoothDevice>) -> Unit,
    onDone: () -> Unit,
) {
    scope.launch {
        try {
            if (!printer.isBluetoothEnabled()) {
                onDevices(emptyList())
                onDone()
                return@launch
            }

            // Start with paired devices
            val paired = printer.getPairedDevices()
            val seenAddresses = paired.map { it.address }.toMutableSet()
            onDevices(paired)

            // Also scan for BLE devices (toy/mini printers often don't pair classically)
            val bleDevices = printer.scanBleDevices(durationMs = 6000L)
            val newBle = bleDevices.filter { it.address !in seenAddresses }

            onDevices(paired + newBle)
        } catch (_: SecurityException) {
            onDevices(emptyList())
        }
        onDone()
    }
}
