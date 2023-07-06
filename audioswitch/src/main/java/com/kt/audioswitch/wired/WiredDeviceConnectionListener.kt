package com.kt.audioswitch.wired

internal interface WiredDeviceConnectionListener {
    fun onDeviceConnected()
    fun onDeviceDisconnected()
}
