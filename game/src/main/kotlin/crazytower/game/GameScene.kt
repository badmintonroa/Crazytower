package crazytower.game

import crazytower.core.Settings
import crazytower.core.Simulation
import crazytower.core.TestTower
import korlibs.event.Key
import korlibs.korge.input.keys
import korlibs.korge.scene.Scene
import korlibs.io.async.launchImmediately
import korlibs.korge.view.Container
import korlibs.korge.view.SContainer
import korlibs.korge.view.container
import korlibs.korge.view.Views
import korlibs.korge.view.addTo
import korlibs.korge.view.addUpdater
import korlibs.korge.view.xy
import korlibs.korge.view.onStageResized

/**
 * The game screen. [loaded] is a simulation from a save game; without one a new tower starts.
 * A [test] tower is never saved, so it cannot overwrite the real game.
 */
class GameScene(private val assets: Assets, private val loaded: Simulation? = null, private val test: Boolean = false) : Scene() {
    private lateinit var tower: TowerView
    private var hud: Hud? = null
    private var debug: DebugOverlay? = null

    /**
     * Everything is laid out in layout units (800 high at 100 % interface size) inside this
     * container, which is scaled to the real window pixels. So text, shapes and room art are
     * drawn at the window's full resolution instead of being blown up from a small image.
     */
    private lateinit var screenRoot: Container

    override suspend fun SContainer.sceneMain() {
        // CRAZYTOWER_TEST=<stage> starts a test tower right away (for screenshots), 6 the maximum tower
        val sim = loaded ?: System.getenv("CRAZYTOWER_TEST")?.toIntOrNull()?.let { TestTower.create(it) }
            ?: Simulation(seed = System.currentTimeMillis())
        val game = GameController(sim)
        // the real game is saved first, so the title screen shows it behind the menu
        game.onMainMenu = {
            if (!test) SaveSlot.save(sim)
            launchImmediately { sceneContainer.changeTo { TitleScene(assets) } }
        }
        screenRoot = container()
        val (w, h) = fitScreen(views)
        tower = TowerView(sim, game, assets, w, h).addTo(screenRoot)
        val hud = layoutHud(sim, game, w, h)
        val debug = DebugOverlay(sim, tower, views).addTo(this).xy(8.0, 80.0)
        this@GameScene.debug = debug
        System.getenv("CRAZYTOWER_SPEED")?.toIntOrNull()?.let { game.speed = it }
        Autopilot.attachIfRequested(this, sim, game, tower, hud)
        game.say(
            when {
                test -> "Testturm: wird nicht gespeichert, F9 holt dein Spiel zurück"
                loaded != null -> "Spielstand geladen"
                else -> "F5 speichert, F9 lädt"
            },
            5.0,
        )
        game.onTestTower = { stage ->
            // the real game is saved first, so F9 brings it back
            if (!test) SaveSlot.save(sim)
            launchImmediately {
                sceneContainer.changeTo { GameScene(assets, TestTower.create(stage, seed = System.currentTimeMillis()), test = true) }
            }
        }

        // window resized or options changed: no black bars, the HUD is laid out again
        onStageResized(firstTrigger = false) { _, _ -> relayout(views, sim, game) }
        Options.onChange = { old, new ->
            applyWindow(views, old, new)
            if (old.borderless != new.borderless) game.say("Fenster-Vollbild gilt ab dem nächsten Start", 4.0)
            game.fastNights = new.fastNights
            if (old.uiScale != new.uiScale) relayout(views, sim, game)
        }

        keys {
            down(Key.F5) { game.say(if (test) "Testtürme werden nicht gespeichert" else SaveSlot.save(sim)) }
            down(Key.F9) {
                val (restored, message) = SaveSlot.load()
                if (restored == null) game.say(message) else sceneContainer.changeTo { GameScene(assets, restored) }
            }
            down(Key.F11) { Options.update { it.copy(fullscreen = !it.fullscreen) } }
            down(Key.F3) { debug.toggle() }
        }
        var lastDay = sim.clock.day
        addUpdater { dt ->
            val t0 = System.nanoTime()
            game.update(dt)
            val t1 = System.nanoTime()
            tower.sync(dt.inWholeMilliseconds / 1000.0)
            val t2 = System.nanoTime()
            this@GameScene.hud?.sync(dt.inWholeMilliseconds / 1000.0)
            debug.add(t1 - t0, t2 - t1, System.nanoTime() - t2)
            debug.frame(dt.inWholeMicroseconds / 1e6)
            if (sim.clock.day != lastDay) {
                lastDay = sim.clock.day
                autosave(sim, game)
            }
        }
    }

    override suspend fun sceneDestroy() {
        debug?.dispose()
        super.sceneDestroy()
    }

    private fun layoutHud(sim: Simulation, game: GameController, w: Double, h: Double): Hud {
        hud?.removeFromParent()
        val created = Hud(sim, game, assets, tower, w, h).addTo(screenRoot)
        tower.isOverUi = created::isOverUi
        hud = created
        return created
    }

    private fun relayout(views: Views, sim: Simulation, game: GameController) {
        val before = layout
        val (w, h) = fitScreen(views)
        if (layout == before) return
        tower.resize(w, h)
        layoutHud(sim, game, w, h)
    }

    private fun autosave(sim: Simulation, game: GameController) {
        val every = Options.current.autosaveDays
        if (every <= 0 || sim.clock.day % every != 0 || Autopilot.active || test) return
        val message = SaveSlot.save(sim)
        game.say(if (message.startsWith("Gespeichert")) "Automatisch gespeichert" else message)
    }

    /** The layout size the HUD was built for. */
    private var layout = 0 to 0

    private fun fitScreen(views: Views): Pair<Double, Double> {
        layout = fitScreen(views, screenRoot)
        return layout.first.toDouble() to layout.second.toDouble()
    }

    companion object {
        /**
         * Makes one virtual pixel one window pixel (no black bars, no upscaling) and scales [root]
         * so the layout fits the window. Returns the layout size in layout units.
         */
        fun fitScreen(views: Views, root: Container): Pair<Int, Int> {
            val window = views.gameWindow
            val width = window.width.coerceAtLeast(1)
            val height = window.height.coerceAtLeast(1)
            if (views.virtualWidth != width || views.virtualHeight != height) views.setVirtualSize(width, height)
            val (w, h) = Options.current.virtualSize(width, height)
            root.scale = height.toDouble() / h
            return w to h
        }

        fun applyWindow(views: Views, old: Settings, new: Settings) {
            val window = views.gameWindow
            if (old.vsync != new.vsync) window.vsync = new.vsync
            // leave the old mode first, then enter the new one
            if (old.fullscreen && !new.fullscreen) window.fullscreen = false
            if (!old.fullscreen && new.fullscreen) window.fullscreen = true
            val sized = old.windowWidth != new.windowWidth || old.windowHeight != new.windowHeight
            val windowed = !new.fullscreen && !new.borderless
            val wasWindowed = !old.fullscreen && !old.borderless
            if (windowed && (sized || !wasWindowed)) window.setSize(new.windowWidth, new.windowHeight)
        }
    }
}
