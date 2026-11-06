package com.example.flipperdroid.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stockage chiffré de petites valeurs texte (secrets TOTP, captures APDU…).
 *
 * Les valeurs sont chiffrées en AES-256-GCM avec une clé non exportable de
 * l'Android Keystore, puis rangées dans des SharedPreferences privées. Le fichier
 * est exclu des sauvegardes (voir res/xml/backup_rules.xml et
 * data_extraction_rules.xml) : la clé Keystore ne suit pas une restauration.
 *
 * Format stocké : Base64(IV 12 octets || ciphertext+tag).
 */
object SecureStore {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "flipperdroid_secure_store_v1"
    const val PREFS = "secure_store"
    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }

    /** @return le texte clair, ou null si la donnée est illisible (clé perdue, corruption). */
    fun decrypt(encoded: String): String? = try {
        val all = Base64.decode(encoded, Base64.NO_WRAP)
        if (all.size <= IV_LEN) null else {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, all, 0, IV_LEN))
            String(cipher.doFinal(all, IV_LEN, all.size - IV_LEN), Charsets.UTF_8)
        }
    } catch (e: Exception) {
        null
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun putString(ctx: Context, name: String, value: String?) {
        prefs(ctx).edit().apply {
            if (value == null) remove(name) else putString(name, encrypt(value))
        }.apply()
    }

    fun getString(ctx: Context, name: String): String? =
        prefs(ctx).getString(name, null)?.let { decrypt(it) }
}
