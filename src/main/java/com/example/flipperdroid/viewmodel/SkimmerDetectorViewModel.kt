package com.example.flipperdroid.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Un appareil détecté, avec verdict de suspicion "skimmer". */
data class DetectedDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val type: String,          // "Classic" ou "BLE"
    val suspicious: Boolean,
    val reason: String
)

/**
 * Détecteur de skimmers de cartes bancaires.
 *
 * Les skimmers de terminaux exfiltrent souvent les données via un module
 * Bluetooth série bon marché (HC-05 / HC-06, JDY, linvor…) laissé avec son
 * nom/PIN par défaut. On scanne le Bluetooth Classic ET le BLE, puis on
 * signale les appareils dont le nom ou le préfixe MAC (OUI) correspond à ces
 * modules. C'est une heuristique (comme l'app "Skimmer Scanner"), pas une
 * preuve : à confirmer sur place.
 */
class SkimmerDetectorViewModel : ViewModel() {

    @SuppressLint("StaticFieldLeak")
    private var context: Context? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var classicReceiver: BroadcastReceiver? = null

    private val deviceMap = LinkedHashMap<String, DetectedDevice>()

    private val _devices = MutableStateFlow<List<DetectedDevice>>(emptyList())
    val devices: StateFlow<List<DetectedDevice>> = _devices

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    companion object {
        // Noms de modules Bluetooth série typiques des skimmers (en minuscule)
        private val SUSPICIOUS_NAMES = listOf(
            "hc-05", "hc-06", "hc05", "hc06", "linvor", "jdy-31", "jdy-30",
            "jdy-33", "bt04", "spp-ca", "bolutek", "h-c-2010", "skimmer",
            "rnbt", "itead", "csr", "at-09", "mlt-bt05", "zs-040"
        )
        // Préfixes MAC (OUI) fréquents des clones HC-05/HC-06 (en minuscule)
        private val SUSPICIOUS_OUIS = listOf(
            "00:14:03", "98:d3:31", "98:d3:32", "98:d3:33", "98:d3:34",
            "98:d3:35", "98:d3:36", "88:25:83", "00:18:e4", "00:1d:a5",
            "8c:de:52", "20:16:", "00:15:83", "00:21:13"
        )

        fun evaluate(name: String?, address: String): Pair<Boolean, String> {
            val n = name?.lowercase() ?: ""
            val a = address.lowercase()
            SUSPICIOUS_NAMES.forEach { sig ->
                if (n.contains(sig)) return true to "Name matches serial BT module '$sig'"
            }
            SUSPICIOUS_OUIS.forEach { oui ->
                if (a.startsWith(oui)) return true to "MAC OUI matches cheap BT module"
            }
            return false to ""
        }
    }

    fun initialize(context: Context) {
        this.context = context.applicationContext
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = manager.adapter
        bleScanner = bluetoothAdapter?.bluetoothLeScanner
        checkPermissions()
    }

    fun checkPermissions() {
        context?.let { ctx ->
            val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            }
            _permissionsGranted.value = granted
        }
    }

    private val bleCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            @SuppressLint("MissingPermission")
            val name = try { result.device.name } catch (_: SecurityException) { null }
            addDevice(name, result.device.address, result.rssi, "BLE")
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_isScanning.value) return
        checkPermissions()
        if (!_permissionsGranted.value) { _status.value = "Bluetooth permissions required"; return }
        if (bluetoothAdapter?.isEnabled != true) { _status.value = "Enable Bluetooth first"; return }

        deviceMap.clear()
        _devices.value = emptyList()
        _isScanning.value = true
        _status.value = "Scanning (Classic + BLE)..."

        // Bluetooth Classic : découverte (attrape les modules HC-05/HC-06)
        registerClassicReceiver()
        try { bluetoothAdapter?.startDiscovery() } catch (_: Exception) {}

        // BLE
        try {
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            bleScanner?.startScan(null, settings, bleCallback)
        } catch (e: SecurityException) {
            _permissionsGranted.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) return
        _isScanning.value = false
        try { bluetoothAdapter?.cancelDiscovery() } catch (_: Exception) {}
        try { bleScanner?.stopScan(bleCallback) } catch (_: Exception) {}
        try { classicReceiver?.let { context?.unregisterReceiver(it) } } catch (_: Exception) {}
        classicReceiver = null
        val flagged = deviceMap.values.count { it.suspicious }
        _status.value = "Done. ${deviceMap.size} devices, $flagged flagged as suspicious."
    }

    private fun registerClassicReceiver() {
        classicReceiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                        device?.let {
                            val name = try { it.name } catch (_: SecurityException) { null }
                            addDevice(name, it.address, rssi, "Classic")
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        // Relance la découverte tant que le scan est actif
                        if (_isScanning.value) {
                            try { bluetoothAdapter?.startDiscovery() } catch (_: Exception) {}
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context?.registerReceiver(classicReceiver, filter)
    }

    private fun addDevice(name: String?, address: String, rssi: Int, type: String) {
        val (suspicious, reason) = evaluate(name, address)
        val entry = DetectedDevice(
            name = if (name.isNullOrBlank()) "<Unknown>" else name,
            address = address,
            rssi = rssi,
            type = type,
            suspicious = suspicious,
            reason = reason
        )
        // Garde la meilleure info (Classic prioritaire, RSSI le plus fort)
        val existing = deviceMap[address]
        if (existing == null || (!existing.suspicious && suspicious) || rssi > existing.rssi) {
            deviceMap[address] = entry
        }
        // Suspects en tête
        _devices.value = deviceMap.values.sortedWith(
            compareByDescending<DetectedDevice> { it.suspicious }.thenByDescending { it.rssi }
        )
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
    }
}
