package com.kt.audioswitch.android

import android.os.SystemClock

internal class SystemClockWrapper {

    fun elapsedRealtime() = SystemClock.elapsedRealtime()
}
