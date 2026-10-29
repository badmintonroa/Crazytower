package crazytower.game

import crazytower.core.Settings
import crazytower.core.Simulation
import korlibs.event.Key
import korlibs.korge.input.keys
import korlibs.korge.scene.Scene
import korlibs.korge.view.SContainer
import korlibs.korge.view.Views
import korlibs.korge.view.addTo
import korlibs.korge.view.addUpdater
import korlibs.korge.view.onStageResized

/** The game screen. [loaded] is a simulation from a save game; without one a new tower starts. */
class GameScene(private val assets: Assets, private val loaded: Simulation? = null) : Scene() {
    private lateinit var tower: TowerView
    private var hud: Hud? = null

    override suspend fun SContainer.sceneMain() {
        val sim = loaded ?: Simulation(seed = System.currentTimeMillis())
        val game = GameController(sim)
        fitVirtualScreen(views)
        val w = views.virtualWidthDouble
        val h = views.virtualHeightDouble
        tower = TowerView(sim, game, assets, w, h).addTo(this)
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

    private fun SContainer.layoutHud(sim: Simulation, game: GameController, w: Double, h: Double): Hud {
        hud?.removeFromParent()
        val created = Hud(sim, game, assets, tower, w, h).addTo(this)
        tower.isOverUi = created::isOverUi
        hud = created
        return created
    }

    private fun SContainer.relayout(views: Views, sim: Simulation, game: GameController) {
        if (!fitVirtualScreen(views)) return
        val w = views.virtualWidthDouble
        val h = views.virtualHeightDouble
        tower.resize(w, h)
        layoutHud(sim, game, w, h)
    }

    private fun autosave(sim: Simulation, game: GameController) {
        val every = Options.current.autosaveDays
        if (every <= 0 || sim.clock.day % every != 0 || Autopilot.active) return
        val message = SaveSlot.save(sim)
        game.say(if (message.startsWith("Gespeichert")) "Automatisch gespeichert" else message)
    }

    companion object {
        /** Sets the virtual screen to the window's aspect ratio. Returns true when it changed. */
        fun fitVirtualScreen(views: Views): Boolean {
            val window = views.gameWindow
            val (w, h) = Options.current.virtualSize(window.width, window.height)
            if (w == views.virtualWidth && h == views.virtualHeight) return false
            views.setVirtualSize(w, h)
            return true
        }

        fun applyWindow(views: Views, old: Settings, new: Settings) {
            val window = views.gameWindow
            if (old.vsync != new.vsync) window.vsync = new.vsync
            if (old.fullscreen != new.fullscreen) window.fullscreen = new.fullscreen
            val sized = old.windowWidth != new.windowWidth || old.windowHeight != new.windowHeight
            if (!new.fullscreen && (sized || old.fullscreen != new.fullscreen)) window.setSize(new.windowWidth, new.windowHeight)
        }
    }
}
