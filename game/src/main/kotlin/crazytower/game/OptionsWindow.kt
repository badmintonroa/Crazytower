package crazytower.game

import crazytower.core.Language
import crazytower.core.Settings
import crazytower.core.TestTower
import crazytower.game.ui.Panel
import crazytower.game.ui.TextButton
import crazytower.game.ui.fitWidth
import crazytower.game.ui.label
import crazytower.core.tr
import korlibs.korge.view.*

/**
 * The options window: display, interface size and what the tower shows. Every row is a
 * button that steps to the next choice; changes apply at once and are saved.
 * In the game [onMainMenu] adds a row that goes back to the title screen.
 */
class OptionsWindow(
    private val onTestTower: (Int) -> Unit,
    private val onMainMenu: (() -> Unit)? = null,
    private val onClose: () -> Unit,
) : Container() {
    val panelWidth = 520.0
    val panelHeight: Double
    private val buttons = ArrayList<Pair<TextButton, (Settings) -> String>>()

    private class Row(val title: String, val value: (Settings) -> String, val next: (Settings) -> Settings)

    private val rows = listOf(
        // both names, so it can be found in a language one does not read
        Row(tr("Sprache · Language", "Language · Sprache"), { it.language.label }) { it.copy(language = next(Language.entries, it.language)) },
        Row(tr("Anzeige", "Display"), { it.displayMode }) { it.nextDisplayMode() },
        Row(tr("Fenstergröße", "Window size"), { "${it.windowWidth} × ${it.windowHeight}" }) {
            val sizes = Settings.WINDOW_SIZES
            val (w, h) = sizes[(sizes.indexOf(it.windowWidth to it.windowHeight) + 1) % sizes.size]
            it.copy(windowWidth = w, windowHeight = h)
        },
        Row(tr("Oberfläche", "Interface size"), { "${(it.uiScale * 100).toInt()} %" }) { it.copy(uiScale = next(Settings.UI_SCALES, it.uiScale)) },
        Row("VSync", { onOff(it.vsync) }) { it.copy(vsync = !it.vsync) },
        Row(tr("Tag und Nacht", "Day and night"), { onOff(it.dayNight) }) { it.copy(dayNight = !it.dayNight) },
        Row(tr("Leute im Turm", "People shown"), {
            when (it.crowd) { 1.0 -> tr("alle", "all"); 0.5 -> tr("jeder Zweite", "every second"); else -> tr("jeder Vierte", "every fourth") }
        }) { it.copy(crowd = next(Settings.CROWDS, it.crowd)) },
        Row(tr("Geld-Anzeigen", "Money pop-ups"), { onOff(it.moneyPopups) }) { it.copy(moneyPopups = !it.moneyPopups) },
        Row(tr("Nachts vorspulen", "Fast-forward nights"), { onOff(it.fastNights) }) { it.copy(fastNights = !it.fastNights) },
        Row(tr("Lautstärke", "Volume"), { if (it.volume == 0) tr("stumm", "muted") else "${it.volume} %" }) { it.copy(volume = (it.volume + 20) % 120) },
        Row(tr("Autospeichern", "Autosave"), {
            when (it.autosaveDays) {
                0 -> onOff(false)
                1 -> tr("jeden Tag", "every day")
                7 -> tr("jede Woche", "every week")
                else -> tr("alle ${it.autosaveDays} Tage", "every ${it.autosaveDays} days")
            }
        }) { it.copy(autosaveDays = next(Settings.AUTOSAVES, it.autosaveDays)) },
    )

    init {
        panelHeight = 72.0 + rows.size * 40.0 + 40.0 + (if (onMainMenu != null) 40.0 else 0.0) + 70.0
        Panel(panelWidth, panelHeight).addTo(this)
        label(tr("OPTIONEN", "OPTIONS"), 20.0, Theme.TEXT, Theme.bodyBold).xy(18.0, 14.0)
        val hint = label(tr("O oder Esc schließt", "O or Esc closes"), 12.0, Theme.TEXT_DIM)
        hint.xy(panelWidth - 22.0 - hint.width, 20.0)
        var y = 60.0
        for (row in rows) {
            label(row.title, 15.0, Theme.TEXT, Theme.bodyBold).xy(22.0, y + 9).fitWidth(TITLE_WIDTH)
            val button = TextButton(220.0, 34.0, row.value(Options.current), 14.0) {
                Options.update(row.next)
            }
            button.xy(panelWidth - 240.0, y)
            addChild(button)
            buttons += button to row.value
            y += 40.0
        }
        // test mode: jump into a finished tower of each stage
        label(tr("Testturm", "Test tower"), 15.0, Theme.TEXT, Theme.bodyBold).xy(22.0, y + 9).fitWidth(TITLE_WIDTH)
        for (stage in 1..TestTower.MAXIMUM) {
            val max = stage == TestTower.MAXIMUM
            TextButton(if (max) 50.0 else 30.0, 34.0, if (max) "Max" else "$stage", 14.0) { onTestTower(stage) }
                .addTo(this).xy(panelWidth - 240.0 + (stage - 1) * 34.0, y)
        }
        if (onMainMenu != null) {
            y += 40.0
            label(tr("Spiel", "Game"), 15.0, Theme.TEXT, Theme.bodyBold).xy(22.0, y + 9).fitWidth(TITLE_WIDTH)
            TextButton(220.0, 34.0, tr("Zum Hauptmenü", "Main menu"), 14.0) { onMainMenu.invoke() }.addTo(this).xy(panelWidth - 240.0, y)
        }
        label(tr("Gespeichert in ", "Saved in ") + Options.file.path.replace(System.getProperty("user.home"), "~"), 11.0, Theme.TEXT_DIM)
            .xy(22.0, panelHeight - 40.0).fitWidth(panelWidth - 128.0 - 34.0)
        TextButton(110.0, 36.0, tr("Schließen", "Close")) { onClose() }.addTo(this).xy(panelWidth - 128.0, panelHeight - 50.0)
    }

    fun sync() {
        val s = Options.current
        for ((button, value) in buttons) {
            val text = value(s)
            if (button.text.text != text) button.setTitle(text)
        }
    }

    private companion object {
        fun onOff(on: Boolean) = if (on) tr("an", "on") else tr("aus", "off")

        /** Row names end before the buttons. */
        const val TITLE_WIDTH = 520.0 - 240.0 - 22.0 - 12.0

        fun <T> next(choices: List<T>, now: T): T = choices[(choices.indexOf(now) + 1) % choices.size]
    }
}
