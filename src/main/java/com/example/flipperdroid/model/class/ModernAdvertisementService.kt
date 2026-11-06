package com.example.flipperdroid.model.`class`

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import com.example.flipperdroid.model.enums.AdvertisementError
import com.example.flipperdroid.model.enums.TxPowerLevel
import com.example.flipperdroid.model.`interface`.IAdvertisementService
import com.example.flipperdroid.model.`interface`.IAdvertisementServiceCallback
import com.example.flipperdroid.model.`object`.AdvertisementPayload
import com.example.flipperdroid.model.`object`.AdvertisementPayload.LEGACY_MAX_PAYLOAD_BYTES as LEGACY_MAX
import com.example.flipperdroid.model.`object`.BluetoothHelpers.bluetoothAdapter

class ModernAdvertisementService(
    private val context: Context,
): IAdvertisementService {
    private val _logTag = "ModernAdvertisementService"
    private var _bluetoothAdapter: BluetoothAdapter? = null
    private var _advertiser: BluetoothLeAdvertiser? = null
    private var _advertisementServiceCallbacks: MutableList<IAdvertisementServiceCallback> = mutableListOf()
    private var _currentAdvertisementSet: AdvertisementSet? = null
    private var _txPowerLevel: TxPowerLevel? = null
    // Le set d'advertising réellement enregistré (renvoyé par onAdvertisingSetStarted) :
    // permet de mettre à jour les données sans ré-enregistrer.
    private var _runningSet: AdvertisingSet? = null

    init {
        _bluetoothAdapter = context.bluetoothAdapter()
        if (_bluetoothAdapter != null) {
            _advertiser = _bluetoothAdapter!!.bluetoothLeAdvertiser
        }
    }

    fun prepareAdvertisementSet(advertisementSet: AdvertisementSet): AdvertisementSet {
        if (_txPowerLevel != null) {
            advertisementSet.advertiseSettings.txPowerLevel = _txPowerLevel!!
            advertisementSet.advertisingSetParameters.txPowerLevel = _txPowerLevel!!
        }
        // Legacy vs étendu : le legacy plafonne la trame à 31 octets, d'où les échecs
        // DATA_TOO_LARGE sur Apple Continuity / SwiftPair. MAIS la plupart des cibles
        // (Windows Swift Pair, iOS/macOS Continuity, Android Fast Pair) n'écoutent QUE
        // l'advertising legacy par défaut : une trame courte envoyée en étendu leur est
        // invisible. On garde donc le LEGACY tant que la charge tient dans 31 octets
        // (cas de l'immense majorité des payloads de spam) et on ne bascule en ÉTENDU
        // que lorsqu'elle déborde réellement.
        val hasScanResponse = advertisementSet.scanResponse != null
        val extendedSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            _bluetoothAdapter?.isLeExtendedAdvertisingSupported == true
        val data = advertisementSet.advertiseData
        val estimatedBytes = AdvertisementPayload.estimateLegacyBytes(
            connectable = advertisementSet.advertiseSettings.connectable,
            includeDeviceName = data.includeDeviceName,
            deviceNameLength = if (data.includeDeviceName) safeAdapterNameLength() else 0,
            includeTxPower = data.includeTxPower,
            manufacturerDataLengths = data.manufacturerData.map { it.manufacturerSpecificData.size },
            service16Count = data.services.count { it.serviceUuid != null },
            serviceData16Lengths = data.services.mapNotNull { it.serviceData?.size },
        )
        val overflowsLegacy = AdvertisementPayload.overflowsLegacy(estimatedBytes)
        val params = advertisementSet.advertisingSetParameters
        if (extendedSupported && !hasScanResponse && overflowsLegacy) {
            // Trame > 31 octets : on n'a pas le choix, advertising étendu (certaines cibles
            // ne la recevront pas, mais le legacy la rejetterait avec DATA_TOO_LARGE).
            Log.d(_logTag, "Payload ~$estimatedBytes B > $LEGACY_MAX — extended advertising (may be invisible to legacy-only scanners)")
            params.legacyMode = false
            params.connectable = advertisementSet.advertiseSettings.connectable
            params.scanable = false
        } else {
            // Trame ≤ 31 octets : LEGACY, pour qu'elle soit réellement reçue par les cibles.
            params.legacyMode = true
            params.connectable = advertisementSet.advertiseSettings.connectable
            params.scanable = hasScanResponse
        }
        advertisementSet.advertisingSetCallback = getAdvertisingSetCallback()
        return advertisementSet
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    override fun startAdvertisement(advertisementSet: AdvertisementSet) {
        if (_advertiser != null) {
            if (advertisementSet.validate()) {
                val preparedAdvertisementSet = prepareAdvertisementSet(advertisementSet)
                if (preparedAdvertisementSet.scanResponse != null) {
                    _advertiser!!.startAdvertisingSet(
                        preparedAdvertisementSet.advertisingSetParameters.build(),
                        preparedAdvertisementSet.advertiseData.build(),
                        preparedAdvertisementSet.scanResponse!!.build(),
                        null, null, preparedAdvertisementSet.advertisingSetCallback
                    )
                } else {
                    _advertiser!!.startAdvertisingSet(
                        preparedAdvertisementSet.advertisingSetParameters.build(),
                        preparedAdvertisementSet.advertiseData.build(),
                        null, null, null, preparedAdvertisementSet.advertisingSetCallback
                    )
                }
                Log.d(_logTag, "Started Modern Advertisement")
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

    @RequiresApi(Build.VERSION_CODES.O)
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    override fun stopAdvertisement() {
        if (_advertiser != null) {
            if (_currentAdvertisementSet != null) {
                _advertiser!!.stopAdvertisingSet(_currentAdvertisementSet!!.advertisingSetCallback)
                _currentAdvertisementSet = null
                _runningSet = null
            } else {
                Log.d(_logTag, "Current Modern Advertising Set is null")
            }
        } else {
            Log.d(_logTag, "Advertiser is null")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    override fun updateAdvertisement(advertisementSet: AdvertisementSet): Boolean {
        val running = _runningSet ?: return false
        val data = advertisementSet.advertiseData.build() ?: return false
        return try {
            running.setAdvertisingData(data)
            _currentAdvertisementSet?.let { advertisementSet.advertisingSetCallback = it.advertisingSetCallback }
            true
        } catch (e: Exception) {
            Log.d(_logTag, "updateAdvertisement failed: ${e.message}")
            false
        }
    }

    override fun setTxPowerLevel(txPowerLevel: TxPowerLevel) {
        _txPowerLevel = txPowerLevel
    }

    override fun getTxPowerLevel(): TxPowerLevel {
        return _txPowerLevel ?: TxPowerLevel.TX_POWER_HIGH
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
        return false
    }

    // getName() peut lever SecurityException sur API 31+ sans BLUETOOTH_CONNECT.
    private fun safeAdapterNameLength(): Int = try {
        _bluetoothAdapter?.name?.length ?: 0
    } catch (e: SecurityException) {
        0
    }

    private fun getAdvertisingSetCallback(): AdvertisingSetCallback {
        return object : AdvertisingSetCallback() {
            override fun onAdvertisingSetStarted(advertisingSet: AdvertisingSet?, txPower: Int, status: Int) {
                if (status == ADVERTISE_SUCCESS) {
                    _runningSet = advertisingSet
                    _advertisementServiceCallbacks.map {
                        it.onAdvertisementSetSucceeded(_currentAdvertisementSet)
                    }
                } else {
                    val advertisementError = when (status) {
                        ADVERTISE_FAILED_ALREADY_STARTED -> AdvertisementError.ADVERTISE_FAILED_ALREADY_STARTED
                        ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> AdvertisementError.ADVERTISE_FAILED_FEATURE_UNSUPPORTED
                        ADVERTISE_FAILED_INTERNAL_ERROR -> AdvertisementError.ADVERTISE_FAILED_INTERNAL_ERROR
                        ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> AdvertisementError.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS
                        ADVERTISE_FAILED_DATA_TOO_LARGE -> AdvertisementError.ADVERTISE_FAILED_DATA_TOO_LARGE
                        else -> AdvertisementError.ADVERTISE_FAILED_UNKNOWN
                    }
                    _advertisementServiceCallbacks.map {
                        it.onAdvertisementSetFailed(_currentAdvertisementSet, advertisementError)
                    }
                }
            }
            override fun onAdvertisingDataSet(advertisingSet: AdvertisingSet, status: Int) {
                // Le contrôleur peut refuser une mise à jour (ex. données > 31 octets sur un
                // set legacy) : on le remonte au lieu de laisser croire à un envoi réussi.
                if (status != ADVERTISE_SUCCESS) {
                    val err = if (status == ADVERTISE_FAILED_DATA_TOO_LARGE)
                        AdvertisementError.ADVERTISE_FAILED_DATA_TOO_LARGE
                    else AdvertisementError.ADVERTISE_FAILED_UNKNOWN
                    _advertisementServiceCallbacks.map { it.onAdvertisementSetFailed(_currentAdvertisementSet, err) }
                }
            }
            override fun onScanResponseDataSet(advertisingSet: AdvertisingSet, status: Int) {}
            override fun onAdvertisingSetStopped(advertisingSet: AdvertisingSet) {
                _runningSet = null
                _advertisementServiceCallbacks.map {
                    it.onAdvertisementSetStop(_currentAdvertisementSet)
                }
            }
        }
    }
}