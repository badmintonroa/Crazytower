package crazytower.game

import crazytower.core.Settings
import crazytower.game.ui.Panel
import crazytower.game.ui.TextButton
import crazytower.game.ui.label
import korlibs.korge.view.*

/**
 * The options window: display, interface size and what the tower shows. Every row is a
 * button that steps to the next choice; changes apply at once and are saved.
 */
class OptionsWindow(private val onClose: () -> Unit) : Container() {
    val panelWidth = 520.0
    val panelHeight: Double
    private val buttons = ArrayList<Pair<TextButton, (Settings) -> String>>()

    private class Row(val title: String, val value: (Settings) -> String, val next: (Settings) -> Settings)

    private val rows = listOf(
        Row("Anzeige", { if (it.fullscreen) "Vollbild" else "Fenster" }) { it.copy(fullscreen = !it.fullscreen) },
        Row("Fenstergröße", { "${it.windowWidth} × ${it.windowHeight}" }) {
            val sizes = Settings.WINDOW_SIZES
            val (w, h) = sizes[(sizes.indexOf(it.windowWidth to it.windowHeight) + 1) % sizes.size]
            it.copy(windowWidth = w, windowHeight = h)
        },
        Row("Oberfläche", { "${(it.uiScale * 100).toInt()} %" }) { it.copy(uiScale = next(Settings.UI_SCALES, it.uiScale)) },
        Row("VSync", { onOff(it.vsync) }) { it.copy(vsync = !it.vsync) },
        Row("Tag und Nacht", { onOff(it.dayNight) }) { it.copy(dayNight = !it.dayNight) },
        Row("Leute im Turm", {
            when (it.crowd) { 1.0 -> "alle"; 0.5 -> "jeder Zweite"; else -> "jeder Vierte" }
        }) { it.copy(crowd = next(Settings.CROWDS, it.crowd)) },
        Row("Geld-Anzeigen", { onOff(it.moneyPopups) }) { it.copy(moneyPopups = !it.moneyPopups) },
        Row("Nachts vorspulen", { onOff(it.fastNights) }) { it.copy(fastNights = !it.fastNights) },
        Row("Lautstärke", { if (it.volume == 0) "stumm" else "${it.volume} %" }) { it.copy(volume = (it.volume + 20) % 120) },
        Row("Autospeichern", {
            when (it.autosaveDays) { 0 -> "aus"; 1 -> "jeden Tag"; 7 -> "jede Woche"; else -> "alle ${it.autosaveDays} Tage" }
        }) { it.copy(autosaveDays = next(Settings.AUTOSAVES, it.autosaveDays)) },
    )

    init {
        panelHeight = 72.0 + rows.size * 40.0 + 70.0
        Panel(panelWidth, panelHeight).addTo(this)
        label("OPTIONEN", 20.0, Theme.TEXT, Theme.bodyBold).xy(18.0, 14.0)
        label("O oder Esc schließt", 12.0, Theme.TEXT_DIM).xy(panelWidth - 140.0, 20.0)
        var y = 60.0
        for (row in rows) {
            label(row.title, 15.0, Theme.TEXT, Theme.bodyBold).xy(22.0, y + 9)
            val button = TextButton(220.0, 34.0, row.value(Options.current), 14.0) {
                Options.update(row.next)
            }
            button.xy(panelWidth - 240.0, y)
            addChild(button)
            buttons += button to row.value
            y += 40.0
        }
        label("Gespeichert in ${Options.file.path.replace(System.getProperty("user.home"), "~")}", 11.0, Theme.TEXT_DIM)
            .xy(22.0, panelHeight - 40.0)
        TextButton(110.0, 36.0, "Schließen") { onClose() }.addTo(this).xy(panelWidth - 128.0, panelHeight - 50.0)
    }

    fun sync() {
        val s = Options.current
        for ((button, value) in buttons) {
            val text = value(s)
            if (button.text.text != text) button.setTitle(text)
        }
    }

    private companion object {
        fun onOff(on: Boolean) = if (on) "an" else "aus"

        fun <T> next(choices: List<T>, now: T): T = choices[(choices.indexOf(now) + 1) % choices.size]
    }
}
