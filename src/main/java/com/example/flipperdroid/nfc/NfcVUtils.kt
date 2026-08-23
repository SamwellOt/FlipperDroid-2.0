package com.example.flipperdroid.nfc

import android.nfc.Tag
import android.nfc.tech.NfcV

/**
 * Lecture / écriture de tags ISO 15693 (NfcV) : ICODE, ST25, badges, bibliothèque…
 * Blocs typiquement de 4 octets. Lecture bloc par bloc jusqu'à la première erreur.
 */
object NfcVUtils {

    /** Lit tous les blocs lisibles ; renvoie un bloc hex par entrée, ou null si non NfcV. */
    fun readDump(tag: Tag): List<String>? {
        val v = NfcV.get(tag) ?: return null
        return try {
            v.connect()
            val blocks = ArrayList<String>()
            var b = 0
            while (b < 256) {
                // READ SINGLE BLOCK : [flags=0x02][0x20][blockNo]
                val resp = try { v.transceive(byteArrayOf(0x02, 0x20, b.toByte())) } catch (e: Exception) { null } ?: break
                // resp[0] = flags de réponse (0 = OK) ; le reste = données du bloc.
                if (resp.size < 2 || (resp[0].toInt() and 0xFF) != 0x00) break
                blocks.add(MifareClassicUtils.bytesToHex(resp.copyOfRange(1, resp.size)))
                b++
            }
            blocks
        } catch (e: Exception) {
            null
        } finally {
            try { v.close() } catch (_: Exception) {}
        }
    }

    /** Écrit un bloc ISO 15693. Best-effort : certains tags exigent le flag "option". */
    fun writeBlock(tag: Tag, block: Int, data: ByteArray): Boolean {
        val v = NfcV.get(tag) ?: return false
        return try {
            v.connect()
            // WRITE SINGLE BLOCK : [flags=0x02][0x21][blockNo][data]
            val cmd = byteArrayOf(0x02, 0x21, block.toByte()) + data
            val resp = try { v.transceive(cmd) } catch (e: Exception) { null }
            if (resp != null && resp.isNotEmpty() && (resp[0].toInt() and 0xFF) == 0x00) return true
            // Retry avec le flag Option (0x40) que certains tags exigent pour l'écriture.
            val cmd2 = byteArrayOf(0x42, 0x21, block.toByte()) + data
            val resp2 = v.transceive(cmd2)
            resp2.isNotEmpty() && (resp2[0].toInt() and 0xFF) == 0x00
        } catch (e: Exception) {
            false
        } finally {
            try { v.close() } catch (_: Exception) {}
        }
    }
}
