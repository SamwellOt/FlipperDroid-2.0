package com.example.flipperdroid.infrared

/** Un code d'extinction pour le balayage TV-B-Gone. */
data class PowerCode(
    val brand: String,
    val protocol: String,
    val address: Int,
    val command: Int
) {
    fun toSignal(): Pair<Int, IntArray>? = IrProtocols.encode(protocol, address, command)

    /** Rafale répétée (une pression fiable) — voir IrProtocols.encodePowerBurst. */
    fun toBurst(presses: Int = 0): Pair<Int, IntArray>? =
        IrProtocols.encodePowerBurst(protocol, address, command, presses)
}

/**
 * Base de codes "power" publiés pour le mode TV-B-Gone : on émet chaque code
 * l'un après l'autre pour éteindre n'importe quelle TV / projecteur / afficheur
 * à portée. Un code qui ne correspond pas à l'appareil est simplement ignoré
 * par celui-ci, donc balayer large est sans risque.
 *
 * Ces codes complètent ceux extraits dynamiquement des fichiers .ir empaquetés
 * (voir IrToolsViewModel.tvBGone).
 */
object PowerCodes {
    val EXTRA: List<PowerCode> = listOf(
        // --- Samsung : 0x07/0x02 = trame E0E040BF (universel TV Samsung) ---
        PowerCode("Samsung", "Samsung32", 0x07, 0x02),
        PowerCode("Samsung-alt", "Samsung32", 0x07, 0x99),   // POWER OFF discret (certains modèles)
        // --- LG : 0x04/0x08 = trame 20DF10EF (universel TV LG, y compris webOS) ---
        PowerCode("LG", "NEC", 0x04, 0x08),
        PowerCode("LG-alt", "NECext", 0x20DF, 0x10),         // forme 16 bits équivalente
        // --- Sony : SIRC exige >=3 répétitions (géré par la rafale) ---
        PowerCode("Sony-12", "SIRC", 0x01, 0x15),
        PowerCode("Sony-15", "SIRC15", 0x01, 0x15),
        // --- Philips : anciens en RC5, récents en RC6 ---
        PowerCode("Philips-RC5", "RC5", 0x00, 0x0C),
        PowerCode("Philips-RC6", "RC6", 0x00, 0x0C),
        // --- Panasonic : Kaseikyo device 0x40 / subdevice 0x04 ---
        PowerCode("Panasonic", "Kaseikyo", 0x0440, 0x3D),
        PowerCode("Panasonic-alt", "Kaseikyo", 0x80, 0x3D),
        PowerCode("Toshiba", "NEC", 0x40, 0x12),
        PowerCode("Vizio", "NEC", 0x00, 0x08),
        PowerCode("Vizio-alt", "NEC", 0x04, 0x08),
        PowerCode("TCL", "NEC", 0x04, 0x08),
        PowerCode("Hisense", "NEC", 0x00, 0x08),
        PowerCode("Element", "NEC", 0x00, 0x0A),
        PowerCode("Insignia", "NEC", 0x04, 0x08),
        PowerCode("Sanyo", "NEC", 0x1C, 0x48),
        PowerCode("JVC", "JVC", 0x03, 0x17),
        PowerCode("Pioneer", "PIONEER", 0xA5, 0x1A),
        PowerCode("RCA", "RCA", 0x0F, 0x2A),
        PowerCode("Sharp", "NEC", 0x01, 0x1E),
        PowerCode("Emerson", "NEC", 0x00, 0x0A),
        PowerCode("Sceptre", "NEC", 0x04, 0x08),
        PowerCode("Epson-Proj", "NECext", 0xC1AA, 0x90),
    )
}
