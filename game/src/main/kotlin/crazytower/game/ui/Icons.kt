package crazytower.game.ui

import crazytower.core.Category
import crazytower.game.Theme
import korlibs.image.color.RGBA
import korlibs.korge.view.*
import korlibs.math.geom.Point
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Faceted icons from the UI design, drawn on a 24x24 grid. */
object Icons {
    fun draw(g: Graphics, category: Category, color: RGBA, size: Double = 26.0) {
        val s = size / 24.0
        fun p(x: Double, y: Double) = Point(x * s, y * s)
        val faint = color.withAd(0.35)
        val mid = color.withAd(0.6)
        g.updateShape {
            when (category) {
                Category.WAYS -> {
                    fill(faint) { moveTo(p(5.0, 2.0)); lineTo(p(19.0, 2.0)); lineTo(p(19.0, 22.0)); lineTo(p(5.0, 22.0)); close() }
                    fill(color) { moveTo(p(12.0, 5.0)); lineTo(p(16.0, 9.0)); lineTo(p(8.0, 9.0)); close() }
                    fill(color) { moveTo(p(12.0, 19.0)); lineTo(p(8.0, 15.0)); lineTo(p(16.0, 15.0)); close() }
                }
                Category.OFFICES -> {
                    fill(mid) { moveTo(p(9.0, 3.0)); lineTo(p(15.0, 3.0)); lineTo(p(15.0, 7.0)); lineTo(p(9.0, 7.0)); close() }
                    fill(faint) { moveTo(p(3.0, 7.0)); lineTo(p(21.0, 7.0)); lineTo(p(21.0, 20.0)); lineTo(p(3.0, 20.0)); close() }
                    fill(color) { moveTo(p(3.0, 7.0)); lineTo(p(21.0, 7.0)); lineTo(p(12.0, 13.0)); close() }
                }
                Category.HOMES -> {
                    fill(color) { moveTo(p(12.0, 3.0)); lineTo(p(21.0, 11.0)); lineTo(p(3.0, 11.0)); close() }
                    fill(faint) { moveTo(p(5.0, 11.0)); lineTo(p(19.0, 11.0)); lineTo(p(19.0, 21.0)); lineTo(p(5.0, 21.0)); close() }
                    fill(color) { moveTo(p(10.0, 15.0)); lineTo(p(14.0, 15.0)); lineTo(p(14.0, 21.0)); lineTo(p(10.0, 21.0)); close() }
                }
                Category.FOOD -> {
                    fill(faint) { moveTo(p(3.0, 10.0)); lineTo(p(21.0, 10.0)); lineTo(p(18.0, 20.0)); lineTo(p(6.0, 20.0)); close() }
                    fill(color) { moveTo(p(3.0, 10.0)); lineTo(p(12.0, 4.0)); lineTo(p(21.0, 10.0)); close() }
                }
                Category.SPECIAL -> {
                    fill(color) { moveTo(p(12.0, 2.0)); lineTo(p(15.0, 11.0)); lineTo(p(12.0, 22.0)); lineTo(p(9.0, 11.0)); close() }
                    fill(mid) { moveTo(p(2.0, 12.0)); lineTo(p(12.0, 9.0)); lineTo(p(22.0, 12.0)); lineTo(p(12.0, 15.0)); close() }
                }
                Category.DEMOLISH -> {
                    fill(color) { moveTo(p(4.0, 9.0)); lineTo(p(12.0, 3.0)); lineTo(p(20.0, 9.0)); lineTo(p(12.0, 13.0)); close() }
                    fill(mid) { moveTo(p(4.0, 9.0)); lineTo(p(12.0, 13.0)); lineTo(p(12.0, 22.0)); lineTo(p(4.0, 17.0)); close() }
                    fill(faint) { moveTo(p(20.0, 9.0)); lineTo(p(12.0, 13.0)); lineTo(p(12.0, 22.0)); lineTo(p(20.0, 17.0)); close() }
                }
            }
        }
    }

