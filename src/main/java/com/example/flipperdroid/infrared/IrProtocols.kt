package com.example.flipperdroid.infrared

/**
 * Encodeurs de protocoles infrarouge : transforment (protocole, adresse, commande)
 * en une paire (fréquence porteuse, motif de durées en µs) prête pour
 * ConsumerIrManager.transmit(). Couvre les protocoles les plus courants des
 * fichiers .ir du Flipper Zero.
 */
object IrProtocols {

    /** @return (fréquence Hz, motif µs) ou null si protocole inconnu. */
    fun encode(protocol: String, address: Int, command: Int): Pair<Int, IntArray>? {
        return when (protocol.uppercase().replace("_", "")) {
            "NEC" -> 38000 to nec(address and 0xFF, command and 0xFF)
            "NECEXT" -> 38000 to necExt(address and 0xFFFF, command and 0xFFFF)
            "SAMSUNG32", "SAMSUNG" -> 38000 to samsung32(address and 0xFF, command and 0xFF)
            "SONY", "SIRC" -> 40000 to sirc(command and 0x7F, address and 0x1F, 5)
            "SIRC15" -> 40000 to sirc(command and 0x7F, address and 0xFF, 8)
            "RC5", "RC5X" -> 36000 to rc5(address and 0x1F, command and 0x3F)
            "JVC" -> 38000 to jvc(address and 0xFF, command and 0xFF)
            "APPLE", "APPLETV" -> 38000 to apple(command and 0xFF)
            "KASEIKYO", "PANASONIC" -> 37000 to kaseikyo(address, command)
            "COOLIX", "AC" -> 38000 to coolix(command and 0xFFFFFF)
            else -> null
        }
    }

    // --- Pulse-distance (NEC / Samsung) ---

    private fun nec(addr: Int, cmd: Int): IntArray {
        val bytes = intArrayOf(addr, addr.inv() and 0xFF, cmd, cmd.inv() and 0xFF)
        return pulseDistance(9000, 4500, 560, 560, 1690, 560, bytes)
    }

    private fun necExt(addr: Int, cmd: Int): IntArray {
        // Adresse 16 bits (sans inversion) + commande 8 bits + son inverse.
        // C'est le "NECext" du Flipper (utilisé par Roku, projecteurs Epson, etc.).
        val bytes = intArrayOf(addr and 0xFF, (addr shr 8) and 0xFF, cmd and 0xFF, cmd.inv() and 0xFF)
        return pulseDistance(9000, 4500, 560, 560, 1690, 560, bytes)
    }

    /** Apple Remote : adresse 0x77E1 + commande + identifiant appareil fixe 0x70 (basé sur NEC). */
    private fun apple(cmd: Int): IntArray {
        val bytes = intArrayOf(0xE1, 0x77, cmd and 0xFF, 0x70)
        return pulseDistance(9000, 4500, 560, 560, 1690, 560, bytes)
    }

    private fun samsung32(addr: Int, cmd: Int): IntArray {
        val bytes = intArrayOf(addr, addr, cmd, cmd.inv() and 0xFF)
        return pulseDistance(4500, 4500, 560, 560, 1690, 560, bytes)
    }

    /**
     * Construit un motif pulse-distance : en-tête, puis chaque octet (LSB d'abord)
     * codé mark + space court/long, terminé par un mark de fin.
     */
    private fun pulseDistance(
        headerMark: Int, headerSpace: Int,
        bitMark: Int, zeroSpace: Int, oneSpace: Int, trailerMark: Int,
        bytes: IntArray
    ): IntArray {
        val out = ArrayList<Int>()
        out.add(headerMark); out.add(headerSpace)
        for (b in bytes) {
            for (i in 0 until 8) {
                val bit = (b shr i) and 1
                out.add(bitMark)
                out.add(if (bit == 1) oneSpace else zeroSpace)
            }
        }
        out.add(trailerMark)
        return out.toIntArray()
    }

