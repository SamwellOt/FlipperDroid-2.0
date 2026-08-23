package com.example.flipperdroid.model.`class`

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.flipperdroid.model.enums.AdvertisementError
import com.example.flipperdroid.model.enums.TxPowerLevel
import com.example.flipperdroid.model.`interface`.IAdvertisementService
import com.example.flipperdroid.model.`interface`.IAdvertisementServiceCallback
import com.example.flipperdroid.model.`object`.BluetoothHelpers.bluetoothAdapter

class LegacyAdvertisementService(context: Context): IAdvertisementService {
    private val _logTag = "LegacyAdvertisementService"
    private var _bluetoothAdapter: BluetoothAdapter? = null
    private var _advertiser: BluetoothLeAdvertiser? = null
    private var _advertisementServiceCallbacks: MutableList<IAdvertisementServiceCallback> = mutableListOf()
    private var _currentAdvertisementSet: AdvertisementSet? = null
    private var _txPowerLevel: TxPowerLevel? = null

    init {
        _bluetoothAdapter = context.bluetoothAdapter()
        if (_bluetoothAdapter != null) {
            _advertiser = _bluetoothAdapter!!.bluetoothLeAdvertiser
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    override fun startAdvertisement(advertisementSet: AdvertisementSet) {
        if (_advertiser != null) {
            if (advertisementSet.validate()) {
                // Permission check à adapter selon ta gestion
                val preparedAdvertisementSet = prepareAdvertisementSet(advertisementSet)
                if (preparedAdvertisementSet.scanResponse != null) {
                    _advertiser!!.startAdvertising(
                        preparedAdvertisementSet.advertiseSettings.build(),
                        preparedAdvertisementSet.advertiseData.build(),
                        preparedAdvertisementSet.scanResponse!!.build(),
                        preparedAdvertisementSet.advertisingCallback
                    )
                } else {
                    _advertiser!!.startAdvertising(
                        preparedAdvertisementSet.advertiseSettings.build(),
                        preparedAdvertisementSet.advertiseData.build(),
                        preparedAdvertisementSet.advertisingCallback
                    )
                }
                Log.d(_logTag, "Started Legacy Advertisement")
                _currentAdvertisementSet = preparedAdvertisementSet
                _advertisementServiceCallbacks.map {
                    it.onAdvertisementSetStart(advertisementSet)
                }
            } else {
                Log.d(_logTag, "Advertisement Set could not be validated")
            }
        } else {
            Log.d(_logTag, "Advertiser is null")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    override fun stopAdvertisement() {
        if (_advertiser != null) {
            if (_currentAdvertisementSet != null) {
                _advertiser!!.stopAdvertising(_currentAdvertisementSet!!.advertisingCallback)
                _advertisementServiceCallbacks.map {
                    it.onAdvertisementSetStop(_currentAdvertisementSet)
                }
                _currentAdvertisementSet = null
            } else {
                Log.d(_logTag, "Current Legacy Advertising Set is null")
            }
        } else {
            Log.d(_logTag, "Advertiser is null")
        }
    }

    override fun setTxPowerLevel(txPowerLevel: TxPowerLevel) {
        _txPowerLevel = txPowerLevel
        Log.d(_logTag, "Setting TX POWER")
    }

    override fun getTxPowerLevel(): TxPowerLevel {
        return _txPowerLevel ?: TxPowerLevel.TX_POWER_HIGH
    }

    fun prepareAdvertisementSet(advertisementSet: AdvertisementSet): AdvertisementSet {
        if (_txPowerLevel != null) {
            advertisementSet.advertiseSettings.txPowerLevel = _txPowerLevel!!
            advertisementSet.advertisingSetParameters.txPowerLevel = _txPowerLevel!!
        }
        advertisementSet.advertisingCallback = getAdvertisingCallback()
        return advertisementSet
    }

    override fun addAdvertisementServiceCallback(callback: IAdvertisementServiceCallback) {
        if (!_advertisementServiceCallbacks.contains(callback)) {
            _advertisementServiceCallbacks.add(callback)
        }
    }
    override fun removeAdvertisementServiceCallback(callback: IAdvertisementServiceCallback) {
        if (_advertisementServiceCallbacks.contains(callback)) {
            _advertisementServiceCallbacks.remove(callback)
        }
    }

    override fun isLegacyService(): Boolean {
        return true
    }

    private fun getAdvertisingCallback(): AdvertiseCallback {
        return object : AdvertiseCallback() {
            override fun onStartFailure(errorCode: Int) {
                super.onStartFailure(errorCode)
                // On mappe le vrai code d'erreur (auparavant tout était réduit à UNKNOWN,
                // masquant notamment DATA_TOO_LARGE pour les payloads > 31 octets).
                val advertisementError = when (errorCode) {
                    ADVERTISE_FAILED_ALREADY_STARTED -> AdvertisementError.ADVERTISE_FAILED_ALREADY_STARTED
                    ADVERTISE_FAILED_DATA_TOO_LARGE -> AdvertisementError.ADVERTISE_FAILED_DATA_TOO_LARGE
                    ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> AdvertisementError.ADVERTISE_FAILED_FEATURE_UNSUPPORTED
                    ADVERTISE_FAILED_INTERNAL_ERROR -> AdvertisementError.ADVERTISE_FAILED_INTERNAL_ERROR
                    ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> AdvertisementError.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS
                    else -> AdvertisementError.ADVERTISE_FAILED_UNKNOWN
                }
                _advertisementServiceCallbacks.map {
                    it.onAdvertisementSetFailed(_currentAdvertisementSet, advertisementError)
                }
            }
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                super.onStartSuccess(settingsInEffect)
                _advertisementServiceCallbacks.map {
                    it.onAdvertisementSetSucceeded(_currentAdvertisementSet)
                }
            }
        }
    }
}