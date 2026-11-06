package com.example.flipperdroid

import com.example.flipperdroid.model.`object`.AdvertisementPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM de l'estimation de taille de trame BLE et du choix legacy/étendu.
 *
 * Le point clé : une trame courte (≤ 31 octets) doit rester en LEGACY pour être
 * réellement reçue par les cibles (Windows Swift Pair, iOS Continuity, Android
 * Fast Pair n'écoutent que le legacy par défaut) ; seule une trame qui déborde
 * passe en advertising étendu.
 */
class BleLogicTest {

    @Test
    fun swiftpair_short_name_fits_in_legacy() {
        // Swift Pair : connectable, pas de nom système, données fabricant =
        // header 3 octets + nom ASCII. "Surface Mouse" = 13 octets.
        // flags(3) + [overhead(2) + companyId(2) + (3 + 13)] = 3 + 20 = 23 ≤ 31.
        val bytes = AdvertisementPayload.estimateLegacyBytes(
            connectable = true,
            includeDeviceName = false,
            deviceNameLength = 0,
            includeTxPower = false,
            manufacturerDataLengths = listOf(3 + "Surface Mouse".length),
            service16Count = 0,
            serviceData16Lengths = emptyList(),
        )
        assertEquals(23, bytes)
        assertFalse(AdvertisementPayload.overflowsLegacy(bytes))
    }

    @Test
    fun swiftpair_long_name_overflows_legacy() {
        // "Microsoft Bluetooth Mouse" = 25 octets :
        // flags(3) + [2 + 2 + (3 + 25)] = 3 + 32 = 35 > 31 → étendu.
        val bytes = AdvertisementPayload.estimateLegacyBytes(
            connectable = true,
            includeDeviceName = false,
            deviceNameLength = 0,
            includeTxPower = false,
            manufacturerDataLengths = listOf(3 + "Microsoft Bluetooth Mouse".length),
            service16Count = 0,
            serviceData16Lengths = emptyList(),
        )
        assertEquals(35, bytes)
        assertTrue(AdvertisementPayload.overflowsLegacy(bytes))
    }

    @Test
    fun continuity_nonconnectable_no_flags() {
        // Continuity : non connectable → pas de structure Flags.
        // manufacturer data Apple de 10 octets : 2 + 2 + 10 = 14 ≤ 31.
        val bytes = AdvertisementPayload.estimateLegacyBytes(
            connectable = false,
            includeDeviceName = false,
            deviceNameLength = 0,
            includeTxPower = false,
            manufacturerDataLengths = listOf(10),
            service16Count = 0,
            serviceData16Lengths = emptyList(),
        )
        assertEquals(14, bytes)
        assertFalse(AdvertisementPayload.overflowsLegacy(bytes))
    }

    @Test
    fun fastpair_service_data_fits_in_legacy() {
        // Fast Pair : service data 16 bits (UUID 0xFE2C) de 3 octets + UUID annoncé.
        // flags(3) + uuid16 list[2 + 2] + serviceData[2 + 2 + 3] = 3 + 4 + 7 = 14.
        val bytes = AdvertisementPayload.estimateLegacyBytes(
            connectable = true,
            includeDeviceName = false,
            deviceNameLength = 0,
            includeTxPower = false,
            manufacturerDataLengths = emptyList(),
            service16Count = 1,
            serviceData16Lengths = listOf(3),
        )
        assertEquals(14, bytes)
        assertFalse(AdvertisementPayload.overflowsLegacy(bytes))
    }

    @Test
    fun device_name_and_tx_power_count_toward_budget() {
        // includeDeviceName (nom de 8) + txPower + fabricant 20 :
        // 0 flags (non connectable) + [2 + 8] + 3 + [2 + 2 + 20] = 10 + 3 + 24 = 37 > 31.
        val bytes = AdvertisementPayload.estimateLegacyBytes(
            connectable = false,
            includeDeviceName = true,
            deviceNameLength = 8,
            includeTxPower = true,
            manufacturerDataLengths = listOf(20),
            service16Count = 0,
            serviceData16Lengths = emptyList(),
        )
        assertEquals(37, bytes)
        assertTrue(AdvertisementPayload.overflowsLegacy(bytes))
    }

    @Test
    fun boundary_exactly_31_stays_legacy() {
        assertFalse(AdvertisementPayload.overflowsLegacy(AdvertisementPayload.LEGACY_MAX_PAYLOAD_BYTES))
        assertTrue(AdvertisementPayload.overflowsLegacy(AdvertisementPayload.LEGACY_MAX_PAYLOAD_BYTES + 1))
    }
}
