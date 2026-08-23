package com.example.flipperdroid.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Un point d'accès observé, associé à une position GPS. */
data class WardriveEntry(
    val mac: String,
    val ssid: String,
    val auth: String,
    val firstSeen: String,
    val channel: Int,
    val rssi: Int,
    val lat: Double,
    val lon: Double,
    val altitude: Double,
    val accuracy: Double
)

/**
 * Wardriving natif (inspiré d'Evil-M5Project) : combine le scan Wi-Fi et la
 * position GPS pour cartographier les réseaux, avec export au format Wigle CSV.
 *
 * Limite Android : les scans Wi-Fi sont bridés (≈4 scans / 2 min en avant-plan
 * depuis Android 9), donc la cadence est volontairement lente.
 */
class WardrivingViewModel(app: Application) : AndroidViewModel(app) {

    @SuppressLint("StaticFieldLeak")
    private val context: Context = app.applicationContext
    private var wifiManager: WifiManager? = null
    private var locationManager: LocationManager? = null

    private val entriesMap = LinkedHashMap<String, WardriveEntry>()

    private val _entries = MutableStateFlow<List<WardriveEntry>>(emptyList())
    val entries: StateFlow<List<WardriveEntry>> = _entries

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    private var lastLocation: Location? = null
    private var scanJob: Job? = null
    private var scanReceiver: BroadcastReceiver? = null

    private val locationListener = LocationListener { location -> lastLocation = location }

    fun initialize() {
        wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (_isRunning.value) return
        if (!hasPermissions()) { _status.value = "Location permission required"; return }
        initialize()
        _isRunning.value = true
        _status.value = "Wardriving started..."

        // Mises à jour GPS
        try {
            locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener)
            locationManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, locationListener)
            lastLocation = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            Log.e("Wardriving", "Location error: ${e.message}")
        }

        // Récepteur de résultats de scan
        registerScanReceiver()

        // Boucle de scan
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive && _isRunning.value) {
                try { wifiManager?.startScan() } catch (_: Exception) {}
                delay(30_000) // respecte le bridage de scan Android
            }
        }
    }

    private fun registerScanReceiver() {
        scanReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                    processResults()
                }
            }
        }
        context.registerReceiver(scanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
    }

    @SuppressLint("MissingPermission")
    private fun processResults() {
        val loc = lastLocation
        if (loc == null) { _status.value = "Waiting for GPS fix..."; return }
        try {
            val results = wifiManager?.scanResults ?: return
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            var added = 0
            for (r in results) {
                if (r.BSSID.isNullOrEmpty()) continue
                if (!entriesMap.containsKey(r.BSSID)) added++
                entriesMap[r.BSSID] = WardriveEntry(
                    mac = r.BSSID,
                    ssid = r.SSID ?: "",
                    auth = r.capabilities ?: "",
                    firstSeen = timestamp,
                    channel = frequencyToChannel(r.frequency),
                    rssi = r.level,
                    lat = loc.latitude,
                    lon = loc.longitude,
                    altitude = loc.altitude,
                    accuracy = loc.accuracy.toDouble()
                )
            }
            _entries.value = entriesMap.values.toList()
            _status.value = "Networks: ${entriesMap.size} (+$added last scan)"
        } catch (e: SecurityException) {
            _status.value = "Permission error during scan"
        }
    }

    fun stop() {
        _isRunning.value = false
        scanJob?.cancel()
        scanJob = null
        try { locationManager?.removeUpdates(locationListener) } catch (_: Exception) {}
        try { scanReceiver?.let { context.unregisterReceiver(it) } } catch (_: Exception) {}
        scanReceiver = null
        _status.value = "Stopped. ${entriesMap.size} networks collected."
    }

    /** Exporte les réseaux au format Wigle CSV (WigleWifi-1.4). */
    fun exportCsv() {
        if (entriesMap.isEmpty()) { _status.value = "Nothing to export"; return }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dir = getApplication<Application>().getExternalFilesDir("wardriving")
                    ?: getApplication<Application>().filesDir
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val file = File(dir, "wardrive_$ts.csv")
                val sb = StringBuilder()
                sb.append("WigleWifi-1.4,appRelease=FlipperDroid,model=Android,release=Android,device=Android,display=,board=,brand=\n")
                sb.append("MAC,SSID,AuthMode,FirstSeen,Channel,RSSI,CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters,Type\n")
                for (e in entriesMap.values) {
                    val ssid = e.ssid.replace(",", " ")
                    sb.append("${e.mac},$ssid,${e.auth},${e.firstSeen},${e.channel},${e.rssi},${e.lat},${e.lon},${e.altitude},${e.accuracy},WIFI\n")
                }
                file.writeText(sb.toString())
                withContext(Dispatchers.Main) { _status.value = "Exported: ${file.absolutePath}" }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _status.value = "Export error: ${e.message}" }
            }
        }
    }

    private fun frequencyToChannel(freq: Int): Int = when {
        freq in 2412..2484 -> (freq - 2412) / 5 + 1
        freq in 5170..5825 -> (freq - 5170) / 5 + 34
        else -> 0
    }

    override fun onCleared() {
        super.onCleared()
        stop()
    }
}
