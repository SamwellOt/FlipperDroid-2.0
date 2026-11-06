package com.example.flipperdroid.util

/**
 * Classification du type de sécurité d'un réseau Wi-Fi à partir de la chaîne
 * `ScanResult.capabilities`, p. ex. `[WPA2-PSK-CCMP][RSN-SAE-CCMP][WPS][ESS]`.
 *
 * L'ancienne détection ne reconnaissait WPA3 qu'avec le littéral "WPA3" : un
 * réseau WPA3 pur (`[RSN-SAE-CCMP]`) était classé OPEN, et l'« Enhanced Open »
 * (OWE) passait pour un réseau ouvert non chiffré. Ce classifieur pur
 * (aucune dépendance `android.*`, donc testable sur la JVM) reconnaît SAE,
 * OWE, la transition WPA2/WPA3, l'authentification entreprise (EAP) et WPS.
 */
enum class WifiSecurity {
    OPEN,               // ouvert, non chiffré
    OWE,                // Enhanced Open (OWE) : ouvert mais chiffré
    WEP,
    WPA,
    WPA2,
    WPA3,
    WPA3_TRANSITION,    // WPA2-PSK + WPA3-SAE sur le même BSS
}

object WifiSecurityClassifier {

    /** Déduit le type de sécurité dominant de la chaîne de capacités. */
    fun classify(capabilities: String): WifiSecurity {
        val caps = capabilities.uppercase()
        val hasSae = caps.contains("SAE")
        // "PSK" couvre WPA2-PSK/WPA-PSK ; on repère aussi les étiquettes WPA/WPA2.
        val hasPsk = caps.contains("PSK") || caps.contains("WPA2") || caps.contains("WPA-")
        return when {
            caps.contains("WEP") -> WifiSecurity.WEP
            hasSae || caps.contains("WPA3") ->
                if (hasSae && hasPsk) WifiSecurity.WPA3_TRANSITION else WifiSecurity.WPA3
            caps.contains("OWE") -> WifiSecurity.OWE
            caps.contains("WPA2") || caps.contains("RSN") -> WifiSecurity.WPA2
            caps.contains("WPA") -> WifiSecurity.WPA
            else -> WifiSecurity.OPEN
        }
    }

    /** true si WPS est annoncé (surface d'attaque supplémentaire : PIN/PBC). */
    fun hasWps(capabilities: String): Boolean = capabilities.uppercase().contains("WPS")

    /** true si le réseau utilise une authentification 802.1X / entreprise (EAP). */
    fun isEnterprise(capabilities: String): Boolean =
        capabilities.uppercase().let { it.contains("EAP") || it.contains("IEEE8021X") }

    /** Libellé lisible, enrichi des drapeaux entreprise et WPS, pour l'UI. */
    fun label(capabilities: String): String {
        val base = when (classify(capabilities)) {
            WifiSecurity.OPEN -> "Open"
            WifiSecurity.OWE -> "Enhanced Open (OWE)"
            WifiSecurity.WEP -> "WEP"
            WifiSecurity.WPA -> "WPA"
            WifiSecurity.WPA2 -> "WPA2"
            WifiSecurity.WPA3 -> "WPA3"
            WifiSecurity.WPA3_TRANSITION -> "WPA2/WPA3"
        }
        val suffix = buildString {
            if (isEnterprise(capabilities)) append(" · Enterprise")
            if (hasWps(capabilities)) append(" · WPS")
        }
        return base + suffix
    }
}
