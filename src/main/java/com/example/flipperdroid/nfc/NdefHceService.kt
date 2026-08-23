package com.example.flipperdroid.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle

/**
 * Émule une tag NFC Forum Type 4 (HCE) : le téléphone répond comme une tag NDEF
 * en lecture, servant le message configuré (URL/texte) via [NdefHceStore]. Un autre
 * téléphone/lecteur en mode reader lit alors la tag.
 */
class NdefHceService : HostApduService() {

    companion object {
        private val OK = byteArrayOf(0x90.toByte(), 0x00)
        private val FAIL = byteArrayOf(0x6A.toByte(), 0x82.toByte())
        // Capability Container : NDEF file E104, taille max 0x0400, lecture libre, écriture interdite (FF).
        private val CC = byteArrayOf(
            0x00, 0x0F, 0x20, 0x00, 0x3B, 0x00, 0x34,
            0x04, 0x06, 0xE1.toByte(), 0x04, 0x04, 0x00, 0x00, 0xFF.toByte()
        )
    }

    private var selectedFile = 0 // 0 = aucun, 1 = CC (E103), 2 = NDEF (E104)
    private var ndef: ByteArray = ByteArray(0)

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        val cmd = commandApdu ?: return FAIL
        fun b(i: Int) = if (i < cmd.size) cmd[i].toInt() and 0xFF else -1

        // SELECT par nom (application NDEF) : 00 A4 04 ...
        if (cmd.size >= 5 && b(0) == 0x00 && b(1) == 0xA4 && b(2) == 0x04) {
            ndef = NdefHceStore.ndefBytes(applicationContext)
            selectedFile = 0
            return OK
        }
        // SELECT par identifiant de fichier : 00 A4 00 0C 02 <id>
        if (cmd.size >= 7 && b(0) == 0x00 && b(1) == 0xA4 && b(2) == 0x00 && b(3) == 0x0C) {
            val id = (b(5) shl 8) or b(6)
            selectedFile = when (id) { 0xE103 -> 1; 0xE104 -> 2; else -> 0 }
            return if (selectedFile != 0) OK else FAIL
        }
        // READ BINARY : 00 B0 offHi offLo Le
        if (cmd.size >= 5 && b(0) == 0x00 && b(1) == 0xB0) {
            val offset = (b(2) shl 8) or b(3)
            val le = if (b(4) == 0) 256 else b(4)
            val file = when (selectedFile) {
                1 -> CC
                2 -> buildNdefFile()
                else -> return FAIL
            }
            if (offset > file.size) return FAIL
            val end = minOf(offset + le, file.size)
            return file.copyOfRange(offset, end) + OK
        }
        return FAIL
    }

    /** Fichier NDEF = NLEN (2 octets) + message NDEF. */
    private fun buildNdefFile(): ByteArray {
        val n = ndef.size
        return byteArrayOf(((n shr 8) and 0xFF).toByte(), (n and 0xFF).toByte()) + ndef
    }

    override fun onDeactivated(reason: Int) {
        selectedFile = 0
    }
}
