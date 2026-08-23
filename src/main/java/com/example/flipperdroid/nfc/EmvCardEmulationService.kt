package com.example.flipperdroid.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log

class EmvCardEmulationService : HostApduService() {
    companion object {
        private const val TAG = "EmvCardEmulationService"
        // Exemple d'AID Visa
        private val VISA_AID = byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10)
        private val SELECT_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val UNKNOWN_CMD = byteArrayOf(0x6A.toByte(), 0x82.toByte())
    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        Log.d(TAG, "processCommandApdu: ${commandApdu?.joinToString { String.format("%02X", it) }}")
        // Simple SELECT AID check. Le SELECT par AID Visa fait 12 octets minimum
        // (CLA INS P1 P2 Lc + 7 octets d'AID) ; toute commande plus courte ne peut
        // pas contenir cet AID, on évite ainsi un copyOfRange hors limites.
        if (commandApdu != null && commandApdu.size >= 12) {
            val aid = commandApdu.copyOfRange(5, 12)
            if (aid.contentEquals(VISA_AID)) {
                // Réponse simulée : juste un succès
                return SELECT_OK
            }
        }
        return UNKNOWN_CMD
    }

    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "onDeactivated: $reason")
    }
} 