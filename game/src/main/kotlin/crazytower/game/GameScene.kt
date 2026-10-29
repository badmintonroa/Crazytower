package crazytower.game

import crazytower.core.Settings
import crazytower.core.Simulation
import korlibs.event.Key
import korlibs.korge.input.keys
import korlibs.korge.scene.Scene
import korlibs.korge.view.Container
import korlibs.korge.view.SContainer
import korlibs.korge.view.container
import korlibs.korge.view.Views
import korlibs.korge.view.addTo
import korlibs.korge.view.addUpdater
import korlibs.korge.view.onStageResized

/** The game screen. [loaded] is a simulation from a save game; without one a new tower starts. */
class GameScene(private val assets: Assets, private val loaded: Simulation? = null) : Scene() {
    private lateinit var tower: TowerView
    private var hud: Hud? = null

    /**
     * Everything is laid out in layout units (800 high at 100 % interface size) inside this
     * container, which is scaled to the real window pixels. So text, shapes and room art are
     * drawn at the window's full resolution instead of being blown up from a small image.
     */
    private lateinit var screenRoot: Container

    override suspend fun SContainer.sceneMain() {
        val sim = loaded ?: Simulation(seed = System.currentTimeMillis())
        val game = GameController(sim)
        screenRoot = container()
        val (w, h) = fitScreen(views)
        tower = TowerView(sim, game, assets, w, h).addTo(screenRoot)
        val hud = layoutHud(sim, game, w, h)
        Autopilot.attachIfRequested(this, sim, game, tower, hud)
        game.say(if (loaded != null) "Spielstand geladen" else "F5 speichert, F9 lädt", 4.0)

        // window resized or options changed: no black bars, the HUD is laid out again
        onStageResized(firstTrigger = false) { _, _ -> relayout(views, sim, game) }
        Options.onChange = { old, new ->
            applyWindow(views, old, new)
            game.fastNights = new.fastNights
            if (old.uiScale != new.uiScale) relayout(views, sim, game)
        }

        keys {
            down(Key.F5) { game.say(SaveSlot.save(sim)) }
            down(Key.F9) {
                val (restored, message) = SaveSlot.load()
                if (restored == null) game.say(message) else sceneContainer.changeTo { GameScene(assets, restored) }
            }
            down(Key.F11) { Options.update { it.copy(fullscreen = !it.fullscreen) } }
        }
        var lastDay = sim.clock.day
        addUpdater { dt ->
            game.update(dt)
            tower.sync(dt.inWholeMilliseconds / 1000.0)
            this@GameScene.hud?.sync(dt.inWholeMilliseconds / 1000.0)
            if (sim.clock.day != lastDay) {
                lastDay = sim.clock.day
                autosave(sim, game)
            }
        }
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
        if (every <= 0 || sim.clock.day % every != 0 || Autopilot.active) return
        val message = SaveSlot.save(sim)
        game.say(if (message.startsWith("Gespeichert")) "Automatisch gespeichert" else message)
    }

    /** The layout size the HUD was built for. */
    private var layout = 0 to 0

    /**
     * Makes one virtual pixel one window pixel (no black bars, no upscaling) and scales [screenRoot]
     * so the layout fits the window. Returns the layout size in layout units.
     */
    private fun fitScreen(views: Views): Pair<Double, Double> {
        val window = views.gameWindow
        val width = window.width.coerceAtLeast(1)
        val height = window.height.coerceAtLeast(1)
        if (views.virtualWidth != width || views.virtualHeight != height) views.setVirtualSize(width, height)
        val (w, h) = Options.current.virtualSize(width, height)
        screenRoot.scale = height.toDouble() / h
        layout = w to h
        return w.toDouble() to h.toDouble()
    }

    companion object {
        fun applyWindow(views: Views, old: Settings, new: Settings) {
            val window = views.gameWindow
            if (old.vsync != new.vsync) window.vsync = new.vsync
            if (old.fullscreen != new.fullscreen) window.fullscreen = new.fullscreen
            val sized = old.windowWidth != new.windowWidth || old.windowHeight != new.windowHeight
            if (!new.fullscreen && (sized || old.fullscreen != new.fullscreen)) window.setSize(new.windowWidth, new.windowHeight)
        }
    }
}
