package com.example.flipperdroid.viewmodel

import android.app.Application
import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Faux GPS via mock location. Nécessite d'activer cette app comme « application de
 * localisation fictive » dans Options développeur (pas de root).
 */
class GpsSpoofViewModel(app: Application) : AndroidViewModel(app) {

    private val lm = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    private var job: Job? = null
    private var lat = 0.0
    private var lon = 0.0

    @Suppress("DEPRECATION")
    fun start(latitude: Double, longitude: Double) {
        val manager = lm ?: run { _status.value = "LocationManager unavailable"; return }
        lat = latitude; lon = longitude
        val provider = LocationManager.GPS_PROVIDER
        try {
            try {
                manager.addTestProvider(
                    provider, false, false, false, false,
                    true, true, true, Criteria.POWER_LOW, Criteria.ACCURACY_FINE
                )
            } catch (_: IllegalArgumentException) { /* déjà ajouté */ }
            manager.setTestProviderEnabled(provider, true)
            _active.value = true
            _status.value = "Spoofing GPS to $lat, $lon"
            job?.cancel()
            job = viewModelScope.launch(Dispatchers.IO) {
                while (isActive) {
                    pushLocation(manager, provider)
                    delay(1000)
                }
            }
        } catch (e: SecurityException) {
            _status.value = "Not allowed. Enable this app in Developer Options → " +
                "\"Select mock location app\", then try again."
        } catch (e: Exception) {
            _status.value = "Error: ${e.message}"
        }
    }

    fun update(latitude: Double, longitude: Double) {
        lat = latitude; lon = longitude
        if (_active.value) _status.value = "Spoofing GPS to $lat, $lon"
    }

    private fun pushLocation(manager: LocationManager, provider: String) {
        try {
            val loc = Location(provider).apply {
                latitude = lat
                longitude = lon
                altitude = 0.0
                accuracy = 1f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            manager.setTestProviderLocation(provider, loc)
        } catch (_: Exception) {}
    }

    @Suppress("DEPRECATION")
    fun stop() {
        job?.cancel(); job = null
        val manager = lm
        val provider = LocationManager.GPS_PROVIDER
        try { manager?.setTestProviderEnabled(provider, false) } catch (_: Exception) {}
        try { manager?.removeTestProvider(provider) } catch (_: Exception) {}
        _active.value = false
        _status.value = "Stopped."
    }

    override fun onCleared() {
        super.onCleared()
        stop()
    }
}
