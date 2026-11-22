package cn.ianzb.hypernavbar.hook.gesture

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup

/** Available canvas area, expressed in the handle's coordinates. Main-thread only. */
internal class GestureHandleViewport {
    private val area = RectF()
    private val clip = Rect()
    private val matrixValues = FloatArray(9)

    fun resolve(handle: View, extendHorizontally: Boolean): RectF? {
        if (!handle.isAttachedToWindow || handle.width <= 0 || handle.height <= 0) return null
        area.set(
            if (extendHorizontally) Float.NEGATIVE_INFINITY else 0f,
            0f,
            if (extendHorizontally) Float.POSITIVE_INFINITY else handle.width.toFloat(),
            handle.height.toFloat(),
        )
        var child = handle
        var originX = 0f
        var originY = 0f
        while (true) {
            // An outline or a legacy animation can clip or move the pill independently of these rectangles.
            if (child.clipToOutline || child.animation != null) return null
            if (child.getClipBounds(clip) && !area.intersect(
                    clip.left - originX, clip.top - originY, clip.right - originX, clip.bottom - originY,
                )
            ) return null
            val matrix = child.matrix
            val translated = !matrix.isIdentity
            if (translated) {
                matrix.getValues(matrixValues)
                if (!isTranslation(matrixValues)) return null
            }

            val parent = child.parent as? ViewGroup
            if (parent == null) {
                if (child !== handle.rootView || !child.getLocalVisibleRect(clip)) return null
                if (!area.intersect(
                        clip.left - originX, clip.top - originY, clip.right - originX, clip.bottom - originY,
                    )
                ) return null
                return area
            }
            // clipChildren clips the child's drawing at the child's bounds, not the parent's bounds.
            if (parent.clipChildren && !area.intersect(
                    -originX, -originY, child.width - originX, child.height - originY,
                )
            ) return null
            originX += child.left - parent.scrollX
            originY += child.top - parent.scrollY
            if (translated) {
                originX += matrixValues[Matrix.MTRANS_X]
                originY += matrixValues[Matrix.MTRANS_Y]
            }
            val hasPadding = parent.paddingLeft != 0 || parent.paddingTop != 0 ||
                parent.paddingRight != 0 || parent.paddingBottom != 0
            if (hasPadding && parent.clipToPadding && !area.intersect(
                    parent.paddingLeft - originX, parent.paddingTop - originY,
                    parent.width - parent.paddingRight - originX, parent.height - parent.paddingBottom - originY,
                )
            ) return null
            child = parent
        }
    }

    fun intersectCanvasClip(handle: View, canvas: Canvas): Boolean {
        if (!canvas.getClipBounds(clip)) return false
        // A hardware recording may report its nominal View bounds even when its parent
        // permits overflow. Honor narrower clips without mistaking the recording size for a clip.
        val hardware = canvas.isHardwareAccelerated
        return area.intersect(
            if (hardware && clip.left <= 0) area.left else clip.left.toFloat(),
            if (hardware && clip.top <= 0) area.top else clip.top.toFloat(),
            if (hardware && clip.right >= handle.width) area.right else clip.right.toFloat(),
            if (hardware && clip.bottom >= handle.height) area.bottom else clip.bottom.toFloat(),
        )
    }

    private fun isTranslation(values: FloatArray): Boolean =
        values[Matrix.MSCALE_X] == 1f && values[Matrix.MSCALE_Y] == 1f &&
            values[Matrix.MSKEW_X] == 0f && values[Matrix.MSKEW_Y] == 0f &&
            values[Matrix.MPERSP_0] == 0f && values[Matrix.MPERSP_1] == 0f && values[Matrix.MPERSP_2] == 1f
}
