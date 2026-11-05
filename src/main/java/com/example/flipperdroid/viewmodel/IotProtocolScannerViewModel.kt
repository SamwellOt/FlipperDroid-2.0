package com.example.flipperdroid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flipperdroid.iot.IotDevice
import com.example.flipperdroid.iot.IotProtocolScanner
import com.example.flipperdroid.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    /** Job du scan en cours ; annulé par [stopScan]. */
    private var scanJob: Job? = null

    private fun timestamp(pattern: String): String =
        SimpleDateFormat(pattern, Locale.US).format(Date())

    fun addLog(message: String) {
        val newLog = "[${timestamp("HH:mm:ss")}] $message\n" + _scanLog.value
        _scanLog.value = newLog.take(5000)
        AppLog.log("IotScanner", message)
    }

    fun startScan(mode: String = "common", range: String = "") {
        if (_isScanning.value || scanJob?.isActive == true) return

        val scanRange = if (range.isNotEmpty()) range else _targetRange.value
        if (IotProtocolScanner.parseIpRange(scanRange).isEmpty()) {
            addLog("Invalid target range: $scanRange")
            return
        }

        _isScanning.value = true
        _discoveredDevices.value = emptyList()
        addLog("Starting $mode scan on $scanRange")

        // LAZY : le Job est enregistré avant de démarrer, pour que le bloc finally le reconnaisse.
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                addLog("Scan error: ${e.message}")
            } finally {
                // Ne réinitialise l'état que si ce scan est encore le scan courant.
                if (scanJob === coroutineContext[Job]) {
                    scanJob = null
                    _isScanning.value = false
                }
            }
        }
        scanJob = job
        job.start()
    }

    fun scanMqttTopics(brokerIp: String) {
        viewModelScope.launch {
            addLog("Listening for MQTT topics on $brokerIp (2 s, SUBSCRIBE #)...")
            try {
                val topics = IotProtocolScanner.scanMqttTopics(brokerIp)
                if (topics.isEmpty()) {
                    addLog("No PUBLISH seen in 2 s (broker silent, or ACL denies '#')")
                } else {
                    addLog("MQTT topics observed: ${topics.size}")
                    topics.forEach { topic -> addLog("  - $topic") }
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
        scanJob?.cancel()
        scanJob = null
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
        sb.append("Timestamp: ${timestamp("yyyy-MM-dd HH:mm:ss")}\n")
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
