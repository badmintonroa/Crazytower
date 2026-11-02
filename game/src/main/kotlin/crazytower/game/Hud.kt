package crazytower.game

import crazytower.core.Catalog
import crazytower.core.Edition
import crazytower.core.Category
import crazytower.core.GameClock
import crazytower.core.NewsItem
import crazytower.core.Rating
import crazytower.core.RoomRole
import crazytower.core.RoomType
import crazytower.core.Simulation
import crazytower.core.Stages
import crazytower.core.Tenants
import crazytower.core.formatMoneyRange
import crazytower.core.Mission
import crazytower.core.SINK_STAGE
import crazytower.core.currentPart
import crazytower.core.liftedOff
import crazytower.core.missionProgress
import crazytower.core.missionStarted
import crazytower.core.spectatorsPerDay
import crazytower.core.openDayBlocked
import crazytower.core.startOpenDay
import crazytower.core.undo
import crazytower.core.tr
import crazytower.game.ui.BakedText
import crazytower.game.ui.bakedLabel
import crazytower.game.ui.Button
import crazytower.game.ui.Icons
import crazytower.game.ui.Panel
import crazytower.game.ui.TextButton
import crazytower.game.ui.Tip
import crazytower.game.ui.TipLayer
import crazytower.game.ui.tip
import crazytower.game.ui.formatMoney
import crazytower.game.ui.fitWidth
import crazytower.game.ui.formatNumber
import crazytower.game.ui.label
import crazytower.game.ui.wrapText
import crazytower.game.ui.wrappedLabel
import crazytower.game.ui.panel
import korlibs.event.Key
import korlibs.korge.input.keys
import korlibs.korge.input.onClick
import korlibs.korge.view.*
import korlibs.math.geom.Point
import korlibs.math.geom.Rectangle
import korlibs.math.geom.Size

/**
 * Screen-space interface: top bar, build bar with fly-out list, inspector, ticker,
 * placement tooltip and short messages. Laid out for the virtual screen it gets: 800 high
 * and as wide as the window's aspect ratio needs (see [crazytower.core.Settings.virtualSize]).
 */
