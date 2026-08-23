package com.example.flipperdroid.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Lecteur de cartes bancaires contactless (EMV).
 *
 * Suit le vrai flux EMV plutôt que de deviner des SFI :
 *   1. SELECT PPSE (2PAY.SYS.DDF01) -> découverte des AID (tag 4F)
 *   2. SELECT AID -> FCI (peut contenir un PDOL, tag 9F38)
 *   3. GET PROCESSING OPTIONS (GPO) -> AIP + AFL (tag 94, ou template 80/77)
 *   4. READ RECORD sur chaque enregistrement pointé par l'AFL
 *   5. Parsing BER-TLV -> PAN (5A), expiration (5F24), porteur (5F20), Track2 (57)
 * Repli : lecture "brute" des SFI 1..10 si le GPO échoue.
 *
 * NB : à usage de test/recherche autorisé uniquement.
 */
class EmvCardReader {

    companion object {
        private const val TAG = "EmvCardReader"

        /** AID de repli si le PPSE ne renvoie rien d'exploitable. */
        private val KNOWN_AIDS = listOf(
            "A0000000041010", // Mastercard
            "A0000000031010", // Visa
            "A0000000651010", // JCB
            "A0000000042203", // Maestro
            "A0000000043060", // Maestro UK
            "A000000025010801", // Amex
            "A0000003330101"  // UnionPay
        )

        private val AID_MAP = mapOf(
            "A0000000041010" to "Mastercard",
            "A0000000031010" to "Visa",
            "A0000000651010" to "JCB",
            "A0000000042203" to "Maestro",
            "A0000000043060" to "Maestro UK",
            "A000000025010801" to "American Express",
            "A0000003330101" to "UnionPay"
        )

        private const val SELECT_PPSE = "2PAY.SYS.DDF01"
    }

    private var isoDep: IsoDep? = null

