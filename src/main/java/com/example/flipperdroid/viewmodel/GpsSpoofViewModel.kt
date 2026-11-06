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
 *
 * Seul le fournisseur GPS de la plateforme (LocationManager) est simulé : les apps qui
 * utilisent le Fused Location Provider (Google Play services) peuvent l'ignorer.
 */
class GpsSpoofViewModel(app: Application) : AndroidViewModel(app) {

    private val lm = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    private var job: Job? = null
    @Volatile private var lat = 0.0
    @Volatile private var lon = 0.0

    /** Retourne un message d'erreur si les coordonnées sont hors bornes (ou NaN), sinon null. */
    private fun validate(latitude: Double, longitude: Double): String? = when {
        latitude !in -90.0..90.0 -> "Invalid latitude (must be between -90 and 90)"
        longitude !in -180.0..180.0 -> "Invalid longitude (must be between -180 and 180)"
        else -> null
    }

    @Suppress("DEPRECATION")
    fun start(latitude: Double, longitude: Double) {
        validate(latitude, longitude)?.let { error ->
            _status.value = error
            return
        }
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
                    val error = pushLocation(manager, provider)
                    if (error != null) _status.value = "Mock location push failed: $error"
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
        validate(latitude, longitude)?.let { error ->
            _status.value = error
            return
        }
        lat = latitude; lon = longitude
        if (_active.value) _status.value = "Spoofing GPS to $lat, $lon"
    }

    /** Envoie une position au fournisseur simulé. Retourne null si OK, sinon le message d'erreur. */
    private fun pushLocation(manager: LocationManager, provider: String): String? = try {
        val loc = Location(provider).apply {
            latitude = lat
            longitude = lon
            altitude = 0.0
            accuracy = 1f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        manager.setTestProviderLocation(provider, loc)
        null
    } catch (e: SecurityException) {
        "not allowed (select this app as mock location app)"
    } catch (e: Exception) {
        e.message ?: e.javaClass.simpleName
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
