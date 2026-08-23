package com.example.flipperdroid.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Résumé d'un appareil BLE détecté pendant le scan. */
data class BleDeviceInfo(
    val name: String,
    val address: String,
    val rssi: Int,
    val connectable: Boolean
)

/** Caractéristique GATT avec ses propriétés décodées et sa dernière valeur lue. */
data class GattCharacteristicInfo(
    val serviceUuid: String,
    val uuid: String,
    val properties: List<String>,
    val value: String? = null
)

/** Service GATT et ses caractéristiques. */
data class GattServiceInfo(
    val uuid: String,
    val characteristics: List<GattCharacteristicInfo>
)

/**
 * ViewModel du scanner BLE + explorateur GATT (façon nRF Connect / LightBlue).
 *
 * Scanne les appareils BLE, se connecte à l'un d'eux, découvre ses services
 * et caractéristiques, et permet de lire les valeurs.
 */
class BluetoothViewModel : ViewModel() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var scanning = false
    private var gatt: BluetoothGatt? = null

    @SuppressLint("StaticFieldLeak")
    private var context: Context? = null

    private val _devices = MutableStateFlow<List<BleDeviceInfo>>(emptyList())
    val devices: StateFlow<List<BleDeviceInfo>> = _devices

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted

    private val _connectionState = MutableStateFlow("Disconnected")
    val connectionState: StateFlow<String> = _connectionState

    private val _connectedDevice = MutableStateFlow<BleDeviceInfo?>(null)
    val connectedDevice: StateFlow<BleDeviceInfo?> = _connectedDevice

    private val _services = MutableStateFlow<List<GattServiceInfo>>(emptyList())
    val services: StateFlow<List<GattServiceInfo>> = _services

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    private fun addLog(msg: String) {
        _log.value = (_log.value + msg).takeLast(200)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            @SuppressLint("MissingPermission")
            val name = try { result.device.name } catch (_: SecurityException) { null }
            val info = BleDeviceInfo(
                name = if (name.isNullOrBlank()) "<Unknown>" else name,
                address = result.device.address,
                rssi = result.rssi,
                connectable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) result.isConnectable else true
            )
            val current = _devices.value.toMutableList()
            val idx = current.indexOfFirst { it.address == info.address }
            if (idx >= 0) current[idx] = info else current.add(info)
            _devices.value = current.sortedByDescending { it.rssi }
        }

        override fun onScanFailed(errorCode: Int) {
            addLog("Scan failed: $errorCode")
            _isScanning.value = false
        }
    }

    fun initialize(context: Context) {
        this.context = context.applicationContext
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        checkPermissions()
    }

    fun checkPermissions() {
        context?.let { ctx ->
            val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
            }
            _permissionsGranted.value = granted
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!_permissionsGranted.value) { checkPermissions(); if (!_permissionsGranted.value) return }
        if (bluetoothAdapter?.isEnabled != true) { addLog("Bluetooth is disabled"); return }
        val scanner = bluetoothLeScanner ?: bluetoothAdapter?.bluetoothLeScanner ?: return
        bluetoothLeScanner = scanner
        _devices.value = emptyList()
        try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            scanner.startScan(null, settings, scanCallback)
            scanning = true
            _isScanning.value = true
            addLog("Scan started")
        } catch (e: SecurityException) {
            _permissionsGranted.value = false
            addLog("Scan permission error")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (scanning) {
            try { bluetoothLeScanner?.stopScan(scanCallback) } catch (_: Exception) {}
            scanning = false
            _isScanning.value = false
            addLog("Scan stopped")
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BleDeviceInfo) {
        stopScan()
        val ctx = context ?: return
        val remote: BluetoothDevice = try {
            bluetoothAdapter?.getRemoteDevice(device.address) ?: return
        } catch (e: Exception) {
            addLog("Invalid address: ${device.address}"); return
        }
        _connectedDevice.value = device
        _connectionState.value = "Connecting"
        _services.value = emptyList()
        addLog("Connecting to ${device.address}...")
        try {
            gatt = remote.connectGatt(ctx, false, gattCallback)
        } catch (e: SecurityException) {
            _permissionsGranted.value = false
            _connectionState.value = "Disconnected"
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try { gatt?.disconnect(); gatt?.close() } catch (_: Exception) {}
        gatt = null
        _connectionState.value = "Disconnected"
        _connectedDevice.value = null
        _services.value = emptyList()
    }

    @SuppressLint("MissingPermission")
    fun readCharacteristic(serviceUuid: String, charUuid: String) {
        val g = gatt ?: return
        try {
            val service = g.services.firstOrNull { it.uuid.toString().equals(serviceUuid, true) } ?: return
            val ch = service.characteristics.firstOrNull { it.uuid.toString().equals(charUuid, true) } ?: return
            g.readCharacteristic(ch)
        } catch (e: Exception) {
            addLog("Read failed: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun writeCharacteristic(serviceUuid: String, charUuid: String, hex: String) {
        val g = gatt ?: return
        try {
            val service = g.services.firstOrNull { it.uuid.toString().equals(serviceUuid, true) } ?: return
            val ch = service.characteristics.firstOrNull { it.uuid.toString().equals(charUuid, true) } ?: return
            val bytes = hex.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(ch, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION") run {
                    ch.value = bytes
                    g.writeCharacteristic(ch)
                }
            }
            addLog("Write ${bytes.size} bytes to ${ch.uuid}")
        } catch (e: Exception) {
            addLog("Write failed: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun enableNotifications(serviceUuid: String, charUuid: String) {
        val g = gatt ?: return
        try {
            val service = g.services.firstOrNull { it.uuid.toString().equals(serviceUuid, true) } ?: return
            val ch = service.characteristics.firstOrNull { it.uuid.toString().equals(charUuid, true) } ?: return
            g.setCharacteristicNotification(ch, true)
            val cccd = ch.getDescriptor(java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            if (cccd != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION") run {
                        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        g.writeDescriptor(cccd)
                    }
                }
            }
            addLog("Notifications enabled on ${ch.uuid}")
        } catch (e: Exception) {
            addLog("Notify failed: ${e.message}")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = "Connected"
                    addLog("Connected, discovering services...")
                    try { g.discoverServices() } catch (_: Exception) {}
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = "Disconnected"
                    _services.value = emptyList()
                    addLog("Disconnected")
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) { addLog("Service discovery failed: $status"); return }
            val services = g.services.map { service ->
                GattServiceInfo(
                    uuid = service.uuid.toString(),
                    characteristics = service.characteristics.map { ch ->
                        GattCharacteristicInfo(
                            serviceUuid = service.uuid.toString(),
                            uuid = ch.uuid.toString(),
                            properties = decodeProperties(ch.properties)
                        )
                    }
                )
            }
            _services.value = services
            addLog("Discovered ${services.size} services")
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                updateCharacteristicValue(ch.service.uuid.toString(), ch.uuid.toString(), bytesToHex(ch.value))
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            updateCharacteristicValue(ch.service.uuid.toString(), ch.uuid.toString(), bytesToHex(ch.value))
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) {
            addLog("Write ${if (status == BluetoothGatt.GATT_SUCCESS) "OK" else "failed ($status)"}: ${ch.uuid}")
        }
    }

    private fun updateCharacteristicValue(serviceUuid: String, charUuid: String, valueHex: String) {
        _services.value = _services.value.map { s ->
            if (s.uuid.equals(serviceUuid, true)) {
                s.copy(characteristics = s.characteristics.map { c ->
                    if (c.uuid.equals(charUuid, true)) c.copy(value = valueHex) else c
                })
            } else s
        }
        addLog("Read $charUuid = $valueHex")
    }

    private fun decodeProperties(props: Int): List<String> {
        val result = mutableListOf<String>()
        if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) result.add("READ")
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) result.add("WRITE")
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) result.add("WRITE_NR")
        if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) result.add("NOTIFY")
        if (props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) result.add("INDICATE")
        return result
    }

    private fun bytesToHex(bytes: ByteArray?): String {
        if (bytes == null || bytes.isEmpty()) return "(empty)"
        return bytes.joinToString(" ") { "%02X".format(it) }
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
        disconnect()
    }
}
