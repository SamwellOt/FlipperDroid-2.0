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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/** Un réseau observé pour l'analyseur de canaux. */
data class WifiAp(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val channel: Int,
    val frequency: Int,
    val band: String
)

/**
 * Analyseur Wi-Fi : scanne les réseaux et agrège l'occupation par canal
 * (2.4 / 5 GHz). Inclut un outil de spoofing MAC (root).
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
        wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
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
        try {
            wifiManager?.startScan()
            processResults() // affiche les résultats en cache immédiatement
        } catch (e: Exception) {
            _status.value = "Scan error: ${e.message}"
        }
    }

    @SuppressLint("MissingPermission")
    private fun processResults() {
        try {
            val results = wifiManager?.scanResults ?: return
            val aps = results.filter { !it.BSSID.isNullOrEmpty() }.map {
                val ch = frequencyToChannel(it.frequency)
                WifiAp(
                    ssid = if (it.SSID.isNullOrEmpty()) "<Hidden>" else it.SSID,
                    bssid = it.BSSID,
                    rssi = it.level,
                    channel = ch,
                    frequency = it.frequency,
                    band = if (it.frequency >= 4900) "5 GHz" else "2.4 GHz"
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
        viewModelScope.launch(Dispatchers.IO) {
            val target = if (mac.isBlank()) randomMac() else mac.trim()
            if (!Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$").matches(target)) {
                withContext(Dispatchers.Main) { _status.value = "Invalid MAC format" }
                return@launch
            }
            val cmd = "ip link set $iface down && ip link set $iface address $target && ip link set $iface up"
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                val err = BufferedReader(InputStreamReader(process.errorStream)).readText()
                val code = process.waitFor()
                withContext(Dispatchers.Main) {
                    _status.value = if (code == 0) "MAC set to $target on $iface"
                    else "MAC spoof failed (root? iface?): ${err.take(120)}"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _status.value = "MAC spoof error: ${e.message}" }
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

    private fun frequencyToChannel(freq: Int): Int = when {
        freq in 2412..2484 -> if (freq == 2484) 14 else (freq - 2412) / 5 + 1
        freq in 5170..5825 -> (freq - 5170) / 5 + 34
        else -> 0
    }

    override fun onCleared() {
        super.onCleared()
        try { receiver?.let { context?.unregisterReceiver(it) } } catch (_: Exception) {}
        receiver = null
    }
}
