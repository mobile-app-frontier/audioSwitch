package com.kt.audioswitch.android

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES
import android.bluetooth.BluetoothDevice
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class BluetoothDeviceWrapperImplTest {

    @Test
    fun `name should return a generic bluetooth device name if none was returned from the BluetoothDevice class`() {
        val device = BluetoothDeviceWrapperImpl(mock())

        assertThat(device.name, equalTo(DEFAULT_DEVICE_NAME))
    }

    @Test
    fun `name should return a the BluetoothDevice name`() {
        val bluetoothDevice = mock<BluetoothDevice> {
            whenever(mock.name).thenReturn("Some Device Name")
        }
        val deviceWrapper = BluetoothDeviceWrapperImpl(bluetoothDevice)

        assertThat(deviceWrapper.name, equalTo("Some Device Name"))
    }

    @Test
    fun `deviceClass should return null if the BluetoothDevice device class is null`() {
        val bluetoothDevice = mock<BluetoothDevice>()
        val deviceWrapper = BluetoothDeviceWrapperImpl(bluetoothDevice)

        assertThat(deviceWrapper.deviceClass, `is`(nullValue()))
    }

    @Test
    fun `deviceClass should return bluetooth class from the BluetoothDevice device class`() {
        val deviceClass = mock<BluetoothClass> {
            whenever(mock.deviceClass).thenReturn(AUDIO_VIDEO_HEADPHONES)
        }
        val bluetoothDevice = mock<BluetoothDevice> {
            whenever(mock.bluetoothClass).thenReturn(deviceClass)
        }
        val deviceWrapper = BluetoothDeviceWrapperImpl(bluetoothDevice)

        assertThat(deviceWrapper.deviceClass, equalTo(AUDIO_VIDEO_HEADPHONES))
    }
}
