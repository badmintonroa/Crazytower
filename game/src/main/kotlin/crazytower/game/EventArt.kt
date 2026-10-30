package crazytower.game

import korlibs.image.color.RGBA
import korlibs.korge.view.Container
import korlibs.korge.view.Graphics
import korlibs.korge.view.View
import korlibs.korge.view.container
import korlibs.korge.view.graphics
import korlibs.math.geom.vector.VectorBuilder
import korlibs.math.geom.Point
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Drawn pictures for the events that last a while: badges, the giant cat, the moon sky. */
object EventArt {
    private val PURPLE = RGBA(150, 82, 220)
    private val PURPLE_LIGHT = RGBA(196, 140, 255)
    private val WHITE = RGBA(255, 255, 255)

    /** A purple badge with a lightning bolt, over rooms where an event is running. */
    fun badge(parent: Container, size: Double = 34.0): Graphics = parent.graphics {
        val r = size / 2
        fill(Theme.INK) { oval(0.0, 0.0, r + 2.5, r + 2.5) }
        fill(PURPLE) { oval(0.0, 0.0, r, r) }
        fill(PURPLE_LIGHT) { oval(-r * 0.25, -r * 0.3, r * 0.5, r * 0.5) }
        val s = r / 12.0
        fill(WHITE) {
            moveTo(Point(2 * s, -9 * s)); lineTo(Point(-5 * s, 1.5 * s)); lineTo(Point(-0.5 * s, 1.5 * s))
            lineTo(Point(-2.5 * s, 9 * s)); lineTo(Point(5 * s, -2 * s)); lineTo(Point(0.5 * s, -2 * s)); close()
        }
    }

    /** A ginger cat as big as a floor, sitting on the roof. Its paws stand at (0, 0). */
    fun giantCat(parent: Container): View = parent.container {
        val fur = RGBA(232, 140, 52)
        val dark = RGBA(186, 98, 30)
        val light = RGBA(250, 210, 160)
        graphics {
            // tail curling up on the right
            fill(dark) {
                moveTo(Point(70.0, -20.0)); lineTo(Point(130.0, -40.0)); lineTo(Point(150.0, -110.0))
                lineTo(Point(135.0, -115.0)); lineTo(Point(118.0, -55.0)); lineTo(Point(72.0, -40.0)); close()
            }
            fill(fur) { oval(0.0, -70.0, 85.0, 72.0) }
            fill(light) { oval(-10.0, -50.0, 45.0, 42.0) }
            // stripes
            fill(dark) {
                rect(30.0, -130.0, 10.0, 34.0); rect(52.0, -118.0, 10.0, 30.0); rect(8.0, -138.0, 10.0, 28.0)
            }
            // head
            fill(fur) { oval(-40.0, -160.0, 58.0, 50.0) }
            fill(fur) {
                moveTo(Point(-90.0, -180.0)); lineTo(Point(-84.0, -238.0)); lineTo(Point(-50.0, -200.0)); close()
                moveTo(Point(-30.0, -200.0)); lineTo(Point(4.0, -238.0)); lineTo(Point(8.0, -176.0)); close()
            }
            fill(RGBA(250, 170, 170)) {
                moveTo(Point(-82.0, -190.0)); lineTo(Point(-80.0, -224.0)); lineTo(Point(-60.0, -202.0)); close()
                moveTo(Point(-20.0, -202.0)); lineTo(Point(0.0, -224.0)); lineTo(Point(2.0, -188.0)); close()
            }
            fill(light) { oval(-40.0, -142.0, 26.0, 18.0) }
            fill(Theme.INK) {
                oval(-62.0, -166.0, 7.0, 10.0); oval(-18.0, -166.0, 7.0, 10.0)
                moveTo(Point(-46.0, -150.0)); lineTo(Point(-34.0, -150.0)); lineTo(Point(-40.0, -143.0)); close()
            }
            fill(WHITE) { oval(-60.0, -169.0, 2.5, 3.5); oval(-16.0, -169.0, 2.5, 3.5) }
            // whiskers
            fill(Theme.INK) {
                rect(-110.0, -148.0, 42.0, 2.0); rect(-108.0, -140.0, 40.0, 2.0)
                rect(-12.0, -148.0, 42.0, 2.0); rect(-12.0, -140.0, 40.0, 2.0)
            }
            // front paws
            fill(light) { oval(-50.0, -8.0, 22.0, 12.0); oval(-6.0, -8.0, 22.0, 12.0) }
        }
    }

    /** Black sky with stars and the Earth, behind the skyline. [width] is the lot in pixels. */
    fun moonSky(parent: Container, width: Double) {
        val random = Random(7)
        parent.graphics {
            fill(WHITE) {
                repeat(1200) {
                    val x = -3000.0 + random.nextDouble() * (width + 6000)
                    val y = -4000.0 + random.nextDouble() * 3850
                    val r = 1.5 + random.nextDouble() * 2.5
                    star(x, y, r)
                }
            }
            val cx = width + 260
            val cy = -520.0
            fill(RGBA(40, 90, 200)) { oval(cx, cy, 150.0, 150.0) }
            fill(RGBA(70, 160, 90)) {
                oval(cx - 40, cy - 30, 55.0, 38.0); oval(cx + 50, cy + 40, 45.0, 30.0); oval(cx + 20, cy - 80, 30.0, 18.0)
            }
            fill(RGBA(255, 255, 255, 140)) { oval(cx - 60, cy + 70, 50.0, 14.0) }
        }.also { it.autoScaling = false }
    }

    private fun VectorBuilder.oval(cx: Double, cy: Double, rx: Double, ry: Double) {
        val corners = 28
        for (i in 0 until corners) {
            val a = 2 * PI * i / corners
            val pt = Point(cx + cos(a) * rx, cy + sin(a) * ry)
            if (i == 0) moveTo(pt) else lineTo(pt)
        }
        close()
    }

    private fun VectorBuilder.star(cx: Double, cy: Double, r: Double) {
        moveTo(Point(cx, cy - r * 2)); lineTo(Point(cx + r * 0.6, cy)); lineTo(Point(cx, cy + r * 2)); lineTo(Point(cx - r * 0.6, cy)); close()
        moveTo(Point(cx - r * 2, cy)); lineTo(Point(cx, cy + r * 0.6)); lineTo(Point(cx + r * 2, cy)); lineTo(Point(cx, cy - r * 0.6)); close()
    }
}
