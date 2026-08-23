package com.example.flipperdroid.model.`object`

import com.example.flipperdroid.model.`class`.AdvertisementSet
import com.example.flipperdroid.model.`class`.ServiceData
import com.example.flipperdroid.model.enums.AdvertisementSetType
import com.example.flipperdroid.model.enums.AdvertisementTarget
import com.example.flipperdroid.model.enums.AdvertisementSetRange
import com.example.flipperdroid.model.enums.TxPowerLevel
import com.example.flipperdroid.model.enums.AdvertiseMode
import java.util.UUID

/**
 * Génère des trames "Google Fast Pair" qui provoquent la fiche de couplage
 * (half-sheet) sur les appareils Android à proximité.
 *
 * Format : Service Data, UUID 16 bits 0xFE2C, payload = Model ID sur 3 octets.
 */
object FastPairAdvertisementSetGenerator {
    // UUID complet correspondant à l'UUID 16 bits 0xFE2C
    private val FAST_PAIR_UUID: UUID = UUID.fromString("0000FE2C-0000-1000-8000-00805F9B34FB")

    // Model IDs Fast Pair connus (3 octets) provoquant une popup
    private val modelIds = mapOf(
        "CD8256" to "Bose QC 35 II",
        "0000F0" to "Fast Pair Device",
        "92BBBD" to "Pixel Buds",
        "F52494" to "Sony WF-1000XM4",
        "718FA4" to "JBL Flip 6",
        "0002F0" to "Bose SoundLink",
        "01E5CE" to "LG HBS-835S",
        "02D815" to "Razer Hammerhead",
        "038CF1" to "JBL Live 300",
        "F00000" to "Fast Pair Test",
    )

    fun getAdvertisementSets(): List<AdvertisementSet> {
        val advertisementSets = mutableListOf<AdvertisementSet>()
        modelIds.forEach { (modelIdHex, name) ->
            val advertisementSet = AdvertisementSet()
            advertisementSet.target = AdvertisementTarget.ADVERTISEMENT_TARGET_GOOGLE
            advertisementSet.type = AdvertisementSetType.ADVERTISEMENT_TYPE_FAST_PAIR
            advertisementSet.range = AdvertisementSetRange.ADVERTISEMENTSET_RANGE_CLOSE
            advertisementSet.advertiseSettings.advertiseMode = AdvertiseMode.ADVERTISEMODE_LOW_LATENCY
            advertisementSet.advertiseSettings.txPowerLevel = TxPowerLevel.TX_POWER_HIGH
            advertisementSet.advertiseSettings.connectable = true
            advertisementSet.advertiseSettings.timeout = 0
            advertisementSet.advertisingSetParameters.legacyMode = true
            advertisementSet.advertisingSetParameters.txPowerLevel = TxPowerLevel.TX_POWER_HIGH
            advertisementSet.advertiseData.includeDeviceName = false
            advertisementSet.advertiseData.includeTxPower = false

            val serviceData = ServiceData()
            serviceData.serviceUuid = FAST_PAIR_UUID
            serviceData.serviceData = StringHelpers.decodeHex(modelIdHex)
            advertisementSet.advertiseData.services.add(serviceData)

            advertisementSet.title = "FastPair: $name"
            advertisementSets.add(advertisementSet)
        }
        return advertisementSets
    }
}
