package com.example.flipperdroid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flipperdroid.iot.IotDevice
import com.example.flipperdroid.iot.IotProtocolScanner
import com.example.flipperdroid.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class IotProtocolScannerViewModel : ViewModel() {

    private val _discoveredDevices = MutableStateFlow<List<IotDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<IotDevice>> = _discoveredDevices

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _scanLog = MutableStateFlow<String>("")
    val scanLog: StateFlow<String> = _scanLog

    private val _scanMode = MutableStateFlow("common")
    val scanMode: StateFlow<String> = _scanMode

    private val _targetRange = MutableStateFlow("192.168.1.1-254")
    val targetRange: StateFlow<String> = _targetRange

    fun addLog(message: String) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        val newLog = "[$timestamp] $message\n" + _scanLog.value
        _scanLog.value = newLog.take(5000)
        AppLog.i("IotScanner: $message")
    }

    fun startScan(mode: String = "common", range: String = "") {
        if (_isScanning.value) return

        viewModelScope.launch {
            _isScanning.value = true
            _discoveredDevices.value = emptyList()

            val scanRange = if (range.isNotEmpty()) range else _targetRange.value
            addLog("Starting $mode scan on $scanRange")

            try {
                val devices = when (mode) {
                    "mqtt" -> {
                        addLog("Scanning for MQTT brokers...")
                        IotProtocolScanner.scanMqtt(scanRange)
                    }
                    "coap" -> {
                        addLog("Scanning for CoAP servers...")
                        IotProtocolScanner.scanCoap(scanRange)
                    }
                    else -> {
                        addLog("Scanning for common IoT services...")
                        IotProtocolScanner.scanCommon(scanRange)
                    }
                }

                _discoveredDevices.value = devices
                addLog("Scan complete: ${devices.size} devices found")

                devices.forEach { device ->
                    addLog("Found: ${device.protocol} on ${device.address}:${device.port} (${device.service})")
                }

            } catch (e: Exception) {
                addLog("Scan error: ${e.message}")
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun scanMqttTopics(brokerIp: String) {
        viewModelScope.launch {
            addLog("Scanning MQTT topics on $brokerIp...")
            try {
                val topics = IotProtocolScanner.scanMqttTopics(brokerIp)
                addLog("MQTT topics discovered: ${topics.size}")
                topics.forEach { topic ->
                    addLog("  - $topic")
                }
            } catch (e: Exception) {
                addLog("Topic scan error: ${e.message}")
            }
        }
    }

    fun scanCoapResources(serverIp: String) {
        viewModelScope.launch {
            addLog("Scanning CoAP resources on $serverIp...")
            try {
                val resources = IotProtocolScanner.scanCoapResources(serverIp)
                addLog("CoAP resources discovered: ${resources.size}")
                resources.forEach { resource ->
                    addLog("  - $resource")
                }
            } catch (e: Exception) {
                addLog("Resource scan error: ${e.message}")
            }
        }
    }

    fun setScanMode(mode: String) {
        _scanMode.value = mode
        addLog("Scan mode set to: $mode")
    }

    fun setTargetRange(range: String) {
        _targetRange.value = range
        addLog("Target range set to: $range")
    }

    fun stopScan() {
        _isScanning.value = false
        addLog("Scan stopped")
    }

    fun clearResults() {
        _discoveredDevices.value = emptyList()
        _scanLog.value = ""
        addLog("Results cleared")
    }

    fun exportResults(): String {
        val sb = StringBuilder()
        sb.append("IoT Protocol Scan Results\n")
        sb.append("=========================\n")
        sb.append("Timestamp: ${LocalDateTime.now()}\n")
        sb.append("Discovered Devices: ${_discoveredDevices.value.size}\n\n")

        _discoveredDevices.value.groupBy { it.protocol }.forEach { (protocol, devices) ->
            sb.append("$protocol (${devices.size})\n")
            devices.forEach { device ->
                sb.append("  - ${device.address}:${device.port} (${device.service})\n")
            }
            sb.append("\n")
        }

        return sb.toString()
    }
}
