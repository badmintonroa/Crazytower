package crazytower.game

import crazytower.core.Decor
import crazytower.core.Gates
import crazytower.core.Insurances
import crazytower.core.Mission
import crazytower.core.Promotion
import crazytower.core.Research
import crazytower.core.SINK_STAGE
import crazytower.core.Simulation
import crazytower.core.buyDecor
import crazytower.core.buyGate
import crazytower.core.campaignBlocked
import crazytower.core.campaignCost
import crazytower.core.campaignRunning
import crazytower.core.currentPart
import crazytower.core.decorBlocked
import crazytower.core.doResearch
import crazytower.core.gateBlocked
import crazytower.core.liftedOff
import crazytower.core.missionStarted
import crazytower.core.partBlocked
import crazytower.core.partyBlocked
import crazytower.core.partyCost
import crazytower.core.researchBlocked
import crazytower.core.researchCost
import crazytower.core.spectatorsPerDay
import crazytower.core.startCampaign
import crazytower.core.startTestflight
import crazytower.core.taskDone
import crazytower.core.taskProgress
import crazytower.core.testflightBlocked
import crazytower.core.throwParty
import crazytower.core.toggleInsurance
import crazytower.core.tr
import crazytower.game.ui.Panel
import crazytower.game.ui.TextButton
import crazytower.game.ui.fitWidth
import crazytower.game.ui.formatMoney
import crazytower.game.ui.formatNumber
import crazytower.game.ui.label
import crazytower.game.ui.wrappedLabel
import korlibs.korge.input.onOut
import korlibs.korge.input.onOver
import korlibs.korge.view.*

/**
 * Where a rich tower spends its money from stage 4 on: insurance, ads and the tower party,
 * decorations, side entrances and the Laboratory of Questionable Science. Opened with the
 * dollar button in the top bar (or X).
 */
class ExtrasWindow(private val sim: Simulation, private val game: GameController, private val onClose: () -> Unit) : Container() {
    val panelWidth = 760.0
    val panelHeight = 590.0
    private val body = container()
    private var shownHour = -1
    private var hovered = false

    init {
        Panel(panelWidth, panelHeight).addTo(this)
        addChild(body) // over the panel
        label(tr("EXTRAS FÜR REICHE TÜRME", "EXTRAS FOR RICH TOWERS"), 20.0, Theme.TEXT, Theme.bodyBold).xy(18.0, 14.0)
        val hint = label(tr("X oder Esc schließt", "X or Esc closes"), 12.0, Theme.TEXT_DIM)
        hint.xy(panelWidth - 22.0 - hint.width, 20.0)
        TextButton(110.0, 36.0, tr("Schließen", "Close")) { onClose() }.addTo(this).xy(panelWidth - 128.0, panelHeight - 50.0)
        onOver { hovered = true }
        onOut { hovered = false }
    }

    fun sync() {
        val hour = sim.clock.totalMinutes.toInt() / 60
        if (hour == shownHour || hovered) return
        rebuild()
    }

