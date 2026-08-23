package com.example.flipperdroid.security

import java.io.ByteArrayOutputStream
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Générateur de codes TOTP (RFC 6238) et HOTP (RFC 4226), HMAC-SHA1.
 * Décode les secrets en Base32 (RFC 4648).
 */
object TotpGenerator {

    private const val BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /** Décode une chaîne Base32 (padding et séparateurs ignorés). */
    fun base32Decode(input: String): ByteArray {
        val s = input.trim().replace(" ", "").replace("-", "").uppercase().trimEnd('=')
        var bits = 0
        var value = 0
        val out = ByteArrayOutputStream()
        for (c in s) {
            val idx = BASE32.indexOf(c)
            if (idx < 0) continue
            value = (value shl 5) or idx
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.write((value shr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }

    /**
     * Génère un code TOTP.
     * @param secretB32 secret partagé en Base32
     * @param timeSec temps Unix en secondes
     */
    fun generate(secretB32: String, timeSec: Long, digits: Int = 6, step: Int = 30): String {
        val key = base32Decode(secretB32)
        if (key.isEmpty()) return "".padStart(digits, '-')
        val counter = timeSec / step
        val msg = ByteArray(8)
        var v = counter
        for (i in 7 downTo 0) { msg[i] = (v and 0xFF).toByte(); v = v shr 8 }

        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val h = mac.doFinal(msg)

        val off = h[h.size - 1].toInt() and 0x0F
        val code = ((h[off].toInt() and 0x7f) shl 24) or
                ((h[off + 1].toInt() and 0xff) shl 16) or
                ((h[off + 2].toInt() and 0xff) shl 8) or
                (h[off + 3].toInt() and 0xff)
        var mod = 1
        repeat(digits) { mod *= 10 }
        return (code % mod).toString().padStart(digits, '0')
    }

    /** Secondes restantes avant le prochain code (pour la barre de progression). */
    fun secondsRemaining(timeSec: Long, step: Int = 30): Int = (step - (timeSec % step)).toInt()

    /** Extrait le secret d'une URI otpauth:// (QR code 2FA). */
    fun parseOtpauthUri(uri: String): Pair<String, String>? {
        if (!uri.startsWith("otpauth://", true)) return null
        val label = uri.substringAfter("otpauth://totp/", "").substringBefore("?")
            .substringAfterLast(':').ifBlank { "Account" }
        val secret = Regex("[?&]secret=([^&]+)", RegexOption.IGNORE_CASE)
            .find(uri)?.groupValues?.get(1) ?: return null
        return label.trim() to secret.trim()
    }
}
