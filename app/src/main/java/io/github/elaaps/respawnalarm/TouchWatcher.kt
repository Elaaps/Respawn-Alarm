package io.github.elaaps.respawnalarm

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

// Games drawn with OpenGL send no accessibility events: a 1×1 px overlay with
// FLAG_WATCH_OUTSIDE_TOUCH gets ACTION_OUTSIDE, without coordinates, for every touch.
class TouchWatcher(context: Context) {
    private val windowContext = context.createWindowContext(
        context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        null,
    )
    private val windowManager = windowContext.getSystemService(WindowManager::class.java)
    private var view: View? = null

    @Volatile
    var lastTouchAt: Long? = null
        private set

    @Volatile
    var touchCount = 0
        private set

    @SuppressLint("ClickableViewAccessibility")
    fun start(): Boolean {
        if (view != null) return true
        if (!Settings.canDrawOverlays(windowContext)) return false

        val overlay = View(windowContext)
        overlay.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE || event.actionMasked == MotionEvent.ACTION_DOWN) {
                lastTouchAt = SystemClock.elapsedRealtime()
                touchCount++
            }
            false
        }
        val params = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "RespawnAlarmTouchWatcher"
        }
        return try {
            windowManager.addView(overlay, params)
            view = overlay
            true
        } catch (_: WindowManager.BadTokenException) {
            false
        } catch (_: WindowManager.InvalidDisplayException) {
            false
        }
    }

    fun stop() {
        view?.let {
            ignoring<IllegalArgumentException> { windowManager.removeViewImmediate(it) }
        }
        view = null
    }
}
