package com.bbioon.plantdisease.data.remote

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.OutputStream
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Manages Bluetooth connections for cat/toy mini thermal printers (MX10, GB01, GB02, GT01, etc.).
 * Protocol reverse-engineered from rbaron/catprinter (Python) — adapted for Kotlin/Android BLE.
 *
 * Packet format: [0x51, 0x78, CMD, 0x00, LEN_LOW, LEN_HIGH, DATA..., CRC8, 0xFF]
 */
class ThermalPrinterManager(private val context: Context) {

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val PRINT_WIDTH_PX = 384
        private const val BYTES_PER_ROW = PRINT_WIDTH_PX / 8  // 48
        private const val BLE_CHUNK_SIZE = 100
        private const val BLE_CHUNK_DELAY_MS = 20L

        // Known BLE service/characteristic UUIDs
        private val KNOWN_PRINTER_SERVICE_UUIDS = listOf(
            UUID.fromString("0000ae30-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("e7810a71-73ae-499d-8c15-faa9aef0c3f2"),
            UUID.fromString("49535343-fe7d-4ae5-8fa9-9fafd205e455"),
            UUID.fromString("000018f0-0000-1000-8000-00805f9b34fb"),
        )

        private val KNOWN_WRITE_CHAR_UUIDS = listOf(
            UUID.fromString("0000ae01-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("0000ae02-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("bef8d6c9-9c21-4c9e-b632-bd58c1009f9f"),
            UUID.fromString("49535343-8841-43f4-a8d4-ecbe34729bb3"),
            UUID.fromString("00002af1-0000-1000-8000-00805f9b34fb"),
        )

        // CRC8 lookup table (same as catprinter's CHECKSUM_TABLE, polynomial 0x07)
        private val CRC_TABLE = intArrayOf(
            0, 7, 14, 9, 28, 27, 18, 21, 56, 63, 54, 49, 36, 35, 42, 45,
            112, 119, 126, 121, 108, 107, 98, 101, 72, 79, 70, 65, 84, 83, 90, 93,
            224, 231, 238, 233, 252, 251, 242, 245, 216, 223, 214, 209, 196, 195, 202, 205,
            144, 151, 158, 153, 140, 139, 130, 133, 168, 175, 166, 161, 180, 179, 186, 189,
            199, 192, 201, 206, 219, 220, 213, 210, 255, 248, 241, 246, 227, 228, 237, 234,
            183, 176, 185, 190, 171, 172, 165, 162, 143, 136, 129, 134, 147, 148, 157, 154,
            39, 32, 41, 46, 59, 60, 53, 50, 31, 24, 17, 22, 3, 4, 13, 10,
            87, 80, 89, 94, 75, 76, 69, 66, 111, 104, 97, 102, 115, 116, 125, 122,
            137, 142, 135, 128, 149, 146, 155, 156, 177, 182, 191, 184, 173, 170, 163, 164,
            249, 254, 247, 240, 229, 226, 235, 236, 193, 198, 207, 200, 221, 218, 211, 212,
            105, 110, 103, 96, 117, 114, 123, 124, 81, 86, 95, 88, 77, 74, 67, 68,
            25, 30, 23, 16, 5, 2, 11, 12, 33, 38, 47, 40, 61, 58, 51, 52,
            78, 73, 64, 71, 82, 85, 92, 91, 118, 113, 120, 127, 106, 109, 100, 99,
            62, 57, 48, 55, 34, 37, 44, 43, 6, 1, 8, 15, 26, 29, 20, 19,
            174, 169, 160, 167, 178, 181, 188, 187, 150, 145, 152, 159, 138, 141, 132, 131,
            222, 217, 208, 215, 194, 197, 204, 203, 230, 225, 232, 239, 250, 253, 244, 243,
        )

        // Pre-built commands (from catprinter source, Java signed bytes → unsigned)
        private val CMD_GET_DEV_STATE = buildStaticPacket(0xA3.toByte(), byteArrayOf(0x00))
        private val CMD_SET_QUALITY = buildStaticPacket(0xA4.toByte(), byteArrayOf(0x32)) // 200 DPI
        private val CMD_LATTICE_START = buildStaticPacket(
            0xA6.toByte(),
            byteArrayOf(
                0xAA.toByte(), 0x55, 0x17, 0x38, 0x44, 0x5F, 0x5F, 0x5F, 0x44, 0x38, 0x2C
            ),
        )
        private val CMD_LATTICE_END = buildStaticPacket(
            0xA6.toByte(),
            byteArrayOf(
                0xAA.toByte(), 0x55, 0x17, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x17
            ),
        )
        private val CMD_SET_PAPER = buildStaticPacket(0xA1.toByte(), byteArrayOf(0x30, 0x00))
        private val CMD_PRINT_IMG = buildStaticPacket(0xBE.toByte(), byteArrayOf(0x00))
        private val CMD_APPLY_ENERGY = buildStaticPacket(0xBE.toByte(), byteArrayOf(0x01))

        /** Build a full protocol packet: [0x51, 0x78, cmd, 0x00, lenL, lenH, data..., crc, 0xFF] */
        private fun buildStaticPacket(cmd: Byte, data: ByteArray): ByteArray {
            val len = data.size
            val packet = ByteArray(8 + len)
            packet[0] = 0x51
            packet[1] = 0x78
            packet[2] = cmd
            packet[3] = 0x00
            packet[4] = (len and 0xFF).toByte()
            packet[5] = ((len shr 8) and 0xFF).toByte()
            System.arraycopy(data, 0, packet, 6, len)
            packet[6 + len] = crc8(packet, 6, len)
            packet[7 + len] = 0xFF.toByte()
            return packet
        }

        /** CRC8 over a range of bytes. */
        private fun crc8(data: ByteArray, offset: Int, length: Int): Byte {
            var crc = 0
            for (i in offset until offset + length) {
                crc = CRC_TABLE[(crc xor (data[i].toInt() and 0xFF)) and 0xFF]
            }
            return (crc and 0xFF).toByte()
        }
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var gatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var isBleConnected = false
    private var bleWriteDeferred: CompletableDeferred<Boolean>? = null

    private enum class ConnectionType { NONE, CLASSIC, BLE }
    private var connectionType = ConnectionType.NONE

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> =
        bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()

    @SuppressLint("MissingPermission")
    suspend fun scanBleDevices(durationMs: Long = 8000L): List<BluetoothDevice> {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return emptyList()
        val foundDevices = mutableMapOf<String, BluetoothDevice>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = try { device.name } catch (_: SecurityException) { null }
                if (device.address != null && !name.isNullOrBlank() && !foundDevices.containsKey(device.address)) {
                    foundDevices[device.address] = device
                }
            }
        }
        scanner.startScan(callback)
        try { delay(durationMs) } finally {
            try { scanner.stopScan(callback) } catch (_: Exception) {}
        }
        return foundDevices.values.toList()
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): Result<Unit> {
        disconnect()
        val bleResult = connectBle(device)
        if (bleResult.isSuccess) return bleResult
        return connectClassic(device)
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectClassic(device: BluetoothDevice): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
            sock.connect()
            socket = sock
            outputStream = sock.outputStream
            connectionType = ConnectionType.CLASSIC
            Result.success(Unit)
        } catch (e: Exception) {
            try { socket?.close() } catch (_: Exception) {}
            socket = null; outputStream = null
            Result.failure(e)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectBle(device: BluetoothDevice): Result<Unit> {
        return withTimeoutOrNull(15_000L) {
            suspendCancellableCoroutine { cont ->
                val callback = object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(g: BluetoothGatt, status: Int, state: Int) {
                        if (state == BluetoothProfile.STATE_CONNECTED) g.requestMtu(512)
                        else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                            isBleConnected = false
                            if (cont.isActive) cont.resume(Result.failure(Exception("Disconnected")))
                        }
                    }
                    override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) { g.discoverServices() }
                    override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            if (cont.isActive) cont.resume(Result.failure(Exception("Service discovery failed")))
                            return
                        }
                        val wc = findWriteCharacteristic(g)
                        if (wc != null) {
                            writeCharacteristic = wc; gatt = g; isBleConnected = true
                            connectionType = ConnectionType.BLE
                            if (cont.isActive) cont.resume(Result.success(Unit))
                        } else {
                            if (cont.isActive) cont.resume(Result.failure(Exception("No writable characteristic")))
                        }
                    }
                    override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, s: Int) {
                        bleWriteDeferred?.complete(s == BluetoothGatt.GATT_SUCCESS)
                    }
                }
                val gi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                else device.connectGatt(context, false, callback)
                cont.invokeOnCancellation { try { gi.close() } catch (_: Exception) {} }
            }
        } ?: Result.failure(Exception("BLE timeout"))
    }

    private fun findWriteCharacteristic(g: BluetoothGatt): BluetoothGattCharacteristic? {
        for (su in KNOWN_PRINTER_SERVICE_UUIDS) {
            val svc = g.getService(su) ?: continue
            for (cu in KNOWN_WRITE_CHAR_UUIDS) {
                val c = svc.getCharacteristic(cu) ?: continue
                if (c.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) return c
            }
        }
        for (svc in g.services) for (c in svc.characteristics) {
            if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) return c
        }
        for (svc in g.services) for (c in svc.characteristics) {
            if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) return c
        }
        return null
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        when (connectionType) {
            ConnectionType.CLASSIC -> {
                try { outputStream?.close() } catch (_: Exception) {}
                try { socket?.close() } catch (_: Exception) {}
                outputStream = null; socket = null
            }
            ConnectionType.BLE -> {
                try { gatt?.disconnect() } catch (_: Exception) {}
                try { gatt?.close() } catch (_: Exception) {}
                gatt = null; writeCharacteristic = null; isBleConnected = false
            }
            ConnectionType.NONE -> {}
        }
        connectionType = ConnectionType.NONE
    }

    val isConnected: Boolean get() = when (connectionType) {
        ConnectionType.CLASSIC -> socket?.isConnected == true
        ConnectionType.BLE -> isBleConnected
        ConnectionType.NONE -> false
    }

    // ─── Cat Printer Protocol ─────────────────────────

    /** Build a dynamic packet with CRC and 0xFF footer. */
    private fun buildPacket(cmd: Byte, data: ByteArray): ByteArray {
        val len = data.size
        val packet = ByteArray(8 + len)
        packet[0] = 0x51
        packet[1] = 0x78
        packet[2] = cmd
        packet[3] = 0x00
        packet[4] = (len and 0xFF).toByte()
        packet[5] = ((len shr 8) and 0xFF).toByte()
        System.arraycopy(data, 0, packet, 6, len)
        packet[6 + len] = crc8(packet, 6, len)
        packet[7 + len] = 0xFF.toByte()
        return packet
    }

    private fun cmdFeedPaper(lines: Int): ByteArray =
        buildPacket(0xBD.toByte(), byteArrayOf((lines and 0xFF).toByte()))

    private fun cmdSetEnergy(energy: Int): ByteArray =
        buildPacket(0xAF.toByte(), byteArrayOf(
            ((energy shr 8) and 0xFF).toByte(),
            (energy and 0xFF).toByte(),
        ))

    private fun cmdPrintRow(rowData: ByteArray): ByteArray =
        buildPacket(0xA2.toByte(), rowData)

    /**
     * Print a 1-bit dithered bitmap using the cat printer protocol.
     * Follows the exact sequence from rbaron/catprinter:
     * GET_STATE → QUALITY → ENERGY → APPLY_ENERGY → LATTICE_START → rows → FEED → PAPER × 3 → LATTICE_END → GET_STATE
     */
    suspend fun printBitmap(bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height

        // 1. Preamble
        writeRaw(CMD_GET_DEV_STATE); delay(20)
        writeRaw(CMD_SET_QUALITY); delay(20)
        writeRaw(cmdSetEnergy(0xFFFF)); delay(20)
        writeRaw(CMD_APPLY_ENERGY); delay(20)
        writeRaw(CMD_LATTICE_START); delay(20)

        // 2. Send each row
        val rowBuffer = ByteArray(BYTES_PER_ROW)
        for (y in 0 until height) {
            for (byteIdx in 0 until BYTES_PER_ROW) {
                var byteVal = 0
                for (bit in 0 until 8) {
                    val x = byteIdx * 8 + bit
                    if (x < width) {
                        val pixel = bitmap.getPixel(x, y)
                        if ((pixel and 0xFF) < 128) {
                            byteVal = byteVal or (1 shl bit)  // LSB first (catprinter format)
                        }
                    }
                }
                rowBuffer[byteIdx] = byteVal.toByte()
            }
            writeRaw(cmdPrintRow(rowBuffer))
            if (y % 2 == 1) delay(BLE_CHUNK_DELAY_MS)
        }

        // 3. Finish
        writeRaw(cmdFeedPaper(25)); delay(20)
        writeRaw(CMD_SET_PAPER); delay(20)
        writeRaw(CMD_SET_PAPER); delay(20)
        writeRaw(CMD_SET_PAPER); delay(20)
        writeRaw(CMD_LATTICE_END); delay(20)
        writeRaw(CMD_GET_DEV_STATE); delay(20)
    }

    // ─── Low-level Write ──────────────────────────────

    @SuppressLint("MissingPermission")
    private suspend fun writeRaw(data: ByteArray) {
        when (connectionType) {
            ConnectionType.CLASSIC -> withContext(Dispatchers.IO) {
                outputStream?.let { it.write(data); it.flush() }
                    ?: throw IllegalStateException("Not connected")
            }
            ConnectionType.BLE -> {
                val char = writeCharacteristic ?: throw IllegalStateException("BLE not connected")
                val gattRef = gatt ?: throw IllegalStateException("BLE not connected")
                var offset = 0
                while (offset < data.size) {
                    val chunkSize = minOf(BLE_CHUNK_SIZE, data.size - offset)
                    val chunk = data.copyOfRange(offset, offset + chunkSize)
                    bleWriteDeferred = CompletableDeferred()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val wt = if (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
                            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        gattRef.writeCharacteristic(char, chunk, wt)
                    } else {
                        @Suppress("DEPRECATION") char.value = chunk
                        if (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
                            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        @Suppress("DEPRECATION") gattRef.writeCharacteristic(char)
                    }
                    withTimeoutOrNull(5000) { bleWriteDeferred?.await() }
                    offset += chunkSize
                    delay(BLE_CHUNK_DELAY_MS)
                }
            }
            ConnectionType.NONE -> throw IllegalStateException("Not connected")
        }
    }
}
