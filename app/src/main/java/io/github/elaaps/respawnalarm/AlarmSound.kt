package io.github.elaaps.respawnalarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.net.toUri
import java.io.IOException

class AlarmSound(private val context: Context, private val alarm: ResolvedAlarm) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var originalVolume: Int? = null
    private var startedAt = 0L

    private val fadeIn = object : Runnable {
        override fun run() {
            val level = crescendoLevel(SystemClock.elapsedRealtime() - startedAt)
            setPlayerVolume(level)
            if (level < 1f) handler.postDelayed(this, FADE_STEP_MS)
        }
    }

    var isPlaying = false
        private set

    fun play() {
        if (isPlaying) return
        if (alarm.volume == VolumeMode.MAX) forceVolume()
        val current = player ?: createPlayer()?.also { player = it }
        startedAt = SystemClock.elapsedRealtime()
        if (alarm.crescendo) {
            setPlayerVolume(0f)
            handler.post(fadeIn)
        }
        current?.let { startQuietly(it) }
        if (alarm.vibrate) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0),
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
            )
        }
        isPlaying = true
    }

    fun pause() {
        if (!isPlaying) return
        isPlaying = false
        handler.removeCallbacks(fadeIn)
        ignoring<IllegalStateException> { player?.pause() }
        vibrator.cancel()
    }

    fun release() {
        pause()
        player?.release()
        player = null
        originalVolume?.let { setAlarmVolume(it) }
        originalVolume = null
    }

    private fun forceVolume() {
        val stream = AudioManager.STREAM_ALARM
        if (originalVolume == null) originalVolume = audioManager.getStreamVolume(stream)
        setAlarmVolume(audioManager.getStreamMaxVolume(stream))
    }

    private fun setAlarmVolume(volume: Int) {
        ignoring<SecurityException> { audioManager.setStreamVolume(AudioManager.STREAM_ALARM, volume, 0) }
    }

    private fun setPlayerVolume(level: Float) {
        ignoring<IllegalStateException> { player?.setVolume(level, level) }
    }

    private fun startQuietly(player: MediaPlayer) {
        ignoring<IllegalStateException> { player.start() }
    }

    private fun createPlayer(): MediaPlayer? {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val sources = listOfNotNull(
            (alarm.tone as? Tone.Custom)?.uri?.toUri(),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            "android.resource://${context.packageName}/${R.raw.fallback_alarm}".toUri(),
        )
        return sources.firstNotNullOfOrNull { prepare(it, attributes) }
    }

    private fun prepare(uri: Uri, attributes: AudioAttributes): MediaPlayer? {
        val player = MediaPlayer()
        return try {
            player.setAudioAttributes(attributes)
            player.setDataSource(context, uri)
            player.isLooping = true
            player.prepare()
            player
        } catch (e: Exception) {
            if (e !is IOException && e !is IllegalArgumentException && e !is SecurityException) throw e
            player.release()
            null
        }
    }

    private companion object {
        const val FADE_STEP_MS = 1_000L
    }
}

const val CRESCENDO_MS = 30_000L

fun crescendoLevel(elapsedMs: Long): Float = (elapsedMs.toFloat() / CRESCENDO_MS).coerceIn(0f, 1f)