class Hud(
    private val sim: Simulation,
    private val game: GameController,
    private val assets: Assets,
    private val tower: TowerView,
    private val screenWidth: Double,
    private val screenHeight: Double,
) : Container() {
    private val topBar = container { xy(12.0, 12.0) }
    private val buildBar = panel(88.0, 470.0).also { it.xy(12.0, 84.0) }
    private val flyout = panel(FLYOUT_WIDTH, 100.0).also { it.xy(110.0, 84.0) }
    private val inspector = Inspector(sim, game, assets).addTo(this).also { it.xy(screenWidth - 302.0, 84.0) }
    private val ticker = container { xy(0.0, screenHeight - 50.0) }
    private val finance = FinanceWindow(sim) { game.financeOpen = false }.addTo(this).also {
        it.xy((screenWidth - it.panelWidth) / 2, 96.0)
    }
    private val options = OptionsWindow(onTestTower = { game.onTestTower(it) }, onMainMenu = game.onMainMenu) { game.optionsOpen = false }.addTo(this).also {
        it.xy((screenWidth - it.panelWidth) / 2, ((screenHeight - it.panelHeight) / 2).coerceAtLeast(84.0))
    }
    // right of the build list where the screen is wide enough
    private val extras = ExtrasWindow(sim, game) { game.extrasOpen = false }.addTo(this).also {
        it.xy(((screenWidth - it.panelWidth) / 2).coerceAtLeast(110.0 + FLYOUT_WIDTH + 10).coerceAtMost(screenWidth - it.panelWidth - 12), 96.0)
    }
    private val mission = MissionWindow(sim, game) { game.missionOpen = false }.addTo(this).also {
        it.xy(((screenWidth - it.panelWidth) / 2).coerceAtLeast(110.0 + FLYOUT_WIDTH + 10).coerceAtMost(screenWidth - it.panelWidth - 12), 96.0)
    }
    private val help = HelpWindow { game.helpOpen = false }.addTo(this).also {
        it.xy((screenWidth - it.panelWidth) / 2, ((screenHeight - it.panelHeight) / 2).coerceAtLeast(84.0))
    }
    private val celebration = Celebration(screenWidth, screenHeight).addTo(this)
    private val demoEnd = DemoEndWindow(screenWidth, screenHeight, game.onMainMenu) { game.onQuit() }.addTo(this)
    /** Darkens the game behind the pause menu and questions; they need an answer first. */
    private val dim = solidRect(screenWidth, screenHeight, Theme.alpha(Theme.INK, 0.55)).also { it.visible = false }
    private val pauseMenu = PauseMenu(game).addTo(this).also {
        it.xy((screenWidth - it.panelWidth) / 2, ((screenHeight - it.panelHeight) / 2).coerceAtLeast(84.0))
        it.visible = false
    }
    // in front of the dimming, so a message from the pause menu stays readable
    private val tooltip = container { visible = false }
    private val toast = container { visible = false }

    private var confirmWindow: ConfirmWindow? = null
    private var shownConfirm: Confirm? = null

    /** Hover tips, in front of everything else. */
    private val tips = TipLayer(screenWidth, screenHeight).addTo(this)

    private lateinit var moneyText: Text
    private lateinit var nextText: Text
    private lateinit var popText: Text
    private lateinit var popLabel: Text
    private lateinit var stageText: Text
    private lateinit var stageHint: Text
    private lateinit var stageBar: Graphics
    private var stageBarWidth = 164.0
    private lateinit var stars: Container
    private lateinit var moodText: Text
    private lateinit var starHint: Text
    private var shownStars = -1
    private lateinit var clockText: Text
    private val speedButtons = ArrayList<Pair<Int, TextButton>>()
    private lateinit var openDayButton: TextButton
    private lateinit var extrasButton: TextButton
    private lateinit var extrasLock: View
    private var shownParts = -1
    private val categoryButtons = HashMap<Category, Button>()

    /** The little star on a category button that holds room types the player has not seen yet. */
    private val categoryNews = HashMap<Category, View>()
    private var flyoutKey: List<Any?>? = null
    private lateinit var tickerText: BakedText
    private var tickerWidth = 0.0
    private lateinit var tickerClip: Container
    private var tickerOffset = 0.0
    private var tickerItems: List<NewsItem> = emptyList()

    init {
        buildTopBar()
        buildBuildBar()
        buildTicker()
        setupKeys()
        // the end of the demo covers everything, toasts and windows too
        addChild(demoEnd)
    }

    /** True when [point] (screen coordinates) lies on a HUD element, so the tower ignores the click. */
    fun isOverUi(point: Point): Boolean {
        // the pause menu and questions take the whole screen
        if (game.pauseMenuOpen || game.confirm != null) return true
        val rects = buildList {
            add(Rectangle(0.0, 0.0, screenWidth, 78.0))
            add(Rectangle(buildBar.x, buildBar.y, buildBar.panelWidth, buildBar.panelHeight))
            if (flyout.visible) add(Rectangle(flyout.x, flyout.y, flyout.panelWidth, flyout.panelHeight))
            if (inspector.visible) add(Rectangle(inspector.x, inspector.y, inspector.panelWidth, inspector.panelHeight))
            if (finance.visible) add(Rectangle(finance.x, finance.y, finance.panelWidth, finance.panelHeight))
            if (extras.visible) add(Rectangle(extras.x, extras.y, extras.panelWidth, extras.panelHeight))
            if (mission.visible) add(Rectangle(mission.x, mission.y, mission.panelWidth, mission.panelHeight))
            if (options.visible) add(Rectangle(options.x, options.y, options.panelWidth, options.panelHeight))
            if (help.visible) add(Rectangle(help.x, help.y, help.panelWidth, help.panelHeight))
            if (celebration.isShowing || demoEnd.visible) add(Rectangle(0.0, 0.0, screenWidth, screenHeight))
            add(Rectangle(0.0, screenHeight - 50.0, screenWidth, 50.0))
        }
        return rects.any { it.contains(point) }
    }

    /** Shows the celebration of the current star right away; for screenshots. Stars and stages share one since they are one ladder. */
    @Suppress("UNUSED_PARAMETER")
    fun celebrate(stage: Boolean) {
        celebration.showStage(sim.stars)
    }

    // ---------------------------------------------------------------- top bar

    private fun buildTopBar() {
        var x = 0.0
        fun box(width: Double, block: Container.() -> Unit): Container {
            val c = topBar.container { xy(x, 0.0) }
            c.panel(width, 60.0)
            c.block()
            x += width + 10
            return c
        }
        box(200.0) {
            coin(this, 16.0, 19.0)
            moneyText = label("", 20.0, Theme.TEXT, Theme.bodyBold).also { it.xy(46.0, 9.0) }
            nextText = label("", 11.0, Theme.TEXT_DIM).also { it.xy(46.0, 36.0) }
            // the money opens the finance window
            onClick { game.financeOpen = !game.financeOpen }
            tip(tr("Geld", "Money"), tr("Darunter die nächste Einnahme. Klick öffnet die Finanzen.", "Below it the next income. Click to open the finances."), "F")
        }
        box(180.0) {
            solidRect(8.0, 8.0, Theme.alpha(Theme.SELECT, 0.9)).xy(20.0, 18.0)
            solidRect(14.0, 12.0, Theme.alpha(Theme.SELECT, 0.6)).xy(17.0, 28.0)
            // the number on top, the mood under it, like the money; the tip says what they are
            popText = label("", 20.0, Theme.TEXT, Theme.bodyBold).also { it.xy(42.0, 9.0) }
            popLabel = label(tr("Bewohner", "Residents"), 12.0, Theme.TEXT_DIM)
            moodText = label("", 12.0, Theme.TEXT_DIM).also { it.xy(42.0, 36.0) }
            tip {
                Tip(
                    tr("Bewohner und Laune", "Residents and mood"),
                    tr("Alle Leute, die im Turm wohnen oder arbeiten, und wie zufrieden sie im Schnitt sind. Die Sterne 3 bis 5 brauchen gute Laune, sonst ziehen jede Nacht die Unzufriedensten aus.",
                        "Everyone who lives or works in the tower, and how happy they are on average. Stars 3 to 5 need a good mood, or the unhappiest move out every night."),
                )
            }
        }
        val stageWidth = screenWidth - 24 - x - 150 - 10 - SPEED_BOX - 10 - 60 - 10 - 60 - 10 - 60
        box(stageWidth) {
            stars = container { xy(16.0, 12.0) }
            starHint = label("", 11.0, Theme.GOLD).also { it.xy(16.0, 36.0) }
            // the stars and the hint under them: everything the next star still needs
            solidRect(STAR_HINT, 44.0, Theme.alpha(Theme.INK, 0.0)).xy(16.0, 8.0).tip { starTip() }
            stageText = label("", 16.0, Theme.TEXT, Theme.bodyBold).also { it.xy(152.0, 12.0) }
            stageHint = label("", 12.0, Theme.TEXT_DIM).also { it.xy(152.0, 34.0) }
            stageBarWidth = stageWidth - 168.0
            stageBar = graphics { }.also { it.xy(152.0, 50.0) }
            // from stage 5 on the stage box is the moon mission's countdown
            onClick { if (sim.missionStarted) game.missionOpen = !game.missionOpen }
            solidRect(stageWidth - 152.0, 52.0, Theme.alpha(Theme.INK, 0.0)).xy(148.0, 4.0).tip { stageTip() }
        }
        box(150.0) {
            sun(this, 16.0, 19.0)
            clockText = label("", 18.0, Theme.TEXT, Theme.bodyBold).also { it.xy(46.0, 18.0) }
            tip {
                Tip(
                    tr("Tag ${sim.clock.day + 1}, ${sim.clock.format()}", "Day ${sim.clock.day + 1}, ${sim.clock.format()}"),
                    (if (game.fastNights) tr("Nachts vorspulen ist an: Von 23 bis 6 Uhr läuft die Zeit viermal so schnell (»).", "Fast-forward nights is on: from 11 pm to 6 am time runs four times as fast (»).")
                    else tr("Nachts vorspulen ist aus.", "Fast-forward nights is off.")) + tr(" Rot heißt: Pause.", " Red means paused."),
                    "N",
                )
            }
        }
        box(SPEED_BOX) {
            val speeds = GameController.SPEEDS.map { it to if (it == 0) "II" else "$it×" }
            speeds.forEachIndexed { i, (speed, title) ->
                val b = TextButton(40.0, 40.0, title) { game.speed = speed }
                b.xy(10.0 + i * 44.0, 10.0)
                if (speed == 0) b.tip(tr("Pause", "Pause"), key = tr("Leertaste", "Space"))
                else b.tip(tr("Tempo $speed×", "Speed $speed×"), tr("Ein Tag dauert ${dayLength(speed)}.", "A day takes ${dayLength(speed)}."), Shortcuts.SPEED)
                addChild(b)
                speedButtons += speed to b
            }
        }
        box(60.0) {
            // the open day: balloons for everybody, once a week
            openDayButton = TextButton(40.0, 40.0, "", onPress = {
                val result = sim.startOpenDay()
                game.say(if (result.ok) tr("Tag der offenen Tür! Bis 22 Uhr kommen doppelt so viele Besucher.", "Open day! Twice the visitors until 10 pm.") else result.reason)
                Sfx.play(if (result.ok) Sfx.BUILD else Sfx.ERROR)
            })
            balloon(openDayButton.content)
            openDayButton.xy(10.0, 10.0)
            openDayButton.tip {
                val running = sim.activeEvents.any { it.id == "openday" }
                Tip(
                    tr("Tag der offenen Tür", "Open day"),
                    if (running) tr("Läuft gerade: Bis 22 Uhr kommen doppelt so viele Besucher.", "Running now: twice the visitors until 10 pm.")
                    else tr("Bis 22 Uhr kommen doppelt so viele Besucher. Einmal pro Woche.", "Twice the visitors until 10 pm. Once a week."),
                    locked = if (running) null else sim.openDayBlocked(),
                )
            }
            addChild(openDayButton)
        }
        box(60.0) {
            // extras for rich towers: where the money goes from stage 4 on
            extrasButton = TextButton(40.0, 40.0, "$", 20.0, onPress = { game.extrasOpen = !game.extrasOpen })
            extrasButton.xy(10.0, 10.0)
            extrasLock = Icons.lock(extrasButton, Theme.TEXT_LOCKED).xy(25.0, 3.0)
            extrasButton.tip {
                Tip(
                    tr("Extras für reiche Türme", "Extras for rich towers"),
                    tr("Versicherungen, Werbung, Turmfest, Deko, Seiteneingänge und das Labor für fragwürdige Wissenschaft.", "Insurance, ads, the tower party, decorations, side entrances and the laboratory of questionable science."),
                    "X",
                    locked = if (sim.stage < SINK_STAGE) tr("Ab $SINK_STAGE Sternen", "From $SINK_STAGE stars") else null,
                )
            }
            addChild(extrasButton)
        }
        box(60.0) {
            val b = TextButton(40.0, 40.0, "", onPress = { game.optionsOpen = !game.optionsOpen })
            Icons.gear(b.content, Theme.TEXT)
            b.xy(10.0, 10.0)
            b.tip(tr("Optionen", "Options"), tr("Sprache, Anzeige, Lautstärke, Autospeichern, Hauptmenü. F1 zeigt alle Tasten.", "Language, display, volume, autosave, main menu. F1 shows all keys."), "O")
            addChild(b)
        }
    }

    /** How long a game day takes in real time at [speed]: "6 Min." or "45 Sek.". */
    private fun dayLength(speed: Int): String {
        val seconds = (GameClock.MINUTES_PER_DAY / (GameController.GAME_MINUTES_PER_SECOND * speed)).toInt()
        return if (seconds >= 120 && seconds % 60 == 0) tr("${seconds / 60} Minuten", "${seconds / 60} minutes") else tr("$seconds Sekunden", "$seconds seconds")
    }

    private fun coin(c: Container, x: Double, y: Double) {
        c.graphics {
            fill(Theme.GOLD) { moveTo(Point(11.0, 0.0)); lineTo(Point(22.0, 6.0)); lineTo(Point(22.0, 17.0)); lineTo(Point(11.0, 23.0)); lineTo(Point(0.0, 17.0)); lineTo(Point(0.0, 6.0)); close() }
            fill(Theme.alpha(Theme.ON_SELECT, 0.25)) { moveTo(Point(11.0, 0.0)); lineTo(Point(22.0, 6.0)); lineTo(Point(22.0, 17.0)); lineTo(Point(11.0, 23.0)); close() }
            fill(Theme.ON_SELECT) { moveTo(Point(10.0, 6.0)); lineTo(Point(12.0, 6.0)); lineTo(Point(12.0, 17.0)); lineTo(Point(10.0, 17.0)); close() }
        }.xy(x, y)
    }

    private fun balloon(c: Container) {
        c.graphics {
            fill(Theme.alpha(Theme.TEXT, 0.8)) { rect(19.5, 24.0, 1.5, 10.0) }
            fill(Theme.ALARM) {
                moveTo(Point(20.0, 6.0)); lineTo(Point(27.0, 10.0)); lineTo(Point(28.0, 17.0)); lineTo(Point(22.0, 24.0))
                lineTo(Point(18.0, 24.0)); lineTo(Point(12.0, 17.0)); lineTo(Point(13.0, 10.0)); close()
            }
            fill(Theme.alpha(Theme.ON_SELECT, 0.35)) { moveTo(Point(16.0, 10.0)); lineTo(Point(19.0, 8.5)); lineTo(Point(17.0, 15.0)); close() }
        }
    }

    private fun sun(c: Container, x: Double, y: Double) {
        c.graphics {
            fill(Theme.GOLD) { moveTo(Point(11.0, 4.0)); lineTo(Point(18.0, 8.0)); lineTo(Point(18.0, 15.0)); lineTo(Point(11.0, 19.0)); lineTo(Point(4.0, 15.0)); lineTo(Point(4.0, 8.0)); close() }
            fill(Theme.GOLD) { moveTo(Point(10.0, -2.0)); lineTo(Point(12.0, -2.0)); lineTo(Point(12.0, 1.0)); lineTo(Point(10.0, 1.0)); close() }
            fill(Theme.GOLD) { moveTo(Point(10.0, 22.0)); lineTo(Point(12.0, 22.0)); lineTo(Point(12.0, 25.0)); lineTo(Point(10.0, 25.0)); close() }
        }.xy(x, y)
    }

    // ---------------------------------------------------------------- build bar

    private fun buildBuildBar() {
        Category.entries.forEachIndexed { i, category ->
            val b = Button(68.0, 68.0) { game.openCategory(category); refreshFlyout(force = true) }
            val icon = b.content.graphics { }.also { it.xy(21.0, 10.0) }
            val name = b.content.label(category.label, 11.0, Theme.TEXT, Theme.bodyBold).fitWidth(62.0)
            val key = b.content.label("${i + 1}", 11.0, Theme.TEXT_DIM, Theme.bodyBold).also { it.xy(56.0, 3.0) }
            b.onRestyle = {
                Icons.draw(icon, category, it.ink)
                name.color = it.ink
                name.xy((68.0 - name.width * name.scaleX) / 2, 42.0)
                key.color = if (it.selected) Theme.ON_SELECT else Theme.TEXT_DIM
            }
            b.selected = false
            b.onRestyle(b)
            b.xy(10.0, 10.0 + i * 76.0)
            buildBar.addChild(b)
            categoryButtons[category] = b
            categoryNews[category] = Icons.star(b, filled = true, size = 18.0).also { it.xy(2.0, 2.0); it.visible = false }
            b.tip {
                val types = Catalog.roomTypes.filter { it.category == category }
                val open = types.count { sim.unlocked(it) }
                Tip(
                    category.label,
                    when {
                        category == Category.DEMOLISH -> tr("Klick auf einen Raum reißt ihn ab, ein Viertel der Baukosten kommt zurück.", "Click a room to tear it down, a quarter of the cost comes back.")
                        open == 0 -> null
                        open < types.size -> tr("$open von ${types.size} Räumen freigeschaltet, der Rest kommt mit den Sternen.", "$open of ${types.size} rooms unlocked, the rest come with the stars.")
                        else -> tr("Alle ${types.size} Räume freigeschaltet.", "All ${types.size} rooms unlocked.")
                    },
                    "${i + 1}",
                    locked = if (category != Category.DEMOLISH && open == 0) tr("Kommt mit mehr Sternen", "Comes with more stars") else null,
                )
            }
        }
    }

    private fun refreshFlyout(force: Boolean = false) {
        // while a room is chosen the list folds into a small card, so the tower stays visible
        val chosen = (game.tool as? Tool.Build)?.type?.takeIf { game.category != null }
        val key = listOf(game.category, sim.stage, sim.stars, chosen)
        if (!force && key == flyoutKey) {
            updateFlyoutSelection()
            return
        }
        flyoutKey = key
        flyout.removeChildren()
        val category = game.category
        if (category == null) {
            flyout.visible = false
            return
        }
        flyout.visible = true
        if (chosen != null) {
            buildCompactFlyout(category, chosen)
            return
        }
        val bg = Panel(FLYOUT_WIDTH, 100.0).addTo(flyout)
        val heading = flyout.label(category.label.uppercase(), 20.0, Theme.TEXT, Theme.bodyBold).xy(14.0, 12.0)
        val close = flyout.label(tr("Esc schließt", "Esc closes"), 12.0, Theme.TEXT_DIM)
        var y = 44.0
        var width = FLYOUT_WIDTH
        if (category == Category.DEMOLISH) {
            val hint = flyout.wrappedLabel(
                tr("Klick auf einen Raum reißt ihn ab. Du bekommst ein Viertel der Baukosten zurück.",
                    "Click a room to tear it down. You get a quarter of the building costs back."),
                FLYOUT_TEXT, 13.0, Theme.TEXT_DIM,
            ).xy(14.0, y)
            y += hint.height + 14.0
        } else {
            // only what stage and stars allow; what came since the last look is marked with a star
            val types = Catalog.roomTypes.filter { it.category == category && sim.unlocked(it) }
            if (types.isEmpty()) {
                val none = flyout.wrappedLabel(tr("Kommt mit mehr Sternen.", "Comes with more stars."), FLYOUT_TEXT, 13.0, Theme.TEXT_DIM).xy(14.0, y)
                y += none.height + 14.0
            }
            val cards = types.map { type ->
                val built = type.maxCount?.let { max -> sim.tower.allRooms.count { it.type == type } >= max } == true
                ItemCard(type, !built, built, fresh = sim.isNew(type))
            }
            types.forEach { sim.seenTypes += it.id }
            // a list too long for the screen continues in a second column
            val bottom = screenHeight - 50.0 - flyout.y - 8.0 - 48.0
            var column = 0
            val top = y
            for (card in cards) {
                if (y > top && y + card.cardHeight > bottom) {
                    column++
                    y = top
                }
                card.xy(14.0 + column * (FLYOUT_TEXT + 14.0), y)
                flyout.addChild(card)
                y += card.cardHeight + 6
            }
            if (column > 0) {
                width += column * (FLYOUT_TEXT + 14.0)
                y = flyout.children.filterIsInstance<ItemCard>().maxOf { it.y + it.cardHeight + 6 }
            }
            val hint = flyout.wrappedLabel(
                tr("Klicken zum Wählen, im Turm klicken zum Bauen. Rechtsklick bricht ab.",
                    "Click to choose, click in the tower to build. Right-click cancels."),
                width - 28.0, 12.0, Theme.TEXT_DIM,
            ).xy(14.0, y + 2)
            y += hint.height + 12.0
        }
        close.xy(width - 14.0 - close.width, 18.0)
        heading.fitWidth(close.x - 24.0)
        bg.resize(width, y + 10)
        flyout.panelWidth = width
        flyout.panelHeight = y + 10
        updateFlyoutSelection()
    }

    /**
     * The folded build list while placing: the chosen room with its price, a button back to the
     * full list and one to stop building. The details are in the card's hover tip.
     */
    private fun buildCompactFlyout(category: Category, type: RoomType) {
        val bg = Panel(FLYOUT_WIDTH, 100.0).addTo(flyout)
        val heading = flyout.label(category.label.uppercase(), 13.0, Theme.TEXT_DIM, Theme.bodyBold).xy(14.0, 10.0)
        val close = flyout.label(tr("Esc bricht ab", "Esc cancels"), 11.0, Theme.TEXT_DIM)
        close.xy(FLYOUT_WIDTH - 14.0 - close.width, 11.0)
        heading.fitWidth(close.x - 24.0)
        var y = 32.0
        val card = ItemCard(type, unlocked = true, info = emptyList(), onPress = { game.tool = null })
        card.selected = true
        card.xy(14.0, y)
        card.tip {
            Tip(
                type.name.toString(),
                roomInfo(type).joinToString("\n") + tr("\n\nKlick zeigt wieder alle Räume.", "\n\nClick to show all rooms again."),
            )
        }
        flyout.addChild(card)
        y += card.cardHeight + 8
        val half = (FLYOUT_TEXT - 8.0) / 2
        TextButton(half, 34.0, tr("Wechseln", "Change"), 13.0) { game.tool = null }.addTo(flyout).xy(14.0, y)
            .tip(tr("Wechseln", "Change"), tr("Zeigt wieder die ganze Liste.", "Shows the whole list again."), "${Category.entries.indexOf(category) + 1}")
        TextButton(half, 34.0, tr("Abbrechen", "Cancel"), 13.0) { game.tool = null; game.category = null }.addTo(flyout).xy(14.0 + half + 8.0, y)
            .tip(tr("Abbrechen", "Cancel"), tr("Hört auf zu bauen und schließt die Liste.", "Stops building and closes the list."), tr("Rechtsklick", "Right-click"))
        y += 34.0 + 12.0
        bg.resize(FLYOUT_WIDTH, y)
        flyout.panelWidth = FLYOUT_WIDTH
        flyout.panelHeight = y
    }

    private fun updateFlyoutSelection() {
        val tool = game.tool
        for (child in flyout.children) {
            if (child is ItemCard) child.selected = tool is Tool.Build && tool.type == child.type
        }
    }

    /**
     * A build list entry: art thumbnail, name and price (or the stage that unlocks it), and
     * below that who lives or works there and what the room earns.
     */
    private inner class ItemCard(
        val type: RoomType, unlocked: Boolean, built: Boolean = false, fresh: Boolean = false,
        // long lines wrap, the card grows with them
        val info: List<String> = roomInfo(type).flatMap { wrapText(it, CARD_TEXT, 12.0).split('\n') },
        val cardHeight: Double = if (info.isEmpty()) 62.0 else 66.0 + info.size * 17.0,
        onPress: () -> Unit = { game.tool = Tool.Build(type) },
    ) : Button(242.0, cardHeight, onPress) {
        init {
            enabled = unlocked
            content.image(assets.room(type.art)) {
                scale = minOf(72.0 / bitmap.width, 44.0 / bitmap.height)
                xy(10.0 + (72.0 - bitmap.width * scale) / 2, (59.0 - bitmap.height * scale) / 2)
                alpha = if (unlocked) 1.0 else 0.35
            }
            val name = content.label(type.name.toString(), 15.0, Theme.TEXT, Theme.bodyBold).also { it.xy(92.0, 12.0) }.fitWidth(buttonWidth - 100.0)
            val sub = content.label(
                when {
                    built -> tr("SCHON GEBAUT", "ALREADY BUILT")
                    type.perSegment -> tr("${formatMoney(sim.listPrice(type, 1))}/FELD", "${formatMoney(sim.listPrice(type, 1))}/TILE")
                    else -> formatMoney(sim.listPrice(type))
                },
                13.0, Theme.TEXT, Theme.bodyBold,
            ).also { it.xy(92.0, 33.0) }.fitWidth(buttonWidth - 100.0)
            val lines = info.mapIndexed { i, line -> content.label(line, 12.0, Theme.TEXT_DIM).also { it.xy(12.0, 60.0 + i * 17.0) }.fitWidth(CARD_TEXT) }
            onRestyle = { b ->
                name.color = b.ink
                sub.color = b.ink
                lines.forEach { it.color = if (b.selected) b.ink else Theme.TEXT_DIM }
            }
            if (fresh) {
                // a star in the corner, NEW under it next to the price
                Icons.star(content, filled = true, size = 22.0).xy(buttonWidth - 28.0, 6.0)
                name.fitWidth(buttonWidth - 132.0)
                sub.fitWidth(buttonWidth - 150.0)
                content.label(tr("NEU", "NEW"), 11.0, Theme.GOLD, Theme.bodyBold).also { it.xy(buttonWidth - 12.0 - it.width, 35.0) }
            }
            onRestyle(this)
        }
    }

    /** Who a room of [type] brings into the tower and how it pays, for the tenants that would come now. */
    private fun roomInfo(type: RoomType): List<String> {
        val tenants = Tenants.candidates(type, sim.stars, sim.stage)
        fun range(values: List<Long>, bonus: Double = sim.starBonus): String {
            if (values.isEmpty()) return "–"
            return formatMoneyRange((values.min() * bonus).toLong(), (values.max() * bonus).toLong())
        }
        return when (type.role) {
            RoomRole.HOME -> if (type.luxury) listOf(
                tr("${type.capacity} Bewohner, nur ab der ${type.minFloor}. Etage", "${type.capacity} residents, only from floor ${type.minFloor} up"),
                tr("Verkauf einmalig: ", "sold once: ") + range(tenants.map { it.salePrice }, bonus = 1.0),
                tr("dann ${formatMoney((Simulation.HOME_FEE * sim.starBonus).toLong() * Simulation.PENTHOUSE_FEES)} Hausgeld pro Tag", "then ${formatMoney((Simulation.HOME_FEE * sim.starBonus).toLong() * Simulation.PENTHOUSE_FEES)} service charge a day"),
                tr("braucht einen der ${sim.flatBuyersPerDay} Käufer pro Tag", "needs one of the ${sim.flatBuyersPerDay} buyers a day"),
            ) else listOf(
                tr("${type.capacity} Bewohner, nur ${sim.flatBuyersPerDay} Käufer pro Tag", "${type.capacity} residents, only ${sim.flatBuyersPerDay} buyers a day"),
                tr("Verkauf einmalig: ", "sold once: ") + range(tenants.map { it.salePrice }, bonus = 1.0),
                tr("dann ${formatMoney((Simulation.HOME_FEE * sim.starBonus).toLong())} Hausgeld pro Tag", "then ${formatMoney((Simulation.HOME_FEE * sim.starBonus).toLong())} service charge a day"),
            )
            RoomRole.OFFICE -> if (type.luxury) listOf(
                tr("${type.capacity} Angestellte, nur feine Mieter", "${type.capacity} employees, posh tenants only"),
                tr("Miete pro Arbeitstag: ", "rent per working day: ") + range(tenants.map { it.rentPerDay }),
                tr("halbe Miete unter ${Simulation.LUXURY_MOOD.toInt()} % Laune, ${formatMoney(type.upkeep ?: 0)} Unterhalt",
                    "half rent below ${Simulation.LUXURY_MOOD.toInt()} % mood, ${formatMoney(type.upkeep ?: 0)} upkeep"),
                tr("braucht ${Simulation.LUXURY_SPACES} Parkplätze im Keller, sonst kein Mieter",
                    "needs ${Simulation.LUXURY_SPACES} parking spaces in the basement, or no tenant"),
            ) else listOf(
                tr("${type.capacity} Angestellte, kommen zur Arbeit", "${type.capacity} employees come to work"),
                tr("Miete pro Arbeitstag: ", "rent per working day: ") + range(tenants.map { it.rentPerDay }),
                if (sim.workforceMatters) {
                    tr("Fachkräfte: ${sim.jobs} von ${sim.workforce} haben schon Arbeit", "staff: ${sim.jobs} of ${sim.workforce} already have a job")
                } else {
                    tr("mehr Sterne bringen bessere Mieter", "more stars bring better tenants")
                },
            )
            RoomRole.DORM -> listOf(
                tr("${type.capacity} Studis, Miete jeden Tag: ", "${type.capacity} students, rent every day: ") + range(tenants.map { it.rentPerDay }),
                tr("brauchen Nebenjobs: ${sim.students} Studis, ${sim.jobs} Angestellte", "need side jobs: ${sim.students} students, ${sim.jobs} employees"),
                tr("laut: Wohnungen auf der Etage −${Simulation.DORM_NOISE.toInt()} Laune", "loud: flats on the floor −${Simulation.DORM_NOISE.toInt()} mood"),
            )
            RoomRole.SHOP -> listOf(
                tr("${type.capacity} Angestellte, Miete pro Tag: ", "${type.capacity} employees, rent per day: ") + range(tenants.map { it.rentPerDay }),
            ) + type.effects.map { it.toString() }
            RoomRole.FOOD -> listOf(
                tr("${type.capacity} Angestellte, Pflicht für Stern 3", "${type.capacity} employees, needed for star 3"),
                tr("Miete pro Tag: ", "rent per day: ") + range(tenants.map { it.rentPerDay }),
                tr("plus ${formatMoneyRange(Simulation.MEAL_PRICE, Simulation.VISITOR_MEAL_PRICE)} pro Essen", "plus ${formatMoneyRange(Simulation.MEAL_PRICE, Simulation.VISITOR_MEAL_PRICE)} per meal"),
            )
            RoomRole.HOTEL -> listOf(
                if (type.capacity == 1) tr("1 Gast, ab ${Simulation.CHECK_IN_FROM} Uhr, eine Nacht", "1 guest, from ${Simulation.CHECK_IN_FROM}:00, one night")
                else tr("${type.capacity} Gäste, ab ${Simulation.CHECK_IN_FROM} Uhr, eine Nacht", "${type.capacity} guests, from ${Simulation.CHECK_IN_FROM}:00, one night"),
                tr("pro Nacht: ", "per night: ") + range(tenants.map { it.rentPerDay }) +
                    tr(", ${formatMoney(type.upkeep ?: Simulation.HOTEL_UPKEEP)} Unterhalt", ", ${formatMoney(type.upkeep ?: Simulation.HOTEL_UPKEEP)} upkeep"),
                tr("danach schmutzig, putzen dauert ${type.hotelClass.cleanMinutes} Min.",
                    "then dirty, cleaning takes ${type.hotelClass.cleanMinutes} min"),
                tr("Luxus für VIPs: ", "luxury for VIPs: ") + "★".repeat(type.hotelClass.vipStars),
            )
            RoomRole.PARKING -> type.effects.map { it.toString() } +
                tr("${sim.parkingSpaces} Plätze für ${sim.carsWanted} Autos, ${sim.carsWithoutSpace.coerceAtLeast(0)} suchen noch",
                    "${sim.parkingSpaces} spaces for ${sim.carsWanted} cars, ${sim.carsWithoutSpace.coerceAtLeast(0)} still looking")
            RoomRole.ELEVATOR -> listOf(
                tr("Unterhalt pro Tag: ${formatMoney(Simulation.UPKEEP.getValue(RoomRole.ELEVATOR))} pro Etage", "upkeep per day: ${formatMoney(Simulation.UPKEEP.getValue(RoomRole.ELEVATOR))} per floor"),
                tr("plus ${formatMoney(Simulation.CABIN_UPKEEP)} pro Kabine", "plus ${formatMoney(Simulation.CABIN_UPKEEP)} per cabin"),
                tr("jeder weitere Schacht +${(Simulation.SHAFT_SURCHARGE * 100).toInt()} % teurer", "every further shaft +${(Simulation.SHAFT_SURCHARGE * 100).toInt()} % dearer"),
            )
            else -> type.effects.map { it.toString() }
        }
    }

    // ---------------------------------------------------------------- ticker

    private fun buildTicker() {
        ticker.solidRect(screenWidth, 50.0, Theme.INK)
        ticker.solidRect(screenWidth, 2.0, Theme.SELECT)
        val badge = Panel(150.0, 28.0, Theme.ALARM, 6.0).addTo(ticker).also { it.xy(12.0, 11.0) }
        val badgeText = badge.label(tr("TOWER-TICKER", "TOWER TICKER"), 15.0, Theme.ON_SELECT, Theme.bodyBold).fitWidth(126.0)
        badgeText.xy((150.0 - badgeText.width * badgeText.scaleX) / 2, 4.0)
        badge.tip(tr("Tower-Ticker", "Tower ticker"), tr("Die neuesten Meldungen aus dem Turm. Klick auf die Laufschrift springt zum Ort der neuesten Meldung.", "The latest news from the tower. Click the scrolling text to go to the newest news."))
        tickerClip = ticker.clipContainer(Size(screenWidth - 180.0, 50.0)) { xy(172.0, 0.0) }
        // a picture of the text: a scrolling text in a vector font would be drawn anew on the processor every frame
        tickerText = tickerClip.bakedLabel("", 15.0, Theme.TEXT, Theme.body).also { it.y = 15.0 }
        tickerClip.onClick { e ->
            // jump to the newest message that has a place
            tickerItems.firstOrNull { it.where != null }?.where?.let { tower.lookAt(it) }
        }
    }

    // ---------------------------------------------------------------- keys

    /** C: the selected room's kind in the hand, like the inspector's copy button. */
    private fun copySelected() {
        val sel = game.selection as? Selection.RoomSel ?: return
        val type = sim.tower.room(sel.roomId)?.type ?: return
        val blocked = game.copyBlocked(type)
        if (blocked != null) {
            game.say(blocked)
            Sfx.play(Sfx.ERROR)
        } else if (game.copy(type)) {
            game.say(tr("${type.name} gewählt, klick in den Turm", "${type.name} chosen, click in the tower"))
        }
    }

    private fun setupKeys() {
        keys {
            Category.entries.forEachIndexed { i, c ->
                down(Key.valueOf("N${i + 1}")) { game.openCategory(c); refreshFlyout(force = true) }
            }
            down(Key.SPACE) { game.togglePause() }
            down(Key.PERIOD) { game.faster(); game.say(tr("Tempo ${game.speed}×", "Speed ${game.speed}×"), 1.5) }
            down(Key.COMMA) { game.slower(); game.say(tr("Tempo ${game.speed}×", "Speed ${game.speed}×"), 1.5) }
            down(Key.F1) { game.helpOpen = !game.helpOpen }
            down(Key.Z) { if (it.ctrl || it.meta) undo() }
            down(Key.ENTER) { game.confirm?.let { answer(it, yes = true) } }
            down(Key.H) { game.helpOpen = !game.helpOpen }
            down(Key.N) {
                game.fastNights = !game.fastNights
                game.say(if (game.fastNights) tr("Nachts vorspulen: an", "Fast-forward nights: on") else tr("Nachts vorspulen: aus", "Fast-forward nights: off"))
            }
            down(Key.F) { game.financeOpen = !game.financeOpen }
            down(Key.O) { game.optionsOpen = !game.optionsOpen }
            down(Key.X) { game.extrasOpen = !game.extrasOpen }
            down(Key.C) { copySelected() }
            down(Key.M) { if (sim.missionStarted) game.missionOpen = !game.missionOpen }
            down(Key.ESCAPE) {
                when {
                    game.confirm != null -> answer(game.confirm!!, yes = false)
                    game.helpOpen -> game.helpOpen = false
                    game.optionsOpen -> game.optionsOpen = false
                    game.financeOpen -> game.financeOpen = false
                    game.extrasOpen -> game.extrasOpen = false
                    game.missionOpen -> game.missionOpen = false
                    game.tool != null || game.category != null -> game.cancel()
                    game.selection != null -> game.selection = null
                    else -> game.pauseMenuOpen = !game.pauseMenuOpen
                }
            }
        }
    }

    // ---------------------------------------------------------------- per frame

    fun sync(dtSeconds: Double) {
        moneyText.text = formatMoney(sim.money)
        moneyText.color = if (sim.money < 0) Theme.ALARM else Theme.TEXT
        val coming = sim.upcoming(24).filter { it.amount > 0 }
        val nextMinute = coming.firstOrNull()?.minute
        nextText.text = nextMinute?.let { m ->
            val amount = formatMoney(coming.filter { it.minute == m }.sumOf { it.amount })
            // short, so it fits at the minimum text size; the tip says what it is
            tr("+$amount um ${clockOf(m)}", "+$amount at ${clockOf(m)}")
        } ?: tr("nichts in Sicht", "nothing in sight")
        nextText.fitWidth(TOP_MONEY_TEXT)
        finance.visible = game.financeOpen
        if (finance.visible) finance.sync()
        extras.visible = game.extrasOpen
        if (extras.visible) extras.sync()
        mission.visible = game.missionOpen
        if (mission.visible) mission.sync()
        extrasButton.selected = game.extrasOpen
        // locked until stage 4; the X key still opens the window that says so
        extrasButton.enabled = sim.stage >= SINK_STAGE
        extrasLock.visible = sim.stage < SINK_STAGE
        options.visible = game.optionsOpen
        if (options.visible) options.sync()
        help.visible = game.helpOpen
        // options and keys opened from the pause menu cover it until they close
        pauseMenu.visible = game.pauseMenuOpen && !game.optionsOpen && !game.helpOpen
        syncConfirm()
        dim.visible = game.pauseMenuOpen || game.confirm != null
        popText.text = formatNumber(sim.population.toLong())
        popText.fitWidth(POP_WIDTH)
        // "Bewohner" right after the number, where it fits
        popLabel.xy(42.0 + popText.width * popText.scaleX + 6.0, 14.0)
        popLabel.visible = popLabel.x + popLabel.width <= 170.0
        val mood = sim.satisfaction.toInt()
        moodText.text = tr("Laune $mood %", "Mood $mood %")
        moodText.fitWidth(POP_LABEL)
        moodText.color = if (mood < 40) Theme.ALARM else Theme.TEXT_DIM
        syncStage()
        celebration.update(dtSeconds)
        // the demo ends once the star celebration is over
        demoEnd.visible = sim.demoOver && !celebration.isShowing
        clockText.text = sim.clock.format() + if (game.speed > 0 && game.nightBoost > 1) " »" else ""
        clockText.color = if (game.speed == 0) Theme.ALARM else Theme.TEXT
        speedButtons.forEach { (speed, b) -> b.selected = game.speed == speed }
        openDayButton.selected = sim.activeEvents.any { it.id == "openday" }
        openDayButton.enabled = openDayButton.selected || sim.openDayBlocked() == null
        categoryButtons.forEach { (c, b) -> b.selected = game.category == c }
        categoryNews.forEach { (c, star) -> star.visible = game.category != c && Catalog.roomTypes.any { it.category == c && sim.isNew(it) } }
        refreshFlyout()
        inspector.sync()
        syncTicker(dtSeconds)
        syncTooltip()
        syncToast()
        tips.update(dtSeconds)
    }

    /** Takes the last build or demolition back (Ctrl+Z). */
    private fun undo() {
        val result = sim.undo()
        game.say(result.reason)
        Sfx.play(if (result.ok) Sfx.BUILD else Sfx.ERROR)
    }

    private fun answer(question: Confirm, yes: Boolean) {
        game.confirm = null
        if (yes) question.onYes()
    }

    /** Shows the question the game asks right now, in front of everything but the tips. */
    private fun syncConfirm() {
        val question = game.confirm
        if (question == shownConfirm) return
        shownConfirm = question
        confirmWindow?.removeFromParent()
        confirmWindow = question?.let { q ->
            ConfirmWindow(q, onNo = { answer(q, yes = false) }, onYes = { answer(q, yes = true) }).also {
                addChildAt(it, getChildIndex(tips))
                it.xy((screenWidth - it.panelWidth) / 2, ((screenHeight - it.panelHeight) / 2).coerceAtLeast(84.0))
            }
        }
    }

    /** Everything the next star still needs, not only the first thing as under the stars. */
    private fun starTip(): Tip {
        if (sim.stars >= Rating.steps.size) return Tip(tr("5 Sterne", "5 stars"), tr("Mehr Sterne gibt es nicht.", "There are no more stars."))
        val next = Stages.info(sim.stars + 1)
        val missing = sim.nextStarMissing
        val title = tr("Für Stern ${next.number} · ${next.name}", "For star ${next.number} · ${next.name}")
        if (sim.stars >= Edition.maxStars) return Tip(title, tr("Weitere Sterne gibt es in der Vollversion.", "More stars come with the full version."))
        return Tip(
            title,
            (if (missing.isEmpty()) tr("Alles erfüllt, der Stern kommt zur nächsten vollen Stunde.", "All done, the star comes on the hour.")
            else missing.joinToString("\n") { "· $it" }) +
                tr("\n\nJeder Stern ist auch die nächste Ausbaustufe: Der Turm darf höher werden und es gibt neue Räume.",
                    "\n\nEvery star is also the next stage: the tower may grow taller and new rooms open up."),
        )
    }

    /** The next star with all it needs, or from five stars on the moon mission's line, which the box often has to cut. */
    private fun stageTip(): Tip = if (sim.stars < Stages.last) starTip() else Tip(
        stageText.fullText(),
        stageHint.fullText() + if (sim.missionStarted) tr("\nKlick öffnet die Mondmission.", "\nClick to open the moon mission.") else "",
        if (sim.missionStarted) "M" else null,
    )

    private val stageFull = HashMap<Text, String>()

    private fun Text.fullText(): String = stageFull[this] ?: text

    private fun clockOf(minute: Int): String {
        val m = minute % GameClock.MINUTES_PER_DAY
        return "%02d:%02d".format(m / 60, m % 60)
    }

    /**
     * The stars box: the stars with the tower's name under them on the left, the next star and
     * what it still needs on the right. Stars and stages are one ladder, so there is one box.
     */
    private fun syncStage() {
        val now = sim.stars
        // a celebration only for real gains, not when the HUD is first drawn
        if (shownStars >= 0 && now > shownStars) celebration.showStage(now)
        if (now != shownStars) {
            shownStars = now
            stars.removeChildren()
            for (i in 0 until 5) Icons.star(stars, i < now).xy(i * 22.0, 0.0)
            starHint.text = Stages.info(now).name.toString()
            starHint.fitWidth(STAR_HINT)
        }
        syncMission()
        val next = if (now < Stages.last) Stages.info(now + 1) else null
        val title = when {
            next != null -> tr("STERN ${next.number}", "STAR ${next.number}") + " · ${next.name.toString().uppercase()}"
            sim.missionStarted -> tr("MONDMISSION", "MOON MISSION")
            else -> Stages.info(now).name.toString().uppercase()
        }
        if (stageFull[stageText] != title) {
            stageText.text = title
            stageFull[stageText] = title
            stageText.fitWidth(stageBarWidth)
        }
        val missing = sim.nextStarMissing
        val part = sim.currentPart
        val hint = when {
            next != null && now >= Edition.maxStars -> tr("Gibt es in der Vollversion", "Available in the full version")
            // everything is there, the rating catches up within the hour
            next != null && missing.isEmpty() -> tr("Alles erfüllt, der Stern kommt gleich", "All done, the star is on its way")
            next != null -> tr("Noch nötig: ", "Still needed: ") + missing.joinToString(" · ")
            sim.liftedOff -> tr("ABGEHOBEN · ${sim.spectatorsPerDay} Schaulustige pro Tag", "LIFT-OFF · ${sim.spectatorsPerDay} spectators a day")
            part != null -> tr("${part.step}: ${part.type.name} · M öffnet die Mission", "${part.step}: ${part.type.name} · M opens the mission")
            else -> tr("Der Turm ist komplett verrückt.", "The tower is completely crazy.")
        }
        // Only a changed hint is set and fitted again: fitWidth shortens it letter by letter, and every
        // try draws the text anew on the processor, which cost several milliseconds in every frame.
        if (stageFull[stageHint] != hint || drawnHintWidth != stageBarWidth) {
            stageFull[stageHint] = hint
            drawnHintWidth = stageBarWidth
            stageHint.text = hint
            stageHint.fitWidth(stageBarWidth)
        }
        val progress = (if (next == null && sim.missionStarted) sim.missionProgress else sim.stageProgress).coerceIn(0.0, 1.0)
        val w = stageBarWidth
        // a new shape is drawn again from scratch, so only when it changed
        if (w to progress == drawnStageBar) return
        drawnStageBar = w to progress
        stageBar.updateShape {
            fill(Theme.INK) { rect(0.0, 0.0, w, 4.0) }
            fill(Theme.SELECT) { rect(0.0, 0.0, w * progress, 4.0) }
        }
    }

    private var drawnStageBar: Pair<Double, Double>? = null
    private var drawnHintWidth = -1.0

    /** A celebration for every station part, a long one for the lift-off. */
    private fun syncMission() {
        val parts = sim.endgame.parts
        if (shownParts >= 0 && parts > shownParts) {
            val built = Mission.parts[parts - 1]
            val liftOff = parts >= Mission.parts.size
            val next = Mission.parts.getOrNull(parts)
            val lines = if (liftOff) listOf(
                tr("Der Mondlift fährt! Von Etage ${Mission.FLOOR} geht es direkt zum Mond.", "The moon lift is running! From floor ${Mission.FLOOR} it goes straight to the moon."),
                tr("Ab jetzt kommen ${sim.spectatorsPerDay} Schaulustige am Tag.", "From now on ${sim.spectatorsPerDay} spectators come every day."),
                tr("Tag ${sim.clock.day} · ${formatNumber(sim.population.toLong())} Bewohner · ${formatMoney(sim.money)} in der Kasse",
                    "Day ${sim.clock.day} · ${formatNumber(sim.population.toLong())} residents · ${formatMoney(sim.money)} in the till"),
                tr("Der Turm ist fertig verrückt. Bau einfach weiter.", "The tower is done being crazy. Just keep building."),
            )
            else listOf(
                tr("${built.type.name} steht auf Etage ${Mission.FLOOR}.", "${built.type.name} stands on floor ${Mission.FLOOR}."),
                next?.let { tr("Weiter mit ${it.step}: ${it.type.name}. Die neuen Aufträge stehen in der Mission (M).", "Next, ${it.step}: ${it.type.name}. The new tasks are in the mission window (M).") } ?: "",
            )
            celebration.showMission(
                if (liftOff) tr("ABGEHOBEN!", "LIFT-OFF!") else tr("COUNTDOWN ${built.step.toString().uppercase()}", "COUNTDOWN ${built.step.toString().uppercase()}"),
                lines, liftOff,
            )
        }
        shownParts = parts
    }

    private fun syncTicker(dt: Double) {
        val items = sim.news.latest
        if (items != tickerItems) {
            tickerItems = items
            tickerText.text = if (items.isEmpty()) {
                tr("Willkommen in CrazyTower. Bau eine Lobby, dann kommen die ersten Neugierigen.", "Welcome to CrazyTower. Build a lobby and the first curious visitors will come.")
            } else {
                items.take(6).joinToString("   ·   ") { it.text }
            }
            tickerOffset = 0.0
            tickerWidth = tickerText.width
        }
        tickerOffset += dt * 60
        val span = tickerWidth + 200
        if (tickerOffset > span) tickerOffset = -tickerClip.width + 200
        tickerText.x = -tickerOffset.coerceAtLeast(0.0) + (if (tickerOffset < 0) -tickerOffset else 0.0)
    }

    private var drawnTooltip: Pair<String, Boolean>? = null
    private var tooltipWidth = 0.0

    private fun syncTooltip() {
        val info = tower.ghostInfo
        val hover = tower.hover
        if (info == null || hover == null || isOverUi(hover.screen)) {
            tooltip.visible = false
            return
        }
        // built again only when it says something else; it follows the mouse as a picture
        if (info != drawnTooltip) {
            drawnTooltip = info
            tooltip.removeChildren()
            val (text, ok) = info
            val t = BakedText(text, 13.0, if (ok) Theme.TEXT else Theme.ON_SELECT, Theme.bodyBold)
            if (t.width > screenWidth - 60.0) t.scale = (screenWidth - 60.0) / t.width
            tooltipWidth = t.width * t.scale
            val box = Panel(tooltipWidth + 28, 32.0, if (ok) Theme.INK else Theme.ALARM, 6.0).addTo(tooltip)
            if (ok) {
                box.color = Theme.SELECT
                Panel(tooltipWidth + 24, 28.0, Theme.INK, 5.0).addTo(tooltip).xy(2.0, 2.0)
            }
            t.addTo(tooltip).xy(14.0, 8.0)
        }
        tooltip.xy(
            (hover.screen.x - (tooltipWidth + 28) / 2).coerceIn(4.0, screenWidth - tooltipWidth - 32),
            hover.screen.y - 62.0,
        )
        tooltip.visible = true
    }

    private fun syncToast() {
        val message = game.toast
        if (message == null) {
            toast.visible = false
            return
        }
        if (toast.name != message) {
            toast.name = message
            toast.removeChildren()
            val t = Text(message, 15.0, Theme.ON_SELECT, Theme.bodyBold).fitWidth(screenWidth - 80.0)
            Panel(t.width + 32, 38.0, Theme.GOLD, 8.0).addTo(toast)
            t.addTo(toast).xy(16.0, 10.0)
            toast.xy((screenWidth - t.width - 32) / 2, 92.0)
        }
        toast.visible = true
    }

    private companion object {
        const val FLYOUT_WIDTH = 270.0

        /** Text width inside the build list. */
        const val FLYOUT_TEXT = 242.0

        /** Text width inside a build list entry. */
        const val CARD_TEXT = 222.0

        /** Room for the population and the mood under it. */
        const val POP_WIDTH = 126.0
        const val POP_LABEL = 126.0

        /** The box with the five speed buttons. */
        const val SPEED_BOX = 234.0

        /** Room for the next payment under the money. */
        const val TOP_MONEY_TEXT = 146.0

        /** Room for the star hint under the five stars, left of the stage. */
        const val STAR_HINT = 130.0
    }
}
