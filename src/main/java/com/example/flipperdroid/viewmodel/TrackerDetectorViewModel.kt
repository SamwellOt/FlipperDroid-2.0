package com.example.flipperdroid.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Un rastreador (tracker) BLE détecté à proximité. */
data class DetectedTracker(
    val type: String,
    val address: String,
    val name: String,
    val rssi: Int
)

/**
 * Détecteur de trackers BLE (anti-stalking) : repère les balises de suivi proches
 * — Apple Find My/AirTag, Samsung SmartTag, Tile — via leurs signatures d'advertising.
 */
class TrackerDetectorViewModel : ViewModel() {

    @SuppressLint("StaticFieldLeak")
    private var context: Context? = null
    private var adapter: BluetoothAdapter? = null
    private var scanner: BluetoothLeScanner? = null
    private var scanning = false

    private val map = LinkedHashMap<String, DetectedTracker>()

    private val _trackers = MutableStateFlow<List<DetectedTracker>>(emptyList())
    val trackers: StateFlow<List<DetectedTracker>> = _trackers

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    fun initialize(context: Context) {
        this.context = context.applicationContext
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        adapter = manager?.adapter
        scanner = adapter?.bluetoothLeScanner
        checkPermissions()
    }

    fun checkPermissions() {
        context?.let { ctx ->
            _permissionsGranted.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val type = classify(result) ?: return
            @SuppressLint("MissingPermission")
            val name = try { result.device.name } catch (_: SecurityException) { null }
            val entry = DetectedTracker(
                type = type,
                address = result.device.address,
                name = if (name.isNullOrBlank()) "<no name>" else name,
                rssi = result.rssi
            )
            map[result.device.address] = entry
            _trackers.value = map.values.sortedByDescending { it.rssi }
        }

        override fun onScanFailed(errorCode: Int) {
            _status.value = "Scan failed: $errorCode"
            _isScanning.value = false
        }
    }

    /** Reconnaît une signature de tracker connue, ou null. */
    private fun classify(result: ScanResult): String? {
        val record = result.scanRecord ?: return null
        // Apple Find My / AirTag : manufacturer 0x004C, 1er octet 0x12 (offline finding).
        val apple = record.getManufacturerSpecificData(0x004C)
        if (apple != null && apple.isNotEmpty() && (apple[0].toInt() and 0xFF) == 0x12) {
            return "Apple Find My / AirTag"
        }
        val uuids = record.serviceUuids
        if (uuids != null) {
            for (u in uuids) {
                val s = u.uuid.toString().lowercase()
                if (s.startsWith("0000feed") || s.startsWith("0000feec")) return "Tile"
                if (s.startsWith("0000fd5a")) return "Samsung SmartTag"
            }
        }
        val sd = record.serviceData
        if (sd != null) {
            for (k in sd.keys) {
                val s = (k as ParcelUuid).uuid.toString().lowercase()
                if (s.startsWith("0000fd5a")) return "Samsung SmartTag"
                if (s.startsWith("0000feed") || s.startsWith("0000feec")) return "Tile"
            }
        }
        // Samsung : manufacturer 0x0075 (SmartThings/SmartTag).
        if (record.getManufacturerSpecificData(0x0075) != null) return "Samsung (SmartTag?)"
        return null
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        checkPermissions()
        if (!_permissionsGranted.value) { _status.value = "Bluetooth scan permission required"; return }
        if (adapter?.isEnabled != true) { _status.value = "Enable Bluetooth first"; return }
        val sc = scanner ?: adapter?.bluetoothLeScanner ?: return
        scanner = sc
        map.clear(); _trackers.value = emptyList()
        try {
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            sc.startScan(null, settings, callback)
            scanning = true
            _isScanning.value = true
            _status.value = "Scanning for nearby trackers…"
        } catch (e: SecurityException) {
            _permissionsGranted.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (scanning) {
            try { scanner?.stopScan(callback) } catch (_: Exception) {}
            scanning = false
            _isScanning.value = false
            _status.value = "Stopped. ${map.size} tracker(s) found."
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
    }
}
