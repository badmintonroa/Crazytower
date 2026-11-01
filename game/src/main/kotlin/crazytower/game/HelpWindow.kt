package crazytower.game

import crazytower.core.tr
import crazytower.game.ui.Panel
import crazytower.game.ui.TextButton
import crazytower.game.ui.fitWidth
import crazytower.game.ui.label
import korlibs.korge.view.*

/** Every key and mouse action of the game, in one place, for the help window and the tips. */
object Shortcuts {
    /** The keys that change the speed, for the tips on the speed buttons. */
    const val SPEED = ", / ."

    val all: List<Pair<String, String>>
        get() = listOf(
            tr("Linksklick", "Left click") to tr("Bauen, auswählen", "Build, select"),
            tr("Rechtsklick", "Right click") to tr("Werkzeug abbrechen", "Cancel the tool"),
            tr("Rechts ziehen", "Right drag") to tr("Kamera verschieben", "Move the camera"),
            tr("Mausrad, + / −", "Wheel, + / −") to tr("Zoom", "Zoom"),
            tr("Pfeile, WASD", "Arrows, WASD") to tr("Kamera bewegen", "Move the camera"),
            "1 – 6" to tr("Baukategorien", "Build categories"),
            tr("Leertaste", "Space") to tr("Pause", "Pause"),
            SPEED to tr("Langsamer / schneller", "Slower / faster"),
            "N" to tr("Nachts vorspulen an/aus", "Fast-forward nights on/off"),
            "F" to tr("Finanzen", "Finances"),
            "X" to tr("Extras (ab 4 Sternen)", "Extras (from 4 stars)"),
            "M" to tr("Mondmission (ab 5 Sternen)", "Moon mission (from 5 stars)"),
            "O" to tr("Optionen", "Options"),
            "F1, H" to tr("Diese Übersicht", "This overview"),
            tr("Strg+Z", "Ctrl+Z") to tr("Letzten Bau oder Abriss zurücknehmen", "Take back the last build or demolition"),
            "F5 / F9" to tr("Speichern / Laden", "Save / load"),
            "F11" to tr("Vollbild", "Fullscreen"),
            "Esc" to tr("Schließen, abbrechen, Pause", "Close, cancel, pause"),
            tr("Klick auf Ticker", "Click the ticker") to tr("Zum Ort der Meldung", "Go to the news"),
        )
}

/** The key overview, opened with F1 or H (and from the options). */
class HelpWindow(private val onClose: () -> Unit) : Container() {
    val panelWidth = 760.0
    val panelHeight: Double

    init {
        val list = Shortcuts.all
        val rows = (list.size + 1) / 2
        panelHeight = 64.0 + rows * 30.0 + 64.0
        Panel(panelWidth, panelHeight).addTo(this)
        label(tr("TASTEN", "KEYS"), 20.0, Theme.TEXT, Theme.bodyBold).xy(18.0, 14.0)
        val hint = label(tr("F1 oder Esc schließt", "F1 or Esc closes"), 13.0, Theme.TEXT_DIM)
        hint.xy(panelWidth - 22.0 - hint.width, 20.0)
        val column = (panelWidth - 36.0) / 2
        list.forEachIndexed { i, (keys, action) ->
            val x = 18.0 + (i / rows) * column
            val y = 60.0 + (i % rows) * 30.0
            label(keys, 14.0, Theme.GOLD, Theme.bodyBold).xy(x, y).fitWidth(KEY_WIDTH)
            label(action, 14.0, Theme.TEXT).xy(x + KEY_WIDTH + 10.0, y).fitWidth(column - KEY_WIDTH - 20.0)
        }
        TextButton(110.0, 36.0, tr("Schließen", "Close")) { onClose() }.addTo(this).xy(panelWidth - 128.0, panelHeight - 50.0)
    }

    private companion object {
        const val KEY_WIDTH = 120.0
    }
}
