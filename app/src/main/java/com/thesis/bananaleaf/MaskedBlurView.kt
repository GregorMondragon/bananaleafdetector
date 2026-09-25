package com.thesis.bananaleaf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import android.util.AttributeSet
import android.view.View
import eightbitlab.com.blurview.BlurView

/**
 * A BlurView that leaves one child view's rounded rectangle completely sharp.
 * The blur is rendered everywhere except the supplied cutout view.
 *
 * The cutout is drawn as a rounded rect (not a plain rect) so its edge is
 * softly rounded rather than a hard rectangle -- a plain rectangular clip
 * would poke sharp square corners out. The cutout position is also
 * recomputed on every layout change of the target (not just once via
 * post{}), since the target's on-screen position shifts whenever dynamic
 * content above it (inference stats bar, status pill) changes size.
 */
class MaskedBlurView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BlurView(context, attrs, defStyleAttr) {

    // Purely cosmetic sizing for the sharp cutout's rounded corners/inset;
    // no longer tied to any drawn border since scanFrame's border is now
    // the four corner brackets painted by ScanCornerFrameView.
    private val cornerRadiusPx = 18f * resources.displayMetrics.density
    private val strokeWidthPx = 3.5f * resources.displayMetrics.density

    private var cutoutView: View? = null
    private val cutoutRect = RectF()
    private val cutoutPath = Path()
    private val tmpLocation = IntArray(2)
    private val cutoutLocation = IntArray(2)

    private val targetLayoutListener =
        View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> invalidate() }

    fun setCutoutView(view: View) {
        cutoutView?.removeOnLayoutChangeListener(targetLayoutListener)
        cutoutView = view
        view.addOnLayoutChangeListener(targetLayoutListener)
        post { invalidate() }
    }

    private fun updateCutoutRect() {
        val target = cutoutView ?: return
        if (!isLaidOut || !target.isLaidOut) return

        getLocationOnScreen(tmpLocation)
        target.getLocationOnScreen(cutoutLocation)

        // Inset by half the border stroke so the clip follows the stroke's
        // centerline (where the drawn border actually sits) rather than the
        // outer edge of the view's full bounds.
        val halfStroke = strokeWidthPx / 2f
        cutoutRect.set(
            (cutoutLocation[0] - tmpLocation[0]) + halfStroke,
            (cutoutLocation[1] - tmpLocation[1]) + halfStroke,
            (cutoutLocation[0] - tmpLocation[0] + target.width) - halfStroke,
            (cutoutLocation[1] - tmpLocation[1] + target.height) - halfStroke
        )
    }

    override fun draw(canvas: Canvas) {
        updateCutoutRect()

        if (cutoutRect.isEmpty) {
            super.draw(canvas)
            return
        }

        cutoutPath.reset()
        cutoutPath.addRoundRect(cutoutRect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)

        val save = canvas.save()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            canvas.clipOutPath(cutoutPath)
        } else {
            @Suppress("DEPRECATION")
            canvas.clipPath(cutoutPath, Region.Op.DIFFERENCE)
        }
        super.draw(canvas)
        canvas.restoreToCount(save)
    }
}
