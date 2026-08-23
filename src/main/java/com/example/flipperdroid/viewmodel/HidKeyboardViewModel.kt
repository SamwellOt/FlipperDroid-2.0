package com.example.flipperdroid.viewmodel

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flipperdroid.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Clavier HID Bluetooth : le téléphone se présente comme un clavier sans fil
 * (BadKB sans câble) auprès d'un PC/tablette apparié. Utilise le profil
 * BluetoothHidDevice (pas de root requis).
 */
class HidKeyboardViewModel : ViewModel() {

    @SuppressLint("StaticFieldLeak")
    private var context: Context? = null
    private var adapter: BluetoothAdapter? = null
    private var hidDevice: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null
    private var registered = false

    private val _status = MutableStateFlow("Not registered")
    val status: StateFlow<String> = _status

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    // Descripteur HID d'un clavier "boot" standard (rapport 8 octets, sans report id)
    private val hidDescriptor = byteArrayOf(
        0x05, 0x01, 0x09, 0x06, 0xA1.toByte(), 0x01, 0x05, 0x07,
        0x19, 0xE0.toByte(), 0x29, 0xE7.toByte(), 0x15, 0x00, 0x25, 0x01,
        0x75, 0x01, 0x95.toByte(), 0x08, 0x81.toByte(), 0x02, 0x95.toByte(), 0x01,
        0x75, 0x08, 0x81.toByte(), 0x01, 0x95.toByte(), 0x05, 0x75, 0x01,
        0x05, 0x08, 0x19, 0x01, 0x29, 0x05, 0x91.toByte(), 0x02,
        0x95.toByte(), 0x01, 0x75, 0x03, 0x91.toByte(), 0x01, 0x95.toByte(), 0x06,
        0x75, 0x08, 0x15, 0x00, 0x25, 0x65, 0x05, 0x07,
        0x19, 0x00, 0x29, 0x65, 0x81.toByte(), 0x00, 0xC0.toByte()
    )

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = proxy as BluetoothHidDevice
                registerApp()
            }
        }
        override fun onServiceDisconnected(profile: Int) {
            hidDevice = null
            _status.value = "HID service disconnected"
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, reg: Boolean) {
            registered = reg
            _status.value = if (reg) "Registered as HID keyboard. Pair from the target PC." else "Unregistered"
        }
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    host = device
                    _connected.value = true
                    _status.value = "Connected to host"
                    AppLog.log("HID", "Host connected: ${device.address}")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (device == host) { host = null; _connected.value = false; _status.value = "Host disconnected" }
                }
            }
        }
    }

    fun initialize(context: Context) {
        this.context = context.applicationContext
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        adapter = manager.adapter
        try {
            adapter?.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)
        } catch (e: Exception) {
            _status.value = "HID profile unavailable: ${e.message}"
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerApp() {
        val hid = hidDevice ?: return
        try {
            val sdp = BluetoothHidDeviceAppSdpSettings(
                "FlipperDroid Keyboard",
                "BLE HID keyboard",
                "FlipperDroid",
                BluetoothHidDevice.SUBCLASS1_KEYBOARD,
                hidDescriptor
            )
            hid.registerApp(sdp, null, null, Executors.newCachedThreadPool(), hidCallback)
            _status.value = "Registering HID app..."
        } catch (e: Exception) {
            _status.value = "registerApp failed: ${e.message}"
        }
    }

    @SuppressLint("MissingPermission")
    fun typeText(text: String) {
        val hid = hidDevice
        val target = host
        if (hid == null || target == null) { _status.value = "No connected host"; return }
        viewModelScope.launch(Dispatchers.IO) {
            _status.value = "Typing..."
            for (c in text) {
                val (mod, code) = RootBadUsbViewModel.charToKeycode(c)
                if (code == 0) continue
                sendKey(hid, target, mod, code)
                delay(15)
            }
            _status.value = "Sent ${text.length} chars"
            AppLog.log("HID", "Typed ${text.length} chars")
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendKey(hid: BluetoothHidDevice, target: BluetoothDevice, modifier: Int, keycode: Int) {
        val press = byteArrayOf(modifier.toByte(), 0, keycode.toByte(), 0, 0, 0, 0, 0)
        hid.sendReport(target, 0, press)
        try { Thread.sleep(8) } catch (_: Exception) {}
        hid.sendReport(target, 0, ByteArray(8)) // release
    }

    @SuppressLint("MissingPermission")
    fun unregister() {
        try { hidDevice?.unregisterApp() } catch (_: Exception) {}
        registered = false
        _status.value = "Unregistered"
    }

    override fun onCleared() {
        super.onCleared()
        try {
            unregister()
            adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        } catch (_: Exception) {}
    }
}