    private fun rebuild() {
        shownHour = sim.clock.totalMinutes.toInt() / 60
        body.removeChildren()
        if (sim.stage < SINK_STAGE) {
            body.wrappedLabel(
                tr("Ab Stufe $SINK_STAGE gibt es hier Versicherungen, Werbung, ein Turmfest, Deko, Seiteneingänge und das Labor für fragwürdige Wissenschaft. Bis dahin braucht jeder Dollar ein Zimmer.",
                    "From stage $SINK_STAGE on you'll find insurance, ads, a tower party, decorations, side entrances and the Laboratory of Questionable Science here. Until then every dollar needs a room."),
                panelWidth - 36, 14.0, Theme.TEXT_DIM,
            ).xy(18.0, 60.0)
            return
        }
        val colWidth = (panelWidth - 54) / 2
        var y = 52.0
        heading(tr("Versicherungen (pro Tag)", "Insurance (per day)"), 18.0, y)
        y += 24
        for (insurance in Insurances.all) {
            val on = insurance.id in sim.endgame.insurances
            val fee = formatMoney(Insurances.fee(sim, insurance))
            button(colWidth, "${insurance.name} · $fee", 18.0, y, selected = on) {
                sim.toggleInsurance(insurance.id)?.let { game.say(it) }
            }
            y += 34
        }
        y += 8
        heading(tr("Werbung und Feste", "Ads and parties"), 18.0, y)
        y += 24
        button(colWidth, tr("Werbekampagne, 3 Tage", "Ad campaign, 3 days") + " · ${formatMoney(sim.campaignCost())}", 18.0, y,
            blocked = sim.campaignBlocked(), selected = sim.campaignRunning) { result(sim.startCampaign()) }
        y += 34
        button(colWidth, tr("Turmfest, 2 Tage", "Tower party, 2 days") + " · ${formatMoney(sim.partyCost())}", 18.0, y,
            blocked = sim.partyBlocked()) { result(sim.throwParty()) }
        y += 34
        body.label(tr("Werbung: ${((Promotion.CAMPAIGN_VISITORS - 1) * 100).toInt()} % mehr Besucher, doppelt so viele VIPs. Fest: +${Promotion.PARTY_MOOD.toInt()} Laune.",
            "Ads: ${((Promotion.CAMPAIGN_VISITORS - 1) * 100).toInt()} % more visitors, twice the VIPs. Party: +${Promotion.PARTY_MOOD.toInt()} mood."), 11.0, Theme.TEXT_DIM)
            .xy(18.0, y).fitWidth(colWidth)

        val x2 = 36.0 + colWidth
        y = 52.0
        heading(tr("Deko (Schaulustige +${(Decor.SPECTATOR_BONUS * 100).toInt()} % je Stück)", "Decorations (spectators +${(Decor.SPECTATOR_BONUS * 100).toInt()} % each)"), x2, y)
        y += 24
        for (item in Decor.all) {
            val owned = item.id in sim.endgame.decor
            button(colWidth, item.name.toString() + if (owned) "" else " · ${formatMoney(item.cost)}", x2, y, blocked = sim.decorBlocked(item).takeUnless { owned }, selected = owned) {
                result(sim.buyDecor(item))
            }
            y += 34
        }
        y += 8
        heading(tr("Oma Hildegards Garten", "Granny Hildegard's garden"), x2, y)
        y += 24
        val gate = Gates.all.getOrNull(sim.endgame.gates)
        val gateTitle = if (gate == null) tr("Alle 3 Seiteneingänge gekauft", "All 3 side entrances bought")
        else tr("Seiteneingang ${sim.endgame.gates + 1} von 3", "Side entrance ${sim.endgame.gates + 1} of 3") + " · ${formatMoney(gate.cost)}"
        button(colWidth, gateTitle, x2, y, blocked = if (gate == null) null else sim.gateBlocked(), selected = gate == null) { result(sim.buyGate()) }
        y += 42
        heading(tr("Labor für fragwürdige Wissenschaft", "Laboratory of Questionable Science") + " · ${formatMoney(sim.researchCost())}", x2, y)
        y += 24
        for (study in Research.all) {
            val done = study.id in sim.endgame.research
            button(colWidth, study.name.toString(), x2, y, blocked = sim.researchBlocked(study).takeUnless { done }, selected = done, tip = study.effect.toString()) {
                result(sim.doResearch(study))
            }
            y += 34
        }
    }

    private fun heading(text: String, x: Double, y: Double) {
        body.label(text, 14.0, Theme.GOLD, Theme.bodyBold).xy(x, y).fitWidth((panelWidth - 54) / 2)
    }

