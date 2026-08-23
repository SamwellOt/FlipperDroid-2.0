package com.example.flipperdroid.nfc

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistance d'une carte ISO-DEP capturée pour l'émulation HCE (replay APDU).
 * Stocke la table commande->réponse (en hex) et les AID auxquels la carte a répondu.
 */
object EmulationStore {
    private const val PREFS = "hce_emulation"
    private const val KEY_AIDS = "aids"
    private const val KEY_MAP = "apdu_map"
    private const val KEY_LABEL = "label"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(context: Context, label: String, aids: List<String>, map: Map<String, String>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        prefs(context).edit()
            .putString(KEY_LABEL, label)
            .putString(KEY_AIDS, JSONArray(aids).toString())
            .putString(KEY_MAP, obj.toString())
            .apply()
    }

    fun label(context: Context): String? = prefs(context).getString(KEY_LABEL, null)

    fun aids(context: Context): List<String> {
        val s = prefs(context).getString(KEY_AIDS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(s)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) { emptyList() }
    }

    fun map(context: Context): Map<String, String> {
        val s = prefs(context).getString(KEY_MAP, "{}") ?: "{}"
        return try {
            val obj = JSONObject(s)
            val out = HashMap<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) { val k = keys.next(); out[k] = obj.getString(k) }
            out
        } catch (e: Exception) { emptyMap() }
    }

    fun isConfigured(context: Context): Boolean = aids(context).isNotEmpty()

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
