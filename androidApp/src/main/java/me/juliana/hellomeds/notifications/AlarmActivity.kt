// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.notifications

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.juliana.hellomeds.data.preferences.AppearancePreferences
import me.juliana.hellomeds.data.util.AppLogger
import me.juliana.hellomeds.ui.features.alarm.ReminderAlarmScreen
import me.juliana.hellomeds.ui.theme.HelloMedsTheme
import org.koin.android.ext.android.inject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Full-screen alarm activity that shows over the lock screen and turns on the screen.
 *
 * Launched by Full Screen Intent (FSI) when an alarm-type medication reminder fires.
 * Provides Take/Skip/Snooze actions that delegate to [NotificationActionReceiver].
 *
 * Uses singleInstance launch mode so only one instance exists at a time.
 * [onNewIntent] refreshes the UI when a new alarm fires while this activity is already showing.
 *
 * Audio + vibration are handled here (not in the notification channel) to enable looping
 * and to avoid the "echo bug" where both the channel sound and MediaPlayer play simultaneously.
 */
class AlarmActivity : ComponentActivity() {

    private var scheduleIds by mutableStateOf(intArrayOf())
    private var scheduledTime by mutableStateOf(0L)
    private var medicationNames by mutableStateOf(emptyList<String>())
    private var notificationId by mutableStateOf(-1)
    private var discreet by mutableStateOf(false)

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var volumeRampJob: Job? = null

    private companion object {
        // Start very quiet and ramp to full over a few seconds so the alarm doesn't
        // jolt the user. The MediaPlayer scalar (0.0–1.0) multiplies whatever the
        // user's system alarm volume is set to.
        const val RAMP_START_VOLUME = 0.05f
        const val RAMP_TARGET_VOLUME = 1.0f
        const val RAMP_DURATION_MS = 10_000L
        const val RAMP_TICK_MS = 100L
    }

    private val appearancePrefs: AppearancePreferences by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // CRITICAL: These flags allow the alarm to show over the lock screen and wake the device.
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Screen privacy (FLAG_SECURE) is applied asynchronously off the main thread.
        // Race window is acceptable: alarm UI rendering is the critical path; the flag
        // takes effect a frame or two later if the user has opted in.
        lifecycleScope.launch {
            appearancePrefs.screenPrivacy.collect { enabled ->
                if (enabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }

        readIntentExtras(intent)
        startAlarmFeedback()

        setContent {
            HelloMedsTheme {
                val timeText = if (scheduledTime > 0) {
                    LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(scheduledTime),
                        ZoneId.systemDefault(),
                    ).format(DateTimeFormatter.ofPattern("HH:mm"))
                } else {
                    ""
                }

                ReminderAlarmScreen(
                    medicationNames = medicationNames,
                    timeText = timeText,
                    discreet = discreet,
                    onTaken = { stopAndPerformAction("TAKEN") },
                    onSkipped = { stopAndPerformAction("SKIPPED") },
                    onSnooze = { stopAndPerformAction("SNOOZED") },
                    onDismiss = {
                        stopAlarmFeedback()
                        finish()
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntentExtras(intent)
        startAlarmFeedback() // Restart for the new alarm
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarmFeedback() // Safety net
    }

    private fun readIntentExtras(intent: Intent) {
        scheduleIds = intent.getIntArrayExtra("scheduleIds") ?: intArrayOf()
        scheduledTime = intent.getLongExtra("scheduledTime", 0L)
        medicationNames = (intent.getStringArrayExtra("medicationNames") ?: arrayOf()).toList()
        notificationId = intent.getIntExtra("notificationId", -1)
        val visibility = intent.getStringExtra("lockScreenVisibility")
        discreet = visibility != "SHOW_WITH_NAMES"
    }

    private fun stopAndPerformAction(action: String) {
        stopAlarmFeedback()
        performAction(action)
        finish()
    }

    private fun performAction(action: String) {
        val intent = Intent(this, NotificationActionReceiver::class.java).apply {
            this.action = "$packageName.NOTIFICATION_ACTION"
            putExtra("action", action)
            putExtra("scheduleIds", scheduleIds)
            putExtra("scheduledTime", scheduledTime)
            putExtra("notificationId", notificationId)
        }
        sendBroadcast(intent)
    }

    private fun startAlarmFeedback() {
        stopAlarmFeedback()

        // Looping audio via MediaPlayer using USAGE_ALARM (respects alarm volume, plays through DnD).
        // Uses prepareAsync() so the main thread is not blocked while the codec resolves the
        // alarm URI — synchronous prepare() can stall 1–3s on cold devices and delay the alarm UI.
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmActivity, Settings.System.DEFAULT_ALARM_ALERT_URI)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                isLooping = true
                setVolume(RAMP_START_VOLUME, RAMP_START_VOLUME)
                setOnPreparedListener { mp ->
                    try {
                        mp.setVolume(RAMP_START_VOLUME, RAMP_START_VOLUME)
                        mp.start()
                        startVolumeRamp(mp)
                    } catch (e: Exception) {
                        AppLogger.e("AlarmActivity", "Failed to start prepared alarm audio", e)
                    }
                }
                setOnErrorListener { _, what, extra ->
                    AppLogger.e("AlarmActivity", "MediaPlayer error: what=$what extra=$extra")
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            AppLogger.e("AlarmActivity", "Failed to start alarm audio", e)
        }

        // Looping vibration
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        // Pattern: wait 0ms, vibrate 500ms, pause 500ms — repeat from index 0
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0))
    }

    private fun startVolumeRamp(player: MediaPlayer) {
        volumeRampJob?.cancel()
        volumeRampJob = lifecycleScope.launch {
            val start = RAMP_START_VOLUME
            val target = RAMP_TARGET_VOLUME
            val totalSteps = (RAMP_DURATION_MS / RAMP_TICK_MS).toInt()
            for (step in 1..totalSteps) {
                delay(RAMP_TICK_MS)
                if (!isActive) return@launch
                val volume = start + (target - start) * (step.toFloat() / totalSteps)
                try {
                    player.setVolume(volume, volume)
                } catch (e: IllegalStateException) {
                    // MediaPlayer was released between the isActive check and setVolume.
                    return@launch
                }
            }
        }
    }

    private fun stopAlarmFeedback() {
        volumeRampJob?.cancel()
        volumeRampJob = null
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {
            AppLogger.w("AlarmActivity", "Failed to stop alarm audio: ${e.message}")
        }
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null
    }
}
