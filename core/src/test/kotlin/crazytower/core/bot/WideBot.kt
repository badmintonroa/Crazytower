package crazytower.core.bot

import crazytower.core.Catalog
import crazytower.core.RoomType
import crazytower.core.Simulation

/**
 * Breitbau: uses the whole lot before it goes up. Local columns every 24 segments across the
 * lot, a new one whenever no spot is left near the open ones, and a higher zone only when the
 * lower one is full. Flats first, with shops, daycare and penthouses; offices and dorms fill
 * in when there are no buyers.
 */
class WideBot(sim: Simulation) : ColumnBot(sim) {
    override val id = "wide"
    override val title = "Breitbau"
    override val idea = "ganze Grundstücksbreite, Aufzug alle 24 Felder, erst breit, dann hoch; Wohnungen zuerst"

    override val columns = listOf(14, 38, 62, 86, 110, 134).map { Column(it) }
    override fun expresses(left: Boolean) = if (left) listOf(0) else listOf(154)
    override val tall = false
    override val wantsKita = true
    override val wantsSubway = false

    override fun footprint(floor: Int): List<Int> = (0 until sim.lotWidth / SLOT).map { it * SLOT }

    override fun chooseType(): RoomType? {
        if (foodNeeded()) return Catalog.FOOD
        if (shopNeeded()) return Catalog.SHOP
        val homesWaiting = unsoldHomes()
        if (sim.unlocked(Catalog.PENTHOUSE) && sim.money > 300_000 && homesWaiting == 0 &&
            count(Catalog.PENTHOUSE) * 8 < count(Catalog.HOME)) return Catalog.PENTHOUSE
        if (homesWaiting < 2 + count(Catalog.KITA)) return Catalog.HOME
        if (dormOk()) return Catalog.DORM
        if (officeOk()) return Catalog.OFFICE
        if (sim.stage >= 3 && count(Catalog.HOTEL) * 30 < rentables().size + 1) return Catalog.HOTEL
        return null
    }

    override fun fallback(type: RoomType): RoomType? = if (type.width > SLOT) Catalog.HOME else null
}
