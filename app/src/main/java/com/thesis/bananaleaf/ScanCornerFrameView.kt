package com.thesis.bananaleaf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * ScanCornerFrameView — Premium AR Viewfinder Scanning Reticle.
 *
 * Draws four sleek, rounded open-corner brackets (top-left, top-right,
 * bottom-left, bottom-right) matching the cutout curvature of MaskedBlurView,
 * plus a subtle hairline guide frame.
 *
 * The corner color dynamically updates:
 * - Green (#32CD32) when a banana leaf is detected inside the frame.
 * - Red (#FF5252) when searching or when no banana leaf is present.
 */
class ScanCornerFrameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val strokeWidthPx = 3.5f * density
    private val cornerRadiusPx = 18f * density
    private val cornerLengthPx = 32f * density

    // Paint for the prominent corner brackets
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#32CD32")
    }

    // Paint for the subtle hairline outline connecting the reticle
    private val guideFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = Color.argb(45, 255, 255, 255)
    }

    private val bracketPath = Path()
    private val arcRect = RectF()
    private val guideRect = RectF()

    /** Color of all four corner brackets. Setting this redraws the view. */
    var cornerColor: Int
        get() = paint.color
        set(value) {
            if (paint.color != value) {
                paint.color = value
                invalidate()
            }
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val halfStroke = strokeWidthPx / 2f
        val left = halfStroke
        val top = halfStroke
        val right = width.toFloat() - halfStroke
        val bottom = height.toFloat() - halfStroke

        if (right <= left || bottom <= top) return

        val r = cornerRadiusPx.coerceAtMost(minOf(right - left, bottom - top) / 2f)
        val len = cornerLengthPx.coerceAtMost((right - left) / 2.5f).coerceAtLeast(r + 4f * density)

        // 1. Draw subtle guide outline frame
        guideRect.set(left, top, right, bottom)
        canvas.drawRoundRect(guideRect, r, r, guideFramePaint)

        // 2. Draw 4 rounded corner brackets
        bracketPath.reset()

        // Top-left
        bracketPath.moveTo(left, top + len)
        bracketPath.lineTo(left, top + r)
        arcRect.set(left, top, left + 2 * r, top + 2 * r)
        bracketPath.arcTo(arcRect, 180f, 90f, false)
        bracketPath.lineTo(left + len, top)

        // Top-right
        bracketPath.moveTo(right - len, top)
        bracketPath.lineTo(right - r, top)
        arcRect.set(right - 2 * r, top, right, top + 2 * r)
        bracketPath.arcTo(arcRect, 270f, 90f, false)
        bracketPath.lineTo(right, top + len)

        // Bottom-right
        bracketPath.moveTo(right, bottom - len)
        bracketPath.lineTo(right, bottom - r)
        arcRect.set(right - 2 * r, bottom - 2 * r, right, bottom)
        bracketPath.arcTo(arcRect, 0f, 90f, false)
        bracketPath.lineTo(right - len, bottom)

        // Bottom-left
        bracketPath.moveTo(left + len, bottom)
        bracketPath.lineTo(left + r, bottom)
        arcRect.set(left, bottom - 2 * r, left + 2 * r, bottom)
        bracketPath.arcTo(arcRect, 90f, 90f, false)
        bracketPath.lineTo(left, bottom - len)

        canvas.drawPath(bracketPath, paint)
    }
}

