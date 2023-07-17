package com.kt.audioswitch

import android.media.AudioManager

// AudioDevice의 정보가 변경 되었을 때 전달되는 data class
data class AudioDeviceChange(
    // 현재 선택 가능한 device의 list 항목
    val audioDevices: List<AudioDevice> = emptyList(),

    // 현재 선택된 device의 정보 전달
    val selectedAudioDevice: AudioDevice? = null,

    // AudioManager.class내에 AUDIOFOCUS value의 description 참고
    val audioFocus: Int = 0
) {
    override fun toString() : String {
        val focusString =
            when(audioFocus) {
                AudioManager.AUDIOFOCUS_GAIN -> "AUDIOFOCUS_GAIN"
                AudioManager.AUDIOFOCUS_LOSS -> "AUDIOFOCUS_LOSS"
                AudioManager.AUDIOFOCUS_NONE -> "AUDIOFOCUS_NONE"
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> "AUDIOFOCUS_GAIN_TRANSIENT"
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE -> "AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE"
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK"
                else -> "UNKNOWN_FOCUS_TYPE"
            }

        return "$focusString ${selectedAudioDevice?.name} ${audioDevices.map { it.name }}"
    }
}