package com.example.flipperdroid.iot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.random.Random

data class IotDevice(
    val address: String,
    val port: Int,
    val protocol: String,
    val service: String,
    val version: String = "unknown",
    val timestamp: Long = System.currentTimeMillis()
)

object IotProtocolScanner {

    /** Nombre maximal d'hôtes retournés pour une plage (évite les scans démesurés). */
    const val MAX_HOSTS = 1024

    private const val MQTT_PORT = 1883
    private const val COAP_PORT = 5683
    /** Code CoAP 2.05 Content (classe 2, détail 5). */
    private const val COAP_CODE_CONTENT = 0x45
    private const val MAX_TOPICS = 200
    private const val MAX_MQTT_PACKET = 65536

    /** Paquet MQTT CONNECT minimal : protocole 3.1.1, clean session, client id vide. */
    private val MQTT_CONNECT = byteArrayOf(
        0x10.toByte(), 0x0c.toByte(),
        0x00.toByte(), 0x04.toByte(),
        0x4d.toByte(), 0x51.toByte(), 0x54.toByte(), 0x54.toByte(),
        0x04.toByte(), 0x02.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte()
    )

    suspend fun scanMqtt(targetRange: String): List<IotDevice> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<IotDevice>()
        val ips = parseIpRange(targetRange)

        ips.forEach { ip ->
            if (isMqttOpen(ip)) {
                // Version non rapportée : le CONNACK ne l'indique pas.
                devices.add(IotDevice(ip, MQTT_PORT, "MQTT", "Broker"))
            }
            delay(100)
        }

