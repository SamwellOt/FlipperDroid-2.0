package com.example.flipperdroid.nfc

import android.nfc.tech.IsoDep
import java.io.ByteArrayOutputStream

/**
 * Sonde une carte ISO-DEP et enregistre son dialogue APDU (commande -> réponse)
 * pour le rejouer ensuite via HCE. Best-effort : ne capture que ce que la carte
 * répond à un jeu de commandes standard (SELECT PPSE/PSE/AID connus, GPO, READ RECORD).
 */
object ApduCapture {

    data class Result(val aids: List<String>, val map: Map<String, String>)

    private val PROBE_AIDS = listOf(
        "325041592E5359532E4444463031", // 2PAY.SYS.DDF01 (PPSE)
        "315041592E5359532E4444463031", // 1PAY.SYS.DDF01 (PSE)
        "A0000000031010", "A0000000041010", "A0000000651010",
        "A0000000042203", "A0000000043060", "A000000025010801", "A0000003330101"
    )

    fun probe(iso: IsoDep): Result {
        val map = LinkedHashMap<String, String>()
        val aids = LinkedHashSet<String>()
        if (!iso.isConnected) iso.connect()
        iso.timeout = 5000
        for (aidHex in PROBE_AIDS) {
            val cmd = buildSelect(hexToBytes(aidHex))
            val resp = try { iso.transceive(cmd) } catch (e: Exception) { null } ?: continue
            map[toHex(cmd)] = toHex(resp)
            val isDirectory = aidHex.startsWith("325041") || aidHex.startsWith("315041")
            if (isOk(resp) && !isDirectory) {
                aids.add(aidHex)
                probeAfterSelect(iso, map)
            }
        }
        return Result(aids.toList(), map)
    }

    /** Enrichit la capture après un SELECT AID réussi : GPO + quelques READ RECORD. */
    private fun probeAfterSelect(iso: IsoDep, map: MutableMap<String, String>) {
        val gpo = byteArrayOf(0x80.toByte(), 0xA8.toByte(), 0x00, 0x00, 0x02, 0x83.toByte(), 0x00, 0x00)
        try { map[toHex(gpo)] = toHex(iso.transceive(gpo)) } catch (_: Exception) {}
        for (sfi in 1..3) {
            for (rec in 1..5) {
                val cmd = byteArrayOf(0x00, 0xB2.toByte(), rec.toByte(), ((sfi shl 3) or 4).toByte(), 0x00)
                try {
                    val r = iso.transceive(cmd)
                    if (isOk(r)) map[toHex(cmd)] = toHex(r)
                } catch (_: Exception) {}
            }
        }
    }

    private fun buildSelect(aid: ByteArray): ByteArray =
        ByteArrayOutputStream().apply {
            write(0x00); write(0xA4); write(0x04); write(0x00)
            write(aid.size); write(aid); write(0x00)
        }.toByteArray()

    private fun isOk(r: ByteArray): Boolean =
        r.size >= 2 && r[r.size - 2] == 0x90.toByte() && r[r.size - 1] == 0x00.toByte()

    fun hexToBytes(hex: String): ByteArray {
        val clean = hex.filter { !it.isWhitespace() }
        val out = ByteArray(clean.length / 2)
        var i = 0
        while (i + 1 < clean.length) {
            out[i / 2] = ((Character.digit(clean[i], 16) shl 4) +
                    Character.digit(clean[i + 1], 16)).toByte()
            i += 2
        }
        return out
    }

    fun toHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) sb.append(String.format("%02X", b))
        return sb.toString()
    }
}
