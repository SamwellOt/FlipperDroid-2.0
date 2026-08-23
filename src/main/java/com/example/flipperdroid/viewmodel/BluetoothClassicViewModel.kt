package com.example.flipperdroid.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.OutputStream
import java.util.UUID

/** Un appareil Bluetooth classique (BR/EDR) découvert. */
data class ClassicDevice(
    val name: String,
    val address: String,
    val rssi: Int?,
    val bonded: Boolean,
    val majorClass: String,
    val services: List<String> = emptyList()
)

/**
 * Bluetooth « classique » (BR/EDR), par opposition au BLE :
 * - scan/découverte des appareils (nom, MAC, RSSI, classe, appairé),
 * - découverte de services par SDP (A2DP/HFP/SPP/OBEX…),
 * - terminal série SPP/RFCOMM (dialoguer avec HC-05/HC-06, Arduino BT…).
 * Sans root.
 */
class BluetoothClassicViewModel : ViewModel() {

    @SuppressLint("StaticFieldLeak")
    private var context: Context? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var receiver: BroadcastReceiver? = null

    private val deviceMap = LinkedHashMap<String, ClassicDevice>()

    private val _devices = MutableStateFlow<List<ClassicDevice>>(emptyList())
    val devices: StateFlow<List<ClassicDevice>> = _devices

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    // --- SPP / RFCOMM ---
    private val _sppConnected = MutableStateFlow(false)
    val sppConnected: StateFlow<Boolean> = _sppConnected
    private val _sppStatus = MutableStateFlow("")
    val sppStatus: StateFlow<String> = _sppStatus
    private val _sppLog = MutableStateFlow<List<String>>(emptyList())
    val sppLog: StateFlow<List<String>> = _sppLog

    private var sppSocket: BluetoothSocket? = null
    private var sppOut: OutputStream? = null
    private var sppJob: Job? = null

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    fun initialize(context: Context) {
        this.context = context.applicationContext
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = manager?.adapter
        checkPermissions()
        loadBonded()
    }

    fun checkPermissions() {
        context?.let { ctx ->
            _permissionsGranted.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun loadBonded() {
        val adapter = bluetoothAdapter ?: return
        try {
            adapter.bondedDevices?.forEach { addDevice(it, null, null, bonded = true) }
        } catch (_: SecurityException) {}
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        checkPermissions()
        if (!_permissionsGranted.value) { _status.value = "Bluetooth permissions required"; return }
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) { _status.value = "Enable Bluetooth first"; return }
        registerReceiver()
        deviceMap.clear()
        loadBonded()
        _isScanning.value = true
        _status.value = "Scanning classic (BR/EDR)…"
        try {
            if (adapter.isDiscovering) adapter.cancelDiscovery()
            adapter.startDiscovery()
        } catch (_: SecurityException) {
            _permissionsGranted.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        _isScanning.value = false
        try { bluetoothAdapter?.cancelDiscovery() } catch (_: Exception) {}
        val flagged = deviceMap.size
        _status.value = "Stopped. $flagged devices."
    }

    /** Découverte des services d'un appareil via SDP (résultats via ACTION_UUID). */
    @SuppressLint("MissingPermission")
    fun discoverServices(address: String) {
        val adapter = bluetoothAdapter ?: return
        registerReceiver()
        try {
            val device = adapter.getRemoteDevice(address)
            if (adapter.isDiscovering) adapter.cancelDiscovery()
            device.fetchUuidsWithSdp()
            _status.value = "Discovering services on $address…"
        } catch (e: Exception) {
            _status.value = "SDP error: ${e.message}"
        }
    }

    @SuppressLint("MissingPermission")
    fun pair(address: String) {
        val adapter = bluetoothAdapter ?: return
        try {
            adapter.getRemoteDevice(address).createBond()
            _status.value = "Pairing with $address…"
        } catch (e: Exception) {
            _status.value = "Pair error: ${e.message}"
        }
    }

    // --- SPP terminal ---

    @SuppressLint("MissingPermission")
    fun connectSpp(address: String) {
        if (_sppConnected.value) { _sppStatus.value = "Already connected"; return }
        val adapter = bluetoothAdapter ?: return
        _sppStatus.value = "Connecting SPP to $address…"
        sppJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val device = adapter.getRemoteDevice(address)
                try { adapter.cancelDiscovery() } catch (_: Exception) {}
                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                sppSocket = socket
                socket.connect() // bloquant
                sppOut = socket.outputStream
                _sppConnected.value = true
                _sppStatus.value = "Connected (SPP) to $address"
                appendSpp("[connected to $address]")
                val input = socket.inputStream
                val buf = ByteArray(1024)
                while (isActive) {
                    val n = input.read(buf)
                    if (n < 0) break
                    appendSpp("< " + String(buf, 0, n, Charsets.UTF_8).trimEnd('\n', '\r'))
                }
            } catch (e: Exception) {
                _sppStatus.value = "SPP error: ${e.message} (device must expose SPP/1101 & be paired)"
            } finally {
                closeSpp()
            }
        }
    }

