package com.example.flipperdroid

import com.example.flipperdroid.infrared.AcProtocols
import com.example.flipperdroid.infrared.IrButton
import com.example.flipperdroid.infrared.IrFile
import com.example.flipperdroid.infrared.IrProtocols
import com.example.flipperdroid.infrared.PowerCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests JVM purs : rafales IR, parseurs .ir, saisie d'adresse, capacités des climatiseurs. */
class IrLogicTest {

    /** Taille attendue d'une rafale de [count] trames identiques de taille [frameSize]. */
    private fun expectedSameFrameBurstSize(frameSize: Int, count: Int): Int =
        count * frameSize + if (frameSize % 2 == 1) count - 1 else 0

    // --- Fix 1 : intervalle inter-trames ---

    @Test
    fun burst_sirc_gap_merges_into_trailing_space() {
        val (_, base) = IrProtocols.encode("SIRC", 0x01, 0x15)!!
        val (_, burst) = IrProtocols.encodePowerBurst("SIRC", 0x01, 0x15)!!
        assertEquals(0, base.size % 2)   // SIRC finit sur un space
        assertEquals(3 * base.size, burst.size)
        assertEquals(base.last() + 24000, burst[base.size - 1])
        assertEquals(base[0], burst[base.size])   // la répétition commence par un mark
    }

    @Test
    fun burst_nec_gap_is_a_separate_space_element() {
        val (_, base) = IrProtocols.encode("NEC", 0x04, 0x08)!!
        val (_, burst) = IrProtocols.encodePowerBurst("NEC", 0x04, 0x08)!!
        assertEquals(1, base.size % 2)   // NEC finit sur un mark
        assertEquals(40000, burst[base.size])        // espace inter-trame (indice impair)
        assertEquals(9000, burst[base.size + 1])     // code de répétition NEC (mark)
    }

    @Test
    fun burst_rc5_sizes_follow_frame_parity() {
        for (cmd in intArrayOf(0x0C, 0x0D)) {
            val (_, base) = IrProtocols.encode("RC5", 0x00, cmd)!!
            val (_, burst) = IrProtocols.encodePowerBurst("RC5", 0x00, cmd)!!
            assertEquals("RC5 cmd=0x${cmd.toString(16)}", expectedSameFrameBurstSize(base.size, 3), burst.size)
            assertTrue(burst.all { it > 0 })
        }
    }

    @Test
    fun burst_samsung_size_and_elements_are_positive() {
        val (_, samsung) = IrProtocols.encodePowerBurst("Samsung32", 0x07, 0x02)!!
        assertEquals(expectedSameFrameBurstSize(IrProtocols.encode("Samsung32", 0x07, 0x02)!!.second.size, 3), samsung.size)
        assertTrue(samsung.all { it > 0 })
    }

    @Test
    fun raw_burst_even_frame_gap_merges_into_space() {
        val raw = IrButton(name = "r", type = "raw", frequency = 38000, rawData = intArrayOf(9000, 4500, 560, 560))
        val (freq, burst) = raw.toBurst(presses = 2)!!
        assertEquals(38000, freq)
        assertEquals(listOf(9000, 4500, 560, 40560, 9000, 4500, 560, 560), burst.toList())
    }

    @Test
    fun raw_burst_odd_frame_gap_is_separate_element() {
        val raw = IrButton(name = "r", type = "raw", frequency = 38000, rawData = intArrayOf(9000, 4500, 560))
        val (_, burst) = raw.toBurst(presses = 2)!!
        assertEquals(listOf(9000, 4500, 560, 40000, 9000, 4500, 560), burst.toList())
    }

    // --- Fix 3 : sweep TV-B-Gone ---

