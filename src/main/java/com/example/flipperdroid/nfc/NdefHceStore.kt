package com.example.flipperdroid.nfc

import android.content.Context

/** Persiste le message NDEF (octets) servi par [NdefHceService] et son libellé. */
object NdefHceStore {
    private const val PREFS = "ndef_hce"
    private const val KEY_NDEF = "ndef_hex"
    private const val KEY_LABEL = "label"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(ctx: Context, label: String, ndef: ByteArray) {
        prefs(ctx).edit()
            .putString(KEY_LABEL, label)
            .putString(KEY_NDEF, MifareClassicUtils.bytesToHex(ndef))
            .apply()
    }

    fun label(ctx: Context): String? = prefs(ctx).getString(KEY_LABEL, null)

    fun ndefBytes(ctx: Context): ByteArray {
        val hex = prefs(ctx).getString(KEY_NDEF, null) ?: return ByteArray(0)
        return MifareClassicUtils.hexToBytes(hex) ?: ByteArray(0)
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }
}
