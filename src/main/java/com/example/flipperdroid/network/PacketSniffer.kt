package com.example.flipperdroid.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

data class PacketCapture(
    val timestamp: String,
    val source: String,
    val destination: String,
    val protocol: String,
    val length: Int,
    val payload: String,
    val flags: String = ""
)

object PacketSniffer {

    suspend fun startTcpdump(
        interface: String = "any",
        filter: String = "",
        maxPackets: Int = 100,
        outputFile: String? = null,
        onPacket: (PacketCapture) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val commands = mutableListOf("tcpdump")

            // Interface
            commands.addAll(listOf("-i", interface))

            // No DNS resolution
            commands.add("-n")

            // Packet count
            commands.addAll(listOf("-c", maxPackets.toString()))

            // Output format (line-based for easy parsing)
            commands.add("-A")

            // Filter expression
            if (filter.isNotEmpty()) {
                commands.add(filter)
            }

            // Output file
            if (outputFile != null) {
                commands.addAll(listOf("-w", outputFile))
            }

            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", commands.joinToString(" ")})
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            var line: String?
            val output = StringBuilder()

            while (reader.readLine().also { line = it } != null) {
                line?.let { l ->
                    val capture = parsePacketLine(l)
                    if (capture != null) {
                        onPacket(capture)
                    }
                    output.append(l).append("\n")
                }
            }

            reader.close()
            process.waitFor()

            Result.success(output.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getNetworkStats(): Result<Map<String, String>> = try {
        val proc = Runtime.getRuntime().exec(arrayOf("netstat", "-an"))
        val reader = BufferedReader(InputStreamReader(proc.inputStream))

        val stats = mutableMapOf<String, String>()
        val lines = reader.readLines()

        stats["tcp_connections"] = lines.count { it.contains("ESTABLISHED") }.toString()
        stats["tcp_listening"] = lines.count { it.contains("LISTEN") }.toString()
        stats["total_lines"] = lines.size.toString()

        Result.success(stats)
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun captureWithFilters(
        sourceIp: String = "",
        destIp: String = "",
        port: Int = 0,
        protocol: String = ""
    ): String {
        val filter = buildFilterExpression(sourceIp, destIp, port, protocol)
        return "tcpdump -i any -n -A $filter"
    }

    private fun buildFilterExpression(
        sourceIp: String,
        destIp: String,
        port: Int,
        protocol: String
    ): String {
        val parts = mutableListOf<String>()

        if (sourceIp.isNotEmpty()) {
            parts.add("src $sourceIp")
        }

        if (destIp.isNotEmpty()) {
            parts.add("dst $destIp")
        }

        if (port > 0) {
            parts.add("port $port")
        }

        if (protocol.isNotEmpty()) {
            parts.add(protocol.lowercase())
        }

        return if (parts.isEmpty()) "" else parts.joinToString(" and ", prefix = "'", postfix = "'")
    }

    private fun parsePacketLine(line: String): PacketCapture? {
        return try {
            val parts = line.split(" ").filter { it.isNotEmpty() }
            if (parts.size < 3) return null

            PacketCapture(
                timestamp = parts.getOrNull(0) ?: "unknown",
                source = parts.getOrNull(1) ?: "unknown",
                destination = parts.getOrNull(2) ?: "unknown",
                protocol = extractProtocol(line),
                length = extractLength(line),
                payload = line.substringAfterLast(":").trim()
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun extractProtocol(line: String): String {
        return when {
            line.contains("TCP") || line.contains(".tcp") -> "TCP"
            line.contains("UDP") || line.contains(".udp") -> "UDP"
            line.contains("ICMP") -> "ICMP"
            line.contains("DNS") -> "DNS"
            line.contains("HTTP") -> "HTTP"
            else -> "Unknown"
        }
    }

    private fun extractLength(line: String): Int {
        return try {
            val regex = Regex("""length (\d+)""")
            regex.find(line)?.groupValues?.get(1)?.toInt() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun exportToCSV(captures: List<PacketCapture>): String {
        val sb = StringBuilder()
        sb.append("Timestamp,Source,Destination,Protocol,Length,Payload\n")

        captures.forEach { capture ->
            sb.append("\"${capture.timestamp}\",")
            sb.append("\"${capture.source}\",")
            sb.append("\"${capture.destination}\",")
            sb.append("\"${capture.protocol}\",")
            sb.append("${capture.length},")
            sb.append("\"${capture.payload.take(100)}\"\n")
        }

        return sb.toString()
    }
}
