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
import crazytower.game.ui.Button
import crazytower.game.ui.Icons
import crazytower.game.ui.Panel
import crazytower.game.ui.TextButton
import crazytower.game.ui.formatMoney
import crazytower.game.ui.formatNumber
import crazytower.game.ui.label
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
    private val flyout = panel(270.0, 100.0).also { it.xy(110.0, 84.0) }
    private val inspector = Inspector(sim, game, assets).addTo(this).also { it.xy(screenWidth - 302.0, 84.0) }
    private val ticker = container { xy(0.0, screenHeight - 50.0) }
    private val finance = FinanceWindow(sim) { game.financeOpen = false }.addTo(this).also {
        it.xy((screenWidth - it.panelWidth) / 2, 96.0)
    }
    private val options = OptionsWindow(onTestTower = { game.onTestTower(it) }) { game.optionsOpen = false }.addTo(this).also {
        it.xy((screenWidth - it.panelWidth) / 2, ((screenHeight - it.panelHeight) / 2).coerceAtLeast(84.0))
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
    private val categoryButtons = HashMap<Category, Button>()
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
            label("Bewohner", 12.0, Theme.TEXT_DIM).xy(95.0, 14.0)
            moodText = label("", 12.0, Theme.TEXT_DIM).also { it.xy(95.0, 30.0) }
        }
        val stageWidth = screenWidth - 24 - x - 150 - 10 - 254 - 10 - 60
        box(stageWidth) {
            stars = container { xy(16.0, 12.0) }
            starHint = label("", 11.0, Theme.GOLD).also { it.xy(16.0, 36.0) }
            stageText = label("", 16.0, Theme.TEXT, Theme.bodyBold).also { it.xy(152.0, 12.0) }
            stageHint = label("", 12.0, Theme.TEXT_DIM).also { it.xy(152.0, 34.0) }
            stageBarWidth = stageWidth - 168.0
            stageBar = graphics { }.also { it.xy(152.0, 50.0) }
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
            val name = b.content.label(category.label, 11.0, Theme.TEXT, Theme.bodyBold)
            val key = b.content.label("${i + 1}", 11.0, Theme.TEXT_DIM, Theme.bodyBold).also { it.xy(56.0, 3.0) }
            b.onRestyle = {
                Icons.draw(icon, category, it.ink)
                name.color = it.ink
                name.xy((68.0 - name.width) / 2, 42.0)
                key.color = if (it.selected) Theme.ON_SELECT else Theme.TEXT_DIM
            }
            b.selected = false
            b.onRestyle(b)
            b.xy(10.0, 10.0 + i * 76.0)
            buildBar.addChild(b)
            categoryButtons[category] = b
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
        val bg = Panel(270.0, 100.0).addTo(flyout)
        flyout.label(category.label.uppercase(), 20.0, Theme.TEXT, Theme.bodyBold).xy(14.0, 12.0)
        flyout.label("Esc schließt", 12.0, Theme.TEXT_DIM).xy(180.0, 18.0)
        var y = 44.0
        if (category == Category.DEMOLISH) {
            flyout.label("Klick auf einen Raum reißt ihn ab.\nDu bekommst ein Viertel der Baukosten\nzurück.", 13.0, Theme.TEXT_DIM).xy(14.0, y)
            y += 64.0
        } else {
            val types = Catalog.roomTypes.filter { it.category == category }
            if (types.isEmpty()) {
                flyout.label("Kommt mit einer späteren Stufe.", 13.0, Theme.TEXT_DIM).xy(14.0, y)
                y += 30.0
            }
            for (type in types) {
                val built = type.maxCount?.let { max -> sim.tower.allRooms.count { it.type == type } >= max } == true
                val card = ItemCard(type, sim.stage >= type.minStage && sim.bestStars >= type.minStars && !built, built)
                card.xy(14.0, y)
                flyout.addChild(card)
                y += card.cardHeight + 6
            }
            flyout.label("Klicken zum Wählen, im Turm\nklicken zum Bauen. Rechtsklick\nbricht ab.", 12.0, Theme.TEXT_DIM).xy(14.0, y + 2)
            y += 54.0
        }
        bg.resize(270.0, y + 10)
        flyout.panelWidth = 270.0
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
        val type: RoomType, unlocked: Boolean, built: Boolean = false, val info: List<String> = roomInfo(type),
        val cardHeight: Double = if (info.isEmpty()) 62.0 else 66.0 + info.size * 17.0,
    ) : Button(242.0, cardHeight, { game.tool = Tool.Build(type) }) {
        init {
            enabled = unlocked
            content.image(assets.room(type.art)) {
                scale = minOf(72.0 / bitmap.width, 44.0 / bitmap.height)
                xy(10.0 + (72.0 - bitmap.width * scale) / 2, (59.0 - bitmap.height * scale) / 2)
                alpha = if (unlocked) 1.0 else 0.35
            }
            val name = content.label(type.name, 15.0, Theme.TEXT, Theme.bodyBold).also { it.xy(92.0, 12.0) }
            val sub = content.label(
                if (built) "SCHON GEBAUT" else if (!unlocked) (if (sim.stage < type.minStage) "AB STUFE ${type.minStage}" else "AB ${type.minStars} STERNEN") else if (type.perSegment) "${formatMoney(type.cost)}/FELD" else formatMoney(type.cost), 13.0, Theme.TEXT, Theme.bodyBold,
            ).also { it.xy(92.0, 33.0) }
            val lines = info.mapIndexed { i, line -> content.label(line, 12.0, Theme.TEXT_DIM).also { it.xy(12.0, 60.0 + i * 17.0) } }
            onRestyle = { b ->
                name.color = b.ink
                sub.color = b.ink
                lines.forEach { it.color = if (b.selected) b.ink else Theme.TEXT_DIM }
            }
            onRestyle(this)
        }
    }

    /** Who a room of [type] brings into the tower and how it pays, for the tenants that would come now. */
    private fun roomInfo(type: RoomType): List<String> {
        val tenants = Tenants.candidates(type.role, sim.stars, sim.stage)
        fun range(values: List<Long>, bonus: Double = sim.starBonus): String {
            if (values.isEmpty()) return "–"
            val low = (values.min() * bonus).toLong()
            val high = (values.max() * bonus).toLong()
            return if (low == high) formatMoney(low) else "${formatNumber(low)}–${formatMoney(high)}"
        }
        return when (type.role) {
            RoomRole.HOME -> listOf(
                "${type.capacity} Bewohner, nur ${sim.flatBuyersPerDay} Käufer pro Tag",
                "Verkauf einmalig: ${range(tenants.map { it.salePrice }, bonus = 1.0)}",
                "dann ${formatMoney((Simulation.HOME_FEE * sim.starBonus).toLong())} Hausgeld pro Tag",
            )
            RoomRole.OFFICE -> listOf(
                "${type.capacity} Angestellte, kommen zur Arbeit",
                "Miete pro Arbeitstag: ${range(tenants.map { it.rentPerDay })}",
                "mehr Sterne bringen bessere Mieter",
            )
            RoomRole.FOOD -> listOf(
                "${type.capacity} Angestellte, Pflicht für Stern 3",
                "Miete pro Tag: ${range(tenants.map { it.rentPerDay })}",
                "plus ${formatMoney(Simulation.MEAL_PRICE)}–${formatMoney(Simulation.VISITOR_MEAL_PRICE)} pro Essen",
            )
            RoomRole.PARKING -> type.effects + "gebucht: ${sim.parkedCars} von ${sim.carsWanted} Autos, ${sim.parkingSpaces} Plätze"
            RoomRole.ELEVATOR -> listOf(
                "Unterhalt pro Tag: ${formatMoney(Simulation.UPKEEP.getValue(RoomRole.ELEVATOR))} pro Etage",
                "plus ${formatMoney(Simulation.CABIN_UPKEEP)} pro Kabine",
                "je Schacht mehr: +${(Simulation.SHAFT_SURCHARGE * 100).toInt()} % Bau, +${(Simulation.UPKEEP_SURCHARGE * 100).toInt()} % Unterhalt",
            )
            else -> type.effects
        }
    }

    // ---------------------------------------------------------------- ticker

    private fun buildTicker() {
        ticker.solidRect(screenWidth, 50.0, Theme.INK)
        ticker.solidRect(screenWidth, 2.0, Theme.SELECT)
        val badge = Panel(150.0, 28.0, Theme.ALARM, 6.0).addTo(ticker).also { it.xy(12.0, 11.0) }
        badge.label("TOWER-TICKER", 15.0, Theme.ON_SELECT, Theme.bodyBold).xy(14.0, 4.0)
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
                game.say(if (game.fastNights) "Nachts vorspulen: an" else "Nachts vorspulen: aus")
            }
            down(Key.F) { game.financeOpen = !game.financeOpen }
            down(Key.O) { game.optionsOpen = !game.optionsOpen }
            down(Key.ESCAPE) {
                when {
                    game.optionsOpen -> game.optionsOpen = false
                    game.financeOpen -> game.financeOpen = false
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
            "nächste: +${formatMoney(coming.filter { it.minute == m }.sumOf { it.amount })} um ${clockOf(m)}"
        } ?: "keine Einnahmen in Sicht"
        finance.visible = game.financeOpen
        if (finance.visible) finance.sync()
        options.visible = game.optionsOpen
        if (options.visible) options.sync()
        popText.text = formatNumber(sim.population.toLong())
        val mood = sim.satisfaction.toInt()
        moodText.text = "Laune $mood %"
        moodText.color = if (mood < 40) Theme.ALARM else Theme.TEXT_DIM
        syncStage()
        celebration.update(dtSeconds)
        clockText.text = sim.clock.format() + if (game.speed > 0 && game.nightBoost > 1) " »" else ""
        clockText.color = if (game.speed == 0) Theme.ALARM else Theme.TEXT
        speedButtons.forEach { (speed, b) -> b.selected = game.speed == speed }
        categoryButtons.forEach { (c, b) -> b.selected = game.category == c }
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
            stageText.text = "STUFE $stage · ${Stages.info(stage).name.uppercase()}"
        }
        val missing = sim.nextStarMissing
        starHint.text = when {
            sim.stars >= Rating.steps.size -> ""
            // everything is there, the rating catches up at the next check
            missing.isEmpty() -> "Stern ${sim.stars + 1}: kommt bald"
            else -> "Stern ${sim.stars + 1}: ${missing.first()}"
        }
        val next = if (stage < Stages.last) Stages.info(stage + 1) else null
        stageHint.text = next?.let { "Für Stufe ${it.number}: ${it.requirement}" } ?: "Der Turm ist komplett verrückt."
        val progress = sim.stageProgress.coerceIn(0.0, 1.0)
        val w = stageBarWidth
        stageBar.updateShape {
            fill(Theme.INK) { rect(0.0, 0.0, w, 4.0) }
            fill(Theme.SELECT) { rect(0.0, 0.0, w * progress, 4.0) }
        }
    }

    private fun syncTicker(dt: Double) {
        val items = sim.news.latest
        if (items != tickerItems) {
            tickerItems = items
            tickerText.text = if (items.isEmpty()) {
                "Willkommen in CrazyTower. Bau eine Lobby, dann kommen die ersten Neugierigen."
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
        val t = Text(text, 13.0, if (ok) Theme.TEXT else Theme.ON_SELECT, Theme.bodyBold)
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
            val t = Text(message, 15.0, Theme.ON_SELECT, Theme.bodyBold)
            Panel(t.width + 32, 38.0, Theme.GOLD, 8.0).addTo(toast)
            t.addTo(toast).xy(16.0, 10.0)
            toast.xy((screenWidth - t.width - 32) / 2, 92.0)
        }
        toast.visible = true
    }
}
