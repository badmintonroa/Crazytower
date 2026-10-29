package crazytower.game

import korlibs.korge.Korge
import korlibs.korge.scene.sceneContainer
import korlibs.math.geom.Size

suspend fun main() {
    // CRAZYTOWER_SOUNDS=<folder> writes all sounds as WAV files and quits
    System.getenv("CRAZYTOWER_SOUNDS")?.let { Sfx.exportWav(java.io.File(it)); return }
    val settings = Options.current
    Korge(
        windowSize = Size(settings.windowWidth, settings.windowHeight),
        // one virtual pixel per window pixel; GameScene scales the layout to fit
        virtualSize = Size(settings.windowWidth, settings.windowHeight),
        title = "CrazyTower",
        backgroundColor = Theme.SKY,
    ) {
        views.gameWindow.vsync = settings.vsync
        if (settings.fullscreen && !Autopilot.active) views.gameWindow.fullscreen = true
        Theme.loadFonts()
        val assets = Assets.load()
        sceneContainer().changeTo { GameScene(assets) }
    }
}