    private fun button(width: Double, title: String, x: Double, y: Double, blocked: String? = null, selected: Boolean = false, tip: String? = null, onPress: () -> Unit) {
        val b = TextButton(width, 30.0, title, 12.0) {
            onPress()
            rebuild()
        }.addTo(body).xy(x, y)
        b.selected = selected
        b.enabled = blocked == null || selected
        if (blocked != null && !selected) b.onOver { game.say(blocked, 2.0) }
        if (tip != null) b.onOver { game.say(tip, 2.0) }
    }

    private fun result(check: crazytower.core.PlaceCheck) {
        Sfx.play(if (check.ok) Sfx.BUILD else Sfx.ERROR)
        if (!check.ok) game.say(check.reason)
    }
}

/**
 * The moon mission: where the countdown stands, what the current station part still needs,
 * and how the spectators are doing. Opened by clicking the stage box at stage 5 (or M).
 */
class MissionWindow(private val sim: Simulation, private val game: GameController, private val onClose: () -> Unit) : Container() {
    val panelWidth = 620.0
    val panelHeight = 470.0
    private val body = container()
    private var shownMinute = -1
    private var hovered = false

    init {
        Panel(panelWidth, panelHeight).addTo(this)
        addChild(body) // over the panel
        label(tr("MONDMISSION", "MOON MISSION"), 20.0, Theme.TEXT, Theme.bodyBold).xy(18.0, 14.0)
        val hint = label(tr("M oder Esc schließt", "M or Esc closes"), 12.0, Theme.TEXT_DIM)
        hint.xy(panelWidth - 22.0 - hint.width, 20.0)
        TextButton(110.0, 36.0, tr("Schließen", "Close")) { onClose() }.addTo(this).xy(panelWidth - 128.0, panelHeight - 50.0)
        onOver { hovered = true }
        onOut { hovered = false }
    }

    fun sync() {
        val minute = sim.clock.totalMinutes.toInt() / 10
        if (minute == shownMinute || hovered) return
        rebuild()
    }