    /**
     * Lit une carte EMV à partir d'un tag NFC.
     * @return les données extraites, ou null si aucune donnée EMV exploitable.
     */
    suspend fun readCard(tag: Tag): EmvCardData? = withContext(Dispatchers.IO) {
        val iso = IsoDep.get(tag) ?: return@withContext null
        isoDep = iso
        try {
            if (!iso.isConnected) iso.connect()
            iso.timeout = 5000

            // 1) PPSE -> AID candidats (ordre : ceux de la carte d'abord)
            val aids = LinkedHashSet<String>()
            val ppse = selectPpse(iso)
            if (ppse != null && isSuccessful(ppse)) {
                tlvFindAll(ppse, 0x4F).forEach { aids.add(bytesToHexString(it)) }
            }
            KNOWN_AIDS.forEach { aids.add(it) }

            for (aidHex in aids) {
                val card = try { tryAid(iso, aidHex) } catch (e: Exception) {
                    Log.e(TAG, "AID $aidHex failed", e); null
                }
                if (card != null && card.isValid()) return@withContext card
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error reading EMV card", e)
            null
        } finally {
            try { iso.close() } catch (_: Exception) {}
        }
    }

    /** Sélectionne un AID, tente le GPO puis lit les enregistrements et parse les données. */
    private fun tryAid(iso: IsoDep, aidHex: String): EmvCardData? {
        val selResp = transceive(iso, buildSelectCommand(aidHex.hexToByteArray())) ?: return null
        if (!isSuccessful(selResp)) return null

        val cardData = EmvCardData()
        cardData.cardType = AID_MAP[aidHex] ?: brandFromAid(aidHex)

        val pool = ByteArrayOutputStream()
        fun collect(resp: ByteArray) { if (resp.size >= 2) pool.write(resp, 0, resp.size - 2) }

        // GPO (avec le PDOL demandé par la carte, rempli de valeurs par défaut)
        val pdol = tlvFindFirst(selResp, 0x9F38)
        val gpoResp = gpo(iso, pdol)
        var readAny = false
        if (gpoResp != null && isSuccessful(gpoResp)) {
            collect(gpoResp)
            val afl = tlvFindFirst(gpoResp, 0x94) ?: run {
                // Format 1 : template 80 = AIP(2 octets) + AFL
                val fmt1 = tlvFindFirst(gpoResp, 0x80)
                if (fmt1 != null && fmt1.size > 2) fmt1.copyOfRange(2, fmt1.size) else null
            }
            if (afl != null) {
                var i = 0
                while (i + 3 < afl.size) {
                    val sfi = (afl[i].toInt() and 0xFF) shr 3
                    val first = afl[i + 1].toInt() and 0xFF
                    val last = afl[i + 2].toInt() and 0xFF
                    if (sfi in 1..30 && first in 1..255 && last in first..255) {
                        for (rec in first..last) {
                            val r = readRecord(iso, sfi, rec)
                            if (r != null && isSuccessful(r)) { collect(r); readAny = true }
                        }
                    }
                    i += 4
                }
            }
        }

        // Repli : lecture brute des SFI/enregistrements courants
        if (!readAny) {
            for (sfi in 1..10) {
                for (rec in 1..10) {
                    val r = readRecord(iso, sfi, rec)
                    if (r != null && isSuccessful(r)) collect(r)
                }
            }
        }

        parseEmv(pool.toByteArray(), cardData)
        return if (cardData.isValid()) cardData else null
    }

    private fun selectPpse(iso: IsoDep): ByteArray? =
        transceive(iso, buildSelectCommand(SELECT_PPSE.toByteArray()))

    /** GET PROCESSING OPTIONS (CLA 80, INS A8). */
    private fun gpo(iso: IsoDep, pdol: ByteArray?): ByteArray? {
        val pdolData = buildPdolData(pdol)
        val body = ByteArrayOutputStream().apply {
            write(0x83)              // tag 83 : Command Template
            write(pdolData.size)
            write(pdolData)
        }.toByteArray()
        val apdu = ByteArrayOutputStream().apply {
            write(0x80); write(0xA8); write(0x00); write(0x00)
            write(body.size); write(body); write(0x00)
        }.toByteArray()
        return transceive(iso, apdu)
    }

    /** Construit les données du PDOL demandé, avec des valeurs par défaut raisonnables. */
    private fun buildPdolData(pdol: ByteArray?): ByteArray {
        if (pdol == null || pdol.isEmpty()) return ByteArray(0)
        val out = ByteArrayOutputStream()
        var i = 0
        try {
            while (i < pdol.size) {
                var tag = pdol[i].toInt() and 0xFF; i++
                if ((tag and 0x1F) == 0x1F) {
                    while (i < pdol.size) {
                        val b = pdol[i].toInt() and 0xFF; tag = (tag shl 8) or b; i++
                        if (b and 0x80 == 0) break
                    }
                }
                if (i >= pdol.size) break
                val len = pdol[i].toInt() and 0xFF; i++
                out.write(defaultPdolValue(tag, len))
            }
        } catch (_: Exception) { /* PDOL malformé : on renvoie ce qu'on a */ }
        return out.toByteArray()
    }

    /** Valeur par défaut pour une entrée PDOL (zéros, sauf quelques champs terminal utiles). */
    private fun defaultPdolValue(tag: Int, len: Int): ByteArray {
        val v = ByteArray(if (len in 0..255) len else 0)
        when (tag) {
            0x9F66 -> if (v.isNotEmpty()) v[0] = 0x36.toByte() // TTQ : mode EMV contactless supporté
            0x9F35 -> if (v.isNotEmpty()) v[0] = 0x22.toByte() // Terminal Type
        }
        return v
    }

    /** READ RECORD (CLA 00, INS B2), P2 = (SFI << 3) | 4. */
    private fun readRecord(iso: IsoDep, sfi: Int, record: Int): ByteArray? {
        val command = byteArrayOf(
            0x00,
            0xB2.toByte(),
            record.toByte(),
            ((sfi shl 3) or 4).toByte(),
            0x00
        )
        return transceive(iso, command)
    }

    private fun transceive(iso: IsoDep, apdu: ByteArray): ByteArray? =
        try { iso.transceive(apdu) } catch (e: Exception) { null }

    /** Construit une commande SELECT (CLA 00, INS A4, P1 04, P2 00). */
    private fun buildSelectCommand(data: ByteArray): ByteArray =
        ByteArrayOutputStream().apply {
            write(0x00); write(0xA4); write(0x04); write(0x00)
            write(data.size); write(data); write(0x00)
        }.toByteArray()

    /** Extrait PAN / expiration / porteur des TLV collectés. */
    private fun parseEmv(data: ByteArray, cardData: EmvCardData) {
        tlvFindFirst(data, 0x5A)?.let {
            cardData.pan = formatPan(bytesToHexString(it).trimEnd('F', 'f'))
        }
        tlvFindFirst(data, 0x5F24)?.let {
            cardData.expiryDate = formatExpiryDate(bytesToHexString(it))
        }
        tlvFindFirst(data, 0x5F20)?.let {
            val name = String(it, Charsets.UTF_8).trim()
            if (name.isNotBlank()) cardData.cardholderName = name
        }
        // Track2 (tag 57) : repli pour PAN/expiration
        if (cardData.pan == null || cardData.expiryDate == null) {
            tlvFindFirst(data, 0x57)?.let { t2 ->
                val s = bytesToHexString(t2)
                val sep = s.indexOfFirst { it == 'D' || it == 'd' }
                if (sep > 0) {
                    if (cardData.pan == null) cardData.pan = formatPan(s.substring(0, sep))
                    if (cardData.expiryDate == null && s.length >= sep + 5) {
                        cardData.expiryDate = formatExpiryDate(s.substring(sep + 1, sep + 5))
                    }
                }
            }
        }
        // Nom applicatif (label) si pas de type déjà défini
        if (cardData.cardType.isNullOrBlank()) {
            (tlvFindFirst(data, 0x50) ?: tlvFindFirst(data, 0x9F12))?.let {
                val label = String(it, Charsets.UTF_8).trim()
                if (label.isNotBlank()) cardData.cardType = label
            }
        }
    }

    private fun brandFromAid(aidHex: String): String {
        val a = aidHex.uppercase()
        return when {
            a.startsWith("A000000004") -> "Mastercard"
            a.startsWith("A000000003") -> "Visa"
            a.startsWith("A000000025") -> "American Express"
            a.startsWith("A000000065") -> "JCB"
            a.startsWith("A000000333") -> "UnionPay"
            else -> "EMV card"
        }
    }

    private fun formatPan(pan: String): String = pan.chunked(4).joinToString(" ")

    /** date "YYMM[DD]" -> "MM/20YY". */
    private fun formatExpiryDate(date: String): String = try {
        val year = date.substring(0, 2)
        val month = date.substring(2, 4)
        "$month/20$year"
    } catch (e: Exception) { date }

    private fun isSuccessful(response: ByteArray): Boolean =
        response.size >= 2 &&
            response[response.size - 2] == 0x90.toByte() &&
            response[response.size - 1] == 0x00.toByte()

    // --- BER-TLV ---

    /** Renvoie toutes les valeurs (récursivement) pour un tag donné. */
    private fun tlvFindAll(data: ByteArray, wanted: Int): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        try { tlvWalk(data, 0, data.size, wanted, out) } catch (_: Exception) {}
        return out
    }

