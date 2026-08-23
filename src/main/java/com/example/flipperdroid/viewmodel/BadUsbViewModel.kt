package com.example.flipperdroid.viewmodel

import android.content.Context
import android.hardware.usb.*
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/**
 * ViewModel gerant la connexion et l'interaction avec un appareil USB en mode clavier
 *
 * Cette classe detecte si le mode USB Host est disponible et gere la connexion a un appareil USB HID clavier
 * Elle permet de faire passer le téléphone comme un clavier via USB pour exécuter un script
 *
 */
class BadUsbViewModel : ViewModel() {

    private val _isUsbHostAvailable = mutableStateOf(false)

    /*
    *  Permet de voir si l'usb est disponible
     */
    val isUsbHostAvailable: State<Boolean> = _isUsbHostAvailable

    private val _isConnected = mutableStateOf(false)
    // test
    val isConnected: State<Boolean> = _isConnected

    private val _currentScript = MutableStateFlow<String>("")
    val currentScript: StateFlow<String> = _currentScript

    private val _status = MutableStateFlow<String>("")
    val status: StateFlow<String> = _status

    private var usbManager: UsbManager? = null
    private var usbDevice: UsbDevice? = null
    private var usbInterface: UsbInterface? = null
    private var usbEndpoint: UsbEndpoint? = null
    private var usbConnection: UsbDeviceConnection? = null

