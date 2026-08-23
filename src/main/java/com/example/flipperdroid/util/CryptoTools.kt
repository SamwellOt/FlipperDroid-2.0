package com.example.flipperdroid.util

import android.util.Base64
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.zip.CRC32
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Boîte à outils crypto/encodage (hors ligne) pour CTF / pentest :
 * hachages, Base64/Hex/URL, ROT13, HMAC, décodage JWT, identification de hash.
 */
object CryptoTools {

    private fun ByteArray.toHex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) sb.append("%02x".format(b))
        return sb.toString()
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

    fun base64Decode(input: String): String = try {
        String(Base64.decode(input.trim(), Base64.DEFAULT), Charsets.UTF_8)
    } catch (e: Exception) { "⚠ invalid Base64" }

    fun hexEncode(input: String): String = input.toByteArray(Charsets.UTF_8).toHex()

    fun hexDecode(input: String): String = try {
        val clean = input.filter { !it.isWhitespace() }
        require(clean.length % 2 == 0)
        val out = ByteArray(clean.length / 2)
        var i = 0
        while (i < clean.length) { out[i / 2] = clean.substring(i, i + 2).toInt(16).toByte(); i += 2 }
        String(out, Charsets.UTF_8)
    } catch (e: Exception) { "⚠ invalid hex" }

    fun urlEncode(input: String): String = URLEncoder.encode(input, "UTF-8")
    fun urlDecode(input: String): String = try { URLDecoder.decode(input, "UTF-8") } catch (e: Exception) { "⚠ invalid URL encoding" }

    fun rot13(input: String): String = input.map {
        when (it) {
            in 'a'..'z' -> 'a' + (it - 'a' + 13) % 26
            in 'A'..'Z' -> 'A' + (it - 'A' + 13) % 26
            else -> it
        }
    }.joinToString("")

    fun hmacSha256(key: String, input: String): String = try {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        mac.doFinal(input.toByteArray(Charsets.UTF_8)).toHex()
    } catch (e: Exception) { "⚠ HMAC error: ${e.message}" }

    /** Décode un JWT (header + payload en Base64URL) sans vérifier la signature. */
    fun jwtDecode(input: String): String {
        val parts = input.trim().split(".")
        if (parts.size < 2) return "⚠ not a JWT (need header.payload.signature)"
        fun dec(s: String) = try {
            String(Base64.decode(s, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8)
        } catch (e: Exception) { "⚠ invalid" }
        val sig = if (parts.size >= 3) parts[2] else "(none)"
        return "HEADER:\n${dec(parts[0])}\n\nPAYLOAD:\n${dec(parts[1])}\n\nSIGNATURE (b64url):\n$sig"
    }

    /** Devine le type de hash d'après sa longueur/charset (indicatif). */
    fun identifyHash(input: String): String {
        val s = input.trim()
        val isHex = s.isNotEmpty() && s.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
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
