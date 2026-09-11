package com.example.flipperdroid.iot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.Socket
import java.net.SocketTimeoutException

data class IotDevice(
    val address: String,
    val port: Int,
    val protocol: String,
    val service: String,
    val version: String = "unknown",
    val timestamp: Long = System.currentTimeMillis()
)

object IotProtocolScanner {

    suspend fun scanMqtt(targetRange: String): List<IotDevice> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<IotDevice>()
        val ips = parseIpRange(targetRange)

        ips.forEach { ip ->
            if (isMqttOpen(ip)) {
                devices.add(IotDevice(ip, 1883, "MQTT", "Broker", "3.1.1"))
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
                devices.add(IotDevice(ip, 5683, "CoAP", "Resource Server", "1.0"))
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
                socket.connect(java.net.InetSocketAddress(ip, port), 1000)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun isMqttOpen(ip: String): Boolean {
        return try {
            Socket().use { socket ->
                socket.soTimeout = 2000
                socket.connect(java.net.InetSocketAddress(ip, 1883), 2000)

                // Try to read MQTT CONNACK
                val input = socket.getInputStream()
                val buffer = ByteArray(4)
                val read = input.read(buffer)

                read > 0 && buffer[0] == 0x20.toByte()
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun isCoapOpen(ip: String): Boolean {
        return try {
            val packet = byteArrayOf(0x40.toByte(), 0x01, 0x00, 0x00)
            val socket = java.net.DatagramSocket()
            socket.soTimeout = 2000

            val address = java.net.InetAddress.getByName(ip)
            val packet_out = java.net.DatagramPacket(packet, packet.size, address, 5683)
            socket.send(packet_out)

            val buffer = ByteArray(128)
            val packet_in = java.net.DatagramPacket(buffer, buffer.size)
            socket.receive(packet_in)

            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun scanMqttTopics(brokerIp: String): List<String> = withContext(Dispatchers.IO) {
        val topics = mutableListOf<String>()

        try {
            Socket(brokerIp, 1883).use { socket ->
                val output = socket.getOutputStream()
                val input = socket.getInputStream()

                // Send MQTT CONNECT
                val connect = byteArrayOf(
                    0x10.toByte(), 0x0c.toByte(),
                    0x00.toByte(), 0x04.toByte(),
                    0x4d.toByte(), 0x51.toByte(), 0x54.toByte(), 0x54.toByte(),
                    0x04.toByte(), 0x02.toByte(), 0x00.toByte(), 0x00
                )
                output.write(connect)
                output.flush()

                // Subscribe to wildcard topics
                val topics_to_scan = listOf(
                    "sensor/#",
                    "device/#",
                    "home/#",
                    "command/#",
                    "telemetry/#",
                    "status/#",
                    "+/+"
                )

                topics_to_scan.forEach { topic ->
                    val subscribe = buildMqttSubscribe(topic)
                    output.write(subscribe)
                    output.flush()
                    delay(100)
                }
            }
        } catch (e: Exception) {
            // Silent fail
        }

        return@withContext topics
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
            if (coapGet(serverIp, path)) {
                resources.add(path)
            }
            delay(100)
        }

        return@withContext resources
    }

    private fun coapGet(ip: String, path: String): Boolean {
        return try {
            // Simplified CoAP GET
            val socket = java.net.DatagramSocket()
            socket.soTimeout = 2000

            val packet = byteArrayOf(0x40.toByte(), 0x01, 0x00, 0x01) + path.toByteArray()
            val address = java.net.InetAddress.getByName(ip)
            val packet_out = java.net.DatagramPacket(packet, packet.size, address, 5683)
            socket.send(packet_out)

            val buffer = ByteArray(256)
            val packet_in = java.net.DatagramPacket(buffer, buffer.size)
            socket.receive(packet_in)

            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun buildMqttSubscribe(topic: String): ByteArray {
        val topicBytes = topic.toByteArray()
        return byteArrayOf(
            0x82.toByte(), (5 + topicBytes.size).toByte(),
            0x00.toByte(), 0x01.toByte(),
            0x00.toByte(), topicBytes.size.toByte()
        ) + topicBytes + byteArrayOf(0x00.toByte())
    }

    private fun parseIpRange(range: String): List<String> {
        return if (range.contains("-")) {
            val parts = range.split("-")
            val start = parts[0].trim()
            val end = parts[1].trim().toIntOrNull() ?: return listOf(start)

            val prefix = start.substringBeforeLast(".")
            (start.substringAfterLast(".").toInt()..end).map { "$prefix.$it" }
        } else if (range.contains("/")) {
            listOf(range)
        } else {
            listOf(range)
        }
    }
}
