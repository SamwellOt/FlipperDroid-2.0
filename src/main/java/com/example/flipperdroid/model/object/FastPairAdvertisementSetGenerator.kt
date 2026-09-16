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

    // Model IDs Fast Pair (3 octets) provoquant la fiche de couplage sur Android.
    // Le nom ci-dessous est une étiquette pour l'opérateur : le téléphone cible
    // affiche le nom réel enregistré chez Google pour ce Model ID (ou une fiche
    // générique si l'ID est inconnu de sa base). Chaque clé fait exactement
    // 6 caractères hexadécimaux (3 octets).
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
        // Modèles additionnels largement reproduits dans les jeux de données BLE-spam
        "2D7A23" to "Bose NC 700",
        "D446A7" to "Sony WF-1000XM3",
        "0E30C3" to "Sony WH-1000XM4",
        "98066F" to "Sony WF-1000XM5",
        "1D1F2B" to "JBL Buds Pro",
        "9C0645" to "JBL Tune 230",
        "E85D01" to "JBL Tune 130",
        "AA187E" to "JBL Live Pro 2",
        "D97EBA" to "Pixel Buds Pro",
        "0002B0" to "Pixel Buds A-Series",
        "D68CB5" to "OnePlus Buds Z2",
        "5B7B62" to "OnePlus Buds Pro",
        "C7B3F8" to "OPPO Enco X",
        "3B5A9B" to "realme Buds Air 3",
        "5BA9C0" to "Nothing Ear (1)",
        "A7C4F2" to "Nothing Ear (2)",
        "6A6362" to "Beats Studio Buds",
        "20B510" to "Beats Fit Pro",
        "27EA20" to "Anker Soundcore",
        "9B7CA8" to "Skullcandy Push",
        "8CD10F" to "Marshall Minor III",
        "149E8B" to "Sennheiser Momentum",
        "4A18B8" to "LG Tone Free",
        "90C8FA" to "Belkin SoundForm",
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