    @Test
    fun power_toggle_name_excludes_explicit_on_off_pairs() {
        assertTrue(PowerCodes.isPowerToggleName("Power"))
        assertTrue(PowerCodes.isPowerToggleName("TV_Power"))
        assertFalse(PowerCodes.isPowerToggleName("Power_on"))
        assertFalse(PowerCodes.isPowerToggleName("Power off"))
        assertFalse(PowerCodes.isPowerToggleName("Vol+"))
    }

    @Test
    fun distinct_power_toggles_dedupes_same_protocol_address_command() {
        val a = IrButton(name = "Power", type = "parsed", protocol = "NEC", address = 4, command = 8)
        val b = IrButton(name = "TV Power", type = "parsed", protocol = "nec", address = 4, command = 8)
        val c = IrButton(name = "Power_off", type = "parsed", protocol = "NEC", address = 4, command = 9)
        val out = PowerCodes.distinctPowerToggles(listOf(a, b, c))
        assertEquals(listOf("Power"), out.map { it.name })
    }

    // --- Fix 5 : parseurs .ir stricts ---

    @Test
    fun ir_file_skips_button_with_invalid_raw_token() {
        assertTrue(IrFile.parse("name: Bad\ntype: raw\nfrequency: 38000\ndata: 9000 abc 560\n").isEmpty())
    }

    @Test
    fun ir_file_skips_button_with_non_positive_raw_token() {
        assertTrue(IrFile.parse("name: Bad\ntype: raw\nfrequency: 38000\ndata: 9000 -4 560\n").isEmpty())
    }

    @Test
    fun ir_file_skips_button_with_invalid_hex_byte() {
        val text = "name: Bad\ntype: parsed\nprotocol: NEC\naddress: 04 ZZ 00 00\ncommand: 08 00 00 00\n"
        assertTrue(IrFile.parse(text).isEmpty())
    }

    @Test
    fun ir_file_parses_little_endian_hex() {
        val text = "name: P\ntype: parsed\nprotocol: NEC\naddress: 04 01 00 00\ncommand: 08 00 00 00\n"
        val b = IrFile.parse(text).single()
        assertEquals(0x0104, b.address)
        assertEquals(8, b.command)
    }

    @Test
    fun ir_file_handles_crlf_and_bom() {
        val text = "﻿Filetype: IR signals file\r\n" +
            "name: Power\r\ntype: parsed\r\nprotocol: NEC\r\naddress: 04 00 00 00\r\ncommand: 08 00 00 00\r\n\r\n" +
            "name: Vol\r\ntype: raw\r\nfrequency: 38000\r\ndata: 9000 4500 560\r\n"
        val buttons = IrFile.parse(text)
        assertEquals(listOf("Power", "Vol"), buttons.map { it.name })
        assertEquals(4, buttons[0].address)
        assertEquals(listOf(9000, 4500, 560), buttons[1].rawData!!.toList())
    }

    // --- Fix 6 : saisie de l'adresse ---

    @Test
    fun hex_address_accepts_optional_prefix() {
        assertEquals(4, IrProtocols.parseHexAddress("04"))
        assertEquals(4, IrProtocols.parseHexAddress("0x04"))
        assertEquals(0x1F, IrProtocols.parseHexAddress("0X1f"))
    }

    @Test
    fun hex_address_rejects_invalid_input() {
        assertNull(IrProtocols.parseHexAddress("zz"))
        assertNull(IrProtocols.parseHexAddress(""))
        assertNull(IrProtocols.parseHexAddress("0x"))
        assertNull(IrProtocols.parseHexAddress("-4"))
    }

    // --- Fix 9 : capacités par marque ---

    @Test
    fun ac_brand_capabilities_match_encoders() {
        assertFalse(AcProtocols.Brand.COOLIX.supportsFanMode)
        assertFalse(AcProtocols.Brand.MIDEA.supportsFanMode)
        assertFalse(AcProtocols.Brand.LG.supportsSwing)
        assertTrue(AcProtocols.Brand.GREE.supportsSwing)
        assertTrue(AcProtocols.Brand.SAMSUNG.approximateChecksum)
    }
}
