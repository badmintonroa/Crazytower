package crazytower.game.ui

import crazytower.core.Category
import korlibs.image.color.RGBA
import korlibs.korge.view.*
import korlibs.math.geom.Point

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
}
