package io.github.elaaps.respawnalarm

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView

fun overlayText(phase: Session.Phase, remainingMs: Long): String = when (phase) {
    Session.Phase.RINGING, Session.Phase.LOADING, Session.Phase.SNOOZED -> SLEEPING
    Session.Phase.PLAYING -> formatDuration(remainingMs)
    Session.Phase.DONE -> DONE
}

private const val SLEEPING = "💤"
private const val DONE = "👍"

class CountdownOverlay(context: Context, private val store: AlarmStore) {
    private val windowContext = context.createWindowContext(
        context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        null,
    )
    private val windowManager = windowContext.getSystemService(WindowManager::class.java)
    private var view: TextView? = null

    val isShown: Boolean get() = view != null

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = "RespawnAlarmCountdown"
        val (savedX, savedY) = store.overlayPosition() ?: (dp(DEFAULT_X_DP) to dp(DEFAULT_Y_DP))
        x = savedX
        y = savedY
    }

    fun show(text: String): Boolean {
        val current = view ?: add() ?: return false
        if (current.text != text) current.text = text
        return true
    }

    fun hide() {
        view?.let {
            ignoring<IllegalArgumentException> { windowManager.removeViewImmediate(it) }
        }
        view = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun add(): TextView? {
        if (!Settings.canDrawOverlays(windowContext)) return null
        val pill = TextView(windowContext).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP)
            setPadding(dp(PADDING_H_DP), dp(PADDING_V_DP), dp(PADDING_H_DP), dp(PADDING_V_DP))
            background = GradientDrawable().apply {
                cornerRadius = dp(CORNER_DP).toFloat()
                setColor(BACKGROUND)
            }
            setOnTouchListener(DragListener())
        }
        return try {
            windowManager.addView(pill, params)
            view = pill
            pill
        } catch (_: WindowManager.BadTokenException) {
            null
        } catch (_: WindowManager.InvalidDisplayException) {
            null
        }
    }

    private inner class DragListener : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var downRawX = 0f
        private var downRawY = 0f

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - downRawX).toInt()
                    params.y = startY + (event.rawY - downRawY).toInt()
                    ignoring<IllegalArgumentException> { windowManager.updateViewLayout(view, params) }
                }
                MotionEvent.ACTION_UP -> store.saveOverlayPosition(params.x, params.y)
            }
            return true
        }
    }

    private fun dp(value: Int): Int = (value * windowContext.resources.displayMetrics.density).toInt()

    private companion object {
        const val DEFAULT_X_DP = 16
        const val DEFAULT_Y_DP = 96
        const val TEXT_SP = 18f
        const val PADDING_H_DP = 12
        const val PADDING_V_DP = 6
        const val CORNER_DP = 16
        const val BACKGROUND = 0x99000000.toInt()
    }
}
