package com.example.flipperdroid.flipper

/** Représentation d'un fichier .nfc du Flipper Zero. */
data class NfcCard(
    val deviceType: String?,
    val uid: String?,
    val atqa: String?,
    val sak: String?,
    val blocks: List<String>
)

/**
 * Lecteur / générateur du format de fichier .nfc du Flipper Zero.
 */
object FlipperNfc {

    fun parse(text: String): NfcCard {
        var deviceType: String? = null
        var uid: String? = null
        var atqa: String? = null
        var sak: String? = null
        val blocks = mutableListOf<String>()

        for (raw in text.split("\n")) {
            val line = raw.trim()
            val idx = line.indexOf(':')
            if (idx <= 0) continue
            val key = line.substring(0, idx).trim()
            val value = line.substring(idx + 1).trim()
            when {
                key.equals("Device type", true) -> deviceType = value
                key.equals("UID", true) -> uid = value
                key.equals("ATQA", true) -> atqa = value
                key.equals("SAK", true) -> sak = value
                key.startsWith("Block", true) -> blocks.add(value)
            }
        }
        return NfcCard(deviceType, uid, atqa, sak, blocks)
    }

    /**
     * Génère un fichier .nfc (format Flipper v4) à partir d'un dump Mifare Classic.
     * @param uidHex UID sans espaces (ex: "04A2B3C4")
     * @param blocks liste de blocs en hex (32 caractères chacun)
     */
    fun generate(uidHex: String, blocks: List<String>, sak: String = "08", atqa: String = "00 04"): String {
        val sb = StringBuilder()
        sb.append("Filetype: Flipper NFC device\n")
        sb.append("Version: 4\n")
        sb.append("Device type: Mifare Classic\n")
        sb.append("UID: ${spaced(uidHex)}\n")
        sb.append("ATQA: $atqa\n")
        sb.append("SAK: $sak\n")
        sb.append("Mifare Classic type: ${if (blocks.size > 64) "4K" else "1K"}\n")
        sb.append("Data format version: 2\n")
        blocks.forEachIndexed { i, b ->
            sb.append("Block $i: ${spaced(b)}\n")
        }
        return sb.toString()
    }

    /** Insère un espace tous les 2 caractères hex. */
    private fun spaced(hex: String): String =
        hex.replace(" ", "").chunked(2).joinToString(" ")
}
