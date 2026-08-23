package com.example.flipperdroid.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import com.example.flipperdroid.model.`class`.AdvertisementSetQueueHandler
import com.example.flipperdroid.model.`object`.BluetoothHelpers
import com.example.flipperdroid.model.`object`.ContinuityNewDevicePopUpAdvertisementSetGenerator
import com.example.flipperdroid.model.`object`.ContinuityNearbyActionAdvertisementSetGenerator
import com.example.flipperdroid.model.`object`.EasySetupWatchAdvertisementSetGenerator
import com.example.flipperdroid.model.`object`.EasySetupBudsAdvertisementSetGenerator
import com.example.flipperdroid.model.`object`.SwiftPairAdvertisementSetGenerator
import com.example.flipperdroid.model.`object`.FastPairAdvertisementSetGenerator
import com.example.flipperdroid.model.`class`.AdvertisementSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BleSpamViewModel(app: Application) : AndroidViewModel(app) {
    enum class BleSpamBrand { APPLE, SAMSUNG, MICROSOFT, GOOGLE, ALL }
    @SuppressLint("StaticFieldLeak")
    private val context = app.applicationContext
    // Advertising étendu (API 26+) pour dépasser la limite legacy de 31 octets, sinon legacy.
    private val handler = AdvertisementSetQueueHandler(
        context,
        BluetoothHelpers.getAdvertisementService(
            context,
            useLegacy = Build.VERSION.SDK_INT < Build.VERSION_CODES.O
        )
    )
    private val _advertisementSets = MutableStateFlow<List<AdvertisementSet>>(emptyList())
    val advertisementSets: StateFlow<List<AdvertisementSet>> = _advertisementSets
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive
    private var _allAdvertisementSets: List<AdvertisementSet> = emptyList()
    private val _brand = MutableStateFlow(BleSpamBrand.APPLE)
    val brand: StateFlow<BleSpamBrand> = _brand
    private val _spamLogs = MutableStateFlow<List<String>>(emptyList())
    val spamLogs: StateFlow<List<String>> = _spamLogs
    private var spamCount = 0

    // Vitesse du spam (intervalle en ms entre deux trames) : plus petit = plus agressif.
    // 100 ms par défaut : setAdvertisingData est débité par le contrôleur ; trop rapide,
    // certaines mises à jour sont ignorées (sans fuite d'advertisers).
    private val _speedMs = MutableStateFlow(100L)
    val speedMs: StateFlow<Long> = _speedMs

    fun setSpeed(ms: Long) {
        _speedMs.value = ms
        handler.setIntervalMillis(ms)
    }

    // Payloads réellement sélectionnés pour le spam (par défaut : tous)
    private var _selectedSets: List<AdvertisementSet> = emptyList()

    init {
        loadAdvertisementSets()
    }

    fun setBrand(brand: BleSpamBrand) {
        _brand.value = brand
        loadAdvertisementSets()
    }

    fun startSpam() {
        // Spam uniquement les payloads sélectionnés ; repli sur tous si aucune sélection.
        val sets = _selectedSets.ifEmpty { _advertisementSets.value }
        if (sets.isEmpty()) return
        _isActive.value = true
        handler.onSpamSent = { set ->
            spamCount++
            val log = "[${System.currentTimeMillis() % 100000}] ${(if (set.title.isNotBlank()) set.title else set.toString())} (${set.type})"
            _spamLogs.value = (_spamLogs.value + log).takeLast(100)
        }
        // Report honnête des échecs (ex. DATA_TOO_LARGE : payload > 31 octets rejeté
        // par l'advertising legacy) au lieu de laisser croire à un envoi réussi.
        handler.onSpamError = { set, error ->
            val name = set?.title?.takeIf { it.isNotBlank() } ?: set?.type?.toString() ?: "?"
            val hint = if (error == com.example.flipperdroid.model.enums.AdvertisementError.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS)
                " — toggle Bluetooth OFF/ON to clear leaked advertisers" else ""
            _spamLogs.value = (_spamLogs.value + "⚠ FAILED: $name ($error)$hint").takeLast(100)
        }
        handler.setIntervalMillis(_speedMs.value)
        handler.startSpam(sets)
    }

    fun stopSpam() {
        _isActive.value = false
        handler.onSpamSent = null
        handler.onSpamError = null
        handler.stopSpam()
    }

    private fun loadAdvertisementSets() {
        val sets = when (_brand.value) {
            BleSpamBrand.APPLE -> (
                ContinuityNewDevicePopUpAdvertisementSetGenerator.getAdvertisementSets() +
                    ContinuityNearbyActionAdvertisementSetGenerator.getAdvertisementSets()
            )
            BleSpamBrand.SAMSUNG -> (
                EasySetupWatchAdvertisementSetGenerator.getAdvertisementSets() + EasySetupBudsAdvertisementSetGenerator.getAdvertisementSets()
            )
            BleSpamBrand.MICROSOFT -> SwiftPairAdvertisementSetGenerator.getAdvertisementSets()
            BleSpamBrand.GOOGLE -> FastPairAdvertisementSetGenerator.getAdvertisementSets()
            BleSpamBrand.ALL -> (
                ContinuityNewDevicePopUpAdvertisementSetGenerator.getAdvertisementSets()
                + ContinuityNearbyActionAdvertisementSetGenerator.getAdvertisementSets()
                + EasySetupWatchAdvertisementSetGenerator.getAdvertisementSets()
                + EasySetupBudsAdvertisementSetGenerator.getAdvertisementSets()
                + SwiftPairAdvertisementSetGenerator.getAdvertisementSets()
                + FastPairAdvertisementSetGenerator.getAdvertisementSets()
            )
        }
        _advertisementSets.value = sets
        _allAdvertisementSets = sets
        _selectedSets = sets
        handler.setAdvertisementSets(sets)
    }

    fun setCheckedPayloads(checkedStates: List<Boolean>) {
        _selectedSets = _allAdvertisementSets.filterIndexed { idx, _ -> checkedStates.getOrNull(idx) == true }
    }
} 