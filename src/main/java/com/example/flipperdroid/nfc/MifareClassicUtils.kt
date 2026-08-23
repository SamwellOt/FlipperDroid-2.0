package com.example.flipperdroid.nfc

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.util.Log
import java.io.IOException

object MifareClassicUtils {
    const val NO_DATA = "--------------------------------"
    const val DEFAULT_KEY = "FFFFFFFFFFFF"

    /**
     * Dictionnaire de clés Mifare Classic répandues (attaque par dictionnaire).
     * Une vraie récupération de clés (Nested/Darkside) n'est pas possible via
     * l'API NFC d'Android : on ne peut que tester des clés connues.
     */
    val COMMON_KEYS = listOf(
        "FFFFFFFFFFFF", "000000000000", "A0A1A2A3A4A5", "B0B1B2B3B4B5",
        "C0C1C2C3C4C5", "D0D1D2D3D4D5", "A0B0C0D0E0F0", "A1B1C1D1E1F1",
        "AABBCCDDEEFF", "1A2B3C4D5E6F", "123456789ABC", "010203040506",
        "D3F7D3F7D3F7", "4D3A99C351DD", "1A982C7E459A", "5C8FF9990DA2",
        "D01AFEEB890A", "75CCB59C9BED", "FC00018778F7", "6471A5EF2D1A",
        "4B791BEA7BCC", "2E4C7C8ED17D", "F2E1AA3B33F5", "000000000001",
        "B127C6F41436", "A9C9321E0000", "484558414354", "44FF292317DA"
    )

    fun bytesToHex(bytes: ByteArray?): String {
        if (bytes == null) return ""
        return bytes.joinToString("") { "%02X".format(it) }
    }

