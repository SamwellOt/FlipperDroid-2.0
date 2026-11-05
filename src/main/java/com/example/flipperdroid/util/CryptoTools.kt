package com.example.flipperdroid.util

import android.util.Base64
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.zip.CRC32
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Boîte à outils crypto/encodage (hors ligne) pour CTF / pentest :
 * hachages, Base64/Hex/URL, ROT13, HMAC, décodage JWT, identification de hash.
 */
object CryptoTools {

    private const val HEX_DIGITS = "0123456789abcdef"

    /** Encodage hexadécimal minuscule, sans String.format (rapide, pas d'allocation par octet). */
    private fun ByteArray.toHex(): String {
        val out = CharArray(size * 2)
        for (i in indices) {
            val v = this[i].toInt() and 0xFF
            out[i * 2] = HEX_DIGITS[v ushr 4]
            out[i * 2 + 1] = HEX_DIGITS[v and 0x0F]
        }
        return String(out)
    }

    /** Vrai pour un chiffre hexadécimal ASCII (0-9, a-f, A-F) uniquement. */
    private fun Char.isAsciiHexDigit(): Boolean =
        this in '0'..'9' || this.lowercaseChar() in 'a'..'f'

    /**
     * Décode des octets en UTF-8 strict. Si ce n'est pas du texte UTF-8 valide,
     * renvoie une note et un dump hexadécimal au lieu de caractères de remplacement.
     */
    private fun decodeUtf8OrHex(bytes: ByteArray): String {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            "[données non textuelles (non UTF-8), ${bytes.size} octets]\n${bytes.toHex()}"
        }
    }

    /** Parse une chaîne hexadécimale stricte (espaces ignorés). @throws IllegalArgumentException si invalide. */
    private fun parseHex(input: String): ByteArray {
        val clean = input.filterNot { it.isWhitespace() }
        require(clean.length % 2 == 0) { "longueur impaire" }
        require(clean.all { it.isAsciiHexDigit() }) { "caractère non hexadécimal" }
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            out[i] = ((Character.digit(clean[i * 2], 16) shl 4) or Character.digit(clean[i * 2 + 1], 16)).toByte()
        }
        return out
    }

    private fun digest(algo: String, input: String): String =
        MessageDigest.getInstance(algo).digest(input.toByteArray(Charsets.UTF_8)).toHex()

    fun md5(input: String) = digest("MD5", input)
    fun sha1(input: String) = digest("SHA-1", input)
    fun sha256(input: String) = digest("SHA-256", input)
    fun sha512(input: String) = digest("SHA-512", input)

    fun crc32(input: String): String {
        val c = CRC32()
        c.update(input.toByteArray(Charsets.UTF_8))
        return "%08x".format(c.value)
    }

    fun base64Encode(input: String): String =
        Base64.encodeToString(input.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    fun base64Decode(input: String): String {
        val bytes = try {
            Base64.decode(input.trim(), Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            return "⚠ invalid Base64"
        }
        return decodeUtf8OrHex(bytes)
    }

    fun hexEncode(input: String): String = input.toByteArray(Charsets.UTF_8).toHex()

    fun hexDecode(input: String): String = try {
        decodeUtf8OrHex(parseHex(input))
    } catch (e: IllegalArgumentException) {
        "⚠ invalid hex"
    }

    fun urlEncode(input: String): String = URLEncoder.encode(input, "UTF-8")
    fun urlDecode(input: String): String = try { URLDecoder.decode(input, "UTF-8") } catch (e: Exception) { "⚠ invalid URL encoding" }

    fun rot13(input: String): String = input.map {
        when (it) {
            in 'a'..'z' -> 'a' + (it - 'a' + 13) % 26
            in 'A'..'Z' -> 'A' + (it - 'A' + 13) % 26
            else -> it
        }
    }.joinToString("")

    /**
     * HMAC-SHA256 du texte. La clé est en UTF-8 par défaut ; préfixer par "hex:"
     * pour donner la clé en hexadécimal (ex. "hex:0a1b2c").
     */
    fun hmacSha256(key: String, input: String): String = try {
        val keyBytes = if (key.startsWith("hex:", ignoreCase = true)) {
            parseHex(key.substring(4))
        } else {
            key.toByteArray(Charsets.UTF_8)
        }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))
        mac.doFinal(input.toByteArray(Charsets.UTF_8)).toHex()
    } catch (e: Exception) { "⚠ HMAC error: ${e.message}" }

    /** Décode un JWT (header + payload en Base64URL) sans vérifier la signature. */
    fun jwtDecode(input: String): String {
        val parts = input.trim().split(".")
        if (parts.size < 2) return "⚠ not a JWT (need header.payload.signature)"
        fun dec(s: String) = try {
            decodeUtf8OrHex(Base64.decode(s, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
        } catch (e: Exception) { "⚠ invalid" }
        val sig = if (parts.size >= 3) parts[2] else "(none)"
        return "HEADER:\n${dec(parts[0])}\n\nPAYLOAD:\n${dec(parts[1])}\n\nSIGNATURE (b64url):\n$sig"
    }

    /** Devine le type de hash d'après sa longueur/charset (indicatif). */
    fun identifyHash(input: String): String {
        val s = input.trim()
        val isHex = s.isNotEmpty() && s.all { it.isAsciiHexDigit() }
        if (!isHex) return "Not hex — could be Base64/plaintext/bcrypt (\$2a\$…) etc."
        return when (s.length) {
            32 -> "MD5 / MD4 / NTLM (128-bit)"
            40 -> "SHA-1 (160-bit)"
            56 -> "SHA-224"
            64 -> "SHA-256"
            96 -> "SHA-384"
            128 -> "SHA-512"
            8 -> "CRC32 / Adler32"
            else -> "Unknown (${s.length} hex chars)"
        }
    }
}
