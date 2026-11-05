package com.example.flipperdroid

import com.example.flipperdroid.network.PacketSniffer
import com.example.flipperdroid.util.ShellArgs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellArgsTest {

    @Test
    fun quote_neutralises_shell_metacharacters() {
        assertEquals("'abc'", ShellArgs.quote("abc"))
        assertEquals("'a'\\''b'", ShellArgs.quote("a'b"))
        assertEquals("'1.1.1.1; reboot'", ShellArgs.quote("1.1.1.1; reboot"))
        assertEquals("'(tcp or udp) and port 53'", ShellArgs.join(listOf("(tcp or udp) and port 53")))
    }

    @Test
    fun targets_are_validated() {
        assertTrue(ShellArgs.isValidTarget("192.168.1.10"))
        assertTrue(ShellArgs.isValidTarget("192.168.1.0/24"))
        assertTrue(ShellArgs.isValidTarget("scanme.nmap.org"))
        assertTrue(ShellArgs.isValidTarget("fe80::1"))
        assertFalse(ShellArgs.isValidTarget("1.1.1.1; reboot"))
        assertFalse(ShellArgs.isValidTarget("\$(id)"))
        assertFalse(ShellArgs.isValidTarget("256.1.1.1"))
        assertFalse(ShellArgs.isValidTarget(""))
    }

    @Test
    fun interfaces_and_macs_are_validated() {
        assertTrue(ShellArgs.isValidInterface("wlan0"))
        assertFalse(ShellArgs.isValidInterface("wlan0;id"))
        assertTrue(ShellArgs.isValidMac("02:11:22:33:44:55"))
        assertFalse(ShellArgs.isValidMac("02:11:22:33:44"))
    }

    @Test
    fun nmap_options_are_filtered() {
        assertEquals(listOf("-sV", "-T4", "-p", "1-1000"), ShellArgs.splitSafeOptions(" -sV  -T4 -p 1-1000 "))
        assertEquals(emptyList<String>(), ShellArgs.splitSafeOptions(""))
        assertNull(ShellArgs.splitSafeOptions("-sV; reboot"))
        assertNull(ShellArgs.splitSafeOptions("-oN /sdcard/x"))
        assertNull(ShellArgs.splitSafeOptions("-iL list.txt"))
        assertNull(ShellArgs.splitSafeOptions("--script vuln"))
        assertNull(ShellArgs.splitSafeOptions("-sV `id`"))
    }

    @Test
    fun tcpdump_header_lines_are_parsed_payload_lines_ignored() {
        val p = PacketSniffer.parsePacketLine(
            "12:00:00.123456 IP 10.0.0.5.443 > 10.0.0.9.51234: Flags [P.], seq 1:43, ack 1, win 501, length 42"
        )!!
        assertEquals("10.0.0.5.443", p.source)
        assertEquals("10.0.0.9.51234", p.destination)
        assertEquals("TCP", p.protocol)
        assertEquals(42, p.length)
        assertNull(PacketSniffer.parsePacketLine("E..(..@.@.....GET / HTTP/1.1"))
        assertNull(PacketSniffer.parsePacketLine(""))
    }
}