    private fun rebuild() {
        shownMinute = sim.clock.totalMinutes.toInt() / 10
        body.removeChildren()
        val w = panelWidth - 36
        if (!sim.missionStarted) {
            body.wrappedLabel(
                tr("Mit Stufe 5 beginnt die Mondmission: Auf der obersten Etage entsteht Teil für Teil eine Mondstation, und jedes Teil braucht Aufträge im Turm.",
                    "The moon mission starts at stage 5: part by part, a moon station rises on the top floor, and every part needs tasks done in the tower."),
                w, 14.0, Theme.TEXT_DIM,
            ).xy(18.0, 60.0)
            return
        }
        // the countdown as four steps
        var x = 18.0
        val stepWidth = (w - 3 * 8) / 4
        for (part in Mission.parts) {
            val done = sim.endgame.parts > part.index
            val current = sim.currentPart == part
            val box = Panel(stepWidth, 52.0, if (current) Theme.SELECT else if (done) Theme.GOOD else Theme.BUTTON, 6.0).addTo(body).also { it.xy(x, 52.0) }
            val ink = if (current || done) Theme.ON_SELECT else Theme.TEXT
            box.label(part.step.toString().uppercase(), 13.0, ink, Theme.bodyBold).xy(8.0, 6.0).fitWidth(stepWidth - 16)
            box.label(part.type.name.toString(), 12.0, ink).xy(8.0, 28.0).fitWidth(stepWidth - 16)
            x += stepWidth + 8
        }
        var y = 120.0
        val part = sim.currentPart
        if (part == null || sim.liftedOff) {
            body.wrappedLabel(
                tr("Der Mondlift fährt! Seit Tag ${sim.endgame.liftOffDay ?: sim.clock.day} pendeln Schaulustige zwischen Turm und Mond. Der Turm baut einfach weiter.",
                    "The moon lift is running! Since day ${sim.endgame.liftOffDay ?: sim.clock.day} spectators have been commuting between the tower and the moon. The tower just keeps growing."),
                w, 14.0, Theme.TEXT,
            ).xy(18.0, y)
            y += 60
        } else {
            body.label(tr("Als Nächstes: ${part.type.name} · ${formatMoney(part.type.cost)} · auf Etage ${Mission.FLOOR}", "Next: ${part.type.name} · ${formatMoney(part.type.cost)} · on floor ${Mission.FLOOR}"),
                15.0, Theme.GOLD, Theme.bodyBold).xy(18.0, y).fitWidth(w)
            y += 28
            for (task in part.tasks) {
                val done = sim.taskDone(task)
                body.label((if (done) "✓ " else "") + task.title, 14.0, if (done) Theme.GOOD else Theme.TEXT, Theme.bodyBold).xy(18.0, y).fitWidth(w * 0.6)
                val count = "${formatNumber(sim.taskProgress(task))} / ${formatNumber(task.goal)}"
                val countLabel = body.label(count, 13.0, Theme.TEXT_DIM, Theme.bodyBold)
                countLabel.xy(18.0 + w - countLabel.width, y)
                y += 20
                val text = body.wrappedLabel(task.hint.toString(), w, 12.0, Theme.TEXT_DIM).xy(18.0, y)
                y += text.height + 4
                val share = (sim.taskProgress(task).toDouble() / task.goal).coerceIn(0.0, 1.0)
                body.solidRect(w, 4.0, Theme.INK).xy(18.0, y)
                body.solidRect(w * share, 4.0, if (done) Theme.GOOD else Theme.SELECT).xy(18.0, y)
                y += 10
                val button = task.button
                if (button != null && !done) {
                    val blocked = sim.testflightBlocked()
                    TextButton(200.0, 32.0, button.toString(), 13.0) {
                        val result = sim.startTestflight()
                        Sfx.play(if (result.ok) Sfx.BUILD else Sfx.ERROR)
                        if (!result.ok) game.say(result.reason)
                        rebuild()
                    }.addTo(body).xy(18.0, y).also { it.enabled = blocked == null }
                    if (blocked != null) body.label(blocked, 11.0, Theme.TEXT_DIM).xy(228.0, y + 8).fitWidth(w - 210)
                    y += 38
                }
                y += 8
            }
            val blocked = sim.partBlocked(part.type, Mission.FLOOR)
            val status = blocked ?: tr("Bereit! Bau die ${part.type.name} im Baumenü unter Spezial auf Etage ${Mission.FLOOR}.", "Ready! Build the ${part.type.name} from the Special menu on floor ${Mission.FLOOR}.")
            body.wrappedLabel(status, w, 13.0, if (blocked == null) Theme.GOOD else Theme.TEXT_DIM).xy(18.0, y)
            y += 30
        }
        val e = sim.endgame
        val decks = sim.tower.allRooms.count { it.type == Mission.DECK }
        body.label(tr("Schaulustige: ${sim.spectatorsPerDay} pro Tag · heute ${e.spectatorsToday} da, ${e.spectatorsUnhappy} verärgert",
            "Spectators: ${sim.spectatorsPerDay} a day · today ${e.spectatorsToday} came, ${e.spectatorsUnhappy} annoyed"), 13.0, Theme.TEXT).xy(18.0, panelHeight - 86.0).fitWidth(w)
        body.label(if (decks == 0) tr("Ohne Aussichtsplattform (ab Etage 90) gehen alle verärgert heim.", "Without an observation deck (floor 90 and up) everybody goes home annoyed.")
        else tr("Mehr als ${(Mission.PRESS_SHARE * 100).toInt()} % verärgert: Die Presse lacht und der Countdown wartet einen Tag.", "More than ${(Mission.PRESS_SHARE * 100).toInt()} % annoyed: the press laughs and the countdown waits a day."),
            11.0, Theme.TEXT_DIM).xy(18.0, panelHeight - 66.0).fitWidth(panelWidth - 170)
    }
}
