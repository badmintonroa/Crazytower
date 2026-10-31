package crazytower.game

import crazytower.core.Catalog
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
import crazytower.core.startOpenDay
import crazytower.core.tr
import crazytower.game.ui.Button
import crazytower.game.ui.Icons
import crazytower.game.ui.Panel
import crazytower.game.ui.TextButton
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
    private val celebration = Celebration(screenWidth, screenHeight).addTo(this)
    private val tooltip = container { visible = false }
    private val toast = container { visible = false }

    private lateinit var moneyText: Text
    private lateinit var nextText: Text
    private lateinit var popText: Text
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
    private var shownParts = -1
    private val categoryButtons = HashMap<Category, Button>()

    /** The little star on a category button that holds room types the player has not seen yet. */
    private val categoryNews = HashMap<Category, View>()
    private var flyoutKey: Triple<Category?, Int, Int>? = null
    private lateinit var tickerText: Text
    private lateinit var tickerClip: Container
    private var tickerOffset = 0.0
    private var tickerItems: List<NewsItem> = emptyList()
    private var shownStage = -1

    init {
        buildTopBar()
        buildBuildBar()
        buildTicker()
        setupKeys()
    }

    /** True when [point] (screen coordinates) lies on a HUD element, so the tower ignores the click. */
    fun isOverUi(point: Point): Boolean {
        val rects = buildList {
            add(Rectangle(0.0, 0.0, screenWidth, 78.0))
            add(Rectangle(buildBar.x, buildBar.y, buildBar.panelWidth, buildBar.panelHeight))
            if (flyout.visible) add(Rectangle(flyout.x, flyout.y, flyout.panelWidth, flyout.panelHeight))
            if (inspector.visible) add(Rectangle(inspector.x, inspector.y, inspector.panelWidth, inspector.panelHeight))
            if (finance.visible) add(Rectangle(finance.x, finance.y, finance.panelWidth, finance.panelHeight))
            if (extras.visible) add(Rectangle(extras.x, extras.y, extras.panelWidth, extras.panelHeight))
            if (mission.visible) add(Rectangle(mission.x, mission.y, mission.panelWidth, mission.panelHeight))
            if (options.visible) add(Rectangle(options.x, options.y, options.panelWidth, options.panelHeight))
            if (celebration.isShowing) add(Rectangle(0.0, 0.0, screenWidth, screenHeight))
            add(Rectangle(0.0, screenHeight - 50.0, screenWidth, 50.0))
        }
        return rects.any { it.contains(point) }
    }

    /** Shows the star (or stage) celebration right away; for screenshots. */
    fun celebrate(stage: Boolean) {
        if (stage) celebration.showStage(sim.stage, sim.stars) else celebration.showStar(sim.stars)
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
        }
        box(160.0) {
            solidRect(8.0, 8.0, Theme.alpha(Theme.SELECT, 0.9)).xy(20.0, 18.0)
            solidRect(14.0, 12.0, Theme.alpha(Theme.SELECT, 0.6)).xy(17.0, 28.0)
            popText = label("", 20.0, Theme.TEXT, Theme.bodyBold).also { it.xy(42.0, 17.0) }
            label(tr("Bewohner", "Residents"), 12.0, Theme.TEXT_DIM).xy(95.0, 14.0).fitWidth(POP_LABEL)
            moodText = label("", 12.0, Theme.TEXT_DIM).also { it.xy(95.0, 30.0) }
        }
        val stageWidth = screenWidth - 24 - x - 150 - 10 - 254 - 10 - 60 - 10 - 60 - 10 - 60
        box(stageWidth) {
            stars = container { xy(16.0, 12.0) }
            starHint = label("", 11.0, Theme.GOLD).also { it.xy(16.0, 36.0) }
            stageText = label("", 16.0, Theme.TEXT, Theme.bodyBold).also { it.xy(152.0, 12.0) }
            stageHint = label("", 12.0, Theme.TEXT_DIM).also { it.xy(152.0, 34.0) }
            stageBarWidth = stageWidth - 168.0
            stageBar = graphics { }.also { it.xy(152.0, 50.0) }
            // from stage 5 on the stage box is the moon mission's countdown
            onClick { if (sim.missionStarted) game.missionOpen = !game.missionOpen }
        }
        box(150.0) {
            sun(this, 16.0, 19.0)
            clockText = label("", 18.0, Theme.TEXT, Theme.bodyBold).also { it.xy(46.0, 18.0) }
        }
        box(254.0) {
            val speeds = GameController.SPEEDS.map { it to if (it == 0) "II" else "$it×" }
            speeds.forEachIndexed { i, (speed, title) ->
                val b = TextButton(44.0, 40.0, title) { game.speed = speed }
                b.xy(10.0 + i * 48.0, 10.0)
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
            addChild(openDayButton)
        }
        box(60.0) {
            // extras for rich towers: where the money goes from stage 4 on
            extrasButton = TextButton(40.0, 40.0, "$", 20.0, onPress = { game.extrasOpen = !game.extrasOpen })
            extrasButton.xy(10.0, 10.0)
            addChild(extrasButton)
        }
        box(60.0) {
            val b = TextButton(40.0, 40.0, "", onPress = { game.optionsOpen = !game.optionsOpen })
            Icons.gear(b.content, Theme.TEXT)
            b.xy(10.0, 10.0)
            addChild(b)
        }
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
        }
    }

    private fun refreshFlyout(force: Boolean = false) {
        val key = Triple(game.category, sim.stage, sim.stars)
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
    ) : Button(242.0, cardHeight, { game.tool = Tool.Build(type) }) {
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
                tr("${type.capacity} Gäste, ab ${Simulation.CHECK_IN_FROM} Uhr, eine Nacht", "${type.capacity} guests, from ${Simulation.CHECK_IN_FROM}:00, one night"),
                tr("pro Nacht: ", "per night: ") + range(tenants.map { it.rentPerDay }),
                tr("danach schmutzig, braucht Reinigungsdienst", "then dirty, needs housekeeping"),
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
        tickerClip = ticker.clipContainer(Size(screenWidth - 180.0, 50.0)) { xy(172.0, 0.0) }
        tickerText = tickerClip.label("", 15.0, Theme.TEXT).also { it.y = 15.0 }
        tickerClip.onClick { e ->
            // jump to the newest message that has a place
            tickerItems.firstOrNull { it.where != null }?.where?.let { tower.lookAt(it) }
        }
    }

    // ---------------------------------------------------------------- keys

    private fun setupKeys() {
        keys {
            Category.entries.forEachIndexed { i, c ->
                down(Key.valueOf("N${i + 1}")) { game.openCategory(c); refreshFlyout(force = true) }
            }
            down(Key.SPACE) { game.togglePause() }
            down(Key.N) {
                game.fastNights = !game.fastNights
                game.say(if (game.fastNights) tr("Nachts vorspulen: an", "Fast-forward nights: on") else tr("Nachts vorspulen: aus", "Fast-forward nights: off"))
            }
            down(Key.F) { game.financeOpen = !game.financeOpen }
            down(Key.O) { game.optionsOpen = !game.optionsOpen }
            down(Key.X) { game.extrasOpen = !game.extrasOpen }
            down(Key.M) { if (sim.missionStarted) game.missionOpen = !game.missionOpen }
            down(Key.ESCAPE) {
                when {
                    game.optionsOpen -> game.optionsOpen = false
                    game.financeOpen -> game.financeOpen = false
                    game.extrasOpen -> game.extrasOpen = false
                    game.missionOpen -> game.missionOpen = false
                    game.tool != null || game.category != null -> game.cancel()
                    game.selection != null -> game.selection = null
                    else -> game.optionsOpen = true
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
            tr("nächste: +$amount um ${clockOf(m)}", "next: +$amount at ${clockOf(m)}")
        } ?: tr("keine Einnahmen in Sicht", "no income in sight")
        nextText.fitWidth(TOP_MONEY_TEXT)
        finance.visible = game.financeOpen
        if (finance.visible) finance.sync()
        extras.visible = game.extrasOpen
        if (extras.visible) extras.sync()
        mission.visible = game.missionOpen
        if (mission.visible) mission.sync()
        extrasButton.selected = game.extrasOpen
        extrasButton.alpha = if (sim.stage >= SINK_STAGE) 1.0 else 0.5
        options.visible = game.optionsOpen
        if (options.visible) options.sync()
        popText.text = formatNumber(sim.population.toLong())
        popText.fitWidth(POP_WIDTH)
        val mood = sim.satisfaction.toInt()
        moodText.text = tr("Laune $mood %", "Mood $mood %")
        moodText.fitWidth(POP_LABEL)
        moodText.color = if (mood < 40) Theme.ALARM else Theme.TEXT_DIM
        syncStage()
        celebration.update(dtSeconds)
        clockText.text = sim.clock.format() + if (game.speed > 0 && game.nightBoost > 1) " »" else ""
        clockText.color = if (game.speed == 0) Theme.ALARM else Theme.TEXT
        speedButtons.forEach { (speed, b) -> b.selected = game.speed == speed }
        openDayButton.selected = sim.activeEvents.any { it.id == "openday" }
        categoryButtons.forEach { (c, b) -> b.selected = game.category == c }
        categoryNews.forEach { (c, star) -> star.visible = game.category != c && Catalog.roomTypes.any { it.category == c && sim.isNew(it) } }
        refreshFlyout()
        inspector.sync()
        syncTicker(dtSeconds)
        syncTooltip()
        syncToast()
    }

    private fun clockOf(minute: Int): String {
        val m = minute % GameClock.MINUTES_PER_DAY
        return "%02d:%02d".format(m / 60, m % 60)
    }

    private fun syncStage() {
        val stage = sim.stage
        // a celebration only for real gains, not when the HUD is first drawn
        if (shownStage >= 0 && stage > shownStage) celebration.showStage(stage, sim.stars)
        else if (shownStars >= 0 && sim.stars > shownStars) celebration.showStar(sim.stars)
        if (sim.stars != shownStars) {
            shownStars = sim.stars
            stars.removeChildren()
            for (i in 0 until 5) Icons.star(stars, i < sim.stars).xy(i * 22.0, 0.0)
        }
        if (stage != shownStage) {
            shownStage = stage
            stageText.text = tr("STUFE", "STAGE") + " $stage · ${Stages.info(stage).name.toString().uppercase()}"
            stageText.fitWidth(stageBarWidth)
        }
        val missing = sim.nextStarMissing
        starHint.text = when {
            sim.stars >= Rating.steps.size -> ""
            // everything is there, the rating catches up at the next check
            missing.isEmpty() -> tr("Stern ${sim.stars + 1}: kommt bald", "Star ${sim.stars + 1}: coming soon")
            else -> tr("Stern ${sim.stars + 1}: ${missing.first()}", "Star ${sim.stars + 1}: ${missing.first()}")
        }
        starHint.fitWidth(STAR_HINT)
        syncMission()
        val next = if (stage < Stages.last) Stages.info(stage + 1) else null
        val part = sim.currentPart
        stageHint.text = when {
            next != null -> tr("Für Stufe ${next.number}: ${next.requirement}", "For stage ${next.number}: ${next.requirement}")
            sim.liftedOff -> tr("ABGEHOBEN · ${sim.spectatorsPerDay} Schaulustige pro Tag", "LIFT-OFF · ${sim.spectatorsPerDay} spectators a day")
            part != null -> tr("${part.step}: ${part.type.name} · M öffnet die Mission", "${part.step}: ${part.type.name} · M opens the mission")
            else -> tr("Der Turm ist komplett verrückt.", "The tower is completely crazy.")
        }
        stageHint.fitWidth(stageBarWidth)
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
        }
        tickerOffset += dt * 60
        val span = tickerText.width + 200
        if (tickerOffset > span) tickerOffset = -tickerClip.width + 200
        tickerText.x = -tickerOffset.coerceAtLeast(0.0) + (if (tickerOffset < 0) -tickerOffset else 0.0)
    }

    private fun syncTooltip() {
        val info = tower.ghostInfo
        val hover = tower.hover
        if (info == null || hover == null || isOverUi(hover.screen)) {
            tooltip.visible = false
            return
        }
        tooltip.removeChildren()
        val (text, ok) = info
        val t = Text(text, 13.0, if (ok) Theme.TEXT else Theme.ON_SELECT, Theme.bodyBold).fitWidth(screenWidth - 60.0)
        val box = Panel(t.width + 28, 32.0, if (ok) Theme.INK else Theme.ALARM, 6.0).addTo(tooltip)
        if (ok) {
            box.color = Theme.SELECT
            Panel(t.width + 24, 28.0, Theme.INK, 5.0).addTo(tooltip).xy(2.0, 2.0)
        }
        t.addTo(tooltip).xy(14.0, 8.0)
        tooltip.xy(
            (hover.screen.x - (t.width + 28) / 2).coerceIn(4.0, screenWidth - t.width - 32),
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

        /** Room for the population left of its label. */
        const val POP_WIDTH = 50.0

        /** Room for "Bewohner" and the mood right of the population. */
        const val POP_LABEL = 58.0

        /** Room for the next payment under the money. */
        const val TOP_MONEY_TEXT = 146.0

        /** Room for the star hint under the five stars, left of the stage. */
        const val STAR_HINT = 130.0
    }
}
