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
            // SIRC20 : 7 bits commande + 5 bits appareil + 8 bits étendus.
            // Les 8 bits étendus sont pris dans les bits hauts de l'adresse.
            "SIRC20" -> 40000 to sirc20(command and 0x7F, address and 0x1F, (address shr 5) and 0xFF)
            "RC5" -> 36000 to rc5(address and 0x1F, command and 0x3F, extended = false)
            // RC5X : commande étendue 7 bits (0..127) via le 2e bit de start.
            "RC5X" -> 36000 to rc5(address and 0x1F, command and 0x7F, extended = true)
            // RC6 mode 0 (Manchester, convention inverse de RC5, bit toggle double).
            "RC6", "RC6X" -> 36000 to rc6(address and 0xFF, command and 0xFF)
            "JVC" -> 38000 to jvc(address and 0xFF, command and 0xFF)
            "APPLE", "APPLETV" -> 38000 to apple(command and 0xFF)
            "KASEIKYO", "PANASONIC" -> 37000 to kaseikyo(address, command)
            // Pioneer utilise le format NEC (porteuse ~40 kHz).
            "PIONEER" -> 40000 to nec(address and 0xFF, command and 0xFF)
            // RCA : en-tête 4000/4000, 4 bits adresse + 8 bits commande puis leur
            // complément, MSB d'abord, porteuse 56 kHz.
            "RCA" -> 56000 to rca(address and 0x0F, command and 0xFF)
            "COOLIX", "AC" -> 38000 to coolix(command and 0xFFFFFF)
            // Denon / Sharp : pulse-distance sans en-tête, 15 bits LSB.
            "DENON" -> 38000 to denon(address and 0x1F, command and 0xFF)
            "SHARP" -> 38000 to sharp(address and 0x1F, command and 0xFF)
            // Mitsubishi (TV) : 16 bits MSB, sans en-tête, porteuse 33 kHz.
            "MITSUBISHI" -> 33000 to mitsubishi(address and 0xFF, command and 0xFF)
            // Sanyo LC7461 : en-tête NEC, 42 bits (adresse 13 bits + inverse, cmd + inverse).
            "SANYO", "SANYOLC7461" -> 38000 to sanyo(address and 0x1FFF, command and 0xFF)
            // Best-effort (timings SB-Projects, non vérifiés sur matériel) :
            "NOKIA", "NRC17" -> 38000 to nokiaNrc17(address and 0xFF, command and 0xFF)
            "RCMM" -> 36000 to rcmm(address and 0xFFFF, command and 0xFF)
            "PANASONICOLD" -> 38000 to panasonicOld(address and 0x1F, command and 0xFF)
            else -> null
        }
    }

    /**
     * Construit une **rafale** fiable représentant une seule pression : la trame
     * complète répétée [presses] fois dans UN SEUL transmit(), avec l'intervalle
     * propre au protocole.
     *
     * Pourquoi c'est indispensable pour que le TV-B-Gone fonctionne réellement :
     * une trame IR **isolée** est presque toujours filtrée comme du bruit par les
     * téléviseurs (LG, Samsung…), et le protocole **Sony SIRC impose au minimum 3
     * répétitions** — une seule trame ne fait jamais rien. Une vraie télécommande
     * répète la trame tant que le bouton est maintenu ; on reproduit ce
     * comportement. Émettre toutes les répétitions **d'un bloc** (une seule pression
     * logique) évite en plus le double-basculement (marche→arrêt) d'une commande
     * "power" à bascule, contrairement à des transmit() séparés espacés dans le temps.
     *
     * @param presses nombre de trames (0 = valeur par défaut adaptée au protocole).
     * @return (fréquence Hz, motif µs) ou null si protocole inconnu.
     */
    fun encodePowerBurst(
        protocol: String,
        address: Int,
        command: Int,
        presses: Int = 0
    ): Pair<Int, IntArray>? {
        val base = encode(protocol, address, command) ?: return null
        val (freq, frame) = base
        val p = protocol.uppercase().replace("_", "")
        // Élément de répétition + intervalle inter-trame + nombre de trames.
        val (repeatFrame, gapUs, defaultCount) = when {
            // Sony : période 45 ms, minimum 3 trames complètes (exigence du protocole).
            p.startsWith("SIRC") || p == "SONY" -> Triple(frame, 24000, 3)
            // Famille NEC : "bouton maintenu" = code de répétition NEC (9000/2250/560).
            p == "NEC" || p == "NECEXT" || p == "APPLE" || p == "APPLETV" ||
                p == "PIONEER" || p.startsWith("SANYO") ->
                Triple(NEC_REPEAT, 40000, 3)
            // Samsung, RC5/RC6, Kaseikyo, etc. : on répète la trame complète.
            else -> Triple(frame, 40000, 3)
        }
        val count = if (presses > 0) presses else defaultCount
        val out = ArrayList<Int>(frame.size + (count - 1) * (repeatFrame.size + 1))
        for (v in frame) out.add(v)
        repeat(count - 1) {
            out.add(gapUs)                 // espace inter-trame (le motif reprend par un mark)
            for (v in repeatFrame) out.add(v)
        }
        return freq to out.toIntArray()
    }

    /** Code de répétition NEC : en-tête 9000/2250 puis mark final 560 (bouton maintenu). */
    private val NEC_REPEAT = intArrayOf(9000, 2250, 560)

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

    /** Sony SIRC 20 bits : 7 bits commande + 5 bits appareil + 8 bits étendus. */
    private fun sirc20(cmd: Int, addr: Int, ext: Int): IntArray {
        val out = ArrayList<Int>()
        out.add(2400); out.add(600)
        for (i in 0 until 7) { out.add(if ((cmd shr i) and 1 == 1) 1200 else 600); out.add(600) }
        for (i in 0 until 5) { out.add(if ((addr shr i) and 1 == 1) 1200 else 600); out.add(600) }
        for (i in 0 until 8) { out.add(if ((ext shr i) and 1 == 1) 1200 else 600); out.add(600) }
        return out.toIntArray()
    }

    // --- RCA (en-tête 4000/4000, MSB d'abord, données + complément) ---

    private fun rca(addr: Int, cmd: Int): IntArray {
        val out = ArrayList<Int>()
        out.add(4000); out.add(4000)
        appendRca(out, addr and 0x0F, 4)
        appendRca(out, cmd and 0xFF, 8)
        appendRca(out, addr.inv() and 0x0F, 4)
        appendRca(out, cmd.inv() and 0xFF, 8)
        out.add(500) // mark final
        return out.toIntArray()
    }

    private fun appendRca(out: ArrayList<Int>, value: Int, bits: Int) {
        for (i in bits - 1 downTo 0) { // MSB d'abord
            out.add(500)
            out.add(if ((value shr i) and 1 == 1) 2000 else 1000)
        }
    }

    // --- RC5 (Manchester) ---

    private fun rc5(addr: Int, cmd: Int, extended: Boolean): IntArray {
        val bits = ArrayList<Int>()
        bits.add(1)                        // S1 (start, toujours 1)
        // S2 : RC5 classique = 1 ; RC5X = inverse du 7e bit de commande (permet 0..127).
        bits.add(if (extended) (cmd shr 6).inv() and 1 else 1)
        bits.add(0)                        // toggle (fixe côté émetteur)
        for (i in 4 downTo 0) bits.add((addr shr i) and 1) // 5 bits adresse (MSB d'abord)
        for (i in 5 downTo 0) bits.add((cmd shr i) and 1)  // 6 bits commande basse (MSB d'abord)

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

    // --- RC6 mode 0 (Manchester, convention inverse de RC5, toggle double largeur) ---

    private fun rc6(addr: Int, cmd: Int): IntArray {
        // Symboles = (valeur du bit, largeur double ?). En RC6 : 1 bit de start (=1),
        // 3 bits de mode (000), 1 bit toggle (largeur double), 8 bits adresse, 8 bits
        // commande (MSB d'abord).
        val symbols = ArrayList<Pair<Int, Boolean>>()
        symbols.add(1 to false)                                   // start
        repeat(3) { symbols.add(0 to false) }                     // mode 0
        symbols.add(0 to true)                                    // toggle (double)
        for (i in 7 downTo 0) symbols.add(((addr shr i) and 1) to false)
        for (i in 7 downTo 0) symbols.add(((cmd shr i) and 1) to false)

        val t = 444
        val levels = ArrayList<Pair<Boolean, Int>>()
        levels.add(true to (6 * t))   // en-tête : mark 6T
        levels.add(false to (2 * t))  // en-tête : space 2T
        for ((bit, dbl) in symbols) {
            val half = if (dbl) 2 * t else t
            // RC6 : '1' = mark→space, '0' = space→mark (inverse de RC5).
            if (bit == 1) { levels.add(true to half); levels.add(false to half) }
            else { levels.add(false to half); levels.add(true to half) }
        }
        return levelsToPattern(levels)
    }

    // --- Pulse-distance générique au bit près (protocoles non alignés sur l'octet) ---

    private fun pdBits(
        headerMark: Int, headerSpace: Int,
        bitMark: Int, zeroSpace: Int, oneSpace: Int, trailerMark: Int,
        value: Long, nbits: Int, lsbFirst: Boolean
    ): IntArray {
        val out = ArrayList<Int>()
        if (headerMark > 0) { out.add(headerMark); out.add(headerSpace) }
        for (i in 0 until nbits) {
            val bitIndex = if (lsbFirst) i else (nbits - 1 - i)
            val bit = ((value shr bitIndex) and 1L).toInt()
            out.add(bitMark)
            out.add(if (bit == 1) oneSpace else zeroSpace)
        }
        out.add(trailerMark)
        return out.toIntArray()
    }

    /** Sharp : 15 bits LSB = adresse(5) + commande(8) + expansion(1) + check(0), sans en-tête. */
    private fun sharp(addr: Int, cmd: Int): IntArray {
        val value = (addr and 0x1F).toLong() or
            ((cmd and 0xFF).toLong() shl 5) or
            (1L shl 13)   // expansion = 1, check = 0
        return pdBits(0, 0, 320, 680, 1680, 320, value, 15, lsbFirst = true)
    }

    /** Denon : proche de Sharp (15 bits LSB), timings propres. */
    private fun denon(addr: Int, cmd: Int): IntArray {
        val value = (addr and 0x1F).toLong() or ((cmd and 0xFF).toLong() shl 5)
        return pdBits(0, 0, 264, 782, 1794, 264, value, 15, lsbFirst = true)
    }

    /** Mitsubishi (TV) : 16 bits MSB, sans en-tête, porteuse 33 kHz. */
    private fun mitsubishi(addr: Int, cmd: Int): IntArray {
        val value = (((addr and 0xFF) shl 8) or (cmd and 0xFF)).toLong()
        return pdBits(0, 0, 300, 500, 1500, 300, value, 16, lsbFirst = false)
    }

    /** Sanyo LC7461 : en-tête NEC 9000/4500, 42 bits LSB (adr13 + ~adr13 + cmd8 + ~cmd8). */
    private fun sanyo(addr: Int, cmd: Int): IntArray {
        val a = (addr and 0x1FFF).toLong()
        val c = (cmd and 0xFF).toLong()
        val value = a or
            ((a.inv() and 0x1FFFL) shl 13) or
            (c shl 26) or
            ((c.inv() and 0xFFL) shl 34)
        return pdBits(9000, 4500, 560, 560, 1690, 560, value, 42, lsbFirst = true)
    }

    /** RCMM (Philips) : 2 bits par symbole via 4 longueurs d'espace. 24 bits, MSB. */
    private fun rcmm(hi16: Int, cmd: Int): IntArray {
        val value = ((hi16 and 0xFFFF) shl 8) or (cmd and 0xFF)
        val spaces = intArrayOf(277, 444, 611, 778) // 00, 01, 10, 11
        val out = ArrayList<Int>()
        out.add(416); out.add(277) // en-tête
        var i = 22
        while (i >= 0) {
            out.add(166); out.add(spaces[(value shr i) and 0x3])
            i -= 2
        }
        out.add(166) // mark final
        return out.toIntArray()
    }

    /**
     * Nokia NRC17 — best-effort (timings SB-Projects, NON vérifié sur matériel).
     * Pré-impulsion 500/2500 puis 16 bits LSB (commande + adresse).
     */
    private fun nokiaNrc17(addr: Int, cmd: Int): IntArray {
        val value = (cmd and 0xFF).toLong() or ((addr and 0xFF).toLong() shl 8)
        return pdBits(500, 2500, 500, 500, 1000, 500, value, 16, lsbFirst = true)
    }

    /**
     * Panasonic "old" (22 bits) — best-effort (timings SB-Projects, NON vérifié).
     */
    private fun panasonicOld(addr: Int, cmd: Int): IntArray {
        val value = (addr and 0x1F).toLong() or ((cmd and 0xFF).toLong() shl 5)
        return pdBits(3502, 3510, 872, 872, 2612, 872, value, 22, lsbFirst = true)
    }
}
