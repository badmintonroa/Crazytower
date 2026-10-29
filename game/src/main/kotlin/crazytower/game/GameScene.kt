package crazytower.game

import crazytower.core.Simulation
import korlibs.event.Key
import korlibs.korge.input.keys
import korlibs.korge.scene.Scene
import korlibs.korge.view.SContainer
import korlibs.korge.view.addTo
import korlibs.korge.view.addUpdater

/** The game screen. [loaded] is a simulation from a save game; without one a new tower starts. */
class GameScene(private val assets: Assets, private val loaded: Simulation? = null) : Scene() {
    override suspend fun SContainer.sceneMain() {
        val sim = loaded ?: Simulation(seed = System.currentTimeMillis())
        val game = GameController(sim)
        val w = views.virtualWidthDouble
        val h = views.virtualHeightDouble
        val tower = TowerView(sim, game, assets, w, h).addTo(this)
        val hud = Hud(sim, game, assets, tower, w, h).addTo(this)
        tower.isOverUi = hud::isOverUi
        Autopilot.attachIfRequested(this, sim, game, tower, hud)
        game.say(if (loaded != null) "Spielstand geladen" else "F5 speichert, F9 lädt", 4.0)
        keys {
            down(Key.F5) { game.say(SaveSlot.save(sim)) }
            down(Key.F9) {
                val (restored, message) = SaveSlot.load()
                if (restored == null) game.say(message) else sceneContainer.changeTo { GameScene(assets, restored) }
            }
        }
        addUpdater { dt ->
            game.update(dt)
            tower.sync(dt.inWholeMilliseconds / 1000.0)
            hud.sync(dt.inWholeMilliseconds / 1000.0)
        }
    }
}
