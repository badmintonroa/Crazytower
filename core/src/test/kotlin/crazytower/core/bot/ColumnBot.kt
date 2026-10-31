package crazytower.core.bot

import crazytower.core.Catalog
import crazytower.core.RoomType
import crazytower.core.Simulation
import crazytower.core.transport.Elevators
import kotlin.math.abs

/**
 * A bot that moves people with local elevator columns, 23 floors per zone, and one express
 * per side of the lot up to the sky lobbies. The transfer at the sky lobby is short because
 * every zone uses the same columns. [TwinBot] and [WideBot] differ in where the columns are
 * and in what they open first.
 */
abstract class ColumnBot(sim: Simulation) : Bot(sim) {
    /** Every column the bot may ever open, in the order it opens them within a zone. */
    protected abstract val columns: List<Column>

    /** Express shafts per side, the first one is built for the sky lobbies, the others when it jams. */
    protected abstract fun expresses(left: Boolean): List<Int>

    /** Segments the bot builds on, for a floor. */
    protected abstract fun footprint(floor: Int): List<Int>

    /** Whether higher zones open as soon as the stage allows, rather than when the lower ones are full. */
    protected abstract val tall: Boolean

    /** Whether the express also serves the floors in between (every fifth) and grows with the tower. */
    protected open val expressServesFloors = false

    protected val open = HashMap<Int, MutableList<Column>>()

    protected fun isOpen(z: Int, c: Column) = open[z]?.contains(c) == true

    /** A column needs its whole slot inside the lot. */
    protected fun usable(c: Column) = (c.x / SLOT + 1) * SLOT <= sim.lotWidth

    override fun setup() {
        super.setup()
        openColumn(0, columns.first())
    }

    override fun stairsUp(floor: Int): List<Int> =
        if (floor % ZONE == ZONE - 1) open[zoneOf(floor)].orEmpty().map { it.stairX } else emptyList()

    override fun slots(floor: Int, type: RoomType): List<Int> {
        val cols = open[zoneOf(floor)].orEmpty()
        val near = cols.map { it.x + 1f } + builtExpresses().filter { floor % Elevators.EXPRESS_EVERY == 0 }.map { it + 1f }
        if (near.isEmpty()) return emptyList()
        return footprint(floor).filter { type.width <= SLOT || (it / SLOT) % 2 == 0 }
            .sortedBy { x -> near.minOf { abs(it - (x + type.width / 2f)) } }
    }

    override fun infrastructure() {
        growColumns()
        for ((z, cols) in open) for (c in cols) buildStairs(zoneShaftTop(z), c.stairX)
        growExpresses()
        relieveExpress()
        openNext(eagerOnly = !noSpot)
    }

    protected fun builtExpresses(): List<Int> =
        (expresses(true) + expresses(false)).filter { shaftTop(Catalog.EXPRESS, it, 0) != null }

    protected fun openColumn(z: Int, c: Column): Boolean {
        val bottom = zoneBottom(z)
        if (!shaft(Catalog.ELEVATOR, c.x, bottom, bottom + 1)) return false
        open.getOrPut(z) { ArrayList() } += c
        return true
    }

    /** Highest floor in [floors] with a room within reach of [x]. */
    protected fun highestNear(x: Int, floors: IntProgression): Int? {
        val lo = (x - REACH).coerceAtLeast(0)
        val hi = (x + REACH).coerceAtMost(sim.lotWidth - 1)
        for (f in floors.reversed()) for (s in lo..hi) if (sim.tower.roomAt(f, s) != null) return f
        return null
    }

    /** Every shaft one floor above the highest room next to it, so the next floor can be let. */
    private fun growColumns() {
        for ((z, cols) in open) for (c in cols) {
            val bottom = zoneBottom(z)
            val highest = highestNear(c.x, bottom..zoneShaftTop(z)) ?: bottom
            shaft(Catalog.ELEVATOR, c.x, bottom, minOf(zoneShaftTop(z), highest + 1))
        }
    }

    private fun growExpresses() {
        if (!sim.unlocked(Catalog.EXPRESS)) return
        for (left in listOf(true, false)) {
            val zones = open.filter { (z, cols) -> z > 0 && cols.any { it.left == left } }.keys
            var want = zones.maxOfOrNull { zoneBottom(it) } ?: 0
            val main = expresses(left).first()
            if (expressServesFloors && (left || sim.lotWidth >= FINAL_WIDTH)) {
                val h = highestNear(main, 1 until top) ?: 0
                want = maxOf(want, (h + 1) / Elevators.EXPRESS_EVERY * Elevators.EXPRESS_EVERY)
            }
            if (want < Elevators.EXPRESS_EVERY) continue
            for (e in expresses(left)) if (shaftTop(Catalog.EXPRESS, e, 0) != null || e == main) shaft(Catalog.EXPRESS, e, 0, want)
        }
    }

    /** A second express next to the first when the first has all its cabins and still jams. */
    private fun relieveExpress() {
        for (left in listOf(true, false)) {
            val list = expresses(left)
            val built = list.filter { shaftTop(Catalog.EXPRESS, it, 0) != null }
            if (built.isEmpty() || built.size == list.size) continue
            val cars = sim.elevators.cars.values.filter { it.express && it.x in built }
            val jammed = cars.all { it.cabins.size >= Elevators.maxCabins(sim.bestStars) && avgWait(it.id) > 3.0 && (carWaitN[it.id] ?: 0) > 50 }
            if (!jammed) continue
            val next = list.first { it !in built }
            val to = cars.maxOf { it.top }
            if (shaft(Catalog.EXPRESS, next, 0, to)) note("Express (Entlastung)")
        }
    }

    /**
     * Opens the next column. Each hour the bot tries the first column of every zone on each
     * side ([eagerOnly], so a tall bot climbs); when no spot was left it tries every column.
     */
    private fun openNext(eagerOnly: Boolean) {
        for (z in 0..3) {
            for (c in columns) {
                if (isOpen(z, c) || !usable(c)) continue
                val first = columns.first { it.left == c.left && usable(it) } == c
                if (eagerOnly && !(tall && first)) continue
                if (z > 0 && !zoneReady(z, c)) continue
                if (openColumn(z, c)) return
            }
            if (!tall && open[z].orEmpty().size < columns.count { usable(it) }) return
        }
    }

    /** Express up to the sky lobby, sky lobby from the express to the column. */
    private fun zoneReady(z: Int, c: Column): Boolean {
        val lobby = zoneBottom(z)
        if (top <= lobby + 1) return false
        // the zone below must have reached its top at this column
        if (!sim.tower.isBuilt(lobby - 1, c.x) || !sim.tower.isBuilt(lobby - 1, c.x + 1)) return false
        val e = expresses(c.left).first()
        if (!shaft(Catalog.EXPRESS, e, 0, lobby)) return false
        val range = if (e < c.x) e..c.x + 1 else c.x..e + 1
        skyLobby(lobby, range.first / SLOT * SLOT..(range.last / SLOT + 1) * SLOT - 1)
        return range.all { sim.tower.isWalkable(lobby, it) } && hasLobby(lobby, c.x) && hasLobby(lobby, e)
    }

    companion object {
        /** Segments a room may be from its column and still count as near it. */
        const val REACH = 30
    }
}
