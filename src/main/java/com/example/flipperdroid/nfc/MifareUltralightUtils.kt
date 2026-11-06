package com.example.flipperdroid.nfc

import android.nfc.Tag
import android.nfc.tech.MifareUltralight

/**
 * Lecture / écriture / clonage des tags Mifare Ultralight et NTAG21x (compatibles
 * Ultralight). Les pages font 4 octets. Les pages 0-3 (UID/lock/OTP/CC) ne sont jamais
 * écrites par le clonage ; les pages de configuration (lock dynamique, CFG, PWD, PACK)
 * en fin de mémoire ne le sont que si l'option explicite [writeConfigPages] est activée,
 * car leur écriture peut verrouiller ou protéger définitivement la puce.
 */
object MifareUltralightUtils {

    /** Nombre de pages de l'Ultralight classique (MF0ICU1). */
    private const val ULTRALIGHT_PAGES = 16
    /** Nombre de pages de l'Ultralight C (MF0ICU2). */
    private const val ULTRALIGHT_C_PAGES = 48

    /**
     * Plage des pages de verrouillage dynamique / configuration / PWD / PACK selon la taille
     * totale du tag, ou null si la taille n'est pas reconnue.
     * Fonction pure, testée dans NfcLogicTest.
     */
    fun protectedPages(totalPages: Int): IntRange? = when (totalPages) {
        ULTRALIGHT_PAGES -> 2..3        // verrouillage statique + OTP (déjà exclus par le clonage)
        45 -> 40..44                    // NTAG213
        135 -> 130..134                 // NTAG215
        231 -> 226..230                 // NTAG216
        else -> null
    }

    /**
     * Nombre de pages d'un NTAG d'après l'octet "storage size" de la réponse GET_VERSION
     * (octet 6 : 0x0F = NTAG213, 0x11 = NTAG215, 0x13 = NTAG216), ou null si inconnu.
     */
    fun ntagPagesFromStorageSize(storageByte: Int): Int? = when (storageByte and 0xFF) {
        0x0F -> 45
        0x11 -> 135
        0x13 -> 231
        else -> null
    }

    /**
     * Capacité réelle de la puce en pages. GET_VERSION (0x60) est interrogé d'abord : Android
     * signale les NTAG213/215/216 comme TYPE_ULTRALIGHT, donc le type seul ne suffit pas.
     * Repli sur le type si GET_VERSION échoue (Ultralight d'origine : ne supporte pas la commande).
     * Renvoie null si la capacité reste inconnue.
     */
    private fun capacityPages(mfu: MifareUltralight): Int? {
        val version = try { mfu.transceive(byteArrayOf(0x60)) } catch (e: Exception) { null }
        if (version != null && version.size >= 7) {
            ntagPagesFromStorageSize(version[6].toInt())?.let { return it }
        }
        return when (mfu.type) {
            MifareUltralight.TYPE_ULTRALIGHT_C -> ULTRALIGHT_C_PAGES
            MifareUltralight.TYPE_ULTRALIGHT -> ULTRALIGHT_PAGES
            else -> null
        }
    }

    /**
     * Lit toutes les pages disponibles ; renvoie une page hex (8 caractères) par entrée.
     * La lecture s'arrête à la capacité réelle de la puce (GET_VERSION) ou, si elle est
     * inconnue, à la première erreur de lecture.
     */
    fun readDump(tag: Tag): List<String>? {
        val mfu = MifareUltralight.get(tag) ?: return null
        return try {
            mfu.connect()
            val max = capacityPages(mfu) ?: 231 // NTAG216 au maximum ; on s'arrête à la première erreur
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

    /**
     * Écrit un dump (pages 4 octets) sur un tag cible Ultralight/NTAG.
     *
     * Refuse d'écrire si la taille du dump ne correspond pas à la puce cible, ou si la
     * taille est inconnue (zone de configuration non identifiable) sans [writeConfigPages].
     * @param writeConfigPages écrire aussi les pages de verrouillage / configuration / PWD / PACK.
     *   Désactivé par défaut : peut verrouiller ou protéger définitivement la puce.
     */
    fun writeDump(
        tag: Tag,
        pages: List<String>,
        writeConfigPages: Boolean = false
    ): MifareClassicUtils.DumpWriteResult {
        val mfu = MifareUltralight.get(tag)
            ?: return MifareClassicUtils.DumpWriteResult(0, 0, 0, "Cible non Ultralight/NTAG.")
        val reserved = protectedPages(pages.size)
        if (reserved == null && !writeConfigPages) {
            return MifareClassicUtils.DumpWriteResult(
                0, 0, pages.size,
                "Dump de ${pages.size} pages : taille de puce inconnue, zone de configuration " +
                    "non identifiable. Écriture annulée."
            )
        }
        var written = 0
        var failed = 0
        var skipped = 0
        try {
            mfu.connect()
            val target = capacityPages(mfu)
            if (target != null && target != pages.size) {
                return MifareClassicUtils.DumpWriteResult(
                    0, 0, pages.size,
                    "Dump de ${pages.size} pages incompatible avec la puce cible ($target pages). Écriture annulée."
                )
            }
            for (i in pages.indices) {
                if (i < 4) { skipped++; continue } // UID / lock / OTP / CC : jamais écrits
                if (!writeConfigPages && reserved?.contains(i) == true) { skipped++; continue }
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
