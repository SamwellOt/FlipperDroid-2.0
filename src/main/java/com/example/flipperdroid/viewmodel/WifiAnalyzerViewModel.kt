package com.example.flipperdroid.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flipperdroid.util.WifiChannels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Un réseau observé pour l'analyseur de canaux. */
data class WifiAp(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val channel: Int,
    val frequency: Int,
    val band: String
)

private val MAC_REGEX = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")

/** Nom d'interface Linux valide (évite toute injection dans la commande root). */
private val IFACE_REGEX = Regex("^[A-Za-z0-9._-]{1,15}$")

/**
 * Analyseur Wi-Fi : scanne les réseaux et agrège l'occupation par canal
 * (2.4 / 5 / 6 GHz). Inclut un outil de spoofing MAC (root).
 */
class WifiAnalyzerViewModel : ViewModel() {

    @SuppressLint("StaticFieldLeak")
    private var context: Context? = null
    private var wifiManager: WifiManager? = null
    private var receiver: BroadcastReceiver? = null

    private val _aps = MutableStateFlow<List<WifiAp>>(emptyList())
    val aps: StateFlow<List<WifiAp>> = _aps

    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    fun initialize(context: Context) {
        this.context = context.applicationContext
        wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiManager == null) _status.value = "Wi-Fi service unavailable"
        checkPermissions()
        registerReceiver()
    }

    fun checkPermissions() {
        context?.let { ctx ->
            _permissionsGranted.value =
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun registerReceiver() {
        if (receiver != null) return
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) processResults()
            }
        }
        context?.registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
    }

    @SuppressLint("MissingPermission")
    fun scan() {
        checkPermissions()
        if (!_permissionsGranted.value) { _status.value = "Location permission required"; return }
        val wifi = wifiManager
        if (wifi == null) { _status.value = "Wi-Fi service unavailable"; return }
        try {
            wifi.startScan()
            processResults() // affiche les résultats en cache immédiatement
        } catch (e: Exception) {
            _status.value = "Scan error: ${e.message}"
        }
    }

    @SuppressLint("MissingPermission")
    private fun processResults() {
        try {
            val results = wifiManager?.scanResults ?: return
            val aps = results.mapNotNull { r ->
                val bssid: String = r.BSSID?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val ssid: String = r.SSID?.takeIf { it.isNotEmpty() } ?: "<Hidden>"
                WifiAp(
                    ssid = ssid,
                    bssid = bssid,
                    rssi = r.level,
                    channel = WifiChannels.frequencyToChannel(r.frequency),
                    frequency = r.frequency,
                    band = WifiChannels.band(r.frequency)
                )
            }.sortedByDescending { it.rssi }
            _aps.value = aps
            _status.value = "${aps.size} networks"
        } catch (e: SecurityException) {
            _permissionsGranted.value = false
        }
    }

    /** Occupation par canal : canal -> nombre de réseaux. */
    fun channelUsage(band: String): Map<Int, Int> {
        return _aps.value.filter { it.band == band && it.channel > 0 }
            .groupingBy { it.channel }.eachCount()
    }

    /**
     * Spoof l'adresse MAC de l'interface Wi-Fi (nécessite root).
     * @param mac au format AA:BB:CC:DD:EE:FF ; vide = MAC aléatoire.
     */
    fun spoofMac(mac: String, iface: String = "wlan0") {
        if (!IFACE_REGEX.matches(iface)) { _status.value = "Invalid interface name"; return }
        viewModelScope.launch(Dispatchers.IO) {
            val target = if (mac.isBlank()) randomMac() else mac.trim()
            if (!MAC_REGEX.matches(target)) {
                _status.value = "Invalid MAC format"
                return@launch
            }
            // L'interface est remise UP dans tous les cas, même si le changement d'adresse échoue.
            val cmd = "ip link set $iface down; ip link set $iface address $target; r=\$?; " +
                "ip link set $iface up; exit \$r"
            try {
                val process = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().use { it.readText() }
                val code = process.waitFor()
                _status.value = if (code == 0) "MAC set to $target on $iface"
                else "MAC spoof failed (root? iface?), interface re-enabled: ${output.take(120)}"
            } catch (e: Exception) {
                _status.value = "MAC spoof error: ${e.message}"
                Log.e("WifiAnalyzer", "spoofMac", e)
            }
        }
    }

    private fun randomMac(): String {
        val b = ByteArray(6)
        java.security.SecureRandom().nextBytes(b)
        b[0] = ((b[0].toInt() and 0xFE) or 0x02).toByte() // locally administered, unicast
        return b.joinToString(":") { "%02X".format(it) }
    }

    override fun onCleared() {
        super.onCleared()
        try { receiver?.let { context?.unregisterReceiver(it) } } catch (_: Exception) {}
        receiver = null
    }
}
