package crazytower.game

import crazytower.core.Catalog
import crazytower.core.Category
import crazytower.core.GameClock
import crazytower.core.NewsItem
import crazytower.core.RoomType
import crazytower.core.Simulation
import crazytower.core.Stages
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
 * placement tooltip and short messages. Layout follows the UI design (1280x800).
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
    private val tooltip = container { visible = false }
    private val toast = container { visible = false }

    private lateinit var moneyText: Text
    private lateinit var popText: Text
    private lateinit var stageText: Text
    private lateinit var stageHint: Text
    private lateinit var stageBar: Graphics
    private lateinit var stars: Container
    private lateinit var moodText: Text
    private var shownStars = -1
    private lateinit var clockText: Text
    private val speedButtons = ArrayList<Pair<Int, TextButton>>()
    private val categoryButtons = HashMap<Category, Button>()
    private var flyoutKey: Pair<Category?, Int>? = null
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
            add(Rectangle(0.0, screenHeight - 50.0, screenWidth, 50.0))
        }
        return rects.any { it.contains(point) }
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
        box(170.0) {
            coin(this, 16.0, 19.0)
            moneyText = label("", 20.0, Theme.TEXT, Theme.pixel).also { it.xy(46.0, 17.0) }
        }
        box(160.0) {
            solidRect(8.0, 8.0, Theme.alpha(Theme.SELECT, 0.9)).xy(20.0, 18.0)
            solidRect(14.0, 12.0, Theme.alpha(Theme.SELECT, 0.6)).xy(17.0, 28.0)
            popText = label("", 20.0, Theme.TEXT, Theme.pixel).also { it.xy(42.0, 17.0) }
            label("Bewohner", 12.0, Theme.TEXT_DIM).xy(95.0, 14.0)
            moodText = label("", 12.0, Theme.TEXT_DIM).also { it.xy(95.0, 30.0) }
        }
        val stageWidth = screenWidth - 24 - x - 150 - 10 - 206
        box(stageWidth) {
            stars = container { xy(16.0, 20.0) }
            stageText = label("", 16.0, Theme.TEXT, Theme.pixel).also { it.xy(140.0, 12.0) }
            stageHint = label("", 12.0, Theme.TEXT_DIM).also { it.xy(140.0, 34.0) }
            stageBar = graphics { }.also { it.xy(stageWidth - 180.0, 26.0) }
        }
        box(150.0) {
            sun(this, 16.0, 19.0)
            clockText = label("", 18.0, Theme.TEXT, Theme.pixel).also { it.xy(46.0, 18.0) }
        }
        box(206.0) {
            val speeds = listOf(0 to "II", 1 to "1×", 2 to "2×", 4 to "4×")
            speeds.forEachIndexed { i, (speed, title) ->
                val b = TextButton(44.0, 40.0, title) { game.speed = speed }
                b.xy(10.0 + i * 48.0, 10.0)
                addChild(b)
                speedButtons += speed to b
            }
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
            val key = b.content.label("${i + 1}", 11.0, Theme.TEXT_DIM, Theme.pixel).also { it.xy(56.0, 3.0) }
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
        val key = game.category to sim.stage
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
        flyout.label(category.label.uppercase(), 20.0, Theme.TEXT, Theme.pixel).xy(14.0, 12.0)
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
                val card = ItemCard(type, sim.stage >= type.minStage)
                card.xy(14.0, y)
                flyout.addChild(card)
                y += 68.0
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

    /** A build list entry: art thumbnail, name and price (or the stage that unlocks it). */
    private inner class ItemCard(val type: RoomType, unlocked: Boolean) :
        Button(242.0, 62.0, { game.tool = Tool.Build(type) }) {
        init {
            enabled = unlocked
            content.image(assets.room(type.art)) {
                scale = minOf(72.0 / bitmap.width, 44.0 / bitmap.height)
                xy(10.0 + (72.0 - bitmap.width * scale) / 2, (59.0 - bitmap.height * scale) / 2)
                alpha = if (unlocked) 1.0 else 0.35
            }
            val name = content.label(type.name, 15.0, Theme.TEXT, Theme.bodyBold).also { it.xy(92.0, 12.0) }
            val sub = content.label(
                if (unlocked) formatMoney(type.cost) else "AB STUFE ${type.minStage}", 13.0, Theme.TEXT, Theme.pixel,
            ).also { it.xy(92.0, 33.0) }
            onRestyle = { name.color = it.ink; sub.color = it.ink }
            onRestyle(this)
        }
    }

    // ---------------------------------------------------------------- ticker

    private fun buildTicker() {
        ticker.solidRect(screenWidth, 50.0, Theme.INK)
        ticker.solidRect(screenWidth, 2.0, Theme.SELECT)
        val badge = Panel(150.0, 28.0, Theme.ALARM, 6.0).addTo(ticker).also { it.xy(12.0, 11.0) }
        badge.label("TOWER-TICKER", 15.0, Theme.ON_SELECT, Theme.pixel).xy(14.0, 4.0)
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
            down(Key.ESCAPE) { game.cancel(); if (game.category == null) game.selection = null }
        }
    }

    // ---------------------------------------------------------------- per frame

    fun sync(dtSeconds: Double) {
        moneyText.text = formatMoney(sim.money)
        moneyText.color = if (sim.money < 0) Theme.ALARM else Theme.TEXT
        popText.text = formatNumber(sim.population.toLong())
        val mood = sim.satisfaction.toInt()
        moodText.text = "Laune $mood %"
        moodText.color = if (mood < 40) Theme.ALARM else Theme.TEXT_DIM
        syncStage()
        clockText.text = sim.clock.format()
        clockText.color = if (game.speed == 0) Theme.ALARM else Theme.TEXT
        speedButtons.forEach { (speed, b) -> b.selected = game.speed == speed }
        categoryButtons.forEach { (c, b) -> b.selected = game.category == c }
        refreshFlyout()
        inspector.sync()
        syncTicker(dtSeconds)
        syncTooltip()
        syncToast()
    }

    private fun syncStage() {
        val stage = sim.stage
        if (sim.stars != shownStars) {
            shownStars = sim.stars
            stars.removeChildren()
            for (i in 0 until 5) Icons.star(stars, i < sim.stars).xy(i * 22.0, 0.0)
        }
        if (stage != shownStage) {
            shownStage = stage
            stageText.text = "STUFE $stage · ${Stages.info(stage).name.uppercase()}"
        }
        val next = if (stage < 5) Stages.info(stage + 1) else null
        stageHint.text = next?.let { "Für Stufe ${it.number}: ${it.requirement}" } ?: "Der Turm ist komplett verrückt."
        val progress = sim.stageProgress.coerceIn(0.0, 1.0)
        stageBar.updateShape {
            fill(Theme.INK) { rect(0.0, 0.0, 164.0, 10.0) }
            var x = 0.0
            while (x < 164.0 * progress - 1) {
                fill(Theme.SELECT) { rect(x, 0.0, minOf(10.0, 164.0 * progress - x), 10.0) }
                x += 12.0
            }
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
