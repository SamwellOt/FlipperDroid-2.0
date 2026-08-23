package com.example.flipperdroid.model.`object`

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.example.flipperdroid.model.`class`.LegacyAdvertisementService
import com.example.flipperdroid.model.`class`.ModernAdvertisementService
import com.example.flipperdroid.model.`interface`.IAdvertisementService

object BluetoothHelpers {
    fun Context.bluetoothManager(): BluetoothManager? =
        this.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    fun Context.bluetoothAdapter(): BluetoothAdapter? = this.bluetoothManager()?.adapter


    fun getAdvertisementService(context: Context, useLegacy: Boolean = true): IAdvertisementService {
        return if (useLegacy) {
            LegacyAdvertisementService(context)
        } else {
            ModernAdvertisementService(context)
        }
    }
} 