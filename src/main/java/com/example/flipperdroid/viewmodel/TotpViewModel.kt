package com.example.flipperdroid.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.example.flipperdroid.security.TotpGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Un compte 2FA (label + secret Base32). */
data class TotpAccount(val label: String, val secret: String, val digits: Int = 6, val period: Int = 30)

/**
 * Coffre 2FA/TOTP : stocke les comptes localement (SharedPreferences) et génère
 * les codes à la volée. Les secrets ne quittent jamais l'appareil.
 */
class TotpViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("totp_vault", Context.MODE_PRIVATE)

    private val _accounts = MutableStateFlow<List<TotpAccount>>(emptyList())
    val accounts: StateFlow<List<TotpAccount>> = _accounts

    init { load() }

    private fun load() {
        val json = prefs.getString("accounts", "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<TotpAccount>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                TotpAccount(
                    label = o.getString("label"),
                    secret = o.getString("secret"),
                    digits = o.optInt("digits", 6),
                    period = o.optInt("period", 30)
                )
            )
        }
        _accounts.value = list
    }

    private fun persist() {
        val arr = JSONArray()
        _accounts.value.forEach {
            arr.put(JSONObject().apply {
                put("label", it.label); put("secret", it.secret)
                put("digits", it.digits); put("period", it.period)
            })
        }
        prefs.edit().putString("accounts", arr.toString()).apply()
    }

    /** Ajoute un compte depuis un label + secret, ou depuis une URI otpauth://. */
    fun addAccount(labelOrUri: String, secret: String) {
        val account = if (labelOrUri.startsWith("otpauth://", true)) {
            TotpGenerator.parseOtpauthUri(labelOrUri)?.let { TotpAccount(it.first, it.second) }
        } else if (secret.isNotBlank()) {
            TotpAccount(labelOrUri.ifBlank { "Account" }, secret)
        } else null

        if (account != null && account.secret.isNotBlank()) {
            _accounts.value = _accounts.value + account
            persist()
        }
    }

    fun removeAccount(account: TotpAccount) {
        _accounts.value = _accounts.value.filterNot { it.label == account.label && it.secret == account.secret }
        persist()
    }

    fun codeFor(account: TotpAccount, nowMillis: Long): String =
        TotpGenerator.generate(account.secret, nowMillis / 1000, account.digits, account.period)

    fun secondsRemaining(account: TotpAccount, nowMillis: Long): Int =
        TotpGenerator.secondsRemaining(nowMillis / 1000, account.period)
}
