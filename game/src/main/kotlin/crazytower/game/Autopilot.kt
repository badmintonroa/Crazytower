package crazytower.game

import crazytower.core.Catalog
import crazytower.core.Loc
import crazytower.core.Simulation
import korlibs.image.format.PNG
import korlibs.image.format.writeTo
import korlibs.io.file.std.localVfs
import korlibs.korge.view.*
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

/**
 * Development helper: with CRAZYTOWER_SCREENSHOT=<file.png> the game builds a demo tower,
 * lets it run for a while, saves a screenshot and quits. Used to check the rendering
 * on machines without a screen.
 */
object Autopilot {
    fun attachIfRequested(root: SContainer, sim: Simulation, game: GameController, tower: TowerView) {
        val target = System.getenv("CRAZYTOWER_SCREENSHOT") ?: return
        val minutes = System.getenv("CRAZYTOWER_MINUTES")?.toDoubleOrNull() ?: 90.0
        DemoTower.build(sim)
        sim.step(minutes)
        game.selection = sim.tower.allRooms.firstOrNull()?.let { Selection.RoomSel(it.id) }
        tower.lookAt(Loc(2, sim.tower.width / 2f))
        var frames = 0
        root.addUpdater {
            frames++
            if (frames == 20) {
                root.launchScreenshot(target)
            }
        }
    }

    private fun SContainer.launchScreenshot(target: String) {
        val stage = stage ?: return
        kotlinx.coroutines.CoroutineScope(stage.views.coroutineContext).launch {
            val bmp = stage.renderToBitmap(stage.views, includeBackground = true)
            bmp.writeTo(localVfs(target), PNG)
            exitProcess(0)
        }
    }
}

/** A small prebuilt tower for demos and screenshots: lobbies, a few floors, stairs and an elevator. */
object DemoTower {
    fun build(sim: Simulation) {
        val mid = sim.tower.width / 2 - 12
        for (i in 0 until 3) sim.build(Catalog.LOBBY, 0, mid + i * 6)
        sim.build(Catalog.ELEVATOR, 0, mid + 18)
        sim.build(Catalog.STAIRS, 0, mid + 20)
        sim.build(Catalog.LOBBY, 0, mid + 22)
        val plan = listOf(
            listOf(Catalog.OFFICE, Catalog.OFFICE, Catalog.FOOD),
            listOf(Catalog.OFFICE, Catalog.HOME, Catalog.OFFICE),
            listOf(Catalog.HOME, Catalog.HOME, Catalog.CORRIDOR),
        )
        for ((i, row) in plan.withIndex()) {
            val floor = i + 1
            row.forEachIndexed { j, type -> sim.build(type, floor, mid + j * 6) }
            sim.build(Catalog.ELEVATOR, floor, mid + 18)
            if (floor < plan.size) sim.build(Catalog.STAIRS, floor, mid + 20)
            sim.build(Catalog.CORRIDOR, floor, mid + 22)
        }
    }
}
