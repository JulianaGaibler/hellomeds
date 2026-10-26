// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package me.juliana.hellomeds.notifications

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.juliana.hellomeds.data.util.AppLogger
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.AudioToolbox.kSystemSoundID_Vibrate
import platform.Foundation.NSBundle

private var audioPlayer: AVAudioPlayer? = null
private var vibrationJob: Job? = null
private var volumeRampJob: Job? = null
private const val TAG = "IOSAlarmAudio"

/**
 * Starts looping alarm audio + vibration.
 * Uses AVAudioPlayer for looping the bundled alarm sound file,
 * and AudioToolbox for looping vibration (iOS does not vibrate automatically with audio).
 *
 * Call [stopAlarmSound] to stop both audio and vibration.
 */
fun startAlarmSound() {
    stopAlarmSound()

    val session = AVAudioSession.sharedInstance()
    session.setCategory(AVAudioSessionCategoryPlayback, error = null)
    session.setActive(true, error = null)

    val url = NSBundle.mainBundle.URLForResource("alarm_sound", withExtension = "caf")
    if (url != null) {
        audioPlayer = AVAudioPlayer(contentsOfURL = url, error = null)?.apply {
            numberOfLoops = -1 // Infinite loop
            // The AVAudioPlayer scalar multiplies the system's media volume.
            volume = AlarmVolumeRamp.START_VOLUME
            prepareToPlay()
            play()
        }
        audioPlayer?.let { startVolumeRamp(it) }
        AppLogger.d(TAG, "Alarm audio started (looping, fading in)")
    } else {
        AppLogger.w(TAG, "alarm_sound.caf not found in bundle, no audio will play")
    }

    // Loop vibration every 1.5 seconds (iOS doesn't vibrate with audio playback)
    vibrationJob = CoroutineScope(Dispatchers.Main).launch {
        while (isActive) {
            AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
            delay(1500L)
        }
    }
}

// AVAudioPlayer's native fade is linear, which sounds loud almost immediately,
// so step through the shared perceptual curve instead.
private fun startVolumeRamp(player: AVAudioPlayer) {
    volumeRampJob?.cancel()
    volumeRampJob = CoroutineScope(Dispatchers.Main).launch {
        val totalSteps = (AlarmVolumeRamp.DURATION_MS / AlarmVolumeRamp.TICK_MS).toInt()
        for (step in 1..totalSteps) {
            delay(AlarmVolumeRamp.TICK_MS)
            if (!isActive) return@launch
            player.volume = AlarmVolumeRamp.volumeAt(step * AlarmVolumeRamp.TICK_MS)
        }
    }
}

/**
 * Stops looping alarm audio and vibration.
 * Notifies other apps (e.g., Spotify, podcasts) that they can resume playback.
 */
fun stopAlarmSound() {
    volumeRampJob?.cancel()
    volumeRampJob = null
    audioPlayer?.stop()
    audioPlayer = null
    vibrationJob?.cancel()
    vibrationJob = null
    // Notify other apps they can resume (polite audio session deactivation)
    AVAudioSession.sharedInstance().setActive(
        false,
        withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation,
        error = null,
    )
    AppLogger.d(TAG, "Alarm audio stopped")
}
