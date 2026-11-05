package com.example.flipperdroid

import com.example.flipperdroid.camera.OnvifTools
import com.example.flipperdroid.iot.IotProtocolScanner
import com.example.flipperdroid.util.WifiChannels
import com.example.flipperdroid.util.csvField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests JVM de la logique réseau pure : canaux Wi-Fi, CSV, plages IP, ONVIF. */
class NetLogicTest {

    // --- Canaux Wi-Fi (2.4 / 5 / 6 GHz) ---

    @Test
    fun wifi_channel_2_4ghz() {
        assertEquals(1, WifiChannels.frequencyToChannel(2412))
        assertEquals(13, WifiChannels.frequencyToChannel(2472))
        assertEquals(14, WifiChannels.frequencyToChannel(2484))
    }

    @Test
    fun wifi_channel_5ghz() {
        assertEquals(36, WifiChannels.frequencyToChannel(5180))
        assertEquals(165, WifiChannels.frequencyToChannel(5825))
    }

    @Test
    fun wifi_channel_6ghz() {
        assertEquals(1, WifiChannels.frequencyToChannel(5955))
        assertEquals(233, WifiChannels.frequencyToChannel(7115))
    }

    @Test
    fun wifi_channel_unknown_frequency_is_zero() {
        assertEquals(0, WifiChannels.frequencyToChannel(1000))
        assertEquals(0, WifiChannels.frequencyToChannel(5950))
    }

    @Test
    fun wifi_band_labels() {
        assertEquals("2.4 GHz", WifiChannels.band(2412))
        assertEquals("2.4 GHz", WifiChannels.band(2484))
        assertEquals("5 GHz", WifiChannels.band(5180))
        assertEquals("6 GHz", WifiChannels.band(5955))
        assertEquals("Unknown", WifiChannels.band(1000))
    }

    // --- CSV Wigle ---

    @Test
    fun csv_field_plain_is_quoted() {
        assertEquals("\"HomeNet\"", csvField("HomeNet"))
        assertEquals("\"\"", csvField(""))
    }

    @Test
    fun csv_field_doubles_embedded_quotes() {
        assertEquals("\"Caf\"\"e\"", csvField("Caf\"e"))
    }

    @Test
    fun csv_field_keeps_commas_and_strips_newlines() {
        assertEquals("\"Net,Work\"", csvField("Net,Work"))
        assertEquals("\"AB\"", csvField("A\r\nB"))
    }

    // --- Plages IP (IoT) ---

    @Test
    fun ip_range_last_octet_dash() {
        val ips = IotProtocolScanner.parseIpRange("192.168.1.1-254")
        assertEquals(254, ips.size)
        assertEquals("192.168.1.1", ips.first())
        assertEquals("192.168.1.254", ips.last())
    }

    @Test
    fun ip_range_full_end_address() {
        assertEquals(
            listOf("10.0.0.5", "10.0.0.6", "10.0.0.7"),
            IotProtocolScanner.parseIpRange("10.0.0.5-10.0.0.7")
        )
    }

    @Test
    fun ip_range_cidr_24_skips_network_and_broadcast() {
        val ips = IotProtocolScanner.parseIpRange("192.168.1.0/24")
        assertEquals(254, ips.size)
        assertEquals("192.168.1.1", ips.first())
        assertEquals("192.168.1.254", ips.last())
    }

    @Test
    fun ip_range_cidr_16_is_capped() {
        val ips = IotProtocolScanner.parseIpRange("10.0.0.0/16")
        assertEquals(IotProtocolScanner.MAX_HOSTS, ips.size)
        assertEquals("10.0.0.1", ips.first())
    }

    @Test
    fun ip_range_cidr_32_single_host() {
        assertEquals(listOf("10.0.0.1"), IotProtocolScanner.parseIpRange("10.0.0.1/32"))
    }

    @Test
    fun ip_range_single_address() {
        assertEquals(listOf("192.168.0.9"), IotProtocolScanner.parseIpRange("192.168.0.9"))
    }

    @Test
    fun ip_range_invalid_input_is_empty() {
        assertTrue(IotProtocolScanner.parseIpRange("").isEmpty())
        assertTrue(IotProtocolScanner.parseIpRange("not an ip").isEmpty())
        assertTrue(IotProtocolScanner.parseIpRange("256.1.1.1").isEmpty())
        assertTrue(IotProtocolScanner.parseIpRange("192.168.1.1/8").isEmpty())
        assertTrue(IotProtocolScanner.parseIpRange("10.0.0.5-10.0.0.1").isEmpty())
        assertTrue(IotProtocolScanner.parseIpRange("192.168.1.1-300").isEmpty())
    }

    // --- ONVIF : WS-Discovery et fautes SOAP ---

    @Test
    fun onvif_probe_declares_network_namespace() {
        val probe = OnvifTools.wsDiscoveryProbe("uuid:1234")
        assertTrue(probe.contains("xmlns:dn=\"http://www.onvif.org/ver10/network/wsdl\""))
        assertTrue(probe.contains("dn:NetworkVideoTransmitter"))
    }

    @Test
    fun onvif_fault_with_xml_lang_attribute() {
        val fault = "<s:Envelope><s:Body><s:Fault><s:Reason>" +
            "<env:Text xml:lang=\"en\">Sender not authorized</env:Text>" +
            "</s:Reason></s:Fault></s:Body></s:Envelope>"
        assertEquals("Sender not authorized", OnvifTools.soapFaultReason(fault))
    }

    @Test
    fun onvif_no_false_positive_on_default_user_text() {
        val response = "<s:Envelope><s:Body><GetUsersResponse>" +
            "<Username>DefaultUser</Username></GetUsersResponse></s:Body></s:Envelope>"
        assertNull(OnvifTools.soapFaultReason(response))
    }
}
