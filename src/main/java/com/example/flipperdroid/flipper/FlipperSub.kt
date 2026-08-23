package com.example.flipperdroid.flipper

/** Représentation d'un fichier .sub (Sub-GHz) du Flipper Zero. */
data class SubFile(
    val frequency: Long?,
    val preset: String?,
    val protocol: String?,
    val rawData: IntArray,
    val keyData: String?
)

/**
 * Lecteur du format .sub du Flipper (Sub-GHz).
 *
 * NOTE : un smartphone n'a pas de radio Sub-GHz — ce module permet seulement de
 * *lire / analyser* un .sub, pas de le transmettre (il faudrait un CC1101/HackRF
 * externe).
 */
object FlipperSub {

    fun parse(text: String): SubFile {
        var frequency: Long? = null
        var preset: String? = null
        var protocol: String? = null
        var keyData: String? = null
        val raw = mutableListOf<Int>()

        for (line in text.split("\n")) {
            val t = line.trim()
            val idx = t.indexOf(':')
            if (idx <= 0) continue
            val key = t.substring(0, idx).trim()
            val value = t.substring(idx + 1).trim()
            when {
                key.equals("Frequency", true) -> frequency = value.toLongOrNull()
                key.equals("Preset", true) -> preset = value
                key.equals("Protocol", true) -> protocol = value
                key.equals("Key", true) -> keyData = value
                key.equals("RAW_Data", true) ->
                    value.split(Regex("\\s+")).mapNotNullTo(raw) { it.toIntOrNull() }
            }
        }
        return SubFile(frequency, preset, protocol, raw.toIntArray(), keyData)
    }

    /** Résumé lisible d'un .sub. */
    fun summarize(sub: SubFile): String {
        val sb = StringBuilder()
        sb.append("Frequency: ${sub.frequency?.let { "%.3f MHz".format(it / 1_000_000.0) } ?: "?"}\n")
        sb.append("Preset: ${sub.preset ?: "?"}\n")
        sb.append("Protocol: ${sub.protocol ?: "?"}\n")
        sub.keyData?.let { sb.append("Key: $it\n") }
        if (sub.rawData.isNotEmpty()) sb.append("RAW samples: ${sub.rawData.size}\n")
        sb.append("\n⚠ Sub-GHz cannot be transmitted from a phone (no <1 GHz radio). " +
            "Use an external CC1101/HackRF.")
        return sb.toString()
    }
}
