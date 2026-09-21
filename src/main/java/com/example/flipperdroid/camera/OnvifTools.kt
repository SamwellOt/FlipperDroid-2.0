package com.example.flipperdroid.camera

import java.security.MessageDigest

/**
 * Noyau logique pur (sans dépendance Android) pour la découverte, l'audit et le
 * pilotage PTZ de caméras IP via ONVIF / RTSP.
 *
 * Tout ce qui est ici est déterministe et testable en JVM (voir LogicUnitTest) :
 * construction des enveloppes SOAP, du jeton de sécurité WS-UsernameToken, des
 * requêtes RTSP, et l'analyse des réponses. Les entrées/sorties réseau vivent
 * dans IpCameraViewModel.
 *
 * Principe du projet : rester honnête sur la capacité. Ces outils ne servent
 * qu'à des tests autorisés sur du matériel dont on a la permission.
 */
object OnvifTools {

    // --- Espaces de noms ONVIF ---
    private const val NS_SOAP = "http://www.w3.org/2003/05/soap-envelope"
    private const val NS_WSA = "http://schemas.xmlsoap.org/ws/2004/08/addressing"
    private const val NS_WSD = "http://schemas.xmlsoap.org/ws/2005/04/discovery"
    private const val NS_DEVICE = "http://www.onvif.org/ver10/device/wsdl"
    private const val NS_MEDIA = "http://www.onvif.org/ver10/media/wsdl"
    private const val NS_PTZ = "http://www.onvif.org/ver20/ptz/wsdl"
    private const val NS_SCHEMA = "http://www.onvif.org/ver10/schema"
    private const val NS_WSSE =
        "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"
    private const val NS_WSU =
        "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd"
    private const val TYPE_PASSWORD_DIGEST =
        "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest"
    private const val ENC_BASE64 =
        "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary"

    /** Ports TCP typiques des caméras IP / NVR, utilisés par l'heuristique et le scan. */
    val CAMERA_PORTS = listOf(80, 443, 554, 8000, 8080, 8443, 8554, 2020, 34567, 37777, 37778, 9000)

    /** Multicast WS-Discovery ONVIF. */
    const val WS_DISCOVERY_ADDR = "239.255.255.250"
    const val WS_DISCOVERY_PORT = 3702

    // ---------------------------------------------------------------------
    // Découverte : WS-Discovery
    // ---------------------------------------------------------------------

    /**
     * Construit le message SOAP Probe WS-Discovery qui cherche les
     * NetworkVideoTransmitter (caméras ONVIF) sur le réseau local.
     *
     * @param messageId identifiant unique (ex: "uuid:...") — passé en paramètre
     *   pour rester déterministe/testable ; le ViewModel fournit un UUID aléatoire.
     */
    fun wsDiscoveryProbe(messageId: String): String =
        """<?xml version="1.0" encoding="UTF-8"?>
<e:Envelope xmlns:e="$NS_SOAP" xmlns:w="$NS_WSA" xmlns:d="$NS_WSD" xmlns:dn="$NS_DEVICE">
<e:Header>
<w:MessageID>$messageId</w:MessageID>
<w:To e:mustUnderstand="true">urn:schemas-xmlsoap-org:ws:2005:04:discovery</w:To>
<w:Action e:mustUnderstand="true">http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</w:Action>
</e:Header>
<e:Body>
<d:Probe><d:Types>dn:NetworkVideoTransmitter</d:Types></d:Probe>
</e:Body>
</e:Envelope>"""

    /**
     * Extrait les URLs de service (XAddrs) d'une réponse ProbeMatch WS-Discovery.
     * Indépendant du préfixe d'espace de noms (d:XAddrs, wsd:XAddrs, …).
     */
    fun parseXAddrs(xml: String): List<String> {
        val out = LinkedHashSet<String>()
        val regex = Regex("<(?:[A-Za-z0-9]+:)?XAddrs>(.*?)</(?:[A-Za-z0-9]+:)?XAddrs>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        for (m in regex.findAll(xml)) {
            m.groupValues[1].trim().split(Regex("\\s+")).forEach { url ->
                if (url.startsWith("http", ignoreCase = true)) out.add(url.trim())
            }
        }
        return out.toList()
    }

