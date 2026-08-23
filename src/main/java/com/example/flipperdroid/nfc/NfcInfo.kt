package com.example.flipperdroid.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV

/**
 * Analyse « statique » d'un tag NFC : UID, ATQA/SAK, ATS, tailles, type, NDEF…
 * N'ouvre aucune connexion (utilise les infos issues de la découverte).
 */
object NfcInfo {

    fun describe(tag: Tag): String {
        val sb = StringBuilder()
        sb.append("UID: ${MifareClassicUtils.bytesToHex(tag.id)} (${tag.id.size} bytes)\n")
        sb.append("Tech: ${tag.techList.joinToString { it.substringAfterLast('.') }}\n")

        NfcA.get(tag)?.let { a ->
            sb.append("ATQA: ${MifareClassicUtils.bytesToHex(a.atqa)}   SAK: %02X\n".format(a.sak.toInt() and 0xFFFF))
        }
        NfcB.get(tag)?.let { b ->
            b.applicationData?.let { sb.append("NfcB appData: ${MifareClassicUtils.bytesToHex(it)}\n") }
        }
        NfcF.get(tag)?.let { f ->
            f.systemCode?.let { sb.append("FeliCa systemCode: ${MifareClassicUtils.bytesToHex(it)}\n") }
        }
        IsoDep.get(tag)?.let { iso ->
            iso.historicalBytes?.let { sb.append("ATS/historical: ${MifareClassicUtils.bytesToHex(it)}\n") }
            iso.hiLayerResponse?.let { sb.append("ATTRIB/hiLayer: ${MifareClassicUtils.bytesToHex(it)}\n") }
        }
        MifareClassic.get(tag)?.let { m ->
            val type = when (m.type) {
                MifareClassic.TYPE_CLASSIC -> "Classic"
                MifareClassic.TYPE_PLUS -> "Plus"
                MifareClassic.TYPE_PRO -> "Pro"
                else -> "Unknown"
            }
            sb.append("Mifare Classic ($type): ${m.size} bytes, ${m.sectorCount} sectors, ${m.blockCount} blocks\n")
        }
        MifareUltralight.get(tag)?.let { u ->
            val type = when (u.type) {
                MifareUltralight.TYPE_ULTRALIGHT -> "Ultralight"
                MifareUltralight.TYPE_ULTRALIGHT_C -> "Ultralight C"
                else -> "Ultralight/NTAG"
            }
            sb.append("Ultralight type: $type\n")
        }
        NfcV.get(tag)?.let { v ->
            sb.append("ISO15693 DSFID: %02X  responseFlags: %02X\n".format(v.dsfId.toInt() and 0xFF, v.responseFlags.toInt() and 0xFF))
        }
        Ndef.get(tag)?.let { n ->
            sb.append("NDEF: ${n.type}, max ${n.maxSize} B, writable=${n.isWritable}, lockable=${n.canMakeReadOnly()}\n")
        }
        return sb.toString().trimEnd()
    }
}
