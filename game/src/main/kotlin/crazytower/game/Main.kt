package crazytower.game

import korlibs.korge.Korge
import korlibs.korge.scene.sceneContainer
import korlibs.math.geom.Size
import korlibs.render.GameWindowCreationConfig

suspend fun main() {
    // CRAZYTOWER_SOUNDS=<folder> writes all sounds as WAV files and quits
    System.getenv("CRAZYTOWER_SOUNDS")?.let { Sfx.exportWav(java.io.File(it)); return }
    val settings = Options.current
    val borderless = settings.borderless && !settings.fullscreen && !Autopilot.active
    val (width, height) = if (borderless) Borderless.screenSize() else settings.windowWidth to settings.windowHeight
    Korge(
        windowSize = Size(width, height),
        // one virtual pixel per window pixel; GameScene scales the layout to fit
        virtualSize = Size(width, height),
        windowCreationConfig = GameWindowCreationConfig(decorated = !borderless),
        title = "CrazyTower",
        backgroundColor = Theme.SKY,
    ).start {
        views.gameWindow.vsync = settings.vsync
        if (settings.fullscreen && !Autopilot.active) views.gameWindow.fullscreen = true
        if (borderless) Borderless.cover(views.gameWindow)
        Theme.loadFonts()
        val assets = Assets.load()
        sceneContainer().changeTo { GameScene(assets) }
    }
}