    // ---------------------------------------------------------------------
    // Reconnaissance : heuristique de classification par ports
    // ---------------------------------------------------------------------

    /** Résultat de l'heuristique : la cible est-elle probablement une caméra ? */
    data class CameraGuess(val isLikelyCamera: Boolean, val vendorHint: String, val confidence: Int)

    /**
     * Devine si un hôte est une caméra/NVR à partir de ses ports ouverts, et
     * propose une piste constructeur. Purement indicatif (jamais une preuve).
     */
    fun classifyCameraPorts(openPorts: Collection<Int>): CameraGuess {
        val ports = openPorts.toSet()
        var score = 0
        var hint = "Unknown"
        if (554 in ports || 8554 in ports) score += 2          // RTSP = forte présomption
        if (37777 in ports || 37778 in ports || 34567 in ports) { score += 2; hint = "Dahua/XM (DVR)" }
        if (8000 in ports && (80 in ports || 554 in ports)) { score += 1; if (hint == "Unknown") hint = "Hikvision" }
        if (2020 in ports) { score += 1; if (hint == "Unknown") hint = "Amcrest/Dahua" }
        if (80 in ports || 8080 in ports || 443 in ports || 8443 in ports) score += 1 // interface web
        val likely = score >= 2
        val confidence = (score * 25).coerceAtMost(100)
        return CameraGuess(likely, hint, confidence)
    }

    // ---------------------------------------------------------------------
    // RTSP
    // ---------------------------------------------------------------------

    /** Construit une requête RTSP OPTIONS (sonde d'ouverture / d'authentification). */
    fun rtspOptions(url: String, cseq: Int = 1): String =
        "OPTIONS $url RTSP/1.0\r\nCSeq: $cseq\r\nUser-Agent: FlipperDroid\r\n\r\n"

    /** Info d'authentification déduite d'une réponse RTSP. */
    data class RtspAuth(val status: Int, val needsAuth: Boolean, val scheme: String, val realm: String)

