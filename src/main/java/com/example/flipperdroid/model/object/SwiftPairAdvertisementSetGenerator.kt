package com.example.flipperdroid.model.`object`

import com.example.flipperdroid.model.`class`.AdvertisementSet
import com.example.flipperdroid.model.`class`.ManufacturerSpecificData
import com.example.flipperdroid.model.enums.AdvertisementSetType
import com.example.flipperdroid.model.enums.AdvertisementTarget
import com.example.flipperdroid.model.enums.AdvertisementSetRange
import com.example.flipperdroid.model.enums.TxPowerLevel
import com.example.flipperdroid.model.enums.AdvertiseMode

/**
 * Génère des trames "Microsoft Swift Pair" qui provoquent une popup de
 * couplage Bluetooth sur Windows 10/11.
 *
 * Format : Manufacturer Specific Data, company ID 0x0006 (Microsoft),
 * payload = [0x03, 0x00, 0x80] + nom d'appareil (ASCII).
 */
object SwiftPairAdvertisementSetGenerator {
    private const val manufacturerId = 0x0006 // Microsoft

    // Le nom est affiché tel quel dans la popup Swift Pair de Windows : ASCII
    // arbitraire, donc chaque entrée fonctionne sans base de données côté cible.
    private val deviceNames = listOf(
        "Surface Keyboard",
        "Surface Mouse",
        "Surface Pen",
        "Xbox Wireless Controller",
        "Surface Headphones",
        "Surface Earbuds",
        "Surface Dock",
        "Microsoft Arc Mouse",
        "Surface Precision Mouse",
        "Surface Ergonomic Keyboard",
        "Xbox Elite Controller",
        "Xbox Adaptive Controller",
        "Microsoft Sculpt Keyboard",
        "Microsoft Modern Keyboard",
        "Microsoft Bluetooth Mouse",
        "Surface Slim Pen 2",
        "Logitech MX Master 3",
        "Logitech MX Keys",
        "Logitech Pebble",
        "Bose QC Ultra",
        "Sony WH-1000XM5",
        "JBL Live 660NC",
        "AirPods Pro",
        "Galaxy Buds2 Pro",
        "DJI Mic",
        "Razer BlackShark V2",
        "Keychron K8",
        "Sennheiser Momentum 4",
    )

    fun getAdvertisementSets(): List<AdvertisementSet> {
        val advertisementSets = mutableListOf<AdvertisementSet>()
        deviceNames.forEach { name ->
            val advertisementSet = AdvertisementSet()
            advertisementSet.target = AdvertisementTarget.ADVERTISEMENT_TARGET_MICROSOFT
            advertisementSet.type = AdvertisementSetType.ADVERTISEMENT_TYPE_SWIFT_PAIR
            advertisementSet.range = AdvertisementSetRange.ADVERTISEMENTSET_RANGE_CLOSE
            advertisementSet.advertiseSettings.advertiseMode = AdvertiseMode.ADVERTISEMODE_LOW_LATENCY
            advertisementSet.advertiseSettings.txPowerLevel = TxPowerLevel.TX_POWER_HIGH
            advertisementSet.advertiseSettings.connectable = true
            advertisementSet.advertiseSettings.timeout = 0
            advertisementSet.advertisingSetParameters.legacyMode = true
            advertisementSet.advertisingSetParameters.txPowerLevel = TxPowerLevel.TX_POWER_HIGH
            advertisementSet.advertiseData.includeDeviceName = false
            advertisementSet.advertiseData.includeTxPower = false

            val manufacturerSpecificData = ManufacturerSpecificData()
            manufacturerSpecificData.manufacturerId = manufacturerId
            // 0x03 = Microsoft Beacon ID, 0x00 = sous-scénario, 0x80 = réservé + Swift Pair
            val header = byteArrayOf(0x03, 0x00, 0x80.toByte())
            manufacturerSpecificData.manufacturerSpecificData = header + name.toByteArray(Charsets.US_ASCII)
            advertisementSet.advertiseData.manufacturerData.add(manufacturerSpecificData)

            advertisementSet.title = "SwiftPair: $name"
            advertisementSets.add(advertisementSet)
        }
        return advertisementSets
    }
}
