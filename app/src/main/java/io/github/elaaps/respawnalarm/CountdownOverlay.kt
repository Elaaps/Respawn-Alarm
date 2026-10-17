package io.github.elaaps.respawnalarm

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import kotlin.math.abs

class CountdownOverlay(
    context: Context,
    private val store: AlarmStore,
    private val style: OverlayStyle,
    private val onKnockedOutTap: () -> Unit,
) {
    private val windowContext = context.createWindowContext(
        context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        null,
    )
    private val windowManager = windowContext.getSystemService(WindowManager::class.java)
    private var view: StateView? = null

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

    fun show(state: OverlayState): Boolean {
        val current = view ?: add() ?: return false
        current.state = state
        return true
    }

    fun hide() {
        view?.let {
            ignoring<IllegalArgumentException> { windowManager.removeViewImmediate(it.view) }
        }
        view = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun add(): StateView? {
        if (!Settings.canDrawOverlays(windowContext)) return null
        val badge = when (style) {
            OverlayStyle.HEALTH_BAR -> BossBadgeView(windowContext)
            OverlayStyle.TIMER -> TimerPillView(windowContext)
        }
        badge.view.setOnTouchListener(DragListener())
        return try {
            windowManager.addView(badge.view, params)
            view = badge
            badge
        } catch (_: WindowManager.BadTokenException) {
            null
        } catch (_: WindowManager.InvalidDisplayException) {
            null
        }
    }

    private inner class DragListener : View.OnTouchListener {
        private val touchSlop = ViewConfiguration.get(windowContext).scaledTouchSlop
        private var startX = 0
        private var startY = 0
        private var downRawX = 0f
        private var downRawY = 0f
        private var dragged = false

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    dragged = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) dragged = true
                    params.x = startX + dx.toInt()
                    params.y = startY + dy.toInt()
                    ignoring<IllegalArgumentException> { windowManager.updateViewLayout(view, params) }
                }
                MotionEvent.ACTION_UP -> {
                    store.saveOverlayPosition(params.x, params.y)
                    if (!dragged && this@CountdownOverlay.view?.state == OverlayState.KnockedOut) onKnockedOutTap()
                }
            }
            return true
        }
    }

    private fun dp(value: Int): Int = (value * windowContext.resources.displayMetrics.density).toInt()

    private companion object {
        const val DEFAULT_X_DP = 16
        const val DEFAULT_Y_DP = 96
    }
}

private interface StateView {
    val view: View
    var state: OverlayState
}

private class TimerPillView(context: Context) : TextView(context), StateView {
    override val view: View get() = this
    private val viewLabel = context.getString(R.string.action_view)

    override var state: OverlayState = OverlayState.Asleep
        set(value) {
            field = value
            val label = when (value) {
                OverlayState.Asleep -> SLEEPING
                is OverlayState.Fighting -> formatDuration(value.remainingMs)
                OverlayState.KnockedOut -> "$KNOCKED_OUT $viewLabel"
            }
            if (text != label) text = label
        }

    init {
        val density = resources.displayMetrics.density
        setTextColor(android.graphics.Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP)
        setPadding((PADDING_H_DP * density).toInt(), (PADDING_V_DP * density).toInt(), (PADDING_H_DP * density).toInt(), (PADDING_V_DP * density).toInt())
        background = GradientDrawable().apply {
            cornerRadius = CORNER_DP * density
            setColor(BACKGROUND)
        }
        state = OverlayState.Asleep
    }

    private companion object {
        const val SLEEPING = "💤"
        const val KNOCKED_OUT = "👍"
        const val TEXT_SP = 18f
        const val PADDING_H_DP = 12f
        const val PADDING_V_DP = 6f
        const val CORNER_DP = 16f
        const val BACKGROUND = 0x99000000.toInt()
    }
}

private class BossBadgeView(context: Context) : View(context), StateView {
    override val view: View get() = this