    private fun tlvFindFirst(data: ByteArray, wanted: Int): ByteArray? =
        tlvFindAll(data, wanted).firstOrNull()

    private fun tlvWalk(data: ByteArray, from: Int, to: Int, wanted: Int, out: MutableList<ByteArray>) {
        var i = from
        while (i < to) {
            val first = data[i].toInt() and 0xFF
            if (first == 0x00 || first == 0xFF) { i++; continue } // padding
            var tag = first; i++
            if ((first and 0x1F) == 0x1F) {
                while (i < to) {
                    val b = data[i].toInt() and 0xFF; tag = (tag shl 8) or b; i++
                    if (b and 0x80 == 0) break
                }
            }
            if (i >= to) break
            val lenByte = data[i].toInt() and 0xFF; i++
            val len: Int
            if (lenByte and 0x80 == 0) {
                len = lenByte
            } else {
                val n = lenByte and 0x7F
                if (n == 0 || n > 4 || i + n > to) break
                var acc = 0
                repeat(n) { acc = (acc shl 8) or (data[i].toInt() and 0xFF); i++ }
                len = acc
            }
            if (len < 0 || i + len > to) break
            val value = data.copyOfRange(i, i + len)
            if (tag == wanted) out.add(value)
            if ((first and 0x20) != 0) tlvWalk(value, 0, value.size, wanted, out) // constructed
            i += len
        }
    }

    private fun String.hexToByteArray(): ByteArray {
        val clean = this.filter { !it.isWhitespace() }
        val data = ByteArray(clean.length / 2)
        var i = 0
        while (i + 1 < clean.length) {
            data[i / 2] = ((Character.digit(clean[i], 16) shl 4) +
                    Character.digit(clean[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun bytesToHexString(bytes: ByteArray): String {
        val hex = StringBuilder(bytes.size * 2)
        for (b in bytes) hex.append(String.format("%02X", b))
        return hex.toString()
    }
}

/**
 * Données extraites d'une carte EMV.
 */
data class EmvCardData(
    var pan: String? = null,
    var expiryDate: String? = null,
    var cardholderName: String? = null,
    var cardType: String? = null
) {
    fun isValid(): Boolean = !pan.isNullOrEmpty() && !expiryDate.isNullOrEmpty()

    fun isComplete(): Boolean = isValid() && !cardType.isNullOrEmpty()

    override fun toString(): String {
        val sb = StringBuilder()
        cardType?.let { sb.appendLine("Type: $it") }
        pan?.let { sb.appendLine("Card Number: $it") }
        expiryDate?.let { sb.appendLine("Expires: $it") }
        cardholderName?.let { sb.appendLine("Cardholder: $it") }
        return sb.toString()
    }
}