    fun hexToBytes(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        return try {
            hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (e: Exception) {
            Log.d("Debug","Error to convert hex to byte: ${e.message}")
            null
        }
    }


    fun readSector(tag: Tag, sectorIndex: Int, key: ByteArray, useAsKeyB: Boolean): List<String>? {
        val mfc = MifareClassic.get(tag) ?: return null
        try {
            mfc.connect()
            val auth = if (useAsKeyB) mfc.authenticateSectorWithKeyB(sectorIndex, key)
            else mfc.authenticateSectorWithKeyA(sectorIndex, key)
            if (!auth) return null
            val firstBlock = mfc.sectorToBlock(sectorIndex)
            val blockCount = mfc.getBlockCountInSector(sectorIndex)
            val blocks = mutableListOf<String>()
            for (i in 0 until blockCount) {
                try {
                    val blockBytes = mfc.readBlock(firstBlock + i)
                    blocks.add(bytesToHex(blockBytes))
                } catch (e: IOException) {
                    blocks.add(NO_DATA)
                }
            }
            return blocks
        } catch (e: Exception) {
            return null
        } finally {
            try { mfc.close() } catch (_: Exception) {}
        }
    }

    fun writeBlock(tag: Tag, sectorIndex: Int, blockIndex: Int, data: ByteArray, key: ByteArray, useAsKeyB: Boolean): Boolean {
        val mfc = MifareClassic.get(tag) ?: return false
        try {
            mfc.connect()
            val auth = if (useAsKeyB) mfc.authenticateSectorWithKeyB(sectorIndex, key)
            else mfc.authenticateSectorWithKeyA(sectorIndex, key)
            if (!auth) return false
            val block = mfc.sectorToBlock(sectorIndex) + blockIndex
            mfc.writeBlock(block, data)
            return true
        } catch (e: Exception) {
            return false
        } finally {
            try { mfc.close() } catch (_: Exception) {}
        }
    }

    fun cloneUid(tag: Tag, newUid: ByteArray): Boolean {
        // ATTENTION: Cette opération ne fonctionne que sur les cartes "magic" (génériques) !
        // On tente d'écrire le block 0 (UID) avec la nouvelle valeur
        val mfc = MifareClassic.get(tag) ?: return false
        try {
            mfc.connect()
            // Authentification avec le key A par défaut
            val auth = mfc.authenticateSectorWithKeyA(0, hexToBytes(DEFAULT_KEY) ?: return false)
            if (!auth) return false
            val block0 = mfc.readBlock(0)
            val newBlock0 = newUid + block0.copyOfRange(newUid.size, block0.size)
            mfc.writeBlock(0, newBlock0)
            return true
        } catch (e: Exception) {
            return false
        } finally {
            try { mfc.close() } catch (_: Exception) {}
        }
    }

    /** Résultat d'une réécriture de dump complet sur une carte cible. */
    data class DumpWriteResult(
        val written: Int,
        val failed: Int,
        val skipped: Int,
        val message: String
    )

    /**
     * Réécrit un dump Mifare Classic complet sur la carte présentée (reproduction/clonage).
     *
     * @param blocks dump source, un bloc hex (32 caractères) par index global, ou [NO_DATA].
     * @param writeTrailers réécrire aussi les blocs "sector trailer" (clés + bits d'accès).
     *   Désactivé par défaut : réécrire un trailer avec de mauvais bits d'accès peut verrouiller
     *   définitivement le secteur.
     * @param writeSectorZero tenter d'écrire le bloc 0 (UID) — ne fonctionne que sur cartes "magic".
     *
     * NB : ne fonctionne que sur des cartes Mifare Classic inscriptibles (magic/vierges).
     * Android ne peut PAS émuler du Mifare Classic (HCE = ISO-DEP uniquement).
     */
    fun writeDump(
        tag: Tag,
        blocks: List<String>,
        writeTrailers: Boolean = false,
        writeSectorZero: Boolean = true
    ): DumpWriteResult {
        val mfc = MifareClassic.get(tag)
            ?: return DumpWriteResult(0, 0, 0, "Carte cible non Mifare Classic (ou puce non compatible).")
        var written = 0
        var failed = 0
        var skipped = 0
        val notes = StringBuilder()
        try {
            mfc.connect()
            for (sector in 0 until mfc.sectorCount) {
                val firstBlock = mfc.sectorToBlock(sector)
                val count = mfc.getBlockCountInSector(sector)
                val srcTrailer = blocks.getOrNull(firstBlock + count - 1)
                var authed = false
                for (key in keyCandidates(srcTrailer)) {
                    val kb = hexToBytes(key) ?: continue
                    try {
                        if (mfc.authenticateSectorWithKeyA(sector, kb) ||
                            mfc.authenticateSectorWithKeyB(sector, kb)) { authed = true; break }
                    } catch (_: Exception) {
                        try { mfc.close(); mfc.connect() } catch (_: Exception) {}
                    }
                }
                if (!authed) { failed += count; notes.append("Secteur $sector : authentification échouée\n"); continue }

                for (i in 0 until count) {
                    val global = firstBlock + i
                    val raw = blocks.getOrNull(global)?.replace(" ", "")
                    if (raw == null || raw == NO_DATA || raw.length != 32) { skipped++; continue }
                    val isTrailer = i == count - 1
                    val isBlock0 = global == 0
                    if (isBlock0 && !writeSectorZero) { skipped++; continue }
                    if (isTrailer && !writeTrailers) { skipped++; continue }
                    val bytes = hexToBytes(raw)
                    if (bytes == null || bytes.size != 16) { skipped++; continue }
                    try {
                        mfc.writeBlock(global, bytes); written++
                    } catch (e: Exception) {
                        failed++
                        if (isBlock0) notes.append("Bloc 0 (UID) non inscriptible — carte non magic\n")
                    }
                }
            }
        } catch (e: Exception) {
            notes.append("Erreur : ${e.message}\n")
        } finally {
            try { mfc.close() } catch (_: Exception) {}
        }
        val msg = "écrits : $written, échecs : $failed, ignorés : $skipped" +
            (if (notes.isNotEmpty()) "\n$notes" else "")
        return DumpWriteResult(written, failed, skipped, msg.trim())
    }

    /** Clés à essayer pour authentifier la cible : défaut, clés du trailer source, dictionnaire. */
    private fun keyCandidates(srcTrailer: String?): List<String> {
        val list = mutableListOf(DEFAULT_KEY)
        if (srcTrailer != null && srcTrailer != NO_DATA) {
            val clean = srcTrailer.replace(" ", "")
            if (clean.length == 32) {
                list.add(clean.substring(0, 12))   // Key A
                list.add(clean.substring(20, 32))  // Key B
            }
        }
        list.addAll(COMMON_KEYS)
        return list.distinct()
    }
} 