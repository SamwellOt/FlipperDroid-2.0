package com.example.flipperdroid.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Lit les cartes bancaires compatibles EMV
 */
class EmvCardReader {

    companion object {
        private const val TAG = "EmvCardReader"

        /**
         * Liste des AID connus pour les cartes EMV
         */
        private val KNOWN_AIDS = arrayOf(
            "A0000000041010", // Mastercard
            "A0000000031010", // Visa
            "A0000000651010", // JCB
            "A0000000042203", // Maestro
            "A0000000043060"  // Maestro UK
        )

        /**
         * Map associant chaque AID à son type de carte
         */
        private val AID_MAP = mapOf(
            "A0000000041010" to "Mastercard",
            "A0000000031010" to "Visa",
            "A0000000651010" to "JCB",
            "A0000000042203" to "Maestro",
            "A0000000043060" to "Maestro UK"
        )

        private const val SELECT_PPSE = "2PAY.SYS.DDF01"
        private const val CLA_ISO = 0x00.toByte()
        private const val INS_SELECT = 0xA4.toByte()
        private const val P1_SELECT = 0x04.toByte()
        private const val P2_SELECT = 0x00.toByte()
    }

    private var isoDep: IsoDep? = null

    /**
     * Lit les données d'une carte EMV à partir d un tag NFC
     *
     * @param tag tag NFC a lire
     * @return les données de la carte ou null si echec
     */
    suspend fun readCard(tag: Tag): EmvCardData? = withContext(Dispatchers.IO) {
        try {
            isoDep = IsoDep.get(tag)
            isoDep?.let { iso ->
                if (!iso.isConnected) {
                    iso.connect()
                }
                val ppseResponse = selectPPSE(iso)
                if (ppseResponse == null) {
                    Log.e(TAG, "Failed to select PPSE")
                    return@withContext null
                }

                for (aid in KNOWN_AIDS) {
                    try {
                        val selectResponse = iso.transceive(buildSelectCommand(aid.hexToByteArray()))
                        if (isSuccessful(selectResponse)) {
                            val cardData = readCardData(iso, aid)
                            if (cardData != null) {
                                return@withContext cardData
                            }
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Error selecting AID: $aid", e)
                        continue
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error reading EMV card", e)
        } finally {
            try {
                isoDep?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing IsoDep", e)
            }
        }
        null
    }

    /**
     * Lit les donnéesd'une carte a partir de son AID
     *
     * @param iso canal de communication IsoDep ouvert
     * @param aid identifiant d application de la carte
     * @return données de carte ou null
     */
    private fun readCardData(iso: IsoDep, aid: String): EmvCardData? {
        val cardData = EmvCardData()
        cardData.cardType = AID_MAP[aid] ?: "Unknown"

        // Sélection de l'application EMV
        // Lecture du premier enregistrement (SFI 1, Record 1 à 10)
        for (record in 1..10) {
            val data = readRecord(iso, 1, record)
            if (data != null) {
                parseRecordData(data, cardData)
                if (cardData.isComplete()) break
            }
        }
        // Lecture du Track2 (SFI 2, Record 1 à 10)
        for (record in 1..10) {
            val data = readRecord(iso, 2, record)
            if (data != null) {
                parseTrack2Data(data, cardData)
                if (cardData.isComplete()) break
            }
        }
        return if (cardData.isValid()) cardData else null
    }

    /**
     * Envoie une commande SELECT PPSE a la carte
     *
     * @param iso canal IsoDep
     * @return reponse brute ou null si erreur
     */
    private fun selectPPSE(iso: IsoDep): ByteArray? {
        val command = buildSelectCommand(SELECT_PPSE.toByteArray())
        return try {
            iso.transceive(command)
        } catch (e: IOException) {
            Log.e(TAG, "Error selecting PPSE", e)
            null
        }
    }

    /**
     * Lit un enregistrement specifique de la carte
     *
     * @param iso canal IsoDep
     * @param sfi identifiant du fichier
     * @param record numero de l enregistrement
     * @return tableau de données ou null
     */
    private fun readRecord(iso: IsoDep, sfi: Int, record: Int): ByteArray? {
        val command = ByteArray(5)
        command[0] = CLA_ISO
        command[1] = 0xB2.toByte()
        command[2] = record.toByte()
        command[3] = (sfi shl 3 or 4).toByte()
        command[4] = 0x00

        return try {
            val response = iso.transceive(command)
            if (isSuccessful(response)) response else null
        } catch (e: IOException) {
            null
        }
    }

    /**
     * Analyse les données recues d'un enregistrement EMV pour extraire PAN et date
     *
     * @param data tableau binaire des données
     * @param cardData objet contenant les champs extraits
     */
    private fun parseRecordData(data: ByteArray, cardData: EmvCardData) {
        // Recherche des tags 0x5A (PAN), 0x5F24 (date d'expiration) et 0x5F20 (porteur).
        // NB : la longueur TLV doit être lue en non signé (and 0xFF) et bornée, sinon
        // une donnée tronquée/malformée ferait planter copyOfRange.
        var i = 0
        while (i < data.size) {
            val tag = data[i].toInt() and 0xFF
            when {
                tag == 0x5A -> { // PAN
                    val len = data.getOrNull(i + 1)?.toInt()?.and(0xFF) ?: break
                    val end = i + 2 + len
                    if (len <= 0 || end > data.size) { i++; continue }
                    cardData.pan = formatPan(bytesToHexString(data.copyOfRange(i + 2, end)))
                    i = end
                }
                tag == 0x5F -> {
                    val subTag = data.getOrNull(i + 1)?.toInt()?.and(0xFF) ?: break
                    when (subTag) {
                        0x24 -> { // Expiry date (5F24)
                            val len = data.getOrNull(i + 2)?.toInt()?.and(0xFF) ?: break
                            val end = i + 3 + len
                            if (len <= 0 || end > data.size) { i++; continue }
                            cardData.expiryDate = formatExpiryDate(bytesToHexString(data.copyOfRange(i + 3, end)))
                            i = end
                        }
                        0x20 -> { // Cardholder name (5F20)
                            val len = data.getOrNull(i + 2)?.toInt()?.and(0xFF) ?: break
                            val end = i + 3 + len
                            if (len <= 0 || end > data.size) { i++; continue }
                            cardData.cardholderName = data.copyOfRange(i + 3, end).toString(Charsets.UTF_8).trim()
                            i = end
                        }
                        else -> i++
                    }
                }
                else -> i++
            }
        }
    }

    /**
     * Extrait les informations du champ Track2 (tag 0x57)
     *
     * @param data tableau binaire du champ
     * @param cardData objet contenant les données extraites
     */
    private fun parseTrack2Data(data: ByteArray, cardData: EmvCardData) {
        // Recherche du tag 0x57 (Track2 Equivalent Data)
        var i = 0
        while (i < data.size) {
            val tag = data[i].toInt() and 0xFF
            if (tag == 0x57) {
                val len = data.getOrNull(i + 1)?.toInt()?.and(0xFF) ?: break
                val end = i + 2 + len
                if (len <= 0 || end > data.size) break
                val track2 = data.copyOfRange(i + 2, end)
                val track2Str = bytesToHexString(track2)
                // Format Track2 : PAN=valeur, séparateur D, date d'expiration (YYMM)
                val parts = track2Str.split("D")
                if (parts.size >= 2) {
                    cardData.pan = formatPan(parts[0])
                    if (parts[1].length >= 4) {
                        val date = parts[1].substring(0, 4)
                        cardData.expiryDate = formatExpiryDate(date)
                    }
                }
                break
            } else {
                i++
            }
        }
    }

    /**
     * Formate le numero de carte en groupes de 4 chiffres separes par un espace
     *
     * @param pan numero de carte brute
     * @return pan formate
     */
    private fun formatPan(pan: String): String {
        return pan.chunked(4).joinToString(" ")
    }

    /**
     * Donne la date d expiration en format MM/YYYY
     *
     * @param date date brute
     * @return date formatee
     */
    private fun formatExpiryDate(date: String): String {
        return try {
            val month = date.substring(2, 4)
            val year = date.substring(0, 2)
            "$month/20$year"
        } catch (e: Exception) {
            date
        }
    }

    /**
     * Cree une commande SELECT pour une application EMV
     *
     * @param data tableau de données de l AID
     * @return tableau binaire de la commande
     */
    private fun buildSelectCommand(data: ByteArray): ByteArray {
        return ByteArrayOutputStream().apply {
            write(CLA_ISO.toInt())
            write(INS_SELECT.toInt())
            write(P1_SELECT.toInt())
            write(P2_SELECT.toInt())
            write(data.size)
            write(data)
            write(0x00)
        }.toByteArray()
    }

    /**
     * Verifie si la reponse de la carte est un success
     *
     * @param response tableau de reponse brute
     * @return vrai si la reponse contient 0x9000
     */
    private fun isSuccessful(response: ByteArray): Boolean {
        return response.size >= 2 &&
                response[response.size - 2] == 0x90.toByte() &&
                response[response.size - 1] == 0x00.toByte()
    }

    /**
     * Convertit une chaine hexadecimal en tableau de bytes
     *
     * @receiver chaine hexadecimale
     * @return tableau binaire
     */
    private fun String.hexToByteArray(): ByteArray {
        val len = this.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(this[i], 16) shl 4) +
                    Character.digit(this[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    /**
     * Convertit un tableau de bytes en chaine hexadecimale
     *
     * @param bytes tableau binaire
     * @return chaine hexadecimale
     */
    private fun bytesToHexString(bytes: ByteArray): String {
        val hex = StringBuilder()
        for (b in bytes) {
            hex.append(String.format("%02X", b))
        }
        return hex.toString()
    }
}

/**
 * données extraites d une carte EMV
 */
data class EmvCardData(
    var pan: String? = null,
    var expiryDate: String? = null,
    var cardholderName: String? = null,
    var cardType: String? = null
) {
    /**
     * Verifie si le numero et la date sont valides
     *
     * @return vrai si pan et date sont presents
     */
    fun isValid(): Boolean {
        return !pan.isNullOrEmpty() && !expiryDate.isNullOrEmpty()
    }

    /**
     * Verifie si toutes les données de la carte sont presentes
     *
     * @return vrai si toutes les données sont remplies
     */
    fun isComplete(): Boolean {
        return isValid() && !cardType.isNullOrEmpty()
    }

    /**
     * Retourne les informations de la cartes en texte
     *
     * @return chaine formattée des informations de carte
     */
    override fun toString(): String {
        val sb = StringBuilder()
        cardType?.let { sb.appendLine("Type: $it") }
        pan?.let { sb.appendLine("Card Number: $it") }
        expiryDate?.let { sb.appendLine("Expires: $it") }
        cardholderName?.let { sb.appendLine("Cardholder: $it") }
        return sb.toString()
    }
}
