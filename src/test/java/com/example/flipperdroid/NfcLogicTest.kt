package com.example.flipperdroid

import com.example.flipperdroid.flipper.FlipperNfc
import com.example.flipperdroid.nfc.EmvCardData
import com.example.flipperdroid.nfc.EmvTlv
import com.example.flipperdroid.nfc.MifareClassicUtils
import com.example.flipperdroid.nfc.MifareUltralightUtils
import com.example.flipperdroid.nfc.Type4Responder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests JVM des noyaux purs NFC (pas d'appareil ni de SDK Android requis). */
class NfcLogicTest {

    /** Construit un tableau d'octets à partir d'entiers 0..255. */
    private fun b(vararg v: Int): ByteArray = ByteArray(v.size) { v[it].toByte() }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02X".format(it) }

    /** "AABB" -> "AA BB" (format d'affichage Flipper). */
    private fun spacedHex(s: String): String = s.chunked(2).joinToString(" ")

    // ---------------------------------------------------------------- Ultralight / NTAG

    @Test
    fun protectedPages_by_tag_size() {
        assertEquals(40..44, MifareUltralightUtils.protectedPages(45))     // NTAG213
        assertEquals(130..134, MifareUltralightUtils.protectedPages(135))  // NTAG215
        assertEquals(226..230, MifareUltralightUtils.protectedPages(231))  // NTAG216
        assertEquals(2..3, MifareUltralightUtils.protectedPages(16))       // Ultralight
        assertNull(MifareUltralightUtils.protectedPages(100))              // taille inconnue
    }

    @Test
    fun ntag_capacity_from_get_version_storage_byte() {
        assertEquals(45, MifareUltralightUtils.ntagPagesFromStorageSize(0x0F))
        assertEquals(135, MifareUltralightUtils.ntagPagesFromStorageSize(0x11))
        assertEquals(231, MifareUltralightUtils.ntagPagesFromStorageSize(0x13))
        assertNull(MifareUltralightUtils.ntagPagesFromStorageSize(0x00))
    }

    // ---------------------------------------------------------------- hex

    @Test
    fun hexToBytes_edge_cases() {
        assertArrayEquals(ByteArray(0), MifareClassicUtils.hexToBytes(""))  // vide -> tableau vide
        assertNull(MifareClassicUtils.hexToBytes("0"))                       // longueur impaire
        assertNull(MifareClassicUtils.hexToBytes("zz"))                      // non hexadécimal
        assertArrayEquals(b(0x0A, 0x0B), MifareClassicUtils.hexToBytes("0A0B"))
        assertArrayEquals(b(0x0A, 0x0B), MifareClassicUtils.hexToBytes("0a0b"))
    }

    // ---------------------------------------------------------------- Type 4 (HCE NDEF)

    private val selectApp = b(0x00, 0xA4, 0x04, 0x00, 0x07, 0xD2, 0x76, 0x00, 0x00, 0x85, 0x01, 0x01, 0x00)
    private val selectCc = b(0x00, 0xA4, 0x00, 0x0C, 0x02, 0xE1, 0x03)
    private val selectNdef = b(0x00, 0xA4, 0x00, 0x0C, 0x02, 0xE1, 0x04)

    /** READ BINARY : 00 B0 offHi offLo Le */
    private fun readBinary(offset: Int, le: Int) = b(0x00, 0xB0, (offset shr 8) and 0xFF, offset and 0xFF, le)

    @Test
    fun type4_capability_container_is_15_bytes_with_expected_fields() {
        val r = Type4Responder(b(0xD1, 0x01, 0x02, 0x54, 0x00))
        assertEquals("9000", hex(r.process(selectApp)))
        assertEquals("9000", hex(r.process(selectCc)))
        val cc = r.process(readBinary(0, 0x0F))
        assertEquals(15 + 2, cc.size)
        // CCLEN 000F, version 20, MLe 003B, MLc 0034, TLV NDEF E104 taille 0400, lecture 00, écriture FF
        assertEquals("000F20003B00340406E104040000FF9000", hex(cc))
    }

    @Test
    fun type4_ndef_file_returns_nlen_and_message() {
        val r = Type4Responder(b(0xD1, 0x01, 0x02, 0x54, 0x00))
        r.process(selectApp)
        assertEquals("9000", hex(r.process(selectNdef)))
        // NLEN = 0005, puis le message de 5 octets, puis 9000
        assertEquals("0005D1010254009000", hex(r.process(readBinary(0, 7))))
    }

    @Test
    fun type4_le_zero_means_256_bytes() {
        val message = ByteArray(300) { 0x11.toByte() }
        val r = Type4Responder(message)
        r.process(selectApp)
        r.process(selectNdef)
        val first = r.process(readBinary(0, 0))
        assertEquals(256 + 2, first.size)            // Le=0 -> 256 octets + SW
        assertEquals(0x01.toByte(), first[0])        // NLEN = 300 = 0x012C
        assertEquals(0x2C.toByte(), first[1])
        val rest = r.process(readBinary(256, 0))
        assertEquals(302 - 256 + 2, rest.size)       // il reste 46 octets + SW
    }

    @Test
    fun type4_offset_past_end_returns_6b00() {
        val r = Type4Responder(b(0x01))
        r.process(selectApp)
        r.process(selectNdef)
        assertEquals("6B00", hex(r.process(readBinary(0x1000, 1))))
    }

    @Test
    fun type4_unknown_application_returns_6a82() {
        val r = Type4Responder(b(0x01))
        val unknownAid = b(0x00, 0xA4, 0x04, 0x00, 0x05, 0xF0, 0x01, 0x02, 0x03, 0x04, 0x00)
        assertEquals("6A82", hex(r.process(unknownAid)))
    }

    @Test
    fun type4_file_selection_requires_application_first() {
        val r = Type4Responder(b(0x01))
        assertEquals("6A82", hex(r.process(selectNdef)))
    }

    @Test
    fun type4_read_without_selected_file_returns_6986() {
        val r = Type4Responder(b(0x01))
        r.process(selectApp)
        assertEquals("6986", hex(r.process(readBinary(0, 2))))
    }

    @Test
    fun type4_update_binary_is_refused_and_unknown_ins_not_supported() {
        val r = Type4Responder(b(0x01))
        r.process(selectApp)
        r.process(selectNdef)
        assertEquals("6982", hex(r.process(b(0x00, 0xD6, 0x00, 0x00, 0x02, 0xAB, 0xCD))))
        assertEquals("6D00", hex(r.process(b(0x00, 0xCA, 0x00, 0x00, 0x00))))
    }

    // ---------------------------------------------------------------- BER-TLV (EMV)

    @Test
    fun tlv_multi_byte_tag_9f38() {
        val data = b(0x9F, 0x38, 0x03, 0x01, 0x02, 0x03)
        assertArrayEquals(b(0x01, 0x02, 0x03), EmvTlv.findFirst(data, 0x9F38))
    }

    @Test
    fun tlv_long_form_length_0x81_and_0x82() {
        val pan81 = b(0x5A, 0x81, 0x10) + ByteArray(16) { 0x41.toByte() }
        assertEquals(16, EmvTlv.findFirst(pan81, 0x5A)?.size)

        val long82 = b(0x5F, 0x20, 0x82, 0x01, 0x00) + ByteArray(256) { 0x42.toByte() }
        assertEquals(256, EmvTlv.findFirst(long82, 0x5F20)?.size)
    }

    @Test
    fun tlv_nested_constructed_template() {
        // 70 (template constructé) contenant 5A
        val inner = b(0x5A, 0x04, 0x41, 0x42, 0x43, 0x44)
        val data = b(0x70, inner.size) + inner
        assertArrayEquals(b(0x41, 0x42, 0x43, 0x44), EmvTlv.findFirst(data, 0x5A))
    }

    @Test
    fun tlv_truncated_input_does_not_crash() {
        assertNull(EmvTlv.findFirst(b(0x70, 0x10, 0x5A, 0x08, 0x41, 0x11), 0x5A)) // longueur > données
        assertNull(EmvTlv.findFirst(b(0x5A), 0x5A))                               // tag sans longueur
        assertNull(EmvTlv.findFirst(b(0x9F), 0x9F38))                             // tag multi-octets tronqué
        assertNull(EmvTlv.findFirst(b(0x5A, 0x81), 0x5A))                         // longueur longue tronquée
        assertNull(EmvTlv.findFirst(b(0x5A, 0x85, 0x01), 0x5A))                   // n > 4 : invalide
        assertEquals(0x07, EmvTlv.findFirst(b(0x00, 0x00, 0x5A, 0x01, 0x07), 0x5A)!![0].toInt())
    }

    @Test
    fun emv_pan_is_masked_in_output() {
        val card = EmvCardData(pan = "4111 1111 1111 1111", expiryDate = "12/30", cardType = "Visa")
        assertEquals("************1111", card.maskedPan())
        assertFalse(card.toString().contains("4111"))
        assertTrue(card.toString().contains("1111"))
    }

    // ---------------------------------------------------------------- Flipper .nfc

    @Test
    fun flipper_nfc_4k_uses_sak_18_and_round_trips() {
        val blocks = MutableList(256) { "00112233445566778899AABBCCDDEEFF" }
        blocks[1] = MifareClassicUtils.NO_DATA
        val text = FlipperNfc.generate("04A2B3C4", blocks)
        assertTrue(text.contains("SAK: 18"))
        assertTrue(text.contains("Mifare Classic type: 4K"))
        val card = FlipperNfc.parse(text)
        assertTrue(card.valid)
        assertEquals("18", card.sak)
        assertEquals("4K", text.lines().first { it.startsWith("Mifare Classic type:") }.substringAfter(": "))
        assertEquals(256, card.blocks.size)
        assertEquals("00 11 22 33 44 55 66 77 88 99 AA BB CC DD EE FF", card.blocks[0])
    }

    @Test
    fun flipper_nfc_unknown_bytes_rendered_as_question_marks() {
        val text = FlipperNfc.generate("04A2B3C4", listOf(MifareClassicUtils.NO_DATA))
        assertFalse(text.contains("--"))
        val expected = "?? ".repeat(15) + "??"
        assertTrue(text.contains("Block 0: $expected"))
    }

    @Test
    fun flipper_nfc_ultralight_layout_round_trip() {
        val pages = List(45) { String.format("%08X", it * 0x01010101) }
        val text = FlipperNfc.generate("04A2B3C4", pages, deviceType = "NTAG/Ultralight")
        assertTrue(text.contains("Device type: NTAG/Ultralight"))
        assertTrue(text.contains("Mifare Ultralight type: NTAG213"))
        assertTrue(text.contains("Pages total: 45"))
        assertTrue(text.contains("Page 44: ${spacedHex(pages[44])}"))

        val card = FlipperNfc.parse(text)
        assertTrue(card.valid)
        assertEquals("NTAG/Ultralight", card.deviceType)
        assertEquals(45, card.pages.size)          // "Pages total/read" ne doivent pas être comptés
        assertEquals(spacedHex(pages[4]), card.pages[4])
        assertTrue(card.blocks.isEmpty())
    }

    @Test
    fun flipper_nfc_parse_flags_missing_filetype_header() {
        val card = FlipperNfc.parse("UID: 04 A2 B3 C4\nBlock 0: 00 11 22 33")
        assertFalse(card.valid)
        assertEquals("04 A2 B3 C4", card.uid)
        assertEquals(1, card.blocks.size)
        assertNull(card.deviceType)
    }
}
