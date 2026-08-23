package com.example.flipperdroid.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log

/**
 * Service HCE : rejoue le dialogue APDU d'une carte ISO-DEP capturée
 * (voir [ApduCapture] / [EmulationStore]). À défaut de capture, conserve un stub
 * qui répond « succès » au SELECT AID Visa (démo).
 *
 * Limite plateforme : HCE ne gère que l'ISO-DEP (APDU) — pas le Mifare Classic.
 * Une carte EMV réelle ne peut pas être rejouée pour payer (cryptogramme dynamique).
 */
class EmvCardEmulationService : HostApduService() {
    companion object {
        private const val TAG = "EmvCardEmulationService"
        private val VISA_AID = byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10)
        private val SELECT_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val UNKNOWN_CMD = byteArrayOf(0x6A.toByte(), 0x82.toByte())
    }

    private var replayMap: Map<String, String> = emptyMap()

    override fun onCreate() {
        super.onCreate()
        replayMap = EmulationStore.map(applicationContext)
    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) return UNKNOWN_CMD
        Log.d(TAG, "APDU: ${ApduCapture.toHex(commandApdu)}")

        // 1) Replay d'un dialogue capturé (émulation HCE ISO-DEP).
        if (replayMap.isEmpty()) replayMap = EmulationStore.map(applicationContext)
        replayMap[ApduCapture.toHex(commandApdu)]?.let {
            return try { ApduCapture.hexToBytes(it) } catch (e: Exception) { UNKNOWN_CMD }
        }

        // 2) Repli : ancien stub Visa (SELECT AID Visa -> succès).
        if (commandApdu.size >= 12) {
            val aid = commandApdu.copyOfRange(5, 12)
            if (aid.contentEquals(VISA_AID)) return SELECT_OK
        }
        return UNKNOWN_CMD
    }

    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "onDeactivated: $reason")
    }
}
