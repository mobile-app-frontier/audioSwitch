package com.kt.audioswitch

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import com.kt.audioswitch.AudioDevice.BluetoothHeadset
import com.kt.audioswitch.AudioDevice.Earpiece
import com.kt.audioswitch.AudioDevice.Speakerphone
import com.kt.audioswitch.AudioDevice.WiredHeadset
import com.kt.audioswitch.android.Logger
import com.kt.audioswitch.android.ProductionLogger
import com.kt.audioswitch.scanners.AudioDeviceScanner
import com.kt.audioswitch.scanners.Scanner
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.*

private const val TAG = "AudioSwitch"

@RequiresApi(Build.VERSION_CODES.M)
class AudioSwitch : AbstractAudioSwitch {
    @JvmOverloads
    constructor(
        context: Context,
        loggingEnabled: Boolean = false,
        audioChangedFlow: MutableStateFlow<AudioDeviceChange>,
        preferredDeviceList: List<Class<out AudioDevice>> = defaultPreferredDeviceList
    ) : this(
        context, audioChangedFlow, ProductionLogger(loggingEnabled), preferredDeviceList
    )

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal constructor(
        context: Context,
        audioChangedFlow: MutableStateFlow<AudioDeviceChange>,
        logger: Logger,
        preferredDeviceList: List<Class<out AudioDevice>>,
        audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager,
        audioDeviceManager: AudioDeviceManager = AudioDeviceManager(
            context = context,
            logger = logger,
            audioManager = audioManager,
            audioChangedFlow = audioChangedFlow
        ),
        handler: Handler = Handler(Looper.getMainLooper()),
        scanner: Scanner = AudioDeviceScanner(audioManager, handler),
    ) : super(
        context = context,
        audioChangedFlow = audioChangedFlow,
        scanner = scanner,
        logger = logger,
        preferredDeviceList = preferredDeviceList,
        audioDeviceManager = audioDeviceManager,
    )

    override fun onDeviceDisconnected(audioDevice: AudioDevice) {
        this.logger.d(TAG_AUDIO_SWITCH, "onDeviceDisconnected($audioDevice)")
        val wasChanged = this.availableUniqueAudioDevices.remove(audioDevice)
        if (this.userSelectedAudioDevice == audioDevice) {
            this.userSelectedAudioDevice = null
        }

        if (audioDevice is WiredHeadset && this.audioDeviceManager.hasEarpiece() && wasChanged) {
            this.availableUniqueAudioDevices.add(Earpiece())
        }
        this.selectAudioDevice(wasChanged)
    }

    override fun onActivate(audioDevice: AudioDevice) {
        this.logger.d(TAG_AUDIO_SWITCH, "onActivate($audioDevice)")
        when (audioDevice) {
            is BluetoothHeadset -> {
                this.audioDeviceManager.enableSpeakerphone(false)
                this.audioDeviceManager.enableBluetoothSco(true)
            }
            is Earpiece, is WiredHeadset -> {
                this.audioDeviceManager.enableSpeakerphone(false)
                this.audioDeviceManager.enableBluetoothSco(false)
            }
            is Speakerphone -> {
                this.audioDeviceManager.enableBluetoothSco(false)
                this.audioDeviceManager.enableSpeakerphone(true)
            }
        }
    }

    override fun onDeactivate() {
        this.logger.d(TAG_AUDIO_SWITCH, "onDeactivate")
    }
}
