package io.github.elaaps.respawnalarm

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.Service
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SessionState(val mode: SessionService.Mode, val snoozesLeft: Int)

class SessionService : Service() {
    enum class Mode { ALARM, DIAGNOSTIC }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var notifications: Notifications

    private var mode: Mode? = null
    private var alarm = GlobalSettings().resolveDefaults()
    private var session: Session? = null
    private var detector: ForegroundAppDetector? = null
    private var touchWatcher: TouchWatcher? = null
    private var overlayOk = false
    private var sound: AlarmSound? = null
    private var countdown: CountdownOverlay? = null
    private var completing = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var startedAt = 0L
    private var ringingSince: Instant = Instant.EPOCH
    private var lastNotifiedAt = 0L
    private var lastPhase: Session.Phase? = null
    private var everPlayed = false
    private var victory: Victory? = null
    private var wonAt = 0L
    private var awayTicks = 0
    private var victoryOnLeave = true

    private val tick = object : Runnable {
        override fun run() {
            onTick()
            if (mode != null) handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        notifications = Notifications(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_DIAGNOSTIC) {
            when (mode) {
                Mode.DIAGNOSTIC -> finish()
                null -> stopSelf(startId)
                Mode.ALARM -> Unit
            }
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_EMERGENCY_STOP) {
            when (mode) {
                Mode.ALARM -> {
                    if (!completing) {
                        notifications.emergencyStopped()
                        record(emergencyStop = true)
                    }
                    finish()
                }
                null -> stopSelf(startId)
                Mode.DIAGNOSTIC -> Unit
            }
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_VICTORY_SHOWN) {
            notifications.cancelDone()
            if (completing) finish() else if (mode == null) stopSelf(startId)
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_SNOOZE) {
            when (mode) {
                Mode.ALARM -> snooze()
                null -> stopSelf(startId)
                Mode.DIAGNOSTIC -> Unit
            }
            return if (mode == Mode.ALARM) START_STICKY else START_NOT_STICKY
        }
        if (intent == null && mode == null && !AlarmStore(this).alarmInProgress) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val requested = intent?.getStringExtra(EXTRA_MODE)?.let { Mode.valueOf(it) } ?: Mode.ALARM
        val alarmId = if (intent == null) {
            AlarmStore(this).ringingAlarmId
        } else {
            intent.getLongExtra(EXTRA_ALARM_ID, NO_ALARM).takeIf { it != NO_ALARM }
        }

        when {
            mode == Mode.ALARM -> startForeground(currentNotification())
            requested == Mode.DIAGNOSTIC && mode == Mode.DIAGNOSTIC -> startForeground(currentNotification())
            else -> {
                if (mode != null) teardown()
                begin(requested, alarmId)
            }
        }
        return if (mode == Mode.ALARM) START_STICKY else START_NOT_STICKY
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun begin(newMode: Mode, alarmId: Long?) {
        mode = newMode
        val store = AlarmStore(this)
        alarm = store.resolve(alarmId)
        if (newMode == Mode.ALARM) {
            store.ringingAlarmId = alarmId
            store.alarmInProgress = true
        }
        startedAt = SystemClock.elapsedRealtime()
        ringingSince = Instant.now()
        lastPhase = null
        everPlayed = false

        if (newMode == Mode.ALARM) {
            session = Session(
                durationMs = alarm.durationMs,
                inactivityMs = alarm.inactivityMs,
                snoozeMs = alarm.snoozeMs,
                maxSnoozes = alarm.snooze.allowedCount,
                loadingGraceMs = alarm.loadingGraceMs,
            )
        }
        publishState()
        try {
            startForeground(currentNotification())
        } catch (_: ForegroundServiceStartNotAllowedException) {
            teardown()
            notifications.interrupted(alarm.targetPackage)
            stopSelf()
            return
        }
        lastNotifiedAt = startedAt

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RespawnAlarm:session")
            .apply { acquire(WAKE_LOCK_TIMEOUT_MS) }
        detector = ForegroundAppDetector(this)
        touchWatcher = TouchWatcher(this).also { overlayOk = it.start() }
        if (newMode == Mode.ALARM) {
            val settings = store.settings()
            victoryOnLeave = settings.victoryOnLeave
            if (settings.overlayEnabled) countdown = CountdownOverlay(this, store, settings.overlayStyle, onKnockedOutTap = ::showVictory)
            sound = AlarmSound(this, alarm).also { it.play() }
            showRingingScreen()
        }

        handler.post(tick)
    }

    private fun onTick() {
        val now = SystemClock.elapsedRealtime()
        val foreground = detector?.poll()
        val watcher = touchWatcher ?: return
        if (!overlayOk) overlayOk = watcher.start()

        when (mode) {
            Mode.DIAGNOSTIC -> {
                val remaining = DIAGNOSTIC_MS - (now - startedAt)
                if (remaining <= 0) return finish()
                if (now - lastNotifiedAt >= NOTIFY_EVERY_MS) {
                    notifications.update(notifications.diagnostic(foreground, diagnosticTouchCount(), remaining))
                    lastNotifiedAt = now
                }
            }
            Mode.ALARM -> {
                if (completing) return watchVictory(now, foreground)
                val session = session ?: return
                val inTarget = foreground != null && foreground == alarm.targetPackage
                val phase = session.tick(now, inTarget, watcher.lastTouchAt)
                countdown?.let { if (inTarget) it.show(overlayState(phase, session.remainingMs, alarm.durationMs)) else it.hide() }
                when (phase) {
                    Session.Phase.RINGING -> sound?.play()
                    Session.Phase.LOADING, Session.Phase.PLAYING, Session.Phase.SNOOZED -> sound?.pause()
                    Session.Phase.DONE -> return complete()
                }
                if (phase == Session.Phase.PLAYING) everPlayed = true
                if (lastPhase == Session.Phase.SNOOZED && phase == Session.Phase.RINGING) showRingingScreen()
                if (phase != lastPhase) publishState()
                val countdownDue = phase == Session.Phase.PLAYING && now - lastNotifiedAt >= NOTIFY_EVERY_MS
                if (phase != lastPhase || countdownDue) {
                    notifications.update(alarmNotification(phase))
                    lastNotifiedAt = now
                }
                lastPhase = phase
            }
            null -> Unit
        }
    }

    private fun complete() {
        completing = true
        sound?.release()
        sound = null
        record(emergencyStop = false)
        AlarmStore(this).apply {
            ringingAlarmId = null
            alarmInProgress = false
        }
        val won = Victory(
            upAt = Instant.now(),
            durationSec = alarm.durationSec,
            resets = session?.resets ?: 0,
            snoozes = alarm.snooze.allowedCount - (session?.snoozesLeft ?: 0),
        )
        victory = won
        wonAt = SystemClock.elapsedRealtime()
        notifications.update(notifications.won(won))
        countdown?.let { if (it.isShown) it.show(OverlayState.KnockedOut) }
    }

    // After the win, the game is left alone: the victory screen waits until the player leaves it.
    // Closing the game can leave no app reported in the foreground, so that counts as away too.
    private fun watchVictory(now: Long, foreground: String?) {
        val won = victory ?: return finish()
        awayTicks = if (foreground == alarm.targetPackage) 0 else awayTicks + 1
        val leftGame = awayTicks >= AWAY_TICKS_BEFORE_VICTORY
        when {
            leftGame && victoryOnLeave -> showVictory()
            leftGame || now - wonAt >= VICTORY_WATCH_MS -> {
                finish()
                notifications.done(won)
            }
        }
    }

    private fun showVictory() {
        val won = victory ?: return
        notifications.done(won)
        val intent = VictoryActivity.intent(this, won).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ignoring<ActivityNotFoundException> { startActivity(intent) }
        finish()
    }

    private fun record(emergencyStop: Boolean) {
        val alarmId = alarm.alarmId ?: return
        val session = session ?: return
        val record = SessionRecord(
            alarmId = alarmId,
            startedAt = ringingSince,
            endedAt = Instant.now(),
            resets = session.resets,
            snoozes = alarm.snooze.allowedCount - session.snoozesLeft,
            emergencyStop = emergencyStop,
        )
        SessionLog(this).add(record)
    }

    private fun snooze() {
        val session = session?.takeIf { it.canSnooze } ?: return
        session.snooze(SystemClock.elapsedRealtime())
        sound?.pause()
        notifications.update(alarmNotification(session.phase))
        lastPhase = session.phase
        publishState()
    }

    // The full screen notification only opens it on the lock screen; the overlay permission,
    // with the touch overlay shown, lets a background app open it anywhere.
    private fun showRingingScreen() {
        val intent = Intent(this, RingingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ignoring<ActivityNotFoundException> { startActivity(intent) }
    }

    private fun publishState() {
        _state.value = mode?.let { SessionState(it, snoozesLeft()) }
    }

    private fun currentNotification(): Notification = when (mode) {
        Mode.DIAGNOSTIC -> notifications.diagnostic(
            detector?.foregroundPackage,
            diagnosticTouchCount(),
            DIAGNOSTIC_MS - (SystemClock.elapsedRealtime() - startedAt),
        )
        else -> alarmNotification(session?.phase ?: Session.Phase.RINGING)
    }

    private fun diagnosticTouchCount(): Int? {
        val watcher = touchWatcher ?: return 0
        return if (overlayOk) watcher.touchCount else null
    }

    private fun alarmNotification(phase: Session.Phase): Notification = when {
        phase == Session.Phase.SNOOZED -> notifications.snoozed(alarm.targetPackage, snoozeEnd())
        phase == Session.Phase.PLAYING -> notifications.playing(
            alarm.targetPackage,
            session?.remainingMs ?: 0,
            alarm.durationMs,
            alarm.inactivitySec,
        )
        everPlayed -> notifications.reset(alarm.targetPackage, snoozesLeft())
        else -> notifications.ringing(alarm.targetPackage, alarm.durationMs, snoozesLeft())
    }

    private fun snoozesLeft(): Int = session?.takeIf { it.canSnooze }?.snoozesLeft ?: 0

    private fun snoozeEnd(): LocalTime {
        val leftMs = (session?.snoozedUntil ?: 0) - SystemClock.elapsedRealtime()
        return LocalTime.now().plusNanos(leftMs.coerceAtLeast(0) * NANOS_PER_MILLI)
    }

    private fun startForeground(notification: Notification) {
        startForeground(Notifications.ID_SESSION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    private fun finish() {
        if (mode == Mode.ALARM) {
            AlarmStore(this).apply {
                ringingAlarmId = null
                alarmInProgress = false
            }
        }
        teardown()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun teardown() {
        handler.removeCallbacks(tick)
        completing = false
        victory = null
        awayTicks = 0
        countdown?.hide()
        countdown = null
        sound?.release()
        sound = null
        touchWatcher?.stop()
        touchWatcher = null
        overlayOk = false
        detector = null
        session = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        mode = null
        publishState()
    }

    companion object {
        const val ACTION_STOP_DIAGNOSTIC = "io.github.elaaps.respawnalarm.STOP_DIAGNOSTIC"
        const val ACTION_SNOOZE = "io.github.elaaps.respawnalarm.SNOOZE"
        const val ACTION_EMERGENCY_STOP = "io.github.elaaps.respawnalarm.EMERGENCY_STOP"
        const val ACTION_VICTORY_SHOWN = "io.github.elaaps.respawnalarm.VICTORY_SHOWN"
        private const val NANOS_PER_MILLI = 1_000_000L
        private const val VICTORY_WATCH_MS = 30 * 60 * 1000L
        private const val AWAY_TICKS_BEFORE_VICTORY = 3
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_ALARM_ID = "alarm_id"
        private const val NO_ALARM = -1L
        private const val TICK_MS = 500L
        private const val NOTIFY_EVERY_MS = 1_000L
        private const val DIAGNOSTIC_MS = 60_000L
        private const val WAKE_LOCK_TIMEOUT_MS = 3 * 60 * 60 * 1000L

        private val _state = MutableStateFlow<SessionState?>(null)

        val state: StateFlow<SessionState?> = _state.asStateFlow()

        fun start(context: Context, mode: Mode, alarmId: Long? = null) {
            val intent = Intent(context, SessionService::class.java).putExtra(EXTRA_MODE, mode.name)
            if (alarmId != null) intent.putExtra(EXTRA_ALARM_ID, alarmId)
            context.startForegroundService(intent)
        }
    }
}
