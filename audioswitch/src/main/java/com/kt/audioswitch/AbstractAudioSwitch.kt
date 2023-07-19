package com.kt.audioswitch

import android.content.Context
import android.media.AudioManager
import androidx.annotation.VisibleForTesting
import com.kt.audioswitch.AbstractAudioSwitch.State.*
import com.kt.audioswitch.AudioDevice.*
import com.kt.audioswitch.android.Logger
import com.kt.audioswitch.android.ProductionLogger
import com.kt.audioswitch.comparators.AudioDevicePriorityComparator
import com.kt.audioswitch.scanners.Scanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*
import java.util.concurrent.ConcurrentSkipListSet

internal const val TAG_AUDIO_SWITCH = "AudioSwitch"

abstract class AbstractAudioSwitch
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE) internal constructor(
        context: Context,
        scanner: Scanner,
        loggingEnabled: Boolean = true,
        internal var logger: Logger = ProductionLogger(loggingEnabled),
        preferredDeviceList: List<Class<out AudioDevice>>,
) : Scanner.Listener {

    companion object {
        const val VERSION = BuildConfig.VERSION_NAME

        internal val defaultPreferredDeviceList by lazy {
            listOf(
                BluetoothHeadset::class.java,
                WiredHeadset::class.java,
                Earpiece::class.java,
                Speakerphone::class.java,
            )
        }
    }

    protected var _audioDeviceChangeFlow = MutableStateFlow(AudioDeviceChange())

    internal val audioDeviceManager: AudioDeviceManager = AudioDeviceManager(
        context,
        logger,
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager,
        audioChangedFlow = _audioDeviceChangeFlow
    )

    internal var state: State = STOPPED

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal val deviceScanner: Scanner = scanner

    private val preferredDeviceList: List<Class<out AudioDevice>>

    protected var userSelectedAudioDevice: AudioDevice? = null

    internal enum class State {
        STARTED, ACTIVATED, STOPPED
    }

    var loggingEnabled: Boolean
        get() = logger.loggingEnabled
        set(value) {
            logger.loggingEnabled = value
        }
    var selectedAudioDevice: AudioDevice? = null
        private set

    val availableUniqueAudioDevices: SortedSet<AudioDevice>

    val availableAudioDevices: List<AudioDevice>
        get() = this.availableUniqueAudioDevices.toList()

    init {
        this.preferredDeviceList = getPreferredDeviceList(preferredDeviceList)
        this.availableUniqueAudioDevices = ConcurrentSkipListSet(AudioDevicePriorityComparator(this.preferredDeviceList))

        logger.d(TAG_AUDIO_SWITCH, "AudioSwitch($VERSION)")
        logger.d(TAG_AUDIO_SWITCH, "Preferred device list = ${this.preferredDeviceList.map { it.simpleName }}")
    }

    private fun getPreferredDeviceList(preferredDeviceList: List<Class<out AudioDevice>>): List<Class<out AudioDevice>> {
        require(hasNoDuplicates(preferredDeviceList))

        return if (preferredDeviceList.isEmpty() || preferredDeviceList == defaultPreferredDeviceList) {
            defaultPreferredDeviceList
        } else {
            val result = defaultPreferredDeviceList.toMutableList()
            result.removeAll(preferredDeviceList)
            preferredDeviceList.forEachIndexed { index, device ->
                result.add(index, device)
            }
            result
        }
    }

    override fun onDeviceConnected(audioDevice: AudioDevice) {
        this.logger.d(TAG_AUDIO_SWITCH, "onDeviceConnected($audioDevice)")
        if (audioDevice is Earpiece && this.availableAudioDevices.contains(WiredHeadset())) {
            return
        }
        val wasAdded = this.availableUniqueAudioDevices.add(audioDevice)
        if (audioDevice is WiredHeadset) {
            this.availableUniqueAudioDevices.removeAll { it is Earpiece }
        }
        this.selectAudioDevice(wasListChanged = wasAdded)
    }

    /**
     * Starts listening for audio device changes and calls the [listener] upon each change.
     * **Note:** When audio device listening is no longer needed, [AudioSwitch.stop] should be
     * called in order to prevent a memory leak.
     */
    fun start() {
        when (state) {
            STOPPED -> {
                this.deviceScanner.start(this)
                state = STARTED
            }

            else -> {
                logger.d(TAG_AUDIO_SWITCH, "Redundant start() invocation while already in the started or activated state")
            }
        }
    }

    /**
     * Stops listening for audio device changes if [AudioSwitch.start] has already been
     * invoked. [AudioSwitch.deactivate] will also get called if a device has been activated
     * with [AudioSwitch.activate].
     */
    fun stop() {
        when (state) {
            ACTIVATED -> {
                deactivate()
                closeListeners()
            }

            STARTED -> {
                closeListeners()
            }

            STOPPED -> {
                logger.d(TAG_AUDIO_SWITCH, "Redundant stop() invocation while already in the stopped state")
            }
        }
    }

    /**
     * Performs audio routing and unmuting on the selected device from
     * [AudioSwitch.selectDevice]. Audio focus is also acquired for the client application.
     * **Note:** [AudioSwitch.deactivate] should be invoked to restore the prior audio
     * state.
     */
    fun activate() {
        when (state) {
            STARTED -> {
                audioDeviceManager.cacheAudioState()

                // Always set mute to false for WebRTC
                audioDeviceManager.mute(false)
                audioDeviceManager.setAudioFocus()
                selectedAudioDevice?.let { this.onActivate(it) }
                state = ACTIVATED
            }

            ACTIVATED -> selectedAudioDevice?.let { this.onActivate(it) }
            STOPPED -> throw IllegalStateException()
        }
    }

    /**
     * Restores the audio state prior to calling [AudioSwitch.activate] and removes
     * audio focus from the client application.
     */
    fun deactivate() {
        when (state) {
            ACTIVATED -> {
                this.onDeactivate()
                // Restore stored audio state
                audioDeviceManager.restoreAudioState()
                state = STARTED
            }

            STARTED, STOPPED -> {
            }
        }
    }

    /**
     * Selects the desired [audioDevice]. If the provided [AudioDevice] is not
     * available, no changes are made. If the provided device is null, one is chosen based on the
     * specified preferred device list or the following default list:
     * [BluetoothHeadset], [WiredHeadset], [Earpiece], [Speakerphone].
     */
    fun selectDevice(audioDevice: AudioDevice?) {
        logger.d(TAG_AUDIO_SWITCH, "Selected AudioDevice = $audioDevice")
        userSelectedAudioDevice = audioDevice

        this.selectAudioDevice(wasListChanged = false, audioDevice = audioDevice)
    }

    protected fun selectAudioDevice(wasListChanged: Boolean, audioDevice: AudioDevice? = this.getBestDevice()) {
        if (selectedAudioDevice == audioDevice) {
            if (wasListChanged) {
                _audioDeviceChangeFlow.value = _audioDeviceChangeFlow.value.copy(
                    audioDevices = availableUniqueAudioDevices.toList(),
                    selectedAudioDevice = selectedAudioDevice
                )
            }
            return
        }

        // Select the audio device
        logger.d(TAG_AUDIO_SWITCH, "Current user selected AudioDevice = $userSelectedAudioDevice")
        selectedAudioDevice = audioDevice

        // Activate the device if in the active state
        if (state == ACTIVATED) {
            activate()
        }
        // trigger audio device change listener if there has been a change

        _audioDeviceChangeFlow.value = _audioDeviceChangeFlow.value.copy(
            audioDevices = availableUniqueAudioDevices.toList(),
            selectedAudioDevice = selectedAudioDevice
        )
    }

    private fun getBestDevice(): AudioDevice? {
        val userSelectedAudioDevice = userSelectedAudioDevice
        return if (userSelectedAudioDevice != null && this.deviceScanner.isDeviceActive(userSelectedAudioDevice)) {
            userSelectedAudioDevice
        } else {
            this.availableUniqueAudioDevices.firstOrNull {
                this.deviceScanner.isDeviceActive(it)
            }
        }
    }

    private fun hasNoDuplicates(list: List<Class<out AudioDevice>>) =
        list.groupingBy { it }.eachCount().filter { it.value > 1 }.isEmpty()

    private fun closeListeners() {
        this.deviceScanner.stop()
        state = STOPPED
    }

    protected abstract fun onActivate(audioDevice: AudioDevice)
    protected abstract fun onDeactivate()
}