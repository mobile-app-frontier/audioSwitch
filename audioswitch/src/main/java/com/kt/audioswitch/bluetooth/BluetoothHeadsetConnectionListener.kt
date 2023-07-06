package com.kt.audioswitch.bluetooth

internal interface BluetoothHeadsetConnectionListener {
    fun onBluetoothHeadsetStateChanged(headsetName: String? = null)
    fun onBluetoothHeadsetActivationError()
}