    // HID Keyboard modifiers
    private val modifierNone = 0x00
    private val modfiferLeftShift = 0x02
    /**
     * Initialise le viewmodel avec le contexte et prepare le gestionnaire USB
     *
     * @param context contexte de l'application necessaire pour acceder au service USB
     */
    fun initialize(context: Context) {
        usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        checkUsbHostSupport()
    }
    /**
     * Verifie si le mode USB Host est disponible sur l'appareil
     *
     * Met a jour l'état isUsbHostAvailable et le status associe
     */
    private fun checkUsbHostSupport() {
        _isUsbHostAvailable.value = usbManager != null
        if (!_isUsbHostAvailable.value) {
            _status.value = "USB Host not supported on this device"
        }
    }
    /**
     * Connecte l'appareil USB fourni et prepare l'interface HID clavier
     *
     * Cherche l'interface HID et le endpoint d'écriture pour envoyer les donnees
     * Met a jour l'état isConnected et le status en fonction du succes de la connexion
     *
     * @param device appareil USB detecte
     * @param connection connexion USB etablie avec l'appareil
     */
    fun connectUsb(device: UsbDevice, connection: UsbDeviceConnection) {
        usbDevice = device
        usbConnection = connection

        // Find HID interface
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == UsbConstants.USB_CLASS_HID) {
                usbInterface = intf
                break
            }
        }

        // Find endpoint
        usbInterface?.let { intf ->
            for (i in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(i)
                if (ep.direction == UsbConstants.USB_DIR_OUT) {
                    usbEndpoint = ep
                    break
                }
            }
        }

        if (usbInterface != null && usbEndpoint != null) {
            connection.claimInterface(usbInterface, true)
            _isConnected.value = true
            _status.value = "Connected as USB Keyboard"
        } else {
            _status.value = "Failed to initialize USB HID interface"
        }
    }
    /**
     * Deconnecte l'appareil USB et libere les ressources
     *
     * Met a jour l'état de isConnected et le status
     */
    fun disconnect() {
        usbConnection?.let { connection ->
            usbInterface?.let { intf ->
                connection.releaseInterface(intf)
            }
            connection.close()
        }
        _isConnected.value = false
        usbConnection = null
        usbInterface = null
        usbEndpoint = null
        _status.value = "Disconnected"
    }
    /**
     * Met a jour le script à éxécuter
     *
     * @param script chaine de caracteres representant le scrpit
     */
    fun updateScript(script: String) {
        _currentScript.value = script
    }

    /**
     * Execute le script en envoyant les caracteres un par un via USB
     *
     * Cette methode fonctionne uniquement si l'appareil est connecte en mode clavier USB
     * Elle envoie chaque caractere avec un delai 50 ms entre chaque frappe
     * Met a jour le status avec le resultat de l execution
     */
    fun executeScript() {
        // Les transferts USB sont bloquants : on exécute sur un thread d'IO
        // et on utilise delay() (non bloquant) au lieu de Thread.sleep().
        viewModelScope.launch(Dispatchers.IO) {
            if (!_isConnected.value) {
                _status.value = "Not connected as USB keyboard"
                return@launch
            }

            try {
                val script = _currentScript.value
                _status.value = "Executing script..."

                script.forEach { char ->
                    sendKey(char)
                    // Petit délai entre chaque frappe
                    delay(50)
                }

                _status.value = "Script executed successfully"
            } catch (e: Exception) {
                _status.value = "Error executing script: ${e.message}"
                Log.e("BadUsbViewModel", "Error executing script", e)
            }
        }
    }

    /**
     * Envoie une touche clavier USB correspondant au caractere donne
     *
     * Calcule le modifier et le keycode correspondant selon la table HID
     *
     * @param char caractere a envoyer
     */
    private fun sendKey(char: Char) {
        val (modifier, keycode) = when (char) {
            in 'A'..'Z' -> Pair(modfiferLeftShift, char.lowercaseChar().toKeycode())
            in 'a'..'z' -> Pair(modifierNone, char.toKeycode())
            in '0'..'9' -> Pair(modifierNone, char.toKeycode())
            ' ' -> Pair(modifierNone, 0x2C) // Spacebar
            '\n' -> Pair(modifierNone, 0x28) // Enter
            '\t' -> Pair(modifierNone, 0x2B) // Tab
            '.' -> Pair(modifierNone, 0x37)
            ',' -> Pair(modifierNone, 0x36)
            '-' -> Pair(modifierNone, 0x2D)
            '_' -> Pair(modfiferLeftShift, 0x2D)
            '=' -> Pair(modifierNone, 0x2E)
            '+' -> Pair(modfiferLeftShift, 0x2E)
            ';' -> Pair(modifierNone, 0x33)
            ':' -> Pair(modfiferLeftShift, 0x33)
            '\'' -> Pair(modifierNone, 0x34)
            '"' -> Pair(modfiferLeftShift, 0x34)
            '/' -> Pair(modifierNone, 0x38)
            '?' -> Pair(modfiferLeftShift, 0x38)
            '!' -> Pair(modfiferLeftShift, 0x1E)
            '@' -> Pair(modfiferLeftShift, 0x1F)
            '#' -> Pair(modfiferLeftShift, 0x20)
            '$' -> Pair(modfiferLeftShift, 0x21)
            '%' -> Pair(modfiferLeftShift, 0x22)
            '^' -> Pair(modfiferLeftShift, 0x23)
            '&' -> Pair(modfiferLeftShift, 0x24)
            '*' -> Pair(modfiferLeftShift, 0x25)
            '(' -> Pair(modfiferLeftShift, 0x26)
            ')' -> Pair(modfiferLeftShift, 0x27)
            else -> Pair(modifierNone, 0x00)
        }

        val buffer = ByteBuffer.allocate(8)
        buffer.put(modifier.toByte()) // Modifier
        buffer.put(0.toByte())        // Reserved
        buffer.put(keycode.toByte())  // Keycode
        buffer.put(ByteArray(5))      // Padding

        usbConnection?.bulkTransfer(
            usbEndpoint,
            buffer.array(),
            buffer.capacity(),
            1000
        )

        // Send key release
        val releaseBuffer = ByteBuffer.allocate(8)
        releaseBuffer.put(ByteArray(8))
        usbConnection?.bulkTransfer(
            usbEndpoint,
            releaseBuffer.array(),
            releaseBuffer.capacity(),
            1000
        )
    }

    /**
     * Convertit un caractere en keycode USB HID
     *
     * @return code USB HID correspondant au caractere
     */
    private fun Char.toKeycode(): Int {
        return when (this) {
            in 'a'..'z' -> this.code - 'a'.code + 4
            in '0'..'9' -> when (this) {
                '0' -> 0x27
                else -> this.code - '1'.code + 0x1E
            }
            else -> 0x00
        }
    }
} 