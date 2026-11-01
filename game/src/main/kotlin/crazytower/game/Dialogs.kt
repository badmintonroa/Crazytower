package crazytower.game

import crazytower.core.tr
import crazytower.game.ui.Panel
import crazytower.game.ui.TextButton
import crazytower.game.ui.label
import crazytower.game.ui.wrappedLabel
import korlibs.korge.view.*

/** A question before something that can't be undone: title, what it means, yes and no. Enter says yes, Esc no. */
class ConfirmWindow(question: Confirm, onNo: () -> Unit, onYes: () -> Unit) : Container() {
    val panelWidth = 460.0
    val panelHeight: Double

    init {
        val bg = Panel(panelWidth, 100.0).addTo(this)
        Panel(panelWidth, 4.0, Theme.ALARM, 0.0).addTo(this)
        val title = wrappedLabel(question.title, panelWidth - 40.0, 19.0, Theme.TEXT, Theme.bodyBold).xy(20.0, 18.0)
        val body = wrappedLabel(question.body, panelWidth - 40.0, 14.0, Theme.TEXT_DIM).xy(20.0, title.y + title.height + 10.0)
        val y = body.y + body.height + 20.0
        val no = TextButton(130.0, 40.0, tr("Abbrechen", "Cancel"), 15.0) { onNo() }.addTo(this)
        val yes = TextButton(130.0, 40.0, question.yes, 15.0) { onYes() }.addTo(this)
        yes.xy(panelWidth - 20.0 - yes.buttonWidth, y)
        no.xy(yes.x - 10.0 - no.buttonWidth, y)
        label(tr("Enter: ja · Esc: nein", "Enter: yes · Esc: no"), 13.0, Theme.TEXT_DIM).xy(20.0, y + 11.0)
        panelHeight = y + 40.0 + 20.0
        bg.resize(panelWidth, panelHeight)
    }
}

/**
 * The pause menu on Esc: go on, save, options, keys, main menu. The game stands still while
 * it is open (see [GameController.pauseMenuOpen]).
 */
class PauseMenu(private val game: GameController) : Container() {
    val panelWidth = 340.0
    val panelHeight: Double

    init {
        val entries = buildList {
            add(tr("Weiterspielen", "Resume") to { game.pauseMenuOpen = false })
            game.onSave?.let { save -> add(tr("Speichern · F5", "Save · F5") to { game.say(save()) }) }
            add(tr("Optionen", "Options") to { game.optionsOpen = true })
            add(tr("Alle Tasten · F1", "All keys · F1") to { game.helpOpen = true })
            game.onMainMenu?.let { menu -> add(tr("Zum Hauptmenü", "Main menu") to { menu() }) }
        }
        panelHeight = 70.0 + entries.size * 50.0 + 20.0
        Panel(panelWidth, panelHeight).addTo(this)
        label(tr("PAUSE", "PAUSED"), 20.0, Theme.TEXT, Theme.bodyBold).xy(20.0, 16.0)
        val hint = label(tr("Esc spielt weiter", "Esc resumes"), 13.0, Theme.TEXT_DIM)
        hint.xy(panelWidth - 20.0 - hint.width, 22.0)
        entries.forEachIndexed { i, (title, action) ->
            TextButton(panelWidth - 40.0, 42.0, title, 16.0) { action() }.addTo(this).xy(20.0, 64.0 + i * 50.0).also {
                it.selected = i == 0
            }
        }
    }
}