        return@withContext devices
    }

    suspend fun scanCoap(targetRange: String): List<IotDevice> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<IotDevice>()
        val ips = parseIpRange(targetRange)

        ips.forEach { ip ->
            if (isCoapOpen(ip)) {
                devices.add(IotDevice(ip, COAP_PORT, "CoAP", "Resource Server"))
            }
            delay(100)
        }

        return@withContext devices
    }

    suspend fun scanCommon(targetRange: String): List<IotDevice> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<IotDevice>()
        val ips = parseIpRange(targetRange)
        val ports = listOf(
            1883 to "MQTT",
            5683 to "CoAP",
            8080 to "HTTP",
            8883 to "MQTTS",
            61616 to "ActiveMQ",
            5672 to "AMQP",
            27017 to "MongoDB",
            6379 to "Redis",
            9200 to "Elasticsearch"
        )

        ips.forEach { ip ->
            ports.forEach { (port, service) ->
                if (isPortOpen(ip, port)) {
                    devices.add(IotDevice(ip, port, "TCP", service, ""))
                }
                delay(50)
            }
        }

        return@withContext devices
    }

    private fun isPortOpen(ip: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.soTimeout = 1000
                socket.connect(InetSocketAddress(ip, port), 1000)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun isMqttOpen(ip: String): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, MQTT_PORT), 2000)
                socket.soTimeout = 2000

                // Un broker MQTT reste muet tant que le client n'a pas envoyé CONNECT.
                val output = socket.getOutputStream()
                output.write(MQTT_CONNECT)
                output.flush()

                // Lecture du CONNACK (type 0x20)
                val input = socket.getInputStream()
                val buffer = ByteArray(4)
                val read = input.read(buffer)

                read > 0 && buffer[0] == 0x20.toByte()
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Envoie un GET CoAP confirmable vers ip:5683 et retourne le code de réponse
     * (ex. 0x45 = 2.05 Content), ou -1 si pas de réponse CoAP valide.
     */
    private fun coapGetCode(ip: String, segments: List<String>): Int {
        return try {
            val request = buildCoapGet(Random.nextInt(0, 0x10000), segments)
            DatagramSocket().use { socket ->
                socket.soTimeout = 2000
                val address = InetAddress.getByName(ip)
                socket.send(DatagramPacket(request, request.size, address, COAP_PORT))

                val buffer = ByteArray(256)
                val reply = DatagramPacket(buffer, buffer.size)
                socket.receive(reply)
                // Version CoAP = 1 dans les deux bits de poids fort de l'octet 0.
                if (reply.length >= 4 && ((buffer[0].toInt() and 0xFF) shr 6) == 1) {
                    buffer[1].toInt() and 0xFF
                } else {
                    -1
                }
            }
        } catch (e: Exception) {
            -1
        }
    }

    /** Sonde CoAP : seule une réponse 2.05 Content sur /.well-known/core compte comme trouvée. */
    private fun isCoapOpen(ip: String): Boolean =
        coapGetCode(ip, listOf(".well-known", "core")) == COAP_CODE_CONTENT

    /**
     * Écoute les messages PUBLISH du broker pendant 2 s après un SUBSCRIBE "#"
     * (les messages retenus sont livrés dès l'abonnement). Lève une exception si
     * la connexion échoue ou si le broker refuse (CONNACK != 0, ex. authentification).
     */
    suspend fun scanMqttTopics(brokerIp: String): List<String> = withContext(Dispatchers.IO) {
        val topics = LinkedHashSet<String>()
        Socket().use { socket ->
            socket.connect(InetSocketAddress(brokerIp, MQTT_PORT), 2000)
            socket.soTimeout = 2000
            val output = socket.getOutputStream()
            val input = DataInputStream(socket.getInputStream())

            output.write(MQTT_CONNECT)
            output.flush()

            // CONNACK : type 0x20, longueur 2, [session present, code retour]
            val connack = readMqttPacket(input)
            if ((connack.first and 0xF0) != 0x20 || connack.second.size < 2) {
                throw IOException("réponse CONNACK invalide")
            }
            val returnCode = connack.second[1].toInt() and 0xFF
            if (returnCode != 0) {
                throw IOException("CONNACK refusé (code $returnCode), authentification requise ?")
            }

            output.write(buildMqttSubscribe("#"))
            output.flush()

            val deadline = System.currentTimeMillis() + 2000
            while (topics.size < MAX_TOPICS) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) break
                socket.soTimeout = remaining.toInt()
                val (header, body) = try {
                    readMqttPacket(input)
                } catch (_: IOException) {
                    break // délai écoulé, fin de flux ou paquet invalide : on garde ce qui a été vu
                }
                // PUBLISH = type 3 ; topic préfixé par sa longueur sur 2 octets.
                if ((header shr 4) == 3 && body.size >= 2) {
                    val topicLen = ((body[0].toInt() and 0xFF) shl 8) or (body[1].toInt() and 0xFF)
                    if (topicLen > 0 && 2 + topicLen <= body.size) {
                        topics.add(String(body, 2, topicLen, Charsets.UTF_8))
                    }
                }
            }
        }
        topics.toList()
    }

    /** Lit un paquet MQTT complet : (octet d'en-tête, corps). */
    private fun readMqttPacket(input: DataInputStream): Pair<Int, ByteArray> {
        val header = input.readUnsignedByte()
        val length = readRemainingLength(input)
        if (length > MAX_MQTT_PACKET) throw IOException("paquet MQTT trop grand")
        val body = ByteArray(length)
        input.readFully(body)
        return header to body
    }

    /** Longueur restante MQTT (varint, 1 à 4 octets). */
    private fun readRemainingLength(input: DataInputStream): Int {
        var multiplier = 1
        var value = 0
        while (true) {
            val b = input.readUnsignedByte()
            value += (b and 0x7F) * multiplier
            if ((b and 0x80) == 0) return value
            multiplier *= 128
            if (multiplier > 128 * 128 * 128) throw IOException("longueur MQTT invalide")
        }
    }

    suspend fun scanCoapResources(serverIp: String): List<String> = withContext(Dispatchers.IO) {
        val resources = mutableListOf<String>()
        val commonPaths = listOf(
            ".well-known/core",
            "api/config",
            "api/info",
            "settings",
            "status",
            "data",
            "update"
        )

        commonPaths.forEach { path ->
            val segments = path.split('/').filter { it.isNotEmpty() }
            if (coapGetCode(serverIp, segments) == COAP_CODE_CONTENT) {
                resources.add(path)
            }
            delay(100)
        }

        return@withContext resources
    }

    /**
     * En-tête CoAP (version 1, CON, TKL 0, code GET, message id) suivi d'une option
     * Uri-Path (numéro 11) par segment de chemin.
     */
    private fun buildCoapGet(messageId: Int, segments: List<String>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0x40)                       // Ver=1, Type=CON, TKL=0
        out.write(0x01)                       // Code 0.01 = GET
        out.write((messageId shr 8) and 0xFF)
        out.write(messageId and 0xFF)
        var previousOption = 0
        for (segment in segments) {
            val value = segment.toByteArray(Charsets.UTF_8)
            writeCoapOption(out, 11 - previousOption, value) // Uri-Path = 11
            previousOption = 11
        }
        return out.toByteArray()
    }

    private fun writeCoapOption(out: ByteArrayOutputStream, delta: Int, value: ByteArray) {
        val (deltaNibble, deltaExt) = coapOptionField(delta)
        val (lengthNibble, lengthExt) = coapOptionField(value.size)
        out.write((deltaNibble shl 4) or lengthNibble)
        out.write(deltaExt)
        out.write(lengthExt)
        out.write(value)
    }

    /** Encode delta ou longueur d'option CoAP : (nibble, octets d'extension). */
    private fun coapOptionField(n: Int): Pair<Int, ByteArray> = when {
        n < 13 -> n to ByteArray(0)
        n < 269 -> 13 to byteArrayOf((n - 13).toByte())
        else -> 14 to byteArrayOf(((n - 269) shr 8).toByte(), ((n - 269) and 0xFF).toByte())
    }

    private fun buildMqttSubscribe(topic: String): ByteArray {
        val topicBytes = topic.toByteArray(Charsets.UTF_8)
        return byteArrayOf(
            0x82.toByte(), (5 + topicBytes.size).toByte(),
            0x00.toByte(), 0x01.toByte(),
            (topicBytes.size shr 8).toByte(), topicBytes.size.toByte()
        ) + topicBytes + byteArrayOf(0x00.toByte()) // QoS 0
    }

    /**
     * Analyse une cible réseau. Formats acceptés :
     * - "192.168.1.10" : une adresse ;
     * - "192.168.1.0/24" : CIDR /16 à /32 (pour /24 et plus large, réseau et
     *   diffusion sont exclus ; au plus [MAX_HOSTS] adresses) ;
     * - "192.168.1.1-254" : plage sur le dernier octet ;
     * - "192.168.1.10-192.168.1.20" : plage entre deux adresses complètes.
     * Retourne une liste vide si la saisie est invalide.
     */
    fun parseIpRange(s: String): List<String> {
        val input = s.trim()
        if (input.isEmpty()) return emptyList()
        return when {
            input.contains('/') -> parseCidr(input)
            input.contains('-') -> parseDashRange(input)
            else -> ipToLong(input)?.let { listOf(longToIp(it)) } ?: emptyList()
        }
    }

    private fun parseCidr(input: String): List<String> {
        val parts = input.split('/')
        if (parts.size != 2) return emptyList()
        val base = ipToLong(parts[0].trim()) ?: return emptyList()
        val prefix = parts[1].trim().toIntOrNull() ?: return emptyList()
        if (prefix !in 16..32) return emptyList()
        val size = 1L shl (32 - prefix)
        val mask = (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        val network = base and mask
        val skipEdges = prefix <= 24
        val first = if (skipEdges) network + 1 else network
        val last = if (skipEdges) network + size - 2 else network + size - 1
        return rangeToList(first, last)
    }

    private fun parseDashRange(input: String): List<String> {
        val parts = input.split('-')
        if (parts.size != 2) return emptyList()
        val start = ipToLong(parts[0].trim()) ?: return emptyList()
        val right = parts[1].trim()
        val end: Long = if (right.contains('.')) {
            ipToLong(right) ?: return emptyList()
        } else {
            val octet = right.toIntOrNull() ?: return emptyList()
            if (octet !in 0..255) return emptyList()
            (start and 0xFFFFFF00L) or octet.toLong() // même préfixe a.b.c, dernier octet = e
        }
        return rangeToList(start, end)
    }

    /** Liste des adresses de first à last inclus, tronquée à [MAX_HOSTS]. */
    private fun rangeToList(first: Long, last: Long): List<String> {
        if (last < first) return emptyList()
        val end = minOf(last, first + MAX_HOSTS - 1)
        return (first..end).map { longToIp(it) }
    }

    /** IPv4 "a.b.c.d" vers entier non signé ; null si la forme est invalide. */
    private fun ipToLong(ip: String): Long? {
        val parts = ip.split('.')
        if (parts.size != 4) return null
        var value = 0L
        for (p in parts) {
            if (p.isEmpty() || p.length > 3 || !p.all { it in '0'..'9' }) return null
            val n = p.toInt()
            if (n > 255) return null
            value = (value shl 8) or n.toLong()
        }
        return value
    }

    private fun longToIp(v: Long): String =
        "${(v shr 24) and 0xFF}.${(v shr 16) and 0xFF}.${(v shr 8) and 0xFF}.${v and 0xFF}"
}
