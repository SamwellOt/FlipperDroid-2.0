package com.example.flipperdroid.model.`interface`

import com.example.flipperdroid.model.`class`.AdvertisementSet
import com.example.flipperdroid.model.enums.TxPowerLevel

interface IAdvertisementService {
    fun startAdvertisement(advertisementSet: AdvertisementSet)
    fun stopAdvertisement()

    /**
     * Met à jour les DONNÉES de l'annonce en cours sans ré-enregistrer un advertising
     * set (évite l'accumulation d'advertisers -> TOO_MANY_ADVERTISERS lors du spam).
     * @return true si la mise à jour a été appliquée ; false s'il n'y a pas d'annonce
     *   en cours (l'appelant doit alors faire un start classique).
     */
    fun updateAdvertisement(advertisementSet: AdvertisementSet): Boolean = false
    fun setTxPowerLevel(txPowerLevel: TxPowerLevel)
    fun getTxPowerLevel(): TxPowerLevel
    fun addAdvertisementServiceCallback(callback: IAdvertisementServiceCallback)
    fun removeAdvertisementServiceCallback(callback: IAdvertisementServiceCallback)
    fun isLegacyService(): Boolean
} 