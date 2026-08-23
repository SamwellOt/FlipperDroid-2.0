package com.example.flipperdroid.viewmodel

import android.nfc.Tag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flipperdroid.nfc.EmvCardData
import com.example.flipperdroid.nfc.EmvCardReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
/**
 * ViewModel gerant la lecture des cartes EMV via NFC
 *
 * Cette classe utilise un lecteur EMV pour extraire les donnees de la carte NFC detectee
 * Elle expose les donnees l etat de lecture et les erreurs eventuelles via des StateFlow
 */
class EmvReaderViewModel : ViewModel() {
    private val emvCardReader = EmvCardReader()

    private val _cardData = MutableStateFlow<EmvCardData?>(null)
    val cardData: StateFlow<EmvCardData?> = _cardData

    private val _isReading = MutableStateFlow(false)
    val isReading: StateFlow<Boolean> = _isReading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /**
     * Lance la lecture de la carte NFC a partir du tag recu
     *
     * Met a jour l etat de lecture les donnees recuperees et les erreurs eventuelles
     *
     * @param tag tag NFC detecte par le systeme
     */
    fun readCard(tag: Tag) {
        viewModelScope.launch {
            try {
                _isReading.emit(true)
                _error.emit(null)
                
                val data = emvCardReader.readCard(tag)
                if (data != null) {
                    _cardData.emit(data)
                } else {
                    _error.emit(
                        "No EMV data could be read. Make sure it's a contactless payment card, " +
                            "held steady against the NFC antenna. Some cards refuse contactless reads " +
                            "outside a real terminal."
                    )
                }
            } catch (e: Exception) {
                _error.emit("Error reading card: ${e.message}")
            } finally {
                _isReading.emit(false)
            }
        }
    }

    /**
     * Vide les donnees et erreurs en memoire
     *
     * Remet a null les flux cardData et error
     */
    fun clearData() {
        viewModelScope.launch {
            _cardData.emit(null)
            _error.emit(null)
        }
    }
} 