    fun sendSpp(text: String, asHex: Boolean) {
        val out = sppOut ?: run { _sppStatus.value = "Not connected"; return }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = if (asHex) hexToBytes(text) else (text + "\r\n").toByteArray(Charsets.UTF_8)
                out.write(bytes); out.flush()
                appendSpp("> $text")
            } catch (e: Exception) {
                _sppStatus.value = "Send error: ${e.message}"
            }
        }
    }

    fun disconnectSpp() {
        sppJob?.cancel()
        sppJob = null
        closeSpp()
        _sppStatus.value = "Disconnected"
    }

    private fun closeSpp() {
        try { sppOut?.close() } catch (_: Exception) {}
        try { sppSocket?.close() } catch (_: Exception) {}
        sppOut = null
        sppSocket = null
        _sppConnected.value = false
    }

    private fun appendSpp(line: String) {
        _sppLog.value = (_sppLog.value + line).takeLast(200)
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.filter { !it.isWhitespace() }
        val out = ByteArray(clean.length / 2)
        var i = 0
        while (i + 1 < clean.length) {
            out[i / 2] = ((Character.digit(clean[i], 16) shl 4) +
                    Character.digit(clean[i + 1], 16)).toByte()
            i += 2
        }
        return out
    }

    // --- Discovery receiver ---

    private fun registerReceiver() {
        if (receiver != null) return
        receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = deviceExtra(intent) ?: return
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                            .toInt().takeIf { it != Short.MIN_VALUE.toInt() }
                        @Suppress("DEPRECATION")
                        val cls = intent.getParcelableExtra<BluetoothClass>(BluetoothDevice.EXTRA_CLASS)
                        addDevice(device, rssi, cls, bonded = device.bondState == BluetoothDevice.BOND_BONDED)
                    }
                    BluetoothDevice.ACTION_UUID -> {
                        val device = deviceExtra(intent) ?: return
                        @Suppress("DEPRECATION")
                        val raw = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID)
                        val names = raw?.filterIsInstance<ParcelUuid>()?.map { serviceName(it) }?.distinct() ?: emptyList()
                        if (names.isNotEmpty()) updateServices(device.address, names)
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        _isScanning.value = false
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_UUID)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context?.registerReceiver(receiver, filter)
    }

    @Suppress("DEPRECATION")
    private fun deviceExtra(intent: Intent): BluetoothDevice? =
        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

    @SuppressLint("MissingPermission")
    private fun addDevice(device: BluetoothDevice, rssi: Int?, cls: BluetoothClass?, bonded: Boolean) {
        val name = try { device.name } catch (_: SecurityException) { null }
        val existing = deviceMap[device.address]
        val entry = ClassicDevice(
            name = if (name.isNullOrBlank()) (existing?.name ?: "<Unknown>") else name,
            address = device.address,
            rssi = rssi ?: existing?.rssi,
            bonded = bonded || (existing?.bonded == true),
            majorClass = if (cls != null) majorClassLabel(cls) else (existing?.majorClass ?: "?"),
            services = existing?.services ?: emptyList()
        )
        deviceMap[device.address] = entry
        emitDevices()
    }

    private fun updateServices(address: String, services: List<String>) {
        val existing = deviceMap[address] ?: return
        deviceMap[address] = existing.copy(services = services)
        emitDevices()
        _status.value = "Services on $address: ${services.joinToString()}"
    }

    private fun emitDevices() {
        _devices.value = deviceMap.values.sortedWith(
            compareByDescending<ClassicDevice> { it.bonded }.thenByDescending { it.rssi ?: Int.MIN_VALUE }
        )
    }

    private fun majorClassLabel(cls: BluetoothClass): String = when (cls.majorDeviceClass) {
        BluetoothClass.Device.Major.PHONE -> "Phone"
        BluetoothClass.Device.Major.COMPUTER -> "Computer"
        BluetoothClass.Device.Major.AUDIO_VIDEO -> "Audio/Video"
        BluetoothClass.Device.Major.WEARABLE -> "Wearable"
        BluetoothClass.Device.Major.PERIPHERAL -> "Peripheral"
        BluetoothClass.Device.Major.IMAGING -> "Imaging"
        BluetoothClass.Device.Major.HEALTH -> "Health"
        BluetoothClass.Device.Major.TOY -> "Toy"
        BluetoothClass.Device.Major.NETWORKING -> "Networking"
        else -> "Other"
    }

    private fun serviceName(u: ParcelUuid): String {
        val s = u.uuid.toString().lowercase()
        return when {
            s.startsWith("00001101") -> "SPP"
            s.startsWith("00001105") -> "OBEX Push"
            s.startsWith("00001106") -> "OBEX FTP"
            s.startsWith("0000110a") -> "A2DP Source"
            s.startsWith("0000110b") -> "A2DP Sink"
            s.startsWith("0000110c") -> "AVRCP Target"
            s.startsWith("0000110e") -> "AVRCP"
            s.startsWith("00001108") -> "Headset"
            s.startsWith("0000111e") -> "HFP"
            s.startsWith("0000111f") -> "HFP AG"
            s.startsWith("0000112f") -> "PBAP"
            s.startsWith("00001132") -> "MAP"
            s.startsWith("00001124") -> "HID"
            s.startsWith("00001200") -> "DeviceID"
            else -> u.uuid.toString().substring(0, 8)
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnectSpp()
        try { bluetoothAdapter?.cancelDiscovery() } catch (_: Exception) {}
        try { receiver?.let { context?.unregisterReceiver(it) } } catch (_: Exception) {}
        receiver = null
    }
}
