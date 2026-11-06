package com.example.flipperdroid.util

/**
 * Conversions fréquence Wi-Fi (MHz) -> numéro de canal et libellé de bande.
 * Source unique utilisée par l'analyseur Wi-Fi et le wardriving (ex-copies privées).
 */
object WifiChannels {

    /** Canaux 2.4 GHz standard. */
    val STANDARD_2_4_GHZ: List<Int> = (1..14).toList()

    /** Canaux 5 GHz standard (largeur 20 MHz). */
    val STANDARD_5_GHZ: List<Int> =
        listOf(36, 40, 44, 48, 52, 56, 60, 64) + (100..144 step 4).toList() + (149..165 step 4).toList()

    /**
     * Canal correspondant à une fréquence en MHz, 0 si la fréquence n'est pas reconnue.
     * 2.4 GHz : 2412..2472 -> (f-2407)/5, canal 14 à 2484 ; 5 GHz : (f-5000)/5 ;
     * 6 GHz : (f-5950)/5 (canal 1 = 5955 MHz).
     */
    fun frequencyToChannel(freq: Int): Int = when (freq) {
        2484 -> 14
        in 2412..2472 -> (freq - 2407) / 5
        in 5160..5885 -> (freq - 5000) / 5
        in 5955..7115 -> (freq - 5950) / 5
        else -> 0
    }

    /** Libellé de bande : "2.4 GHz", "5 GHz", "6 GHz" ou "Unknown". */
    fun band(freq: Int): String = when (freq) {
        in 2400..2500 -> "2.4 GHz"
        in 4900..5895 -> "5 GHz"
        in 5955..7115 -> "6 GHz"
        else -> "Unknown"
    }
}
