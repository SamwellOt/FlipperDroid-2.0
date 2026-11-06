package com.example.flipperdroid.nfc

import android.content.Context
import com.example.flipperdroid.util.SecureStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistance d'une carte ISO-DEP capturée pour l'émulation HCE (replay APDU).
 * Stocke la table commande->réponse (en hex) et les AID auxquels la carte a répondu.
 *
 * Les réponses APDU peuvent contenir des données de porteur (PAN, Track2, nom) : elles sont
 * donc chiffrées via [SecureStore] (Android Keystore), et non plus en clair dans des
 * SharedPreferences. L'ancien stockage en clair ("hce_emulation") est migré puis effacé.
 */
object EmulationStore {
    /** Ancien fichier de préférences en clair : migré puis supprimé. */
    private const val LEGACY_PREFS = "hce_emulation"
    /** Clé (chiffrée) qui contient le JSON complet de la capture. */
    private const val KEY_DATA = "hce_emulation_v2"
    // Noms des champs : identiques à ceux de l'ancien stockage, pour la migration.
    private const val F_LABEL = "label"
    private const val F_AIDS = "aids"
    private const val F_MAP = "apdu_map"

    fun save(context: Context, label: String, aids: List<String>, map: Map<String, String>) {
        val mapObj = JSONObject()
        map.forEach { (k, v) -> mapObj.put(k, v) }
        val obj = JSONObject()
            .put(F_LABEL, label)
            .put(F_AIDS, JSONArray(aids))
            .put(F_MAP, mapObj)
        SecureStore.putString(context, KEY_DATA, obj.toString())
        legacyPrefs(context).edit().clear().apply()
    }

    fun label(context: Context): String? =
        load(context)?.optString(F_LABEL, "")?.takeIf { it.isNotEmpty() }

    fun aids(context: Context): List<String> {
        val arr = load(context)?.optJSONArray(F_AIDS) ?: return emptyList()
        return try {
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) { emptyList() }
    }

    fun map(context: Context): Map<String, String> {
        val obj = load(context)?.optJSONObject(F_MAP) ?: return emptyMap()
        val out = HashMap<String, String>()
        try {
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                out[k] = obj.optString(k, "")
            }
        } catch (e: Exception) { /* entrée corrompue : on garde ce qui a été lu */ }
        return out
    }

    fun isConfigured(context: Context): Boolean = aids(context).isNotEmpty()

    fun clear(context: Context) {
        SecureStore.putString(context, KEY_DATA, null)
        legacyPrefs(context).edit().clear().apply()
    }

    /** Déchiffre et parse la capture ; null si absente ou illisible. */
    private fun load(context: Context): JSONObject? {
        migrateLegacy(context)
        val json = SecureStore.getString(context, KEY_DATA) ?: return null
        return try { JSONObject(json) } catch (e: Exception) { null }
    }

    /** Migre l'ancien stockage en clair vers [SecureStore], puis supprime les anciennes préférences. */
    private fun migrateLegacy(context: Context) {
        val legacy = legacyPrefs(context)
        if (legacy.all.isEmpty()) return
        try {
            if (SecureStore.getString(context, KEY_DATA) == null) {
                val obj = JSONObject()
                    .put(F_LABEL, legacy.getString(F_LABEL, "") ?: "")
                    .put(F_AIDS, JSONArray(legacy.getString(F_AIDS, "[]") ?: "[]"))
                    .put(F_MAP, JSONObject(legacy.getString(F_MAP, "{}") ?: "{}"))
                SecureStore.putString(context, KEY_DATA, obj.toString())
            }
        } catch (e: Exception) {
            // Ancien contenu illisible : on le supprime sans le migrer.
        }
        legacy.edit().clear().apply()
    }

    private fun legacyPrefs(context: Context) =
        context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
}