    override var state: OverlayState = OverlayState.Asleep
        set(value) {
            if (field == value) return
            val resized = (field == OverlayState.KnockedOut) != (value == OverlayState.KnockedOut)
            field = value
            if (resized) requestLayout()
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val font = ResourcesCompat.getFont(context, R.font.jersey_10) ?: Typeface.DEFAULT_BOLD
    private val fill = Paint()
    private val labelPaint = textPaint(font, LABEL_SP)
    private val valuePaint = textPaint(font, VALUE_SP)
    private val koPaint = textPaint(font, KO_SP).apply { textAlign = Paint.Align.CENTER }
    private val hintPaint = textPaint(font, HINT_SP).apply { textAlign = Paint.Align.CENTER }
    private val boss = context.getString(R.string.overlay_boss)
    private val ko = context.getString(R.string.victory_ko)
    private val tapToSee = context.getString(R.string.overlay_tap_to_see).uppercase()

    private fun textPaint(typeface: Typeface, sizeSp: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, resources.displayMetrics)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (state == OverlayState.KnockedOut) {
            val width = hintPaint.measureText(tapToSee) + px(KO_PADDING_H_DP) * 2
            setMeasuredDimension(width.toInt(), px(KO_HEIGHT_DP).toInt())
        } else {
            setMeasuredDimension(px(BADGE_WIDTH_DP).toInt(), px(BADGE_HEIGHT_DP).toInt())
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (state == OverlayState.KnockedOut) drawKnockedOut(canvas) else drawFighting(canvas)
    }

    private fun drawFighting(canvas: Canvas) {
        val border = px(BORDER_DP)
        drawFrame(canvas, BossColors.Night.copy(alpha = BADGE_ALPHA), BossColors.Pale, border)
        val pad = px(PADDING_DP)
        val (value, health) = when (val current = state) {
            is OverlayState.Fighting -> formatDuration(current.remainingMs) to current.health
            else -> SLEEPING to 1f
        }
        labelPaint.color = BossColors.Pale.toArgb()
        val textBaseline = pad + valuePaint.textSize * TEXT_BASELINE
        canvas.drawText(boss, pad, textBaseline, labelPaint)
        valuePaint.color = BossColors.Gold.toArgb()
        canvas.drawText(value, width - pad - valuePaint.measureText(value), textBaseline, valuePaint)
        drawBar(canvas, pad, textBaseline + px(BAR_GAP_DP), width - 2 * pad, filledSegments(health, HP_SEGMENTS))
    }

    private fun drawBar(canvas: Canvas, left: Float, top: Float, barWidth: Float, filled: Int) {
        val height = px(BAR_HEIGHT_DP)
        val gap = px(SEGMENT_GAP_DP)
        fill.color = BossColors.Ink.toArgb()
        canvas.drawRect(left, top, left + barWidth, top + height, fill)
        val segment = (barWidth - gap * (HP_SEGMENTS + 1)) / HP_SEGMENTS
        repeat(HP_SEGMENTS) { index ->
            fill.color = (if (index < filled) BossColors.Hp else BossColors.HpEmpty).toArgb()
            val x = left + gap + index * (segment + gap)
            canvas.drawRect(x, top + gap, x + segment, top + height - gap, fill)
        }
    }

    private fun drawKnockedOut(canvas: Canvas) {
        drawFrame(canvas, BossColors.Gold, BossColors.Night, px(BORDER_DP))
        koPaint.color = BossColors.Night.toArgb()
        hintPaint.color = BossColors.Night.toArgb()
        val center = width / 2f
        canvas.drawText(ko, center, px(PADDING_DP) + koPaint.textSize * TEXT_BASELINE, koPaint)
        canvas.drawText(tapToSee, center, height - px(PADDING_DP), hintPaint)
    }

    private fun drawFrame(canvas: Canvas, background: Color, border: Color, thickness: Float) {
        fill.color = background.toArgb()
        canvas.drawRect(thickness, thickness, width - thickness, height - thickness, fill)
        fill.color = border.toArgb()
        canvas.drawRect(thickness, 0f, width - thickness, thickness, fill)
        canvas.drawRect(thickness, height - thickness, width - thickness, height.toFloat(), fill)
        canvas.drawRect(0f, thickness, thickness, height - thickness, fill)
        canvas.drawRect(width - thickness, thickness, width.toFloat(), height - thickness, fill)
    }

    private fun px(dp: Float): Float = dp * density

    private companion object {
        const val SLEEPING = "zZ"
        const val LABEL_SP = 15f
        const val VALUE_SP = 20f
        const val KO_SP = 32f
        const val HINT_SP = 14f
        const val BADGE_WIDTH_DP = 150f
        const val BADGE_HEIGHT_DP = 52f
        const val KO_HEIGHT_DP = 64f
        const val KO_PADDING_H_DP = 14f
        const val BORDER_DP = 2f
        const val PADDING_DP = 9f
        const val BAR_GAP_DP = 6f
        const val BAR_HEIGHT_DP = 11f
        const val SEGMENT_GAP_DP = 2f
        const val TEXT_BASELINE = 0.85f
        const val BADGE_ALPHA = 0.9f
    }
}
