package crazytower.game

import crazytower.core.Simulation
import crazytower.core.Stages
import crazytower.core.TestTower
import crazytower.game.ui.Panel
import crazytower.game.ui.TextButton
import crazytower.game.ui.formatNumber
import crazytower.game.ui.label
import korlibs.event.Key
import korlibs.image.paint.LinearGradientPaint
import korlibs.io.async.launchImmediately
import korlibs.korge.input.keys
import korlibs.korge.scene.Scene
import korlibs.korge.view.*

/**
 * The title screen: the menu on the left, the last played tower behind it. The tower keeps
 * living (people come and go, day turns to night) while the camera drifts up and down.
 * Without a save game a test tower stands in.
 */
class TitleScene(private val assets: Assets) : Scene() {
    private lateinit var screenRoot: Container
    private lateinit var tower: TowerView
    private var menu: Container? = null
    private var options: OptionsWindow? = null

    /** The save game, if there is one that loads. */
    private var saved: Simulation? = null

    /** A press on "Neues Spiel" while a save exists asks first; the second press starts. */
    private var confirmNew = false

    override suspend fun SContainer.sceneMain() {
        // CRAZYTOWER_TITLE=<stage> shows a test tower instead of the save game (for screenshots), any other value the save
        val demoStage = System.getenv("CRAZYTOWER_TITLE")?.toIntOrNull()
        saved = if (demoStage != null) null else SaveSlot.load().first
        val sim = saved ?: TestTower.create(demoStage ?: 3, seed = System.currentTimeMillis())
        val game = GameController(sim)
        screenRoot = container()
        val (w, h) = fit(views)
        tower = TowerView(sim, game, assets, w, h, interactive = false).addTo(screenRoot)
        buildMenu(sim, w, h)

        onStageResized(firstTrigger = false) { _, _ -> relayout(views, sim) }
        Options.onChange = { old, new ->
            GameScene.applyWindow(views, old, new)
            if (old.uiScale != new.uiScale) relayout(views, sim)
        }
        keys {
            down(Key.ENTER) { if (saved != null) loadGame() else newGame() }
            down(Key.O) { toggleOptions() }
            down(Key.ESCAPE) { if (options?.visible == true) toggleOptions() }
            down(Key.F11) { Options.update { it.copy(fullscreen = !it.fullscreen) } }
        }

        var seconds = 0.0
        // a tall tower takes longer for the trip to the roof and back
        val top = sim.tower.allRooms.maxOfOrNull { it.floor } ?: 0
        val period = (24.0 + top * 0.8).coerceAtMost(110.0)
        addUpdater { dt ->
            val step = dt.inWholeMilliseconds / 1000.0
            seconds += step
            game.update(dt)
            tower.showcase(seconds, centerX = towerCenter(), width = (layoutWidth - MENU_WIDTH) * 0.9, period = period)
            tower.sync(step)
            options?.let { if (it.visible) it.sync() }
        }
        System.getenv("CRAZYTOWER_SCREENSHOT")?.let { Autopilot.screenshotAfter(this, it, 30) }
    }

    /** The middle of the space right of the menu. */
    private fun towerCenter(): Double = MENU_WIDTH + (layoutWidth - MENU_WIDTH) / 2

    private var layoutWidth = 0.0
    private var layoutSize = 0 to 0

    private fun fit(views: Views): Pair<Double, Double> {
        layoutSize = GameScene.fitScreen(views, screenRoot)
        layoutWidth = layoutSize.first.toDouble()
        return layoutSize.first.toDouble() to layoutSize.second.toDouble()
    }

    private fun relayout(views: Views, sim: Simulation) {
        val before = layoutSize
        val (w, h) = fit(views)
        if (layoutSize == before) return
        tower.resize(w, h)
        buildMenu(sim, w, h)
    }

