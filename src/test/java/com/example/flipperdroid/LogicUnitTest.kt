package com.example.flipperdroid

import com.example.flipperdroid.camera.OnvifTools
import com.example.flipperdroid.flipper.FlipperNfc
import com.example.flipperdroid.flipper.FlipperSub
import com.example.flipperdroid.infrared.AcProtocols
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

    @Test
    fun ir_sirc20_structure() {
        val (freq, pattern) = IrProtocols.encode("SIRC20", 0x97, 0x25)!!
        assertEquals(40000, freq)
        assertEquals(42, pattern.size)   // header(2) + (7+5+8)=20 bits * 2
        assertEquals(2400, pattern[0])
        assertEquals(600, pattern[1])
    }

    @Test
    fun ir_pioneer_uses_nec_structure() {
        val (freq, pattern) = IrProtocols.encode("PIONEER", 0xA5, 0x1A)!!
        assertEquals(40000, freq)
        assertEquals(67, pattern.size)   // même structure que NEC
        assertEquals(9000, pattern[0])
    }

    @Test
    fun ir_rca_structure() {
        val (freq, pattern) = IrProtocols.encode("RCA", 0x0F, 0xAB)!!
        assertEquals(56000, freq)
        assertEquals(51, pattern.size)   // header(2) + 24 bits * 2 + trailer(1)
        assertEquals(4000, pattern[0])
        assertEquals(4000, pattern[1])
        assertEquals(500, pattern[pattern.size - 1])
    }

    @Test
    fun ir_rc6_structure() {
        val (freq, pattern) = IrProtocols.encode("RC6", 0x00, 0x0C)!!
        assertEquals(36000, freq)
        assertEquals(2664, pattern[0])   // en-tête mark = 6T (T=444)
        assertTrue(pattern.size > 20)
    }

    @Test
    fun ir_rc5x_extends_command() {
        // RC5X doit encoder une commande 7 bits (0..127) sans planter.
        val (freq, pattern) = IrProtocols.encode("RC5X", 0x05, 0x50)!!
        assertEquals(36000, freq)
        assertTrue(pattern.isNotEmpty())
    }

    @Test
    fun ir_denon_sharp_15bits() {
        val (fd, denon) = IrProtocols.encode("DENON", 0x02, 0x40)!!
        assertEquals(38000, fd)
        assertEquals(31, denon.size)     // pas d'en-tête : 15 bits * 2 + trailer(1)
        assertEquals(264, denon[0])
        val (fs, sharp) = IrProtocols.encode("SHARP", 0x02, 0x40)!!
        assertEquals(38000, fs)
        assertEquals(31, sharp.size)
        assertEquals(320, sharp[0])
    }

    @Test
    fun ir_mitsubishi_structure() {
        val (freq, pattern) = IrProtocols.encode("MITSUBISHI", 0x23, 0xCB)!!
        assertEquals(33000, freq)
        assertEquals(33, pattern.size)   // 16 bits * 2 + trailer(1)
        assertEquals(300, pattern[0])
    }

    @Test
    fun ir_sanyo_lc7461_structure() {
        val (freq, pattern) = IrProtocols.encode("SANYO", 0x1AB2, 0x1C)!!
        assertEquals(38000, freq)
        assertEquals(87, pattern.size)   // header(2) + 42 bits * 2 + trailer(1)
        assertEquals(9000, pattern[0])
        assertEquals(4500, pattern[1])
    }

    @Test
    fun ir_rcmm_structure() {
        val (freq, pattern) = IrProtocols.encode("RCMM", 0x1234, 0x56)!!
        assertEquals(36000, freq)
        assertEquals(27, pattern.size)   // header(2) + 12 symboles * 2 + trailer(1)
        assertEquals(416, pattern[0])
        assertEquals(277, pattern[1])
    }

    // --- TV-B-Gone : rafales répétées (fiabilité réelle) ---
    @Test
    fun ir_burst_nec_uses_repeat_codes() {
        // La rafale NEC = trame complète (67) + 2 codes de répétition NEC (gap + 9000/2250/560).
        val (_, base) = IrProtocols.encode("NEC", 0x04, 0x08)!!
        val (freq, burst) = IrProtocols.encodePowerBurst("NEC", 0x04, 0x08)!!
        assertEquals(38000, freq)
        assertEquals(67 + 2 * (1 + 3), burst.size)   // 3 trames, 2 répétitions de 3 valeurs
        // Les premières valeurs sont exactement la trame de base.
        for (i in base.indices) assertEquals(base[i], burst[i])
        // Le code de répétition NEC apparaît après le premier espace inter-trame.
        assertEquals(9000, burst[68])
        assertEquals(2250, burst[69])
        assertEquals(560, burst[70])
    }

    @Test
    fun ir_burst_sony_repeats_full_frame_at_least_3x() {
        // Sony SIRC DOIT être émis >=3 fois ; la rafale répète la trame complète.
        val (_, base) = IrProtocols.encode("SIRC", 0x01, 0x15)!!
        val (freq, burst) = IrProtocols.encodePowerBurst("SIRC", 0x01, 0x15)!!
        assertEquals(40000, freq)
        assertEquals(base.size + 2 * (1 + base.size), burst.size)   // 3 trames complètes
        for (i in base.indices) assertEquals(base[i], burst[i])
    }

    @Test
    fun ir_burst_respects_presses_override() {
        val (_, base) = IrProtocols.encode("NEC", 0x04, 0x08)!!
        val (_, once) = IrProtocols.encodePowerBurst("NEC", 0x04, 0x08, presses = 1)!!
        assertEquals(base.size, once.size)   // une seule trame
        val (_, five) = IrProtocols.encodePowerBurst("NEC", 0x04, 0x08, presses = 5)!!
        assertEquals(base.size + 4 * (1 + 3), five.size)
    }

    @Test
    fun ir_burst_unknown_protocol_null() {
        assertEquals(null, IrProtocols.encodePowerBurst("BOGUS", 1, 2))
    }

    @Test
    fun ac_gree_frame_structure() {
        val (freq, pattern) = AcProtocols.gree(
            power = true, mode = AcProtocols.MODE_COOL, tempC = 22,
            fan = AcProtocols.FAN_AUTO, swing = false
        )
        assertEquals(38000, freq)
        // header(2) + 32b*2 + 3b*2 + sep(2) + 32b*2 + footer(2) = 140
        assertEquals(140, pattern.size)
        assertEquals(9000, pattern[0])
        assertEquals(4500, pattern[1])
    }

    @Test
    fun ac_gree_temp_is_clamped() {
        // Deux températures hors bornes doivent produire des trames valides
        val (_, hot) = AcProtocols.gree(true, AcProtocols.MODE_HEAT, 99, AcProtocols.FAN_HIGH, true)
        val (_, cold) = AcProtocols.gree(true, AcProtocols.MODE_COOL, 0, AcProtocols.FAN_LOW, false)
        assertEquals(140, hot.size)
        assertEquals(140, cold.size)
    }

    // --- AC : toutes les marques encodent une trame déterministe et non vide ---
    @Test
    fun ac_all_brands_encode() {
        for (brand in AcProtocols.Brand.values()) {
            val (freq, pattern) = AcProtocols.encode(
                brand, power = true, mode = AcProtocols.MODE_COOL,
                tempC = 24, fan = AcProtocols.FAN_AUTO, swing = false
            )
            assertEquals(38000, freq)
            assertTrue("trame vide pour $brand", pattern.size > 8)
            // Déterministe : mêmes entrées → même trame.
            val (_, again) = AcProtocols.encode(
                brand, true, AcProtocols.MODE_COOL, 24, AcProtocols.FAN_AUTO, false
            )
            assertArrayEquals(again, pattern)
        }
    }

    @Test
    fun ac_temp_changes_frame() {
        // Deux températures différentes doivent produire des trames différentes.
        for (brand in listOf(AcProtocols.Brand.LG, AcProtocols.Brand.FUJITSU, AcProtocols.Brand.DAIKIN)) {
            val (_, a) = AcProtocols.encode(brand, true, AcProtocols.MODE_COOL, 18, AcProtocols.FAN_AUTO, false)
            val (_, b) = AcProtocols.encode(brand, true, AcProtocols.MODE_COOL, 28, AcProtocols.FAN_AUTO, false)
            assertFalse("température ignorée pour $brand", a.contentEquals(b))
        }
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
        assertEquals(13, WifiNetwork.frequencyToChannel(2472))
        assertEquals(14, WifiNetwork.frequencyToChannel(2484)) // cas particulier Japon
        assertEquals(36, WifiNetwork.frequencyToChannel(5180))
        assertEquals(165, WifiNetwork.frequencyToChannel(5825))
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

    // --- ONVIF / IP cameras (noyau logique pur) ---
    @Test
    fun onvif_ws_discovery_probe_targets_cameras() {
        val probe = OnvifTools.wsDiscoveryProbe("uuid:1234")
        assertTrue(probe.contains("NetworkVideoTransmitter"))
        assertTrue(probe.contains("ws/2005/04/discovery/Probe"))
        assertTrue(probe.contains("uuid:1234"))
    }

    @Test
    fun onvif_parse_xaddrs() {
        val xml = """
            <e:Envelope><e:Body><d:ProbeMatches><d:ProbeMatch>
            <d:XAddrs>http://192.168.1.64/onvif/device_service http://[fe80::1]/onvif/device_service</d:XAddrs>
            </d:ProbeMatch></d:ProbeMatches></e:Body></e:Envelope>
        """.trimIndent()
        val urls = OnvifTools.parseXAddrs(xml)
        assertEquals(2, urls.size)
        assertEquals("http://192.168.1.64/onvif/device_service", urls[0])
    }

    @Test
    fun onvif_port_heuristic() {
        assertTrue(OnvifTools.classifyCameraPorts(listOf(80, 554, 8000)).isLikelyCamera)
        assertEquals("Dahua/XM (DVR)", OnvifTools.classifyCameraPorts(listOf(80, 37777)).vendorHint)
        assertFalse(OnvifTools.classifyCameraPorts(listOf(22, 443)).isLikelyCamera)
        assertFalse(OnvifTools.classifyCameraPorts(emptyList()).isLikelyCamera)
    }

    @Test
    fun onvif_rtsp_auth_parsing() {
        val resp = "RTSP/1.0 401 Unauthorized\r\nCSeq: 1\r\n" +
            "WWW-Authenticate: Digest realm=\"IP Camera\", nonce=\"abc\"\r\n\r\n"
        val a = OnvifTools.parseRtspAuth(resp)
        assertEquals(401, a.status)
        assertTrue(a.needsAuth)
        assertEquals("Digest", a.scheme)
        assertEquals("IP Camera", a.realm)
        // Flux ouvert
        val ok = OnvifTools.parseRtspAuth("RTSP/1.0 200 OK\r\nCSeq: 1\r\n\r\n")
        assertFalse(ok.needsAuth)
    }

    @Test
    fun onvif_base64_matches_jdk() {
        val data = "FlipperDroid".toByteArray()
        assertEquals(java.util.Base64.getEncoder().encodeToString(data), OnvifTools.base64(data))
        // Cas de padding
        assertEquals("TQ==", OnvifTools.base64("M".toByteArray()))
        assertEquals("TWE=", OnvifTools.base64("Ma".toByteArray()))
    }

    @Test
    fun onvif_password_digest_vector() {
        // Base64(SHA1(nonce + created + password)), vérifié contre un calcul JDK indépendant.
        val nonce = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)
        val created = "2010-09-16T07:50:45Z"
        val password = "userpassword"
        val md = java.security.MessageDigest.getInstance("SHA-1")
        md.update(nonce); md.update(created.toByteArray()); md.update(password.toByteArray())
        val expected = java.util.Base64.getEncoder().encodeToString(md.digest())
        assertEquals(expected, OnvifTools.passwordDigest(nonce, created, password))
    }

    @Test
    fun onvif_security_header_structure() {
        val header = OnvifTools.securityHeader("admin", "pass", byteArrayOf(1, 2, 3, 4), "2026-09-21T10:00:00Z")
        assertTrue(header.contains("<Username>admin</Username>"))
        assertTrue(header.contains("PasswordDigest"))
        assertTrue(header.contains("<Created"))
        assertTrue(header.contains("2026-09-21T10:00:00Z"))
    }

    @Test
    fun onvif_ptz_continuous_move_clamps_and_formats() {
        val body = OnvifTools.continuousMoveBody("Profile_1", 2.0f, -0.5f, 0.0f)
        assertTrue(body.contains("<ProfileToken>Profile_1</ProfileToken>"))
        assertTrue(body.contains("x=\"1.00\""))   // pan borné à 1.0
        assertTrue(body.contains("y=\"-0.50\""))  // tilt
        assertTrue(body.contains("ContinuousMove"))
    }

    @Test
    fun onvif_ptz_stop_body() {
        val body = OnvifTools.stopBody("Profile_1")
        assertTrue(body.contains("<Stop"))
        assertTrue(body.contains("<PanTilt>true</PanTilt>"))
        assertTrue(body.contains("<Zoom>true</Zoom>"))
    }

    @Test
    fun onvif_parse_device_information() {
        val xml = """
            <s:Body><tds:GetDeviceInformationResponse>
            <tds:Manufacturer>ACME</tds:Manufacturer><tds:Model>Cam-9000</tds:Model>
            <tds:FirmwareVersion>1.2.3</tds:FirmwareVersion><tds:SerialNumber>SN42</tds:SerialNumber>
            </tds:GetDeviceInformationResponse></s:Body>
        """.trimIndent()
        val info = OnvifTools.parseDeviceInformation(xml)
        assertEquals("ACME", info.manufacturer)
        assertEquals("Cam-9000", info.model)
        assertEquals("1.2.3", info.firmware)
    }

    @Test
    fun onvif_parse_profile_tokens() {
        val xml = """
            <trt:GetProfilesResponse>
            <trt:Profiles token="MainStream" fixed="true"></trt:Profiles>
            <trt:Profiles token="SubStream"></trt:Profiles>
            </trt:GetProfilesResponse>
        """.trimIndent()
        val tokens = OnvifTools.parseProfileTokens(xml)
        assertEquals(listOf("MainStream", "SubStream"), tokens)
    }

    @Test
    fun onvif_soap_fault_detected() {
        val fault = "<s:Body><s:Fault><s:Reason><s:Text>Sender not authorized</s:Text></s:Reason></s:Fault></s:Body>"
        assertEquals("Sender not authorized", OnvifTools.soapFaultReason(fault))
        assertEquals(null, OnvifTools.soapFaultReason("<s:Body><ok/></s:Body>"))
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
