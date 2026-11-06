package com.example.flipperdroid.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle

/**
 * Service HCE : rejoue le dialogue APDU d'une carte ISO-DEP capturée
 * (voir [ApduCapture] / [EmulationStore]). À défaut de capture, conserve un stub
 * qui répond « succès » au SELECT AID Visa (démo).
 *
 * Limite plateforme : HCE ne gère que l'ISO-DEP (APDU) — pas le Mifare Classic.
 * Une carte EMV réelle ne peut pas être rejouée pour payer (cryptogramme dynamique).
 *
 * Confidentialité : aucune commande ni réponse n'est journalisée.
 */
class EmvCardEmulationService : HostApduService() {
    companion object {
        private val VISA_AID = byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10)
        private val SELECT_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val UNKNOWN_CMD = byteArrayOf(0x6A.toByte(), 0x82.toByte())
    }

    /** Table capturée : lue une fois au premier APDU, puis gardée en cache. */
    private var replayMap: Map<String, String>? = null

    /** AID sélectionné par le lecteur : sert à retrouver les réponses propres à cet AID. */
    private var selectedAid: String? = null

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) return UNKNOWN_CMD

        // 1) Replay d'un dialogue capturé (émulation HCE ISO-DEP).
        val map = replayMap ?: EmulationStore.map(applicationContext).also { replayMap = it }
        ApduCapture.selectedAidOf(commandApdu)?.let { selectedAid = it }
        val cmdHex = MifareClassicUtils.bytesToHex(commandApdu)
        // Réponse propre à l'AID courant ; à défaut, ancienne clé "commande seule" (données anciennes).
        val scoped = selectedAid?.let { map[ApduCapture.scopedKey(it, cmdHex)] }
        val replay = scoped ?: map[cmdHex]
        if (replay != null) {
            return MifareClassicUtils.hexToBytes(replay) ?: UNKNOWN_CMD
        }

        // 2) Repli : ancien stub Visa (SELECT AID Visa -> succès).
        if (commandApdu.size >= 12) {
            val aid = commandApdu.copyOfRange(5, 12)
            if (aid.contentEquals(VISA_AID)) return SELECT_OK
        }
        return UNKNOWN_CMD
    }

    override fun onDeactivated(reason: Int) {
        // Fin de session de lecture : le prochain SELECT redéfinira l'AID courant.
        selectedAid = null
    }
}
