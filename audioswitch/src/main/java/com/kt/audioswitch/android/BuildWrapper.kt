package com.kt.audioswitch.android

import android.os.Build

internal class BuildWrapper {

    fun getVersion(): Int = Build.VERSION.SDK_INT
}
