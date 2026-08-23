package com.example.flipperdroid.infrared

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
 */
object IrFile {

    fun parse(text: String): List<IrButton> {
        val buttons = mutableListOf<IrButton>()
        var cur = mutableMapOf<String, String>()

        fun flush() {
            val name = cur["name"] ?: return
            val type = cur["type"] ?: return
            val button = IrButton(
                name = name,
                type = type,
                protocol = cur["protocol"],
                address = parseHexBytesLE(cur["address"]),
                command = parseHexBytesLE(cur["command"]),
                frequency = cur["frequency"]?.trim()?.toIntOrNull() ?: 38000,
                rawData = cur["data"]?.let { parseRaw(it) }
            )
            buttons.add(button)
        }

        for (raw in text.split("\n")) {
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

    /** Convertit "04 00 00 00" (little-endian) en entier. */
    private fun parseHexBytesLE(s: String?): Int {
        if (s.isNullOrBlank()) return 0
        var result = 0
        s.trim().split(Regex("\\s+")).forEachIndexed { i, byteStr ->
            val b = byteStr.toIntOrNull(16) ?: 0
            if (i < 4) result = result or (b shl (8 * i))
        }
        return result
    }

    /** Convertit "9000 4500 560 ..." en tableau d'entiers. */
    private fun parseRaw(s: String): IntArray {
        return s.trim().split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }.toIntArray()
    }
}
