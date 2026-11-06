package com.example.flipperdroid.model.`object`

/**
 * Estimation de la taille (en octets) d'une trame d'advertising BLE *legacy*
 * et choix du mode legacy / étendu.
 *
 * Le legacy plafonne la charge utile à 31 octets. Beaucoup de cibles
 * (Windows Swift Pair, iOS/macOS Continuity, Android Fast Pair) n'écoutent
 * QUE l'advertising legacy par défaut : envoyer une trame courte en étendu
 * la rend invisible (Windows exige `AllowExtendedAdvertisements`, false par
 * défaut). On garde donc le legacy tant que la charge tient dans 31 octets et
 * on ne bascule en étendu que lorsqu'elle déborde réellement.
 *
 * Objet pur (aucune dépendance `android.*`) pour être testable sur la JVM.
 */
object AdvertisementPayload {
    /** Charge utile maximale d'une trame d'advertising legacy, en octets. */
    const val LEGACY_MAX_PAYLOAD_BYTES = 31

    // Surcoût d'une structure AD : 1 octet de longueur + 1 octet de type.
    private const val AD_STRUCT_OVERHEAD = 2
    private const val FLAGS_BYTES = 3        // longueur(1) + type(1) + valeur(1)
    private const val TX_POWER_BYTES = 3     // longueur(1) + type(1) + valeur(1)
    private const val COMPANY_ID_BYTES = 2   // identifiant fabricant (little-endian)
    private const val UUID16_BYTES = 2       // UUID de service 16 bits

    /**
     * Estime le nombre d'octets qu'occuperait la trame en advertising legacy.
     *
     * @param connectable l'adv est connectable (le stack ajoute alors la structure Flags)
     * @param includeDeviceName le nom complet de l'appareil est inclus dans la trame
     * @param deviceNameLength longueur du nom (en octets) s'il est inclus
     * @param includeTxPower le niveau de puissance d'émission est inclus
     * @param manufacturerDataLengths longueur des données spécifiques fabricant
     *        (hors identifiant fabricant), une entrée par structure
     * @param service16Count nombre d'UUID de service 16 bits annoncés
     * @param serviceData16Lengths longueur des données de service 16 bits, une entrée par structure
     */
    fun estimateLegacyBytes(
        connectable: Boolean,
        includeDeviceName: Boolean,
        deviceNameLength: Int,
        includeTxPower: Boolean,
        manufacturerDataLengths: List<Int>,
        service16Count: Int,
        serviceData16Lengths: List<Int>,
    ): Int {
        var bytes = 0
        if (connectable) bytes += FLAGS_BYTES
        if (includeTxPower) bytes += TX_POWER_BYTES
        if (includeDeviceName && deviceNameLength > 0) bytes += AD_STRUCT_OVERHEAD + deviceNameLength
        for (len in manufacturerDataLengths) bytes += AD_STRUCT_OVERHEAD + COMPANY_ID_BYTES + len
        if (service16Count > 0) bytes += AD_STRUCT_OVERHEAD + UUID16_BYTES * service16Count
        for (len in serviceData16Lengths) bytes += AD_STRUCT_OVERHEAD + UUID16_BYTES + len
        return bytes
    }

    /** true si la trame déborde du legacy et doit passer en advertising étendu. */
    fun overflowsLegacy(estimatedBytes: Int): Boolean = estimatedBytes > LEGACY_MAX_PAYLOAD_BYTES
}
