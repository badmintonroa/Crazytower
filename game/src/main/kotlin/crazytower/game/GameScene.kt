package crazytower.game

import crazytower.core.Simulation
import korlibs.korge.scene.Scene
import korlibs.korge.view.SContainer
import korlibs.korge.view.addTo
import korlibs.korge.view.addUpdater

class GameScene(private val assets: Assets) : Scene() {
    override suspend fun SContainer.sceneMain() {
        val sim = Simulation(seed = System.currentTimeMillis())
        val game = GameController(sim)
        val w = views.virtualWidthDouble
        val h = views.virtualHeightDouble
        val tower = TowerView(sim, game, assets, w, h).addTo(this)
        val hud = Hud(sim, game, assets, tower, w, h).addTo(this)
        tower.isOverUi = hud::isOverUi
        Autopilot.attachIfRequested(this, sim, game, tower)
        addUpdater { dt ->
            game.update(dt)
            tower.sync()
            hud.sync(dt.inWholeMilliseconds / 1000.0)
        }
    }
}
