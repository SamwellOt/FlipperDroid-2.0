package com.example.flipperdroid.viewmodel

import android.app.Application
import android.content.Context
import android.hardware.ConsumerIrManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.flipperdroid.infrared.AcProtocols
import com.example.flipperdroid.infrared.IrProtocols
import com.example.flipperdroid.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Contrôle de climatiseur à état complet (multi-marques). Émet toute la
 * configuration (marche, mode, température, ventilation, oscillation) en une trame.
 * Seul Gree a une structure de trame testée unitairement ; aucun encodage n'est validé
 * sur appareil réel.
 */
class AcControlViewModel(app: Application) : AndroidViewModel(app) {

    private val irManager =
        app.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    val hasEmitter: Boolean get() = irManager?.hasIrEmitter() == true

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    fun send(brand: AcProtocols.Brand, power: Boolean, mode: Int, tempC: Int, fan: Int, swing: Boolean) {
        val manager = irManager
        if (manager == null || !manager.hasIrEmitter()) {
            _status.value = IrProtocols.NO_EMITTER_MSG
            return
        }
        if (mode == AcProtocols.MODE_FAN && !brand.supportsFanMode) {
            _status.value = "${brand.name}: fan-only mode is not supported by this encoder — nothing sent"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (freq, pattern) = AcProtocols.encode(brand, power, mode, tempC, fan, swing)
                manager.transmit(freq, pattern)
                _status.value = "${brand.name} sent: ${if (power) "ON" else "OFF"}, ${tempC}°C, mode=$mode, fan=$fan"
                AppLog.log("AC", "${brand.name} power=$power mode=$mode temp=$tempC fan=$fan swing=$swing")
            } catch (e: Exception) {
                _status.value = "Transmit failed: ${e.message}"
            }
        }
    }
}
