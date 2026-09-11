package com.example.flipperdroid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flipperdroid.network.PacketCapture
import com.example.flipperdroid.network.PacketSniffer
import com.example.flipperdroid.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class PacketSnifferViewModel : ViewModel() {

    private val _packets = MutableStateFlow<List<PacketCapture>>(emptyList())
    val packets: StateFlow<List<PacketCapture>> = _packets

    private val _isSniffing = MutableStateFlow(false)
    val isSniffing: StateFlow<Boolean> = _isSniffing

    private val _snifferLog = MutableStateFlow<String>("")
    val snifferLog: StateFlow<String> = _snifferLog

    private val _selectedInterface = MutableStateFlow("any")
    val selectedInterface: StateFlow<String> = _selectedInterface

    private val _filterExpression = MutableStateFlow("")
    val filterExpression: StateFlow<String> = _filterExpression

    private val _packetStats = MutableStateFlow<Map<String, Int>>(emptyMap())
    val packetStats: StateFlow<Map<String, Int>> = _packetStats

    fun addLog(message: String) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        val newLog = "[$timestamp] $message\n" + _snifferLog.value
        _snifferLog.value = newLog.take(5000)
        AppLog.i("PacketSniffer: $message")
    }

    fun startSniffing(maxPackets: Int = 100) {
        if (_isSniffing.value) return

        viewModelScope.launch {
            _isSniffing.value = true
            _packets.value = emptyList()
            addLog("Starting packet capture on ${_selectedInterface.value}")
            addLog("Filter: ${_filterExpression.value.ifEmpty { "none" }}")

            val capturedPackets = mutableListOf<PacketCapture>()

            val result = PacketSniffer.startTcpdump(
                interface = _selectedInterface.value,
                filter = _filterExpression.value,
                maxPackets = maxPackets,
                onPacket = { packet ->
                    capturedPackets.add(packet)
                    _packets.value = capturedPackets.toList()
                    addLog("${packet.protocol} ${packet.source} -> ${packet.destination} (${packet.length} bytes)")
                }
            )

            result.onSuccess { output ->
                addLog("Capture complete: ${capturedPackets.size} packets")
                updateStats(capturedPackets)
            }.onFailure { error ->
                addLog("Capture error: ${error.message}")
            }

            _isSniffing.value = false
        }
    }

    fun stopSniffing() {
        _isSniffing.value = false
        addLog("Sniffing stopped")
    }

    fun setInterface(interface: String) {
        _selectedInterface.value = interface
        addLog("Interface set to: $interface")
    }

    fun setFilter(filter: String) {
        _filterExpression.value = filter
        addLog("Filter set to: ${filter.ifEmpty { "none" }}")
    }

    fun getNetworkStats() {
        viewModelScope.launch {
            val result = PacketSniffer.getNetworkStats()
            result.onSuccess { stats ->
                addLog("Network stats: ${stats.entries.joinToString(", ") { "${it.key}=${it.value}" }}")
            }.onFailure { error ->
                addLog("Stats error: ${error.message}")
            }
        }
    }

    fun exportToCSV(): String {
        return PacketSniffer.exportToCSV(_packets.value)
    }

    fun clearPackets() {
        _packets.value = emptyList()
        _packetStats.value = emptyMap()
        _snifferLog.value = ""
        addLog("Packets cleared")
    }

    private fun updateStats(packets: List<PacketCapture>) {
        val stats = mutableMapOf<String, Int>()

        packets.forEach { packet ->
            stats[packet.protocol] = (stats[packet.protocol] ?: 0) + 1
        }

        _packetStats.value = stats
        addLog("Stats updated: ${stats.entries.joinToString(", ") { "${it.key}=${it.value}" }}")
    }

    fun getBuildCommand(): String {
        return PacketSniffer.captureWithFilters(
            protocol = "tcp"
        )
    }
}
