package com.example.flipperdroid.infrared

/** Intervalle inter-trame utilisé pour la rafale d'un bouton "raw". */
private const val RAW_GAP_US = 40000

/** Un bouton d'un fichier .ir (soit "parsed" avec protocole, soit "raw"). */
data class IrButton(
    val name: String,
    val type: String,
    val protocol: String? = null,
    val address: Int = 0,
    val command: Int = 0,
    val frequency: Int = 38000,
    val rawData: IntArray? = null
) {
    /** @return (fréquence, motif µs) prêt à transmettre, ou null si non encodable. */
    fun toSignal(): Pair<Int, IntArray>? {
        return when (type.lowercase()) {
            "raw" -> if (rawData != null && rawData.isNotEmpty()) frequency to rawData else null
            "parsed" -> protocol?.let { IrProtocols.encode(it, address, command) }
            else -> null
        }
    }

    /**
     * Rafale répétée (une pression fiable) pour le TV-B-Gone. Pour un bouton "parsed",
     * délègue à IrProtocols.encodePowerBurst ; pour un bouton "raw", répète simplement
     * la capture [presses] fois (une trame brute isolée est souvent ignorée).
     */
    fun toBurst(presses: Int = 0): Pair<Int, IntArray>? {
        return when (type.lowercase()) {
            "parsed" -> protocol?.let {
                IrProtocols.encodePowerBurst(it, address, command, presses)
            }
            "raw" -> {
                val data = rawData ?: return null
                if (data.isEmpty()) return null
                val count = if (presses > 0) presses else 3
                frequency to IrProtocols.repeatFrames(data, data, count, RAW_GAP_US)
            }
            else -> null
        }
    }
}

/**
 * Parseur du format de fichier .ir du Flipper Zero.
 *
 * Exemple d'entrée :
 * ```
 * name: Power
 * type: parsed
 * protocol: NEC
 * address: 04 00 00 00
 * command: 08 00 00 00
 * ```
 * ou brute :
 * ```
 * name: Power
 * type: raw
 * frequency: 38000
 * data: 9000 4500 560 560 ...
 * ```
 *
 * Un bouton dont un octet hexadécimal ou une valeur de donnée brute est invalide est
 * **ignoré** (et non transformé en 0) : mieux vaut un bouton absent qu'un code faux.
 */
object IrFile {

    private val HEX_BYTE = Regex("[0-9A-Fa-f]{1,2}")
    private val WHITESPACE = Regex("\\s+")

    fun parse(text: String): List<IrButton> {
        val buttons = mutableListOf<IrButton>()
        var cur = mutableMapOf<String, String>()

        fun flush() {
            val name = cur["name"] ?: return
            val type = cur["type"] ?: return
            val address = parseHexBytesLE(cur["address"]) ?: return   // octet invalide : bouton ignoré
            val command = parseHexBytesLE(cur["command"]) ?: return
            val rawData = cur["data"]?.let { parseRaw(it) }
            // Donnée brute présente mais corrompue : bouton ignoré.
            if (cur.containsKey("data") && rawData == null) return
            buttons.add(
                IrButton(
                    name = name,
                    type = type,
                    protocol = cur["protocol"],
                    address = address,
                    command = command,
                    frequency = cur["frequency"]?.trim()?.toIntOrNull() ?: 38000,
                    rawData = rawData
                )
            )
        }

        // lines() gère "\n" et "\r\n" ; le BOM éventuel en tête de fichier est retiré.
        for (raw in text.removePrefix("﻿").lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("Filetype") || line.startsWith("Version")) {
                continue
            }
            val idx = line.indexOf(':')
            if (idx <= 0) continue
            val key = line.substring(0, idx).trim().lowercase()
            val value = line.substring(idx + 1).trim()
            // Une nouvelle clé "name" démarre un nouveau bouton
            if (key == "name" && cur.isNotEmpty()) {
                flush()
                cur = mutableMapOf()
            }
            cur[key] = value
        }
        flush()
        return buttons
    }

    /**
     * Convertit "04 00 00 00" (little-endian) en entier.
     * @return 0 si la valeur est absente, null si un octet n'est pas hexadécimal valide.
     */
    private fun parseHexBytesLE(s: String?): Int? {
        if (s.isNullOrBlank()) return 0
        var result = 0
        val tokens = s.trim().split(WHITESPACE)
        for ((i, tok) in tokens.withIndex()) {
            if (!HEX_BYTE.matches(tok)) return null
            if (i < 4) result = result or (tok.toInt(16) shl (8 * i))
        }
        return result
    }

    /**
     * Convertit "9000 4500 560 ..." en tableau d'entiers.
     * @return null si un jeton n'est pas un entier strictement positif, ou si la liste est vide.
     */
    private fun parseRaw(s: String): IntArray? {
        val tokens = s.trim().split(WHITESPACE)
        val out = IntArray(tokens.size)
        for ((i, tok) in tokens.withIndex()) {
            val v = tok.toIntOrNull()
            if (v == null || v <= 0) return null
            out[i] = v
        }
        return if (out.isEmpty()) null else out
    }
}
