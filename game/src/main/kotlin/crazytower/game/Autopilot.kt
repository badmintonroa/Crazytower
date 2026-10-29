package crazytower.game

import crazytower.core.Catalog
import crazytower.core.Category
import crazytower.core.GROUND_FLOOR
import crazytower.core.Loc
import crazytower.core.MoneyKind
import crazytower.core.Simulation
import korlibs.image.format.PNG
import korlibs.image.format.writeTo
import korlibs.io.file.std.localVfs
import korlibs.korge.view.*
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

/**
 * Development helper: with CRAZYTOWER_SCREENSHOT=<file.png> the game builds a demo tower,
 * lets it run for a while, saves a screenshot and quits (CRAZYTOWER_FINANCE=1 opens the finance window, CRAZYTOWER_OPTIONS=1 the options, CRAZYTOWER_CELEBRATE=star or stage the celebration). Used to check the rendering
 * on machines without a screen.
 */
object Autopilot {
    /** True when the game runs for a screenshot; then it must not touch the player's files. */
    val active: Boolean get() = System.getenv("CRAZYTOWER_SCREENSHOT") != null

    fun attachIfRequested(root: SContainer, sim: Simulation, game: GameController, tower: TowerView, hud: Hud) {
        val target = System.getenv("CRAZYTOWER_SCREENSHOT") ?: return
        val minutes = System.getenv("CRAZYTOWER_MINUTES")?.toDoubleOrNull() ?: 90.0
        if (sim.tower.allRooms.isEmpty()) DemoTower.build(sim)
        sim.step(minutes)
        game.selection = sim.tower.allRooms.firstOrNull()?.let { Selection.RoomSel(it.id) }
        tower.lookAt(Loc(2, sim.lotWidth / 2f))
        if (System.getenv("CRAZYTOWER_FINANCE") != null) game.financeOpen = true
        if (System.getenv("CRAZYTOWER_OPTIONS") != null) game.optionsOpen = true
        System.getenv("CRAZYTOWER_CATEGORY")?.let { name -> Category.entries.firstOrNull { it.name == name }?.let { game.openCategory(it) } }
        System.getenv("CRAZYTOWER_CELEBRATE")?.let { hud.celebrate(stage = it == "stage") }
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

/** A small prebuilt tower for demos and screenshots: a long lobby, a few floors, stairs and an elevator through the rooms. */
object DemoTower {
    fun build(sim: Simulation) {
        sim.book(1_000_000, MoneyKind.LUCK, "Demo-Zuschuss")
        val mid = sim.lotWidth / 2 - 12
        sim.build(sim.planLobby(mid, mid + 23))
        val plan = listOf(
            listOf(Catalog.OFFICE, Catalog.OFFICE, Catalog.FOOD),
            listOf(Catalog.OFFICE, Catalog.HOME, Catalog.OFFICE),
            listOf(Catalog.HOME, Catalog.HOME, Catalog.CORRIDOR),
        )
        for ((i, row) in plan.withIndex()) {
            val floor = i + 1
            row.forEachIndexed { j, type -> sim.build(type, floor, mid + j * 6) }
            sim.build(Catalog.CORRIDOR, floor, mid + 18)
        }
        sim.build(sim.planColumn(Catalog.ELEVATOR, mid + 10, GROUND_FLOOR, plan.size))
        // a second cabin, as if the tower had two stars already
        val stars = sim.stars
        sim.stars = 2
        sim.elevators.cars.values.firstOrNull()?.let { sim.addCabin(it) }
        sim.stars = stars
        sim.build(sim.planColumn(Catalog.STAIRS, mid + 20, GROUND_FLOOR, plan.size))
    }
}