    private fun buildMenu(sim: Simulation, w: Double, h: Double) {
        menu?.removeFromParent()
        val root = Container().addTo(screenRoot)
        menu = root

        // the menu side fades from dark ink into the tower
        val shade = Theme.alpha(Theme.INK, 0.82)
        val fadeStart = MENU_WIDTH - 40.0
        root.graphics {
            fill(LinearGradientPaint(fadeStart, 0.0, fadeStart + SHADE_WIDTH, 0.0).add(0.0, shade).add(1.0, Theme.alpha(Theme.INK, 0.0))) {
                rect(fadeStart, 0.0, SHADE_WIDTH, h)
            }
        }.also { it.autoScaling = false }
        root.solidRect(fadeStart, h, shade)

        val left = 64.0
        var y = (h / 2 - 300).coerceAtLeast(40.0)
        val crazy = root.label("CRAZY", 64.0, Theme.TEXT, Theme.bodyBold).xy(left, y)
        root.label("TOWER", 64.0, Theme.SELECT, Theme.bodyBold).xy(left + crazy.width + 4, y)
        y += 80.0
        root.label("Der Turm, in dem nichts normal ist", 17.0, Theme.TEXT_DIM).xy(left + 2, y)
        y += 56.0

        val save = saved
        val load = button(root, left, y, "Spiel laden", primary = save != null) { loadGame() }
        load.enabled = SaveSlot.file.exists()
        y += 46.0
        root.label(saveInfo(save), 13.0, Theme.TEXT_DIM).xy(left + 2, y)
        y += 34.0
        lateinit var newButton: TextButton
        newButton = button(root, left, y, "Neues Spiel", primary = save == null) {
            if (saved != null && !confirmNew) {
                confirmNew = true
                newButton.setTitle("Wirklich neu anfangen?")
                newHint.visible = true
            } else {
                newGame()
            }
        }
        y += BUTTON_STEP
        newHint = root.label("Dein Spielstand wird beim nächsten Speichern überschrieben", 12.0, Theme.ALARM)
            .xy(left + 2, y - 10).also { it.visible = false }
        y += 8.0
        button(root, left, y, "Optionen") { toggleOptions() }
        y += BUTTON_STEP

        // the test towers from the options, right here as well
        root.label("Testturm", 15.0, Theme.TEXT, Theme.bodyBold).xy(left + 2, y + 10)
        for (stage in 1..TestTower.MAXIMUM) {
            val max = stage == TestTower.MAXIMUM
            TextButton(if (max) 50.0 else 30.0, 36.0, if (max) "Max" else "$stage", 14.0) { testTower(stage) }
                .addTo(root).xy(left + 96 + (stage - 1) * 34.0, y)
        }
        y += BUTTON_STEP
        button(root, left, y, "Beenden") { views.gameWindow.close() }

        root.label("Enter spielt · O Optionen · F11 Vollbild", 12.0, Theme.TEXT_DIM).xy(left + 2, h - 36.0)
        val caption = if (save != null) "Dein Turm · ${sim.clock.format()}" else "Testturm · ${Stages.all[sim.stage].name}"
        val capText = root.label(caption, 13.0, Theme.alpha(Theme.TEXT, 0.75), Theme.bodyBold)
        capText.xy(w - capText.width - 24.0, h - 36.0)

        val window = OptionsWindow(onTestTower = { testTower(it) }) { toggleOptions() }.addTo(root)
        window.xy((w - window.panelWidth) / 2, ((h - window.panelHeight) / 2).coerceAtLeast(20.0))
        window.visible = options?.visible ?: false
        options = window
    }

    private lateinit var newHint: Text

    private fun button(root: Container, x: Double, y: Double, title: String, primary: Boolean = false, onPress: () -> Unit): TextButton =
        TextButton(BUTTON_WIDTH, 50.0, title, if (primary) 20.0 else 18.0, onPress = onPress).addTo(root).xy(x, y).also {
            it.selected = primary
        }

    private fun saveInfo(save: Simulation?): String {
        if (save == null) return if (SaveSlot.file.exists()) SaveSlot.load().second else "Noch kein Spielstand vorhanden"
        val stars = when (save.stars) { 0 -> "ohne Sterne"; 1 -> "1 Stern"; else -> "${save.stars} Sterne" }
        return "${Stages.all[save.stage].name} · $stars · ${formatNumber(save.population.toLong())} Leute · Tag ${save.clock.day + 1}"
    }

    private fun toggleOptions() {
        val window = options ?: return
        window.visible = !window.visible
    }

    /** Loads the save fresh from disk, as F9 does in the game: the tower behind the menu has run on a little. */
    private fun loadGame() {
        val sim = SaveSlot.load().first ?: return
        launchImmediately { sceneContainer.changeTo { GameScene(assets, sim) } }
    }

    private fun newGame() {
        launchImmediately { sceneContainer.changeTo { GameScene(assets) } }
    }

    private fun testTower(stage: Int) {
        launchImmediately {
            sceneContainer.changeTo { GameScene(assets, TestTower.create(stage, seed = System.currentTimeMillis()), test = true) }
        }
    }

    private companion object {
        const val MENU_WIDTH = 460.0
        const val SHADE_WIDTH = 220.0
        const val BUTTON_WIDTH = 320.0
        const val BUTTON_STEP = 62.0
    }
}
