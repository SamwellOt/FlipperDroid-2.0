package com.example.flipperdroid

import com.example.flipperdroid.flipper.FlipperNfc
import com.example.flipperdroid.flipper.FlipperSub
import com.example.flipperdroid.infrared.IrFile
import com.example.flipperdroid.infrared.IrProtocols
import com.example.flipperdroid.nfc.MifareClassicUtils
import com.example.flipperdroid.security.TotpGenerator
import com.example.flipperdroid.viewmodel.RootBadUsbViewModel
import com.example.flipperdroid.viewmodel.SkimmerDetectorViewModel
import com.example.flipperdroid.viewmodel.WifiNetwork
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class LogicUnitTest {

    // --- TOTP (RFC 6238 test vectors) ---
    @Test
    fun totp_rfc6238_vectors() {
        val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ" // "12345678901234567890" en Base32
        assertEquals("94287082", TotpGenerator.generate(secret, 59, 8))
        assertEquals("07081804", TotpGenerator.generate(secret, 1111111109, 8))
        assertEquals("89005924", TotpGenerator.generate(secret, 1234567890, 8))
    }

    @Test
    fun totp_base32_decode() {
        // "JBSWY3DPEE======" est le Base32 de "Hello!"
        assertArrayEquals("Hello!".toByteArray(), TotpGenerator.base32Decode("JBSWY3DPEE======"))
    }

    @Test
    fun totp_parse_otpauth_uri() {
        val (label, secret) = TotpGenerator.parseOtpauthUri("otpauth://totp/GitHub:alice?secret=JBSWY3DPEHPK3PXP&issuer=GitHub")!!
        assertEquals("alice", label)
        assertEquals("JBSWY3DPEHPK3PXP", secret)
    }

    // --- IR NEC encoding ---
    @Test
    fun ir_nec_structure() {
        val (freq, pattern) = IrProtocols.encode("NEC", 0x04, 0x08)!!
        assertEquals(38000, freq)
        assertEquals(67, pattern.size)        // header(2) + 32 bits*2 + trailer(1)
        assertEquals(9000, pattern[0])        // header mark
        assertEquals(4500, pattern[1])        // header space
        assertEquals(560, pattern[pattern.size - 1]) // trailer
    }

    @Test
    fun ir_necext_uses_inverted_command() {
        // NECext = adresse 16 bits + commande + inverse
        val (_, pattern) = IrProtocols.encode("NECext", 0xC2EA, 0x57)!!
        assertEquals(67, pattern.size)
    }

    @Test
    fun ir_unknown_protocol_null() {
        assertEquals(null, IrProtocols.encode("BOGUS", 1, 2))
    }

    // --- .ir file parsing ---
    @Test
    fun irfile_parse_parsed_and_raw() {
        val text = """
            Filetype: IR signals file
            Version: 1
            #
            name: Power
            type: parsed
            protocol: NEC
            address: 04 00 00 00
            command: 08 00 00 00
            #
            name: Beep
            type: raw
            frequency: 38000
            data: 9000 4500 560 560
        """.trimIndent()
        val buttons = IrFile.parse(text)
        assertEquals(2, buttons.size)
        assertEquals("Power", buttons[0].name)
        assertEquals(0x04, buttons[0].address)
        assertEquals(0x08, buttons[0].command)
        assertEquals("raw", buttons[1].type)
        assertArrayEquals(intArrayOf(9000, 4500, 560, 560), buttons[1].rawData)
        assertTrue(buttons[0].toSignal() != null)
    }

    // --- DuckyScript keycodes ---
    @Test
    fun ducky_keycodes() {
        assertEquals(0x04, RootBadUsbViewModel.charToKeycode('a').second)
        assertEquals(0x02, RootBadUsbViewModel.charToKeycode('A').first) // shift
        assertEquals(0x27, RootBadUsbViewModel.charToKeycode('0').second)
        assertEquals(0x28, RootBadUsbViewModel.charToKeycode('\n').second) // enter
    }

    // --- Mifare hex utils ---
    @Test
    fun mifare_hex_roundtrip() {
        val bytes = byteArrayOf(0x04, 0xA2.toByte(), 0xFF.toByte(), 0x00)
        val hex = MifareClassicUtils.bytesToHex(bytes)
        assertEquals("04A2FF00", hex)
        assertArrayEquals(bytes, MifareClassicUtils.hexToBytes(hex))
    }

    // --- Wi-Fi channel ---
    @Test
    fun wifi_frequency_to_channel() {
        assertEquals(1, WifiNetwork.frequencyToChannel(2412))
        assertEquals(6, WifiNetwork.frequencyToChannel(2437))
        assertEquals(36, WifiNetwork.frequencyToChannel(5180))
    }

    // --- Skimmer heuristic ---
    @Test
    fun skimmer_flags_hc05() {
        assertTrue(SkimmerDetectorViewModel.evaluate("HC-05", "12:34:56:78:9A:BC").first)
        assertTrue(SkimmerDetectorViewModel.evaluate("MyPhone", "00:14:03:11:22:33").first) // OUI suspect
        assertFalse(SkimmerDetectorViewModel.evaluate("AirPods", "AA:BB:CC:DD:EE:FF").first)
    }

    // --- Flipper .nfc round-trip ---
    @Test
    fun flipper_nfc_generate_and_parse() {
        val nfc = FlipperNfc.generate("04A2B3C4", listOf("00112233445566778899AABBCCDDEEFF"))
        val card = FlipperNfc.parse(nfc)
        assertEquals("Mifare Classic", card.deviceType)
        assertEquals("04 A2 B3 C4", card.uid)
        assertEquals(1, card.blocks.size)
    }

    // --- Flipper .sub parsing ---
    @Test
    fun flipper_sub_parse() {
        val text = """
            Filetype: Flipper SubGhz RAW File
            Version: 1
            Frequency: 433920000
            Preset: FuriHalSubGhzPresetOok650Async
            Protocol: RAW
            RAW_Data: 200 -300 400 -500
        """.trimIndent()
        val sub = FlipperSub.parse(text)
        assertEquals(433920000L, sub.frequency)
        assertEquals("RAW", sub.protocol)
        assertEquals(4, sub.rawData.size)
    }
}
