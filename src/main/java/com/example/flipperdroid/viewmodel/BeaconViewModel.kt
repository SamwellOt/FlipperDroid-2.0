package com.example.flipperdroid.viewmodel

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.util.UUID

/**
 * Émetteur de balises BLE : iBeacon (Apple), Eddystone-URL (Google) et
 * manufacturer data personnalisé. Utilise directement BluetoothLeAdvertiser.
 */
class BeaconViewModel : ViewModel() {

    @SuppressLint("StaticFieldLeak")
    private var context: Context? = null
    private var adapter: BluetoothAdapter? = null
    private var advertiser: BluetoothLeAdvertiser? = null

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    private val callback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            _isAdvertising.value = true
            _status.value = "Advertising..."
        }
        override fun onStartFailure(errorCode: Int) {
            _isAdvertising.value = false
            _status.value = "Advertise failed: $errorCode"
        }
    }

    fun initialize(context: Context) {
        this.context = context.applicationContext
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        adapter = manager.adapter
        advertiser = adapter?.bluetoothLeAdvertiser
    }

    private fun settings(): AdvertiseSettings = AdvertiseSettings.Builder()
        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
        .setConnectable(false)
        .build()

    @SuppressLint("MissingPermission")
    fun startIBeacon(uuidStr: String, major: Int, minor: Int) {
        stop()
        val adv = advertiser ?: run { _status.value = "BLE advertiser unavailable"; return }
        try {
            val uuid = UUID.fromString(uuidStr)
            val payload = ByteBuffer.allocate(23)
            payload.put(0x02); payload.put(0x15) // iBeacon type + length
            payload.put(uuidToBytes(uuid))
            payload.put((major shr 8).toByte()); payload.put(major.toByte())
            payload.put((minor shr 8).toByte()); payload.put(minor.toByte())
            payload.put(0xC5.toByte()) // TX power calibré
            val data = AdvertiseData.Builder()
                .addManufacturerData(0x004C, payload.array()) // Apple
                .build()
            adv.startAdvertising(settings(), data, callback)
            _status.value = "iBeacon: $uuidStr / $major / $minor"
        } catch (e: Exception) {
            _status.value = "iBeacon error: ${e.message}"
            Log.e("Beacon", "iBeacon", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun startEddystoneUrl(url: String) {
        stop()
        val adv = advertiser ?: run { _status.value = "BLE advertiser unavailable"; return }
        try {
            val (scheme, rest) = encodeUrlScheme(url)
            val body = rest.toByteArray(Charsets.US_ASCII)
            val frame = ByteArray(4 + body.size)
            frame[0] = 0x10           // Eddystone-URL frame
            frame[1] = 0xC5.toByte()  // TX power
            frame[2] = scheme
            System.arraycopy(body, 0, frame, 3, body.size)
            // (dernier octet reste 0)
            val data = AdvertiseData.Builder()
                .addServiceUuid(ParcelUuid(UUID.fromString("0000FEAA-0000-1000-8000-00805F9B34FB")))
                .addServiceData(ParcelUuid(UUID.fromString("0000FEAA-0000-1000-8000-00805F9B34FB")), frame)
                .build()
            adv.startAdvertising(settings(), data, callback)
            _status.value = "Eddystone-URL: $url"
        } catch (e: Exception) {
            _status.value = "Eddystone error: ${e.message}"
            Log.e("Beacon", "Eddystone", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun startCustom(companyId: Int, hexData: String) {
        stop()
        val adv = advertiser ?: run { _status.value = "BLE advertiser unavailable"; return }
        try {
            val bytes = hexData.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val data = AdvertiseData.Builder()
                .addManufacturerData(companyId, bytes)
                .build()
            adv.startAdvertising(settings(), data, callback)
            _status.value = "Custom mfr 0x%04X (${bytes.size} bytes)".format(companyId)
        } catch (e: Exception) {
            _status.value = "Custom error: ${e.message}"
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        try { advertiser?.stopAdvertising(callback) } catch (_: Exception) {}
        _isAdvertising.value = false
    }

    private fun uuidToBytes(uuid: UUID): ByteArray {
        val bb = ByteBuffer.allocate(16)
        bb.putLong(uuid.mostSignificantBits)
        bb.putLong(uuid.leastSignificantBits)
        return bb.array()
    }

    /** Retourne (code de schéma Eddystone, reste de l'URL). */
    private fun encodeUrlScheme(url: String): Pair<Byte, String> = when {
        url.startsWith("https://www.") -> 0x01.toByte() to url.removePrefix("https://www.")
        url.startsWith("http://www.") -> 0x00.toByte() to url.removePrefix("http://www.")
        url.startsWith("https://") -> 0x03.toByte() to url.removePrefix("https://")
        url.startsWith("http://") -> 0x02.toByte() to url.removePrefix("http://")
        else -> 0x03.toByte() to url
    }

    override fun onCleared() {
        super.onCleared()
        stop()
    }
}
