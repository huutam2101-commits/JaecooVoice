package com.jaecoo.voice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.View

/**
 * Custom View vẽ waveform động cho giao diện Overlay
 */
class WaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "WaveView"
        private const val BAR_COUNT = 5
    }

    private val colors = intArrayOf(
        Color.parseColor("#4285F4"), // Xanh dương
        Color.parseColor("#EA4335"), // Đỏ
        Color.parseColor("#FBBC05"), // Vàng
        Color.parseColor("#34A853"), // Xanh lá
        Color.parseColor("#4285F4")  // Xanh dương
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val barHeights = FloatArray(BAR_COUNT) { 0.1f }
    private val targetHeights = FloatArray(BAR_COUNT) { 0.1f }

    private var isListening = false

    private val animRunnable = object : Runnable {
        override fun run() {
            if (isListening) {
                for (i in 0 until BAR_COUNT) {
                    targetHeights[i] = (0.2f + Math.random().toFloat() * 0.8f)
                    barHeights[i] += (targetHeights[i] - barHeights[i]) * 0.3f
                }
                invalidate()
                postDelayed(this, 80)
            }
        }
    }

    fun setListening(listening: Boolean) {
        Log.d(TAG, "setListening: $listening")
        isListening = listening
        removeCallbacks(animRunnable)
        if (listening) {
            post(animRunnable)
        } else {
            for (i in 0 until BAR_COUNT) {
                barHeights[i] = 0.05f
            }
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val totalSpacingRatio = (BAR_COUNT * 2 - 1).toFloat()
        val barWidth = w / totalSpacingRatio
        val cornerRadius = barWidth / 2f

        for (i in 0 until BAR_COUNT) {
            paint.color = colors[i % colors.size]
            val barH = if (isListening) (h * barHeights[i]).coerceAtLeast(6f) else 6f
            val left = i * barWidth * 2f
            val right = left + barWidth
            val top = (h - barH) / 2f
            val bottom = top + barH

            val rect = RectF(left, top, right, bottom)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        }
    }
}
