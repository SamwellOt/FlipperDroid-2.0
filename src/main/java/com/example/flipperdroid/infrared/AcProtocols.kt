package com.example.flipperdroid.infrared

/**
 * Encodeurs de climatiseurs à "état complet" : contrairement aux télécommandes
 * TV (une trame = une touche), un climatiseur émet tout son état (marche, mode,
 * température, ventilation, oscillation) dans une seule trame longue avec somme
 * de contrôle. Timings et layout repris d'IRremoteESP8266.
 */
object AcProtocols {

    // Modes
    const val MODE_AUTO = 0
    const val MODE_COOL = 1
    const val MODE_DRY = 2
    const val MODE_FAN = 3
    const val MODE_HEAT = 4

    // Ventilation
    const val FAN_AUTO = 0
    const val FAN_LOW = 1
    const val FAN_MED = 2
    const val FAN_HIGH = 3

    /**
     * Gree YAW1F / YB1F (8 octets). @return (fréquence Hz, motif µs).
     * @param tempC température en °C, bornée à 16..30.
     */
    fun gree(
        power: Boolean,
        mode: Int,
        tempC: Int,
        fan: Int,
        swing: Boolean
    ): Pair<Int, IntArray> {
        // État par défaut documenté (light on, signature 0x5 sur l'octet 3).
        val state = intArrayOf(0x00, 0x09, 0x20, 0x50, 0x00, 0x20, 0x00, 0x50)

        // Octet 0 : mode(0-2), marche(3), ventilation(4-5), oscillation auto(6)
        state[0] = (mode and 0x07) or
            (if (power) 0x08 else 0) or
            ((fan and 0x03) shl 4) or
            (if (swing) 0x40 else 0)

        // Octet 1 : température (bits 0-3) = tempC - 16
        val t = tempC.coerceIn(16, 30) - 16
        state[1] = (state[1] and 0xF0) or (t and 0x0F)

        // Somme de contrôle (nibble haut de l'octet 7)
        var sum = 0x0A
        for (i in 0..3) sum += state[i] and 0x0F
        for (i in 4..6) sum += (state[i] shr 4) and 0x0F
        sum = sum and 0x0F
        state[7] = (state[7] and 0x0F) or (sum shl 4)

        return 38000 to greeFrame(state)
    }

    private fun greeFrame(state: IntArray): IntArray {
        val out = ArrayList<Int>()
        out.add(9000); out.add(4500) // en-tête
        for (i in 0..3) appendBitsLsb(out, state[i], 8) // premier bloc (4 octets)
        appendBitsLsb(out, 0b010, 3)                    // 3 bits constants
        out.add(620); out.add(19000)                    // séparateur de bloc
        for (i in 4..7) appendBitsLsb(out, state[i], 8) // second bloc (4 octets)
        out.add(620); out.add(19000)                    // pied
        return out.toIntArray()
    }

    private fun appendBitsLsb(out: ArrayList<Int>, value: Int, bits: Int) {
        for (i in 0 until bits) {
            out.add(620)
            out.add(if ((value shr i) and 1 == 1) 1600 else 540)
        }
    }
}
