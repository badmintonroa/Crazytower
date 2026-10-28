package crazytower.game

import korlibs.korge.Korge
import korlibs.korge.scene.sceneContainer
import korlibs.math.geom.Size

suspend fun main() = Korge(
    windowSize = Size(1280, 800),
    virtualSize = Size(1280, 800),
    title = "CrazyTower",
    backgroundColor = Theme.SKY,
) {
    Theme.loadFonts()
    val assets = Assets.load()
    sceneContainer().changeTo { GameScene(assets) }
}
