package dev.hytalpioneers.lockscreen.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class DrawView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var onDrawData: ((Float, Float, String, Int, Float) -> Unit)? = null
    private var currentColor = Color.WHITE
    private var currentStrokeWidth = 8f

    private val paint = Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    private val paths = mutableListOf<ColoredPath>()
    private var currentPath = Path()

    private val remotePaths = mutableListOf<ColoredPath>()
    private val activeRemotePaths = mutableMapOf<String, Path>()

    private data class ColoredPath(val path: Path, val color: Int, val strokeWidth: Float)

    fun setOnDrawDataListener(listener: (Float, Float, String, Int, Float) -> Unit) {
        onDrawData = listener
    }

    fun setDrawingColor(color: Int) {
        currentColor = color
    }

    fun setStrokeWidth(width: Float) {
        currentStrokeWidth = width
    }

    fun clear() {
        paths.clear()
        remotePaths.clear()
        activeRemotePaths.clear()
        currentPath = Path()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw finished local paths
        paths.forEach {
            paint.color = it.color
            paint.strokeWidth = it.strokeWidth
            canvas.drawPath(it.path, paint)
        }

        // Draw current local path being drawn
        paint.color = currentColor
        paint.strokeWidth = currentStrokeWidth
        canvas.drawPath(currentPath, paint)

        // Draw finished remote paths
        remotePaths.forEach {
            paint.color = it.color
            paint.strokeWidth = it.strokeWidth
            canvas.drawPath(it.path, paint)
        }

        // Draw active remote paths
        activeRemotePaths.values.forEach {
            paint.color = currentColor // Fallback for active remote paths
            paint.strokeWidth = currentStrokeWidth
            canvas.drawPath(it, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentPath = Path()
                currentPath.moveTo(event.x, event.y)
                onDrawData?.invoke(event.x, event.y, "down", currentColor, currentStrokeWidth)
            }

            MotionEvent.ACTION_MOVE -> {
                currentPath.lineTo(event.x, event.y)
                onDrawData?.invoke(event.x, event.y, "move", currentColor, currentStrokeWidth)
            }

            MotionEvent.ACTION_UP -> {
                paths.add(ColoredPath(currentPath, currentColor, currentStrokeWidth))
                onDrawData?.invoke(event.x, event.y, "up", currentColor, currentStrokeWidth)
            }
        }
        invalidate()
        return true
    }

    fun remoteDraw(
        x: Float,
        y: Float,
        action: String,
        color: Int,
        sender: String,
        strokeWidth: Float
    ) {
        when (action) {
            "down" -> {
                val path = Path()
                path.moveTo(x, y)
                activeRemotePaths[sender] = path
            }

            "move" -> {
                activeRemotePaths[sender]?.lineTo(x, y)
            }

            "up" -> {
                activeRemotePaths[sender]?.let {
                    remotePaths.add(ColoredPath(it, color, strokeWidth))
                }
                activeRemotePaths.remove(sender)
            }

            "clear" -> {
                clear()
            }
        }
        invalidate()
    }
}
