package com.example.flipperdroid.nfc

import android.content.Context

/** Persiste le message NDEF (octets) servi par [NdefHceService] et son libellé. */
object NdefHceStore {
    /**
     * Taille maximale d'un message NDEF servi : le fichier NDEF annoncé dans le CC fait 1024 octets
     * (0x0400), NLEN (2 octets) compris.
     */
    const val MAX_MESSAGE_BYTES = 1022

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
