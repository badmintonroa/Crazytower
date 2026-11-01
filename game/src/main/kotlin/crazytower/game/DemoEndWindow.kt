package crazytower.game

import crazytower.core.Edition
import crazytower.core.tr
import crazytower.game.ui.Icons
import crazytower.game.ui.Panel
import crazytower.game.ui.TextButton
import crazytower.game.ui.fitWidth
import crazytower.game.ui.label
import crazytower.game.ui.wrapText
import korlibs.image.color.RGBA
import korlibs.korge.input.onClick
import korlibs.korge.view.*

/**
 * The end of the Steam demo: the tower reached its second star. The screen dims, the game
 * stands still, and the card points to the full version. The tower stays saved, so it can be
 * built on in the full game.
 */
class DemoEndWindow(
    screenWidth: Double,
    screenHeight: Double,
    onMainMenu: (() -> Unit)?,
    onQuit: () -> Unit,
) : Container() {
    init {
        visible = false
        // swallows every click, so nothing in the tower can be built any more
        solidRect(screenWidth, screenHeight, RGBA(8, 10, 24, 190)).onClick { }
        val width = 600.0
        val lines = listOf(
            tr("Zwei Sterne! Die Clowns stehen schon Schlange, und im Keller übt ein Vampir das Lächeln.",
                "Two stars! The clowns are already queuing, and a vampire in the basement is practising his smile."),
            tr("In der Vollversion geht es weiter: bis zu fünf Sterne, Wolkenkratzer, VIP-Besuche, Penthouses, U-Bahn und die Mondmission.",
                "The full version goes on: up to five stars, skyscrapers, VIP visits, penthouses, an underground station and the moon mission."),
            tr("Dein Turm ist gespeichert. Die Vollversion baut ihn einfach weiter.",
                "Your tower is saved. The full version simply carries on building it."),
        ).flatMap { wrapText(it, width - 56.0, 15.0).split('\n') + "" }.dropLast(1)
        val height = 250.0 + lines.size * 22.0
        val card = container()
        Panel(width, height, Theme.SELECT, 14.0).addTo(card)
        Panel(width - 8, height - 8, Theme.PANEL, 12.0).addTo(card).xy(4.0, 4.0)
        val row = card.container()
        for (i in 0 until 5) Icons.star(row, i < Edition.DEMO_STARS, 44.0).xy(i * 52.0, 0.0)
        row.xy((width - 5 * 52.0 + 8) / 2, 24.0)
        val heading = card.label(tr("ENDE DER TESTVERSION", "END OF THE DEMO"), 30.0, Theme.GOLD, Theme.bodyBold).fitWidth(width - 48.0)
        heading.xy((width - heading.width * heading.scaleX) / 2, 86.0)
        var y = 134.0
        for (line in lines) {
            if (line.isNotEmpty()) {
                val l = card.label(line, 15.0, Theme.TEXT)
                l.xy((width - l.width) / 2, y)
            }
            y += 22.0
        }
        // the store button only when the build knows the store page
        val buttons = buildList {
            if (Edition.storeUrl.isNotBlank()) add(tr("Vollversion auf Steam", "Full version on Steam") to { openStore() })
            onMainMenu?.let { add(tr("Zum Hauptmenü", "Main menu") to it) }
            add(tr("Beenden", "Quit") to onQuit)
        }
        val gap = 12.0
        val buttonWidth = (width - 48.0 - gap * (buttons.size - 1)) / buttons.size
        buttons.forEachIndexed { i, (title, action) ->
            TextButton(buttonWidth, 46.0, title, 16.0) { action() }.addTo(card).xy(24.0 + i * (buttonWidth + gap), height - 76.0).also {
                it.selected = i == 0
            }
        }
        card.xy((screenWidth - width) / 2, ((screenHeight - height) / 2 - 20).coerceAtLeast(80.0))
    }

    private fun openStore() {
        runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(Edition.storeUrl)) }
    }
}
