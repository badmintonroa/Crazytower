package crazytower.core.bot

import crazytower.core.Catalog
import crazytower.core.RoomType
import crazytower.core.Simulation

/**
 * Zwillingstürme: two narrow towers, one at each end of the street, and nothing in the middle
 * of the lot, where the walk from either end is longest. Each tower has its express at the
 * outer edge and two local columns, and climbs as soon as the stage allows. Builds mostly
 * offices, with dorms for staff, hotels and luxury offices later.
 */
class TwinBot(sim: Simulation) : ColumnBot(sim) {
    override val id = "twin"
    override val title = "Zwillingstürme"
    override val idea = "zwei schmale Türme an den Straßenenden, Express außen, früh hoch; Büros, Wohnheime, Hotels"

    override val columns = listOf(Column(14), Column(42), Column(140), Column(112))
    override fun expresses(left: Boolean) = if (left) listOf(0, 28) else listOf(154, 126)
    override val tall = true
    override val expressServesFloors = true
    override val wantsConference = true

    /** Slots 0..9 on the left, 16..25 on the right once the lot is at its widest, the middle last. */
    override fun footprint(floor: Int): List<Int> {
        val left = (0 until 10).map { it * SLOT }
        val right = if (sim.lotWidth >= FINAL_WIDTH) (16 until 26).map { it * SLOT } else emptyList()
        // once both towers hit the height limit, a bridge between them
        val middle = if (right.isNotEmpty() && reachedTop(0 until 60) && reachedTop(96 until 156)) (10 until 16).map { it * SLOT } else emptyList()
        return (left + right + middle).filter { it + SLOT <= sim.lotWidth }
    }

    override fun chooseType(): RoomType? {
        if (!sim.unlocked(Catalog.OFFICE)) return if (unsoldHomes() < 2) Catalog.HOME else null
        val n = rentables().size + 1
        if (foodNeeded()) return Catalog.FOOD
        if (sim.stage >= 3 && count(Catalog.HOTEL) * 14 < n) return Catalog.HOTEL
        if (shopNeeded()) return Catalog.SHOP
        if (sim.stage >= 4 && sim.money > 500_000 && luxuryOk() && count(Catalog.LUXURY_OFFICE) * 12 < n) return Catalog.LUXURY_OFFICE
        if (dormOk()) return Catalog.DORM
        if (officeOk()) return Catalog.OFFICE
        if (unsoldHomes() < 2) return Catalog.HOME
        return null
    }
}
