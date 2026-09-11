package com.example.flipperdroid.viewmodel

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flipperdroid.redteam.FuzzPayload
import com.example.flipperdroid.redteam.FuzzPayloadLibrary
import com.example.flipperdroid.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class BleGattFuzzResult(
    val device: String,
    val service: String,
    val characteristic: String,
    val payload: String,
    val response: String,
    val timestamp: String,
    val severity: String,
)

class BleGattFuzzerViewModel(private val context: Context) : ViewModel() {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private val _devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val devices: StateFlow<List<BluetoothDevice>> = _devices

    private val _fuzzResults = MutableStateFlow<List<BleGattFuzzResult>>(emptyList())
    val fuzzResults: StateFlow<List<BleGattFuzzResult>> = _fuzzResults

    private val _isFuzzing = MutableStateFlow(false)
    val isFuzzing: StateFlow<Boolean> = _isFuzzing

    private val _log = MutableStateFlow<String>("")
    val log: StateFlow<String> = _log

    private var currentGatt: BluetoothGatt? = null
    private val fuzzPayloads = FuzzPayloadLibrary.getPayloads()

    fun addLog(message: String) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        val newLog = "[$timestamp] $message\n" + _log.value
        _log.value = newLog.take(5000)
        AppLog.log("GATT-Fuzzer", message)
    }

    fun startFuzzing(device: BluetoothDevice) {
        if (_isFuzzing.value) return
        viewModelScope.launch {
            _isFuzzing.value = true
            _fuzzResults.value = emptyList()
            addLog("Starting GATT fuzzing on: ${device.name} (${device.address})")

            connectDevice(device)
        }
    }

    private fun connectDevice(device: BluetoothDevice) {
        try {
            val gattCallback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == BluetoothGatt.STATE_CONNECTED) {
                        addLog("Connected to ${device.name}")
                        gatt.discoverServices()
                    } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                        addLog("Disconnected from ${device.name}")
                        _isFuzzing.value = false
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    addLog("Services discovered: ${gatt.services.size} services found")
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        fuzzServices(gatt)
                    }
                }
            }

            currentGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(context, false, gattCallback)
            }
        } catch (e: Exception) {
            addLog("Error connecting: ${e.message}")
            _isFuzzing.value = false
        }
    }

    private fun fuzzServices(gatt: BluetoothGatt) {
        viewModelScope.launch {
            gatt.services.forEach { service ->
                service.characteristics.forEach { characteristic ->
                    addLog("Fuzzing ${service.uuid} / ${characteristic.uuid}")

                    fuzzPayloads.forEach { payload ->
                        try {
                            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
                                writeToCharacteristic(gatt, characteristic, payload)
                            } else if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                                writeToCharacteristicNoResponse(gatt, characteristic, payload)
                            }
                        } catch (e: Exception) {
                            addLog("Error fuzzing: ${e.message}")
                        }
                        Thread.sleep(100)
                    }
                }
            }

            gatt.disconnect()
            _isFuzzing.value = false
            addLog("Fuzzing completed")
        }
    }

    private fun writeToCharacteristic(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, payload: FuzzPayload) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(characteristic, payload.data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = payload.data
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(characteristic)
            }
            addLog("Wrote ${payload.name} to characteristic")
        } catch (e: Exception) {
            addLog("Write failed: ${e.message}")
        }
    }

    private fun writeToCharacteristicNoResponse(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, payload: FuzzPayload) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(characteristic, payload.data, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = payload.data
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(characteristic)
            }
            addLog("Wrote ${payload.name} (no response)")
        } catch (e: Exception) {
            addLog("Write no response failed: ${e.message}")
        }
    }

    fun stopFuzzing() {
        _isFuzzing.value = false
        currentGatt?.disconnect()
        currentGatt?.close()
        addLog("Fuzzing stopped")
    }

    fun clearLogs() {
        _log.value = ""
    }

    override fun onCleared() {
        super.onCleared()
        stopFuzzing()
    }
}
