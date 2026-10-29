package crazytower.game

import crazytower.core.Stages
import crazytower.game.ui.Icons
import crazytower.game.ui.Panel
import crazytower.game.ui.label
import korlibs.image.color.RGBA
import korlibs.korge.input.onClick
import korlibs.korge.view.*
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * The big moment when the tower gains a star or reaches a new stage: the screen dims,
 * a card pops up with the stars and what changed, and confetti falls. Goes away by
 * itself after a few seconds or with a click.
 */
class Celebration(private val screenWidth: Double, private val screenHeight: Double) : Container() {
    private class Confetti(val view: View, var vx: Double, var vy: Double, var spin: Double)

    private val dim = solidRect(screenWidth, screenHeight, RGBA(8, 10, 24, 150))
    private val card = container()
    private val confetti = ArrayList<Confetti>()
    private val random = Random(11)
    private var age = 0.0
    private var lifetime = 0.0

    init {
        visible = false
        dim.onClick { close() }
        card.onClick { close() }
    }

    val isShowing: Boolean get() = visible

    /** A new star: [stars] is the new rating. */
    fun showStar(stars: Int) {
        val bonus = ((stars - 1) * 20)
        show(
            title = if (stars == 1) "1 STERN" else "$stars STERNE",
            lines = listOf(
                "Der Turm spricht sich herum.",
                if (bonus > 0) "Alle Mieten und Kaufpreise jetzt +$bonus %." else "",
            ),
            stars = stars,
            seconds = 4.5,
        )
        Sfx.play(Sfx.STAR)
    }

    /** A new stage: [stage] is the stage just reached. */
    fun showStage(stage: Int, stars: Int) {
        val info = Stages.info(stage)
        show(
            title = "STUFE $stage · ${info.name.uppercase()}",
            lines = listOf("Jetzt bis Etage ${info.maxFloor}.", info.unlocks),
            stars = stars,
            seconds = 6.5,
        )
        Sfx.play(Sfx.STAGE)
    }

    private fun show(title: String, lines: List<String>, stars: Int, seconds: Double) {
        card.removeChildren()
        confetti.forEach { it.view.removeFromParent() }
        confetti.clear()
        val width = 560.0
        val text = lines.filter { it.isNotBlank() }
        val height = 190.0 + text.size * 24.0
        Panel(width, height, Theme.SELECT, 14.0).addTo(card)
        Panel(width - 8, height - 8, Theme.PANEL, 12.0).addTo(card).xy(4.0, 4.0)
        val row = card.container()
        for (i in 0 until 5) Icons.star(row, i < stars, 48.0).xy(i * 56.0, 0.0)
        row.xy((width - 5 * 56.0 + 8) / 2, 28.0)
        val heading = card.label(title, 30.0, Theme.GOLD, Theme.pixel)
        heading.xy((width - heading.width) / 2, 96.0)
        var y = 144.0
        for (line in text) {
            val l = card.label(line, 15.0, Theme.TEXT)
            l.xy((width - l.width) / 2, y)
            y += 24.0
        }
        val hint = card.label("Klicken zum Weiterbauen", 11.0, Theme.TEXT_DIM)
        hint.xy((width - hint.width) / 2, height - 28.0)
        card.xy((screenWidth - width) / 2, (screenHeight - height) / 2 - 40)
        val colors = listOf(Theme.GOLD, Theme.SELECT, Theme.ALARM, Theme.GOOD, Theme.TEXT)
        repeat(90) {
            val piece = solidRect(6.0 + random.nextDouble() * 6, 4.0 + random.nextDouble() * 4, colors[it % colors.size])
            piece.xy(random.nextDouble() * screenWidth, -20.0 - random.nextDouble() * screenHeight * 0.6)
            confetti += Confetti(piece, random.nextDouble() * 60 - 30, 120 + random.nextDouble() * 160, random.nextDouble() * 400 - 200)
        }
        age = 0.0
        lifetime = seconds
        visible = true
    }

    fun close() {
        visible = false
    }

    fun update(dt: Double) {
        if (!visible) return
        age += dt
        // the card pops in with a little overshoot
        val pop = if (age < 0.35) sin(age / 0.35 * PI / 2) * (1 + 0.12 * sin(age / 0.35 * PI)) else 1.0
        card.scale = pop
        card.x = (screenWidth - 560.0 * pop) / 2
        for (c in confetti) {
            c.view.x += (c.vx + sin(age * 3 + c.spin) * 20) * dt
            c.view.y += c.vy * dt
            c.view.rotation = korlibs.math.geom.Angle.fromDegrees(age * c.spin)
        }
        val fade = ((lifetime - age) / 0.6).coerceIn(0.0, 1.0)
        alpha = fade
        if (age >= lifetime) close()
    }
}
