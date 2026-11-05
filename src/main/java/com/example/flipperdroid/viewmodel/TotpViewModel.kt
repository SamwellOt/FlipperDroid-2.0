package com.example.flipperdroid.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.example.flipperdroid.security.TotpGenerator
import com.example.flipperdroid.util.SecureStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Un compte 2FA (label + secret Base32, paramètres TOTP). */
data class TotpAccount(
    val label: String,
    val secret: String,
    val digits: Int = 6,
    val period: Int = 30,
    val algorithm: String = "SHA1"
)

/**
 * Coffre 2FA/TOTP : les comptes sont stockés chiffrés (AES-GCM via l'Android Keystore,
 * voir [SecureStore]) et les codes sont générés à la volée. Les secrets ne quittent
 * jamais l'appareil.
 */
class TotpViewModel(app: Application) : AndroidViewModel(app) {

    private val appContext: Context = app

    private companion object {
        const val TAG = "TotpViewModel"
        /** Clé du coffre chiffré dans SecureStore. */
        const val STORE_KEY = "totp_accounts"
        /** Ancien stockage en clair (SharedPreferences "totp_vault", clé "accounts"), migré puis supprimé. */
        const val LEGACY_PREFS = "totp_vault"
        const val LEGACY_KEY = "accounts"
    }

