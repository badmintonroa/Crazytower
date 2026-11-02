package crazytower.game.ui

import korlibs.image.bitmap.Bitmap
import korlibs.image.bitmap.Bitmap32
import korlibs.image.bitmap.Bitmaps
import korlibs.image.bitmap.context2d
import korlibs.image.bitmap.slice
import korlibs.image.color.RGBA
import korlibs.image.font.Font
import korlibs.image.text.TextAlignment
import korlibs.image.vector.Context2d
import korlibs.image.font.getTextBounds
import korlibs.korge.render.RenderContext
import korlibs.korge.view.Container
import korlibs.korge.view.image
import korlibs.math.geom.Point
import korlibs.math.geom.Rectangle
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max

/**
 * A one-line text drawn once into a bitmap, for text that moves: the ticker, the signs in the
 * tower, the build tooltip.
 *
 * KorGE draws a [korlibs.korge.view.Text] in a vector font anew on the processor whenever the
 * text or one of its parents moves, curve by curve. The scrolling ticker alone cost a few
 * milliseconds in every frame that way. This text is a picture that moves for free; it is drawn
 * again only when its words change or it is shown noticeably bigger or smaller (zoom, window size).
 */
class BakedText(
    text: String,
    private val textSize: Double,
    private val color: RGBA,
    private val font: Font,
) : Container() {
    var text: String = text
        set(value) {
            if (field == value) return
            field = value
            bake(bakedScale)
        }

    private val picture = image(Bitmaps.transparent) { smoothing = true }
    private var bakedScale = 1.0
    private val oldBitmaps = ArrayList<Bitmap>()

    /** The text's own box, without the room left around it in the picture, like a [korlibs.korge.view.Text]. */
    private var layout = Rectangle()

    init {
        bake(1.0)
    }

    private fun bake(scale: Double) {
        bakedScale = scale
        picture.bitmap.base.let { if (it !== Bitmaps.transparent.base) oldBitmaps += it }
        if (text.isEmpty()) {
            picture.bitmap = Bitmaps.transparent
            layout = Rectangle()
            invalidateLocalBounds()
            return
        }
        // copied first: inside the drawing, `font` would mean the drawing context's own font
        val words = text
        val face = font
        val size = textSize
        val paint = color
        val drawing: Context2d.() -> Unit = {
            fillText(words, Point.ZERO, face, size, TextAlignment.TOP_LEFT, paint)
        }
        // where the text sits, as a vector text would report it, and room around it for letters that reach out
        val metrics = font.getTextBounds(textSize, text, align = TextAlignment.TOP_LEFT)
        layout = metrics.bounds
        val pad = textSize * 0.3
        val area = Rectangle(layout.x - pad, layout.y - pad, layout.width + pad * 2, layout.height + pad * 2)
        // a texture can't be wider than the graphics card allows, so very long texts are drawn a little coarser
        val s = minOf(scale, MAX_PIXELS / area.width.coerceAtLeast(1.0), MAX_PIXELS / area.height.coerceAtLeast(1.0))
        val w = ceil(area.width * s).toInt().coerceAtLeast(1)
        val h = ceil(area.height * s).toInt().coerceAtLeast(1)
        val bitmap = Bitmap32(w, h, premultiplied = true).context2d {
            scale(s)
            translate(-area.x, -area.y)
            drawing()
        }
        picture.bitmap = bitmap.slice()
        picture.scale = 1.0 / s
        picture.x = area.x
        picture.y = area.y
        invalidateLocalBounds()
    }

    override fun getLocalBoundsInternal(): Rectangle = layout

    override fun renderInternal(ctx: RenderContext) {
        for (old in oldBitmaps) ctx.agBitmapTextureManager.removeBitmap(old, "BakedText")
        oldBitmaps.clear()
        val m = globalMatrix
        val shown = max(hypot(m.a, m.b), hypot(m.c, m.d))
        if (shown > 0 && abs(shown / bakedScale - 1.0) > RESCALE) bake(shown)
        super.renderInternal(ctx)
    }

    companion object {
        /** Drawn again when shown this much bigger or smaller than it was drawn. */
        const val RESCALE = 0.05
        const val MAX_PIXELS = 8000.0
    }
}

/** A [BakedText] with the same defaults as [label]. */
fun Container.bakedLabel(value: String, size: Double = 14.0, color: RGBA = korlibs.image.color.Colors.WHITE, font: Font): BakedText =
    BakedText(value, maxOf(size, MIN_TEXT), color, font).also { addChild(it) }
