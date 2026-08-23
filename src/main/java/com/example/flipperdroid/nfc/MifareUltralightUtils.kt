package com.example.flipperdroid.nfc

import android.nfc.Tag
import android.nfc.tech.MifareUltralight

/**
 * Lecture / écriture / clonage des tags Mifare Ultralight et NTAG21x (compatibles
 * Ultralight). Les pages font 4 octets. Les pages 0-3 (UID/lock/OTP) sont
 * généralement non inscriptibles : le clonage écrit à partir de la page 4.
 */
object MifareUltralightUtils {

    /** Lit toutes les pages disponibles ; renvoie une page hex (8 caractères) par entrée. */
    fun readDump(tag: Tag): List<String>? {
        val mfu = MifareUltralight.get(tag) ?: return null
        return try {
            mfu.connect()
            val max = when (mfu.type) {
                MifareUltralight.TYPE_ULTRALIGHT -> 16
                MifareUltralight.TYPE_ULTRALIGHT_C -> 48
                else -> 231 // NTAG216 max ; on s'arrête à la première erreur
            }
            val pages = ArrayList<String>()
            var i = 0
            while (i < max) {
                val block = try { mfu.readPages(i) } catch (e: Exception) { break } // 16 octets = 4 pages
                var p = 0
                while (p < 4 && pages.size < max) {
                    val start = p * 4
                    if (start + 4 <= block.size) pages.add(MifareClassicUtils.bytesToHex(block.copyOfRange(start, start + 4)))
                    p++
                }
                i += 4
            }
            pages
        } catch (e: Exception) {
            null
        } finally {
            try { mfu.close() } catch (_: Exception) {}
        }
    }

    /** Écrit un dump (pages 4 octets) sur un tag cible Ultralight/NTAG. */
    fun writeDump(tag: Tag, pages: List<String>): MifareClassicUtils.DumpWriteResult {
        val mfu = MifareUltralight.get(tag)
            ?: return MifareClassicUtils.DumpWriteResult(0, 0, 0, "Cible non Ultralight/NTAG.")
        var written = 0
        var failed = 0
        var skipped = 0
        try {
            mfu.connect()
            for (i in pages.indices) {
                if (i < 4) { skipped++; continue } // UID / lock / OTP : non écrits
                val raw = pages[i].replace(" ", "")
                if (raw.length != 8 || raw.all { it == '-' }) { skipped++; continue }
                val bytes = MifareClassicUtils.hexToBytes(raw)
                if (bytes == null || bytes.size != 4) { skipped++; continue }
                try { mfu.writePage(i, bytes); written++ } catch (e: Exception) { failed++ }
            }
        } catch (e: Exception) {
            // ignore ; on renvoie le compte partiel
        } finally {
            try { mfu.close() } catch (_: Exception) {}
        }
        return MifareClassicUtils.DumpWriteResult(
            written, failed, skipped,
            "écrites : $written, échecs : $failed, ignorées : $skipped"
        )
    }
}
