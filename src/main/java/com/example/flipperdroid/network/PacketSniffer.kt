package com.example.flipperdroid.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.flipperdroid.util.ShellArgs
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

    @Volatile
    private var currentProcess: Process? = null

    /** Stops any in-progress tcpdump capture started by [startTcpdump]. */
    fun stop() {
        currentProcess?.destroy()
        currentProcess = null
    }

    suspend fun startTcpdump(
        iface: String = "any",
        filter: String = "",
        maxPackets: Int = 100,
        outputFile: String? = null,
        onPacket: (PacketCapture) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!ShellArgs.isValidInterface(iface)) {
                return@withContext Result.failure(IllegalArgumentException("Invalid interface name: $iface"))
            }
            if (filter.any { it == '\n' || it == '\r' }) {
                return@withContext Result.failure(IllegalArgumentException("Invalid BPF filter"))
            }
            // Chaque argument est quoté : le filtre BPF est passé tel quel à tcpdump
            // (parenthèses comprises) sans être interprété par le shell root.
            val args = mutableListOf("tcpdump", "-i", iface, "-n", "-l", "-c", maxPackets.coerceIn(1, 100_000).toString())
            if (outputFile != null) args.addAll(listOf("-w", outputFile))
            if (filter.isNotBlank()) args.add(filter.trim())

            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", ShellArgs.join(args)))
            currentProcess = process
            val stderr = StringBuffer()
            val errThread = Thread {
                try { process.errorStream.bufferedReader().forEachLine { stderr.append(it).append('\n') } } catch (_: Exception) {}
            }.apply { isDaemon = true; start() }

            val output = StringBuilder()
            process.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val l = reader.readLine() ?: break
                    parsePacketLine(l)?.let(onPacket)
                    output.append(l).append("\n")
                }
            }
            val code = process.waitFor()
            errThread.join(1000)
            val err = stderr.toString()
            when {
                err.contains("not found", true) || err.contains("inaccessible or not found", true) ->
                    Result.failure(IllegalStateException("tcpdump is not installed on this device (it is not bundled with the app)."))
                code != 0 && output.isEmpty() ->
                    Result.failure(IllegalStateException("tcpdump failed (exit $code): ${err.take(300)}"))
                else -> Result.success(output.toString())
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            currentProcess = null
        }
    }

    suspend fun getNetworkStats(): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        try {
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

    // En-tête tcpdump -n : "12:00:00.123456 IP 10.0.0.5.443 > 10.0.0.9.51234: Flags [P.], ... length 42"
    private val HEADER = Regex("""^(\d{2}:\d{2}:\d{2}\.\d+)\s+(IP6?|ARP)\s+(\S+)\s+>\s+(\S+?):\s*(.*)$""")

    /** Ne retient que les lignes d'en-tête de paquet (pas les lignes de charge utile). */
    fun parsePacketLine(line: String): PacketCapture? {
        val m = HEADER.find(line) ?: return null
        val (ts, family, src, dst, rest) = m.destructured
        return PacketCapture(
            timestamp = ts,
            source = src,
            destination = dst,
            protocol = if (family == "ARP") "ARP" else extractProtocol(rest),
            length = extractLength(rest),
            payload = rest.take(200)
        )
    }

    private fun extractProtocol(line: String): String {
        return when {
            line.contains("Flags [") -> "TCP"
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

        fun q(v: String) = "\"" + v.replace("\"", "\"\"").replace('\n', ' ').replace('\r', ' ') + "\""
        captures.forEach { capture ->
            sb.append(q(capture.timestamp)).append(',')
            sb.append(q(capture.source)).append(',')
            sb.append(q(capture.destination)).append(',')
            sb.append(q(capture.protocol)).append(',')
            sb.append(capture.length).append(',')
            sb.append(q(capture.payload.take(100))).append('\n')
        }

        return sb.toString()
    }
}
