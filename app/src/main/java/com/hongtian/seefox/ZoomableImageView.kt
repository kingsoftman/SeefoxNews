package com.hongtian.seefox

import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    private val matrix = Matrix()
    private var scale = 1f
    private var startX = 0f
    private var startY = 0f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val newScale = scale * detector.scaleFactor
            scale = newScale.coerceIn(1f, 5f)
            applyMatrix()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (scale > 1f) {
                // 恢复原始大小
                scale = 1f
                startX = 0f
                startY = 0f
            } else {
                // 放大到2倍
                scale = 2f
                startX = -e.x * scale + width / 2
                startY = -e.y * scale + height / 2
            }
            applyMatrix()
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (scale > 1f) {
                startX -= distanceX
                startY -= distanceY
                // 边界限制
                val scaledWidth = width * scale
                val scaledHeight = height * scale
                startX = startX.coerceIn(-(scaledWidth - width), 0f)
                startY = startY.coerceIn(-(scaledHeight - height), 0f)
                applyMatrix()
            }
            return true
        }
    })

    init {
        scaleType = ScaleType.MATRIX
    }

    private fun applyMatrix() {
        matrix.reset()
        matrix.postScale(scale, scale)
        matrix.postTranslate(startX, startY)
        imageMatrix = matrix
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }
}
