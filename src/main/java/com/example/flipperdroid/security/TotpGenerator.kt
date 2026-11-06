package com.example.flipperdroid.security

import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Résultat du parsing d'une URI otpauth:// (QR code 2FA).
 * Les deux premières propriétés (label, secret) sont conservées : la déstructuration
 * `val (label, secret) = ...` reste valide.
 */
data class OtpauthData(
    val label: String,
    val secret: String,
    val issuer: String?,
    val algorithm: String,
    val digits: Int,
    val period: Int,
    val type: String
)

/**
 * Générateur de codes TOTP (RFC 6238) et HOTP (RFC 4226).
 * Algorithmes HMAC : SHA1 (défaut), SHA256, SHA512.
 * Décode les secrets en Base32 (RFC 4648).
 */
object TotpGenerator {

    private const val BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private const val OTPAUTH_SCHEME = "otpauth://"

    /** Algorithmes acceptés, sous forme normalisée (majuscules, sans tiret). */
    val SUPPORTED_ALGORITHMS: List<String> = listOf("SHA1", "SHA256", "SHA512")

    /** Normalise un nom d'algorithme : "sha-256" -> "SHA256". */
    fun normalizeAlgorithm(algorithm: String): String =
        algorithm.trim().uppercase().replace("-", "")

    fun isSupportedAlgorithm(algorithm: String): Boolean =
        normalizeAlgorithm(algorithm) in SUPPORTED_ALGORITHMS

    private fun macName(algorithm: String): String = when (normalizeAlgorithm(algorithm)) {
        "SHA1" -> "HmacSHA1"
        "SHA256" -> "HmacSHA256"
        "SHA512" -> "HmacSHA512"
        else -> throw IllegalArgumentException("Algorithme non supporté : $algorithm")
    }

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
     * Vérifie qu'une chaîne est du Base32 valide (A-Z, 2-7, padding '=' optionnel en fin).
     * Les espaces sont ignorés et la casse n'a pas d'importance.
     */
    fun isValidBase32(s: String): Boolean {
        val compact = s.filterNot { it.isWhitespace() }.uppercase()
        val body = compact.trimEnd('=')
        val padding = compact.length - body.length
        if (body.isEmpty() || padding > 6) return false
        return body.all { it in 'A'..'Z' || it in '2'..'7' }
    }

    /**
     * Génère un code TOTP.
     * @param secretB32 secret partagé en Base32
     * @param timeSec temps Unix en secondes
     * @param digits nombre de chiffres (6 à 8 typiquement)
     * @param step durée d'une fenêtre en secondes
     * @param algorithm SHA1, SHA256 ou SHA512
     */
    fun generate(
        secretB32: String,
        timeSec: Long,
        digits: Int = 6,
        step: Int = 30,
        algorithm: String = "SHA1"
    ): String {
        require(step > 0) { "Période invalide : $step" }
        val key = base32Decode(secretB32)
        if (key.isEmpty()) return "".padStart(digits, '-')
        return hotpFromKey(key, timeSec / step, digits, algorithm)
    }

    /**
     * Génère un code HOTP (RFC 4226) pour un compteur donné.
     */
    fun hotp(secretB32: String, counter: Long, digits: Int = 6, algorithm: String = "SHA1"): String {
        val key = base32Decode(secretB32)
        if (key.isEmpty()) return "".padStart(digits, '-')
        return hotpFromKey(key, counter, digits, algorithm)
    }

    private fun hotpFromKey(key: ByteArray, counter: Long, digits: Int, algorithm: String): String {
        require(digits in 1..9) { "Nombre de chiffres non supporté : $digits" }
        // Compteur sur 8 octets, big-endian
        val msg = ByteArray(8)
        var v = counter
        for (i in 7 downTo 0) { msg[i] = (v and 0xFF).toByte(); v = v shr 8 }

        val name = macName(algorithm)
        val mac = Mac.getInstance(name)
        mac.init(SecretKeySpec(key, name))
        val h = mac.doFinal(msg)

        // Troncature dynamique (RFC 4226 §5.3)
        val off = h[h.size - 1].toInt() and 0x0F
        val bin = ((h[off].toInt() and 0x7f) shl 24) or
                ((h[off + 1].toInt() and 0xff) shl 16) or
                ((h[off + 2].toInt() and 0xff) shl 8) or
                (h[off + 3].toInt() and 0xff)
        var mod = 1L
        repeat(digits) { mod *= 10 }
        return (bin.toLong() % mod).toString().padStart(digits, '0')
    }

    /** Secondes restantes avant le prochain code (pour la barre de progression). */
    fun secondsRemaining(timeSec: Long, step: Int = 30): Int = (step - (timeSec % step)).toInt()

    /**
     * Parse une URI otpauth:// (QR code 2FA).
     * Lit les paramètres secret, issuer, algorithm, digits, period ; le label est décodé (URL).
     * @return null si l'URI est mal formée (schéma, type, secret absent, nombres invalides).
     */
    fun parseOtpauthUri(uri: String): OtpauthData? {
        val input = uri.trim()
        if (!input.startsWith(OTPAUTH_SCHEME, ignoreCase = true)) return null
        val rest = input.substring(OTPAUTH_SCHEME.length)
        val beforeQuery = rest.substringBefore('?')
        val type = beforeQuery.substringBefore('/').lowercase()
        if (type != "totp" && type != "hotp") return null
        val rawLabel = if (beforeQuery.contains('/')) beforeQuery.substringAfter('/') else ""

        val params = parseQuery(input.substringAfter('?', "").substringBefore('#'))
        val secret = params["secret"]?.filterNot { it.isWhitespace() }.orEmpty()
        if (secret.isEmpty()) return null

        // '+' est un caractère littéral dans le label : on le protège avant le décodage URL
        val label = decodeComponent(rawLabel.replace("+", "%2B"))
        val account = label.substringAfterLast(':').trim().ifBlank { "Account" }
        val issuer = params["issuer"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: label.takeIf { it.contains(':') }?.substringBefore(':')?.trim()?.takeIf { it.isNotEmpty() }

        val digits = parseIntParam(params, "digits", 6) ?: return null
        val period = parseIntParam(params, "period", 30) ?: return null
        val algorithm = normalizeAlgorithm(params["algorithm"] ?: "SHA1")

        return OtpauthData(account, secret, issuer, algorithm, digits, period, type)
    }

    private fun parseIntParam(params: Map<String, String>, key: String, default: Int): Int? {
        val raw = params[key] ?: return default
        return raw.trim().toIntOrNull()
    }

    /** Découpe une query string en paires clé/valeur (clés en minuscules, première occurrence gagne). */
    private fun parseQuery(query: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val key = decodeComponent(pair.substringBefore('=')).lowercase()
            val value = decodeComponent(pair.substringAfter('=', ""))
            if (key !in out) out[key] = value
        }
        return out
    }

    /** Décodage URL UTF-8 ; une séquence % invalide renvoie la chaîne telle quelle. */
    private fun decodeComponent(s: String): String = try {
        URLDecoder.decode(s, "UTF-8")
    } catch (e: IllegalArgumentException) {
        s
    }
}