    private fun jvc(addr: Int, cmd: Int): IntArray {
        // 16 bits (8 adresse + 8 commande), LSB d'abord, en-tête 8400/4200
        val bytes = intArrayOf(addr and 0xFF, cmd and 0xFF)
        return pulseDistance(8400, 4200, 525, 525, 1575, 525, bytes)
    }

    /** Kaseikyo (variante Panasonic, vendor 0x2002). address = device | (subdevice<<8). */
    private fun kaseikyo(address: Int, command: Int): IntArray {
        val device = address and 0xFF
        val subdevice = (address shr 8) and 0xFF
        val cmd = command and 0xFF
        val checksum = device xor subdevice xor cmd
        // vendor 0x2002 en LSB (0x02, 0x20), puis device, subdevice, commande, checksum
        val bytes = intArrayOf(0x02, 0x20, device, subdevice, cmd, checksum)
        return pulseDistance(3456, 1728, 432, 432, 1296, 432, bytes)
    }

    // --- Coolix (climatiseurs), 24 bits, chaque octet suivi de son complément (MSB d'abord) ---

    private fun coolix(data: Int): IntArray {
        val out = ArrayList<Int>()
        repeat(2) { // Coolix répète la trame
            out.add(4692); out.add(4416) // en-tête
            for (shift in intArrayOf(16, 8, 0)) {
                val b = (data shr shift) and 0xFF
                appendByteMsb(out, b)
                appendByteMsb(out, b.inv() and 0xFF)
            }
            out.add(560); out.add(4416) // séparateur
        }
        out.add(560) // mark final
        return out.toIntArray()
    }

    private fun appendByteMsb(out: ArrayList<Int>, b: Int) {
        for (i in 7 downTo 0) {
            out.add(560)
            out.add(if ((b shr i) and 1 == 1) 1656 else 560)
        }
    }

    // --- Sony SIRC (mark variable) ---

    private fun sirc(cmd: Int, addr: Int, addrBits: Int): IntArray {
        val out = ArrayList<Int>()
        out.add(2400); out.add(600) // en-tête
        for (i in 0 until 7) { // 7 bits commande, LSB d'abord
            out.add(if ((cmd shr i) and 1 == 1) 1200 else 600)
            out.add(600)
        }
        for (i in 0 until addrBits) { // bits adresse, LSB d'abord
            out.add(if ((addr shr i) and 1 == 1) 1200 else 600)
            out.add(600)
        }
        return out.toIntArray()
    }

    // --- RC5 (Manchester) ---

    private fun rc5(addr: Int, cmd: Int): IntArray {
        val bits = ArrayList<Int>()
        bits.add(1); bits.add(1)          // bits de start
        bits.add(0)                        // toggle (fixe)
        for (i in 4 downTo 0) bits.add((addr shr i) and 1) // 5 bits adresse (MSB d'abord)
        for (i in 5 downTo 0) bits.add((cmd shr i) and 1)  // 6 bits commande (MSB d'abord)

        val half = 889
        val levels = ArrayList<Pair<Boolean, Int>>()
        for (b in bits) {
            if (b == 1) { levels.add(false to half); levels.add(true to half) }  // 1 = space->mark
            else { levels.add(true to half); levels.add(false to half) }         // 0 = mark->space
        }
        return levelsToPattern(levels)
    }

    /** Fusionne les niveaux identiques consécutifs et produit un motif on/off commençant par ON. */
    private fun levelsToPattern(levels: List<Pair<Boolean, Int>>): IntArray {
        val merged = ArrayList<Pair<Boolean, Int>>()
        for (l in levels) {
            if (merged.isNotEmpty() && merged.last().first == l.first) {
                val last = merged.removeAt(merged.size - 1)
                merged.add(l.first to (last.second + l.second))
            } else merged.add(l)
        }
        // Retire un éventuel niveau OFF en tête (rien n'est émis avant le premier mark)
        val start = if (merged.isNotEmpty() && !merged[0].first) 1 else 0
        val out = ArrayList<Int>()
        for (i in start until merged.size) out.add(merged[i].second)
        return out.toIntArray()
    }
}
