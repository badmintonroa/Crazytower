package crazytower.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class LooksTest {
    private val sheets: Set<String> =
        File("../game/src/main/resources/people/index.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    @Test
    fun everyLookHasASheetOrAStandIn() {
        val looks = Tenants.all.flatMap { listOf(it.look) + it.crew } + Vips.all.map { it.look } +
            Simulation.VISITOR_LOOKS + Simulation.SPECTATOR_LOOKS
        val missing = looks.toSet().filter { Looks.resolve(it, sheets) == null }
        assertTrue(missing.isEmpty(), "no sheet and no stand-in for $missing")
    }

    @Test
    fun standInsAreRealSheets() {
        val broken = Looks.standIns.filterValues { it !in sheets }
        assertTrue(broken.isEmpty(), "stand-ins without a sheet: $broken")
    }
}
