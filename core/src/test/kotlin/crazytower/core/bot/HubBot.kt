package crazytower.core.bot

import crazytower.core.Catalog
import crazytower.core.RoomType
import crazytower.core.Simulation
import crazytower.core.Tower
import crazytower.core.transport.Elevators
import kotlin.math.abs

/**
 * Express-Treppen: no local elevators above the 9th floor. Express shafts stop on every fifth
 * floor, and a staircase next to each one connects every floor with its neighbours, so no room
 * is more than two flights from an express stop. Few shafts keep the shaft surcharge low and the
 * money goes into business: offices, hotels, luxury offices and the conference centre.
 */
open class HubBot(sim: Simulation) : Bot(sim) {
    override val id: String = "hub"
    override val title: String = "Express-Treppen"
    override val idea: String = "nur Expressaufzüge mit Treppenhaus daneben, höchstens zwei Etagen zu Fuß; Hotels und Geschäft"

    /** One hub: an express at [x] and its staircase at [stairX]. */
    class Hub(val x: Int, val stairX: Int) { val left get() = x < FINAL_WIDTH / 2 }

    private val hubs = listOf(Hub(0, 2), Hub(30, 32), Hub(54, 56), Hub(154, 152), Hub(124, 122), Hub(100, 98))
    private val opened = ArrayList<Hub>().apply { add(hubs[0]) }

    /** The only local elevator, for the first floors before the express is there. */
    private val earlyX = 14
    private val earlyTop = 9

    override val wantsConference = true

    override fun setup() {
        super.setup()
        shaft(Catalog.ELEVATOR, earlyX, 0, 1)
    }

    private fun hasExpress(h: Hub) = shaftTop(Catalog.EXPRESS, h.x, 0) != null

    override fun stairsUp(floor: Int): List<Int> = opened.map { it.stairX }

    private fun footprint(): List<Int> {
        val left = (0 until 10).map { it * SLOT }
        val right = if (sim.lotWidth >= FINAL_WIDTH) (16 until 26).map { it * SLOT } else emptyList()
        val middle = if (right.isNotEmpty() && reachedTop(0 until 60) && reachedTop(96 until 156)) (10 until 16).map { it * SLOT } else emptyList()
        return (left + right + middle).filter { it + SLOT <= sim.lotWidth }
    }

    override fun slots(floor: Int, type: RoomType): List<Int> {
        val near = opened.map { it.stairX + 1f } + (if (floor <= earlyTop) listOf(earlyX + 1f) else emptyList())
        return footprint().filter { type.width <= SLOT || (it / SLOT) % 2 == 0 }
            .sortedBy { x -> near.minOf { abs(it - (x + type.width / 2f)) } }
    }

    private fun highestNear(x: Int): Int? {
        val lo = (x - 30).coerceAtLeast(0)
        val hi = (x + 30).coerceAtMost(sim.lotWidth - 1)
        for (f in top - 1 downTo 1) for (s in lo..hi) if (sim.tower.roomAt(f, s) != null) return f
        return null
    }

    override fun infrastructure() {
        // the early local elevator, up to the 9th floor
        val early = highestNear(earlyX) ?: 0
        shaft(Catalog.ELEVATOR, earlyX, 0, minOf(earlyTop, early + 1))
        for (h in opened) {
            // express to the next stop above the highest room, so nobody climbs more than four floors
            if (sim.unlocked(Catalog.EXPRESS)) {
                val highest = highestNear(h.x) ?: 0
                val every = Elevators.EXPRESS_EVERY
                var want = ((highest + 1 + every - 1) / every) * every
                if (want >= top) want = (top - 1) / every * every
                if (want >= every) shaft(Catalog.EXPRESS, h.x, 0, want)
            }
            for (f in 0 until top - 1) {
                if (f > 0 && !sim.tower.isBuilt(f, h.stairX)) break
                buildStairs(f, h.stairX)
            }
        }
        // sky lobbies over the floor below, before anything goes above them
        for (lobby in Tower.skyLobbiesBelow(top)) {
            if (opened.none { sim.tower.isBuilt(lobby - 1, it.stairX) }) continue
            for (h in opened) skyLobby(lobby, (h.x / SLOT * SLOT - SLOT).coerceAtLeast(0)..h.x / SLOT * SLOT + 4 * SLOT - 1)
        }
        if (noSpot || jammed()) openNextHub()
    }

    private fun jammed(): Boolean {
        val cars = sim.elevators.cars.values.filter { it.express }
        return cars.isNotEmpty() && cars.all { it.cabins.size >= Elevators.maxCabins(sim.bestStars) && avgWait(it.id) > 3.0 && (carWaitN[it.id] ?: 0) > 50 }
    }

    private fun openNextHub() {
        if (!sim.unlocked(Catalog.EXPRESS)) return
        val next = hubs.firstOrNull { it !in opened && (it.x / SLOT + 1) * SLOT <= sim.lotWidth && (it.left || sim.lotWidth >= FINAL_WIDTH) } ?: return
        // a new hub on the right starts from the ground; on the left it reaches as high as the others
        val to = if (next.left) opened.filter { it.left }.maxOfOrNull { shaftTop(Catalog.EXPRESS, it.x, 0) ?: 0 } ?: 5 else 5
        if (shaft(Catalog.EXPRESS, next.x, 0, maxOf(5, to))) opened += next
    }

    override fun chooseType(): RoomType? {
        if (!sim.unlocked(Catalog.OFFICE)) return if (unsoldHomes() < 2) Catalog.HOME else null
        val n = rentables().size + 1
        if (foodNeeded(12)) return Catalog.FOOD
        if (sim.stage >= 3 && count(Catalog.HOTEL) * 5 < n) return Catalog.HOTEL
        if (sim.stage >= 4 && sim.money > 400_000 && luxuryOk() && count(Catalog.LUXURY_OFFICE) * 8 < n) return Catalog.LUXURY_OFFICE
        if (shopNeeded()) return Catalog.SHOP
        if (officeOk()) return Catalog.OFFICE
        if (dormOk()) return Catalog.DORM
        if (unsoldHomes() < 2) return Catalog.HOME
        return null
    }
}