    private val legacyPrefs = app.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)

    private val _accounts = MutableStateFlow<List<TotpAccount>>(emptyList())
    val accounts: StateFlow<List<TotpAccount>> = _accounts

    /** Dernier message d'erreur à afficher (null = aucun). */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init { load() }

    fun dismissError() {
        _error.value = null
    }

    private fun load() {
        // Données chiffrées actuelles
        val hasEncrypted = appContext
            .getSharedPreferences(SecureStore.PREFS, Context.MODE_PRIVATE)
            .contains(STORE_KEY)
        val decrypted = try {
            SecureStore.getString(appContext, STORE_KEY)
        } catch (e: Exception) {
            Log.e(TAG, "Lecture du coffre impossible", e)
            null
        }

        var list: List<TotpAccount> = when {
            decrypted != null -> parseAccounts(decrypted) ?: run {
                Log.e(TAG, "Coffre corrompu : démarrage avec un coffre vide")
                _error.value = "Coffre corrompu : données ignorées"
                emptyList()
            }
            hasEncrypted -> {
                Log.e(TAG, "Coffre illisible (clé Keystore perdue ou données altérées)")
                _error.value = "Coffre illisible (clé Keystore perdue ou données altérées)"
                emptyList()
            }
            else -> emptyList()
        }

        // Migration de l'ancien stockage en clair
        val legacyJson = try {
            legacyPrefs.getString(LEGACY_KEY, null)
        } catch (e: Exception) {
            Log.e(TAG, "Lecture de l'ancien coffre impossible", e)
            null
        }
        if (legacyJson != null) {
            val legacy = parseAccounts(legacyJson)
            if (legacy == null) {
                // Donnée illisible : on la laisse en place, sans la migrer
                Log.e(TAG, "Ancien coffre corrompu, migration ignorée")
            } else {
                val merged = list + legacy.filter { l ->
                    list.none { it.label == l.label && it.secret == l.secret }
                }
                // N'effacer l'ancien stockage que si la nouvelle écriture est vérifiée
                if (persistList(merged)) {
                    try {
                        legacyPrefs.edit().remove(LEGACY_KEY).apply()
                        Log.i(TAG, "Migration du coffre TOTP terminée (${legacy.size} comptes)")
                    } catch (e: Exception) {
                        Log.e(TAG, "Suppression de l'ancien coffre impossible", e)
                    }
                }
                list = merged
            }
        }

        _accounts.value = list
    }

    /**
     * Parse le JSON du coffre. Les comptes invalides sont ignorés.
     * @return null si le JSON lui-même est corrompu.
     */
    private fun parseAccounts(json: String): List<TotpAccount>? = try {
        val arr = JSONArray(json)
        val out = mutableListOf<TotpAccount>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i)
            if (o == null) {
                Log.w(TAG, "Entrée ignorée à l'index $i (format inattendu)")
                continue
            }
            val account = TotpAccount(
                label = o.optString("label", "Account").ifBlank { "Account" },
                secret = o.optString("secret", ""),
                digits = o.optInt("digits", 6),
                period = o.optInt("period", 30),
                algorithm = o.optString("algorithm", "SHA1")
            )
            val problem = validationError(account)
            if (problem == null) out.add(account) else Log.w(TAG, "Compte ignoré à l'index $i : $problem")
        }
        out
    } catch (e: Exception) {
        Log.e(TAG, "JSON du coffre invalide", e)
        null
    }

    /**
     * Enregistre la liste dans le coffre chiffré et vérifie la relecture.
     * @return true si l'écriture a réussi.
     */
    private fun persistList(list: List<TotpAccount>): Boolean = try {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("label", it.label)
                put("secret", it.secret)
                put("digits", it.digits)
                put("period", it.period)
                put("algorithm", it.algorithm)
            })
        }
        val json = arr.toString()
        SecureStore.putString(appContext, STORE_KEY, json)
        if (SecureStore.getString(appContext, STORE_KEY) != json) {
            throw IllegalStateException("Relecture du coffre différente de l'écriture")
        }
        true
    } catch (e: Exception) {
        Log.e(TAG, "Sauvegarde du coffre impossible", e)
        _error.value = "Impossible de sauvegarder le coffre chiffré"
        false
    }

    /** Message d'erreur de validation d'un compte, ou null s'il est valide. */
    private fun validationError(a: TotpAccount): String? = when {
        !TotpGenerator.isValidBase32(a.secret) -> "Secret Base32 invalide"
        a.digits !in 6..8 -> "Nombre de chiffres non supporté : ${a.digits} (6 à 8)"
        a.period <= 0 -> "Période invalide : ${a.period} (doit être > 0)"
        !TotpGenerator.isSupportedAlgorithm(a.algorithm) ->
            "Algorithme non supporté : ${a.algorithm} (SHA1, SHA256, SHA512)"
        else -> null
    }

    private fun fail(message: String): Boolean {
        _error.value = message
        return false
    }

    /**
     * Ajoute un compte depuis un label + secret, ou depuis une URI otpauth://.
     * @return true si le compte a été ajouté ; sinon [error] contient la raison.
     */
    fun addAccount(labelOrUri: String, secret: String): Boolean {
        val input = labelOrUri.trim()
        val account: TotpAccount
        if (input.startsWith("otpauth://", ignoreCase = true)) {
            val data = TotpGenerator.parseOtpauthUri(input)
                ?: return fail("URI otpauth:// invalide (secret requis, digits/period numériques)")
            if (data.type != "totp") {
                return fail("Type '${data.type}' non pris en charge (TOTP uniquement)")
            }
            account = TotpAccount(
                label = data.label,
                secret = data.secret,
                digits = data.digits,
                period = data.period,
                algorithm = data.algorithm
            )
        } else {
            if (secret.isBlank()) return fail("Secret Base32 requis")
            account = TotpAccount(label = input.ifBlank { "Account" }, secret = secret)
        }

        val normalized = account.copy(
            secret = account.secret.filterNot { it.isWhitespace() }.uppercase(),
            algorithm = TotpGenerator.normalizeAlgorithm(account.algorithm)
        )
        validationError(normalized)?.let { return fail(it) }

        val updated = _accounts.value + normalized
        if (!persistList(updated)) return false
        _accounts.value = updated
        _error.value = null
        return true
    }

    fun removeAccount(account: TotpAccount) {
        val updated = _accounts.value.filterNot { it.label == account.label && it.secret == account.secret }
        if (persistList(updated)) {
            _accounts.value = updated
            _error.value = null
        }
    }

    fun codeFor(account: TotpAccount, nowMillis: Long): String = try {
        TotpGenerator.generate(account.secret, nowMillis / 1000, account.digits, account.period, account.algorithm)
    } catch (e: IllegalArgumentException) {
        "-".repeat(account.digits.coerceIn(6, 8))
    }

    fun secondsRemaining(account: TotpAccount, nowMillis: Long): Int =
        TotpGenerator.secondsRemaining(nowMillis / 1000, account.period)
}
