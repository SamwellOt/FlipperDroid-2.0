package com.example.flipperdroid.flipper

/**
 * Représentation d'un fichier .nfc du Flipper Zero.
 * @property blocks blocs Mifare Classic ("Block i:")
 * @property pages pages Ultralight/NTAG ("Page i:")
 * @property valid false si l'en-tête "Filetype: Flipper NFC ..." est absent
 */
data class NfcCard(
    val deviceType: String?,
    val uid: String?,
    val atqa: String?,
    val sak: String?,
    val blocks: List<String>,
    val pages: List<String> = emptyList(),
    val valid: Boolean = true
)

/**
 * Lecteur / générateur du format de fichier .nfc du Flipper Zero.
 */
object FlipperNfc {

    private val BLOCK_KEY = Regex("(?i)block\\s+\\d+")
    private val PAGE_KEY = Regex("(?i)page\\s+\\d+")

    fun parse(text: String): NfcCard {
        var hasHeader = false
        var deviceType: String? = null
        var uid: String? = null
        var atqa: String? = null
        var sak: String? = null
        val blocks = mutableListOf<String>()
        val pages = mutableListOf<String>()

        for (raw in text.split("\n")) {
            val line = raw.trim()
            val idx = line.indexOf(':')
            if (idx <= 0) continue
            val key = line.substring(0, idx).trim()
            val value = line.substring(idx + 1).trim()
            when {
                key.equals("Filetype", true) -> hasHeader = value.contains("NFC", ignoreCase = true)
                key.equals("Device type", true) -> deviceType = value
                key.equals("UID", true) -> uid = value
                key.equals("ATQA", true) -> atqa = value
                key.equals("SAK", true) -> sak = value
                BLOCK_KEY.matches(key) -> blocks.add(value)
                PAGE_KEY.matches(key) -> pages.add(value)
            }
        }
        return NfcCard(deviceType, uid, atqa, sak, blocks, pages, hasHeader)
    }

    /**
     * Génère un fichier .nfc (format Flipper v4).
     *
     * @param uidHex UID (espaces tolérés, ex. "04A2B3C4")
     * @param blocks Mifare Classic : blocs de 32 caractères hex. NTAG/Ultralight : pages de 8 caractères.
     *   Une entrée illisible (tirets) est écrite "?? ?? ..." (convention Flipper pour "inconnu").
     * @param deviceType "Mifare Classic" (défaut) ou "NTAG/Ultralight"
     * @param atqa ATQA réel (hex) ; si null, valeur par défaut du type
     * @param sak SAK réel (hex) ; si null : 4K -> "18", 1K -> "08", Ultralight -> "00"
     */
    fun generate(
        uidHex: String,
        blocks: List<String>,
        deviceType: String = "Mifare Classic",
        atqa: String? = null,
        sak: String? = null
    ): String {
        val sb = StringBuilder()
        sb.append("Filetype: Flipper NFC device\n")
        sb.append("Version: 4\n")
        if (isUltralight(deviceType)) {
            sb.append("Device type: NTAG/Ultralight\n")
            sb.append("UID: ${spaced(uidHex)}\n")
            sb.append("ATQA: ${spaced(atqa ?: "00 44")}\n")
            sb.append("SAK: ${spaced(sak ?: "00")}\n")
            sb.append("Mifare Ultralight type: ${ultralightType(blocks.size)}\n")
            sb.append("Pages total: ${blocks.size}\n")
            sb.append("Pages read: ${blocks.count { !it.contains('-') }}\n")
            blocks.forEachIndexed { i, p -> sb.append("Page $i: ${spaced(p)}\n") }
        } else {
            val is4k = blocks.size > 64
            sb.append("Device type: Mifare Classic\n")
            sb.append("UID: ${spaced(uidHex)}\n")
            sb.append("ATQA: ${spaced(atqa ?: "00 04")}\n")
            sb.append("SAK: ${spaced(sak ?: if (is4k) "18" else "08")}\n")
            sb.append("Mifare Classic type: ${if (is4k) "4K" else "1K"}\n")
            sb.append("Data format version: 2\n")
            blocks.forEachIndexed { i, b -> sb.append("Block $i: ${spaced(b)}\n") }
        }
        return sb.toString()
    }

    private fun isUltralight(deviceType: String): Boolean =
        deviceType.contains("NTAG", ignoreCase = true) || deviceType.contains("Ultralight", ignoreCase = true)

    /** Nom de la puce selon le nombre de pages du dump. */
    private fun ultralightType(pages: Int): String = when (pages) {
        16 -> "Ultralight"
        48 -> "Ultralight C"
        45 -> "NTAG213"
        135 -> "NTAG215"
        231 -> "NTAG216"
        else -> "NTAG/Ultralight"
    }

    /**
     * Insère un espace tous les 2 caractères hex. Une paire contenant un tiret (octet inconnu)
     * devient "??".
     */
    private fun spaced(hex: String): String =
        hex.replace(" ", "").chunked(2).joinToString(" ") { if (it.contains('-')) "??" else it }
}
