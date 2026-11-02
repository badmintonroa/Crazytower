package crazytower.game

import crazytower.game.ui.Icons
import crazytower.game.ui.chamferPath
import korlibs.image.bitmap.Bitmap32
import korlibs.image.bitmap.BmpSlice
import korlibs.image.bitmap.context2d
import korlibs.image.color.RGBA
import korlibs.image.vector.Context2d
import korlibs.image.vector.buildShape
import korlibs.math.geom.Anchor
import kotlin.math.ceil

/** Express elevators wear red so they stand out from the normal shafts. */
val EXPRESS_RED = RGBA(214, 64, 52)

/**
 * The little pictures the tower shows many times over: stars on done-up rooms, problem and event
 * badges, elevator cabins. Each used to be a vector shape with a texture of its own, which ended
 * KorGE's draw call every time one came up. Now they are drawn into bitmaps once and packed onto
 * the same texture as the rooms and people ([Assets]).
 */
object WorldSprites {
    const val STAR = "star"
    const val PROBLEM = "problem"
    const val PROBLEM_ANGRY = "problem_angry"
    const val BADGE = "badge"
    const val CABIN = "cabin"
    const val CABIN_EXPRESS = "cabin_express"

    /** Drawn this many times finer than shown, so they stay sharp when zoomed in. */
    private const val DETAIL = 3.0

    /** A drawing as a bitmap; [originX], [originY] is where the drawing's 0,0 lies in it, in bitmap pixels. */
    class Raster(val bitmap: Bitmap32, val originX: Double, val originY: Double, val scale: Double)

    /** A packed [Raster]: shown at 1 / [scale] with [anchor] on the drawing's 0,0. */
    class Sprite(val slice: BmpSlice, val anchor: Anchor, val scale: Double)

    fun draw(): Map<String, Raster> = mapOf(
        STAR to raster { Icons.drawStar(this, filled = true, size = 14.0) },
        PROBLEM to raster { Icons.drawProblem(this, angry = false) },
        PROBLEM_ANGRY to raster { Icons.drawProblem(this, angry = true) },
        BADGE to raster { EventArt.drawBadge(this) },
        CABIN to cabin(express = false),
        CABIN_EXPRESS to cabin(express = true),
    )

    /** [drawing] into a bitmap just big enough for it, [DETAIL] times finer. */
    private fun raster(drawing: Context2d.() -> Unit): Raster {
        val bounds = buildShape { drawing() }.bounds
        val pad = 2.0
        val w = ceil(bounds.width * DETAIL + pad * 2).toInt()
        val h = ceil(bounds.height * DETAIL + pad * 2).toInt()
        val originX = pad - bounds.x * DETAIL
        val originY = pad - bounds.y * DETAIL
        val bitmap = Bitmap32(w, h, premultiplied = true).context2d {
            translate(originX, originY)
            scale(DETAIL)
            drawing()
        }
        return Raster(bitmap, originX, originY, DETAIL)
    }

    /** An elevator cabin, 64 x 82, with its top left corner at 0,0. */
    private fun cabin(express: Boolean): Raster {
        val bitmap = Bitmap32(64, 82, premultiplied = true).context2d {
            fill(Theme.INK) { chamferPath(64.0, 82.0, 6.0, 0.0) }
            fill(if (express) EXPRESS_RED else Theme.GOLD) { chamferPath(64.0, 82.0, 6.0, 3.0) }
            fill(Theme.alpha(Theme.INK, 0.85)) { rect(10.0, 12.0, 44.0, 54.0) }
        }
        return Raster(bitmap, 0.0, 0.0, 1.0)
    }
}
