package com.example.flipperdroid.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.flipperdroid.model.`class`.AdvertisementSetQueueHandler
import com.example.flipperdroid.model.`object`.BluetoothHelpers
import com.example.flipperdroid.model.`object`.ContinuityNewDevicePopUpAdvertisementSetGenerator
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
    private val handler = AdvertisementSetQueueHandler(context, BluetoothHelpers.getAdvertisementService(context))
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
        handler.startSpam(sets)
    }

    fun stopSpam() {
        _isActive.value = false
        handler.onSpamSent = null
        handler.stopSpam()
    }

    private fun loadAdvertisementSets() {
        val sets = when (_brand.value) {
            BleSpamBrand.APPLE -> (
                ContinuityNewDevicePopUpAdvertisementSetGenerator.getAdvertisementSets()
                // + autres générateurs Apple à ajouter ici
            )
            BleSpamBrand.SAMSUNG -> (
                EasySetupWatchAdvertisementSetGenerator.getAdvertisementSets() + EasySetupBudsAdvertisementSetGenerator.getAdvertisementSets()
            )
            BleSpamBrand.MICROSOFT -> SwiftPairAdvertisementSetGenerator.getAdvertisementSets()
            BleSpamBrand.GOOGLE -> FastPairAdvertisementSetGenerator.getAdvertisementSets()
            BleSpamBrand.ALL -> (
                ContinuityNewDevicePopUpAdvertisementSetGenerator.getAdvertisementSets()
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