    /** Analyse une réponse RTSP : code de statut, exigence d'auth, schéma et realm. */
    fun parseRtspAuth(response: String): RtspAuth {
        val status = Regex("RTSP/1\\.0\\s+(\\d{3})").find(response)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val authLine = response.lineSequence()
            .firstOrNull { it.startsWith("WWW-Authenticate:", ignoreCase = true) }
        val scheme = when {
            authLine == null -> ""
            authLine.contains("Digest", true) -> "Digest"
            authLine.contains("Basic", true) -> "Basic"
            else -> "Unknown"
        }
        val realm = authLine?.let { Regex("realm=\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) } ?: ""
        return RtspAuth(status, status == 401 || scheme.isNotEmpty(), scheme, realm)
    }

    // ---------------------------------------------------------------------
    // WS-Security UsernameToken (authentification ONVIF standard)
    // ---------------------------------------------------------------------

    /**
     * Calcule le PasswordDigest WS-Security :
     *   Base64( SHA1( nonce_bytes + created_bytes + password_bytes ) ).
     *
     * @param nonce octets bruts du nonce (le même que celui encodé en Base64 dans le jeton)
     * @param created horodatage ISO-8601 UTC (ex: "2026-09-21T10:00:00Z")
     */
    fun passwordDigest(nonce: ByteArray, created: String, password: String): String {
        val sha1 = MessageDigest.getInstance("SHA-1")
        sha1.update(nonce)
        sha1.update(created.toByteArray(Charsets.UTF_8))
        sha1.update(password.toByteArray(Charsets.UTF_8))
        return base64(sha1.digest())
    }

    /**
     * Construit l'en-tête SOAP <Security> WS-UsernameToken (mot de passe haché).
     * @param nonce octets bruts du nonce (aléatoire, fourni par l'appelant)
     */
    fun securityHeader(username: String, password: String, nonce: ByteArray, created: String): String {
        val digest = passwordDigest(nonce, created, password)
        val nonceB64 = base64(nonce)
        return """<s:Header><Security s:mustUnderstand="1" xmlns="$NS_WSSE">""" +
            "<UsernameToken><Username>${escapeXml(username)}</Username>" +
            """<Password Type="$TYPE_PASSWORD_DIGEST">$digest</Password>""" +
            """<Nonce EncodingType="$ENC_BASE64">$nonceB64</Nonce>""" +
            """<Created xmlns="$NS_WSU">$created</Created>""" +
            "</UsernameToken></Security></s:Header>"
    }

    /** Enveloppe un corps SOAP, avec en-tête de sécurité optionnel. */
    fun soapEnvelope(body: String, header: String = ""): String =
        """<?xml version="1.0" encoding="UTF-8"?>""" +
            """<s:Envelope xmlns:s="$NS_SOAP">$header<s:Body>$body</s:Body></s:Envelope>"""

    // ---------------------------------------------------------------------
    // Corps SOAP ONVIF (device / media / ptz)
    // ---------------------------------------------------------------------

    /** Action HTTP SOAPAction pour GetDeviceInformation. */
    const val ACTION_DEVICE_INFO = "$NS_DEVICE/GetDeviceInformation"
    const val ACTION_GET_PROFILES = "$NS_MEDIA/GetProfiles"
    const val ACTION_CONTINUOUS_MOVE = "$NS_PTZ/ContinuousMove"
    const val ACTION_STOP = "$NS_PTZ/Stop"

    fun getDeviceInformationBody(): String =
        """<GetDeviceInformation xmlns="$NS_DEVICE"/>"""

    fun getProfilesBody(): String =
        """<GetProfiles xmlns="$NS_MEDIA"/>"""

    /**
     * Corps PTZ ContinuousMove : déplacement continu jusqu'au Stop.
     * pan/tilt/zoom sont bornés à [-1.0, 1.0] (vitesses normalisées ONVIF).
     */
    fun continuousMoveBody(profileToken: String, pan: Float, tilt: Float, zoom: Float): String {
        val p = clampVel(pan); val t = clampVel(tilt); val z = clampVel(zoom)
        return """<ContinuousMove xmlns="$NS_PTZ">""" +
            "<ProfileToken>${escapeXml(profileToken)}</ProfileToken>" +
            """<Velocity><PanTilt x="$p" y="$t" xmlns="$NS_SCHEMA"/>""" +
            """<Zoom x="$z" xmlns="$NS_SCHEMA"/></Velocity></ContinuousMove>"""
    }

    /** Corps PTZ Stop : arrête pan/tilt et zoom. */
    fun stopBody(profileToken: String): String =
        """<Stop xmlns="$NS_PTZ"><ProfileToken>${escapeXml(profileToken)}</ProfileToken>""" +
            "<PanTilt>true</PanTilt><Zoom>true</Zoom></Stop>"

    // ---------------------------------------------------------------------
    // Analyse des réponses ONVIF
    // ---------------------------------------------------------------------

    /** Infos matériel extraites d'une réponse GetDeviceInformation. */
    data class DeviceInfo(val manufacturer: String, val model: String, val firmware: String, val serial: String)

    fun parseDeviceInformation(xml: String): DeviceInfo = DeviceInfo(
        manufacturer = tagText(xml, "Manufacturer"),
        model = tagText(xml, "Model"),
        firmware = tagText(xml, "FirmwareVersion"),
        serial = tagText(xml, "SerialNumber")
    )

    /**
     * Extrait les tokens de profils média (attribut token="…") d'une réponse
     * GetProfiles. Un token de profil est requis pour piloter le PTZ.
     */
    fun parseProfileTokens(xml: String): List<String> {
        val out = LinkedHashSet<String>()
        Regex("<(?:[A-Za-z0-9]+:)?Profiles[^>]*\\btoken=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
            .findAll(xml).forEach { out.add(it.groupValues[1]) }
        return out.toList()
    }

    /** Détecte un SOAP Fault (mauvais identifiants, action non supportée…). */
    fun soapFaultReason(xml: String): String? {
        if (!xml.contains("Fault", ignoreCase = true)) return null
        return tagText(xml, "Text").ifBlank { tagText(xml, "faultstring") }
            .ifBlank { "SOAP Fault" }
    }

    // ---------------------------------------------------------------------
    // Utilitaires
    // ---------------------------------------------------------------------

    private fun clampVel(v: Float): String {
        val c = v.coerceIn(-1.0f, 1.0f)
        // Format stable, insensible à la locale (pas de virgule décimale).
        return String.format(java.util.Locale.US, "%.2f", c)
    }

    /** Contenu textuel d'une balise, insensible au préfixe d'espace de noms. */
    private fun tagText(xml: String, local: String): String =
        Regex("<(?:[A-Za-z0-9]+:)?$local>(.*?)</(?:[A-Za-z0-9]+:)?$local>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(xml)?.groupValues?.get(1)?.trim() ?: ""

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    private const val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    /**
     * Encodeur Base64 standard (avec padding) en Kotlin pur : évite
     * android.util.Base64 (non mocké en tests JVM) et java.util.Base64 (API 26+,
     * alors que minSdk = 24).
     */
    fun base64(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val sb = StringBuilder((data.size + 2) / 3 * 4)
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xFF
            val b1 = if (i + 1 < data.size) data[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < data.size) data[i + 2].toInt() and 0xFF else 0
            sb.append(B64[b0 shr 2])
            sb.append(B64[(b0 and 0x03 shl 4) or (b1 shr 4)])
            sb.append(if (i + 1 < data.size) B64[(b1 and 0x0F shl 2) or (b2 shr 6)] else '=')
            sb.append(if (i + 2 < data.size) B64[b2 and 0x3F] else '=')
            i += 3
        }
        return sb.toString()
    }
}

/** Une caméra/NVR découverte ou saisie manuellement. */
data class IpCamera(
    val host: String,
    val openPorts: List<Int> = emptyList(),
    val vendorHint: String = "",
    val confidence: Int = 0,
    val xaddr: String = "",              // URL du service ONVIF device (WS-Discovery)
    val rtsp: String = "",               // état de la sonde RTSP
    val deviceInfo: String = "",         // résultat GetDeviceInformation / audit
    val foundCredentials: String = "",   // identifiants par défaut validés (audit)
    val profileTokens: List<String> = emptyList()
)

/** Un couple d'identifiants par défaut couramment livré sur les caméras IP. */
data class CameraCredential(val username: String, val password: String, val vendor: String)

/**
 * Petit dictionnaire d'identifiants par défaut de caméras IP, pour l'audit
 * (même logique que le dictionnaire de clés Mifare). Test autorisé uniquement.
 */
val DEFAULT_CAMERA_CREDENTIALS = listOf(
    CameraCredential("admin", "admin", "Générique"),
    CameraCredential("admin", "12345", "Hikvision (ancien)"),
    CameraCredential("admin", "", "Générique (vide)"),
    CameraCredential("admin", "password", "Générique"),
    CameraCredential("admin", "123456", "Générique"),
    CameraCredential("admin", "888888", "Dahua"),
    CameraCredential("admin", "9999", "Dahua"),
    CameraCredential("root", "root", "Axis/Générique"),
    CameraCredential("root", "pass", "Vivotek"),
    CameraCredential("root", "12345", "Générique"),
    CameraCredential("user", "user", "Générique"),
    CameraCredential("supervisor", "supervisor", "Générique"),
    CameraCredential("admin", "meinsm", "Panasonic"),
    CameraCredential("admin", "4321", "Ubiquiti/Générique"),
    CameraCredential("service", "service", "Bosch")
)