    /** Five-point star, filled or empty. */
    fun star(parent: Container, filled: Boolean, size: Double = 20.0): Graphics {
        val s = size / 24.0
        val gold = RGBA(0xFF, 0xC9, 0x4D)
        val goldDark = RGBA(0xF2, 0xB6, 0x32)
        val empty = RGBA(0x3A, 0x40, 0x60)
        val outline = listOf(12 to 2, 15 to 9, 22 to 9, 16.5 to 13.5, 18.5 to 21, 12 to 16.5, 5.5 to 21, 7.5 to 13.5, 2 to 9, 9 to 9)
        return parent.graphics {
            fill(if (filled) gold else empty) {
                outline.forEachIndexed { i, (x, y) ->
                    val pt = Point(x.toDouble() * s, y.toDouble() * s)
                    if (i == 0) moveTo(pt) else lineTo(pt)
                }
                close()
            }
            if (filled) {
                fill(goldDark) {
                    moveTo(Point(12 * s, 2 * s)); lineTo(Point(15 * s, 9 * s)); lineTo(Point(22 * s, 9 * s))
                    lineTo(Point(16.5 * s, 13.5 * s)); lineTo(Point(18.5 * s, 21 * s)); lineTo(Point(12 * s, 16.5 * s)); close()
                }
            }
        }
    }

    /**
     * The badge over a room nobody moves into, centred on 0,0: an exclamation mark, or an
     * angry face when the way there is the problem.
     */
    fun problem(parent: Container, angry: Boolean, size: Double = 34.0): Graphics = parent.graphics {
        val r = size / 2
        fun disc(cx: Double, cy: Double, radius: Double) {
            val corners = 20
            for (i in 0 until corners) {
                val a = 2 * PI * i / corners
                val pt = Point(cx + cos(a) * radius, cy + sin(a) * radius)
                if (i == 0) moveTo(pt) else lineTo(pt)
            }
            close()
        }
        val ink = Theme.INK
        fill(ink) { disc(0.0, 0.0, r + 2.5) }
        if (angry) {
            val s = r / 12.0
            fun p(x: Double, y: Double) = Point(x * s, y * s)
            fill(RGBA(232, 72, 52)) { disc(0.0, 0.0, r) }
            fill(RGBA(255, 120, 90)) { disc(-3 * s, -4 * s, r * 0.55) }
            fill(ink) {
                // brows pulled down towards the nose
                moveTo(p(-8.5, -6.0)); lineTo(p(-1.5, -2.5)); lineTo(p(-2.0, -0.8)); lineTo(p(-8.8, -4.0)); close()
                moveTo(p(8.5, -6.0)); lineTo(p(1.5, -2.5)); lineTo(p(2.0, -0.8)); lineTo(p(8.8, -4.0)); close()
            }
            fill(ink) { disc(-4.2 * s, 1.2 * s, 1.7 * s); disc(4.2 * s, 1.2 * s, 1.7 * s) }
            fill(ink) {
                // a frown
                moveTo(p(-6.0, 8.5)); lineTo(p(-3.0, 5.6)); lineTo(p(3.0, 5.6)); lineTo(p(6.0, 8.5))
                lineTo(p(4.6, 9.4)); lineTo(p(2.4, 7.4)); lineTo(p(-2.4, 7.4)); lineTo(p(-4.6, 9.4)); close()
            }
        } else {
            fill(Theme.GOLD) { disc(0.0, 0.0, r) }
            fill(ink) {
                moveTo(Point(-r * 0.16, -r * 0.62)); lineTo(Point(r * 0.16, -r * 0.62))
                lineTo(Point(r * 0.1, r * 0.22)); lineTo(Point(-r * 0.1, r * 0.22)); close()
                disc(0.0, r * 0.5, r * 0.14)
            }
        }
    }

    /** Low-poly cog for the options button, centred in a 40 x 37 button face. */
    fun gear(parent: Container, color: RGBA): Graphics = parent.graphics {
        val teeth = 8
        fill(color) {
            for (i in 0 until teeth * 4) {
                val angle = i * PI * 2 / (teeth * 4) - PI / (teeth * 4)
                val r = if ((i / 2) % 2 == 0) 11.0 else 8.0
                val pt = Point(20.0 + r * cos(angle), 18.5 + r * sin(angle))
                if (i == 0) moveTo(pt) else lineTo(pt)
            }
            close()
        }
        fill(Theme.INK) {
            for (i in 0 until 6) {
                val angle = i * PI / 3
                val pt = Point(20.0 + 3.5 * cos(angle), 18.5 + 3.5 * sin(angle))
                if (i == 0) moveTo(pt) else lineTo(pt)
            }
            close()
        }
    }
}
