package crazytower.core.bot

import crazytower.core.Catalog
import crazytower.core.Loc
import crazytower.core.Lot
import crazytower.core.MoneyKind
import crazytower.core.Room
import crazytower.core.RoomRole
import crazytower.core.RoomType
import crazytower.core.Simulation
import crazytower.core.Stages
import crazytower.core.Tower
import crazytower.core.GROUND_FLOOR
import crazytower.core.nav.NavCosts
import crazytower.core.transport.Elevators
import crazytower.core.Decor
import crazytower.core.Gates
import crazytower.core.Insurances
import crazytower.core.Mission
import crazytower.core.Research
import crazytower.core.SINK_STAGE
import crazytower.core.Tuning
import crazytower.core.Upgrades
import crazytower.core.buyDecor
import crazytower.core.buyGate
import crazytower.core.campaignBlocked
import crazytower.core.campaignCost
import crazytower.core.currentPart
import crazytower.core.decorBlocked
import crazytower.core.doResearch
import crazytower.core.gateBlocked
import crazytower.core.kind
import crazytower.core.liftedOff
import crazytower.core.missionStarted
import crazytower.core.partBlocked
import crazytower.core.partyBlocked
import crazytower.core.partyCost
import crazytower.core.remedyBlocked
import crazytower.core.researchBlocked
import crazytower.core.researchCost
import crazytower.core.startCampaign
import crazytower.core.startTestflight
import crazytower.core.taskOpen
import crazytower.core.taskDone
import crazytower.core.testflightBlocked
import crazytower.core.throwParty
import crazytower.core.toggleInsurance
import crazytower.core.tuneElevator
import crazytower.core.tuningBlocked
import crazytower.core.upgradeBlocked
import crazytower.core.upgradeRoom
import crazytower.core.useRemedy
import crazytower.core.Tenants
import crazytower.core.recruit
import crazytower.core.recruitBlocked
import kotlin.math.abs
import kotlin.math.max

/** Where a room goes: [floor] and its leftmost segment [x]. */
data class Spot(val floor: Int, val x: Int)

/**
 * A local elevator column at [x]: one shaft per 25-floor zone, each from the zone's lobby floor
 * up to 23 floors higher (two shafts at the same x can't touch, they would count as one). The
 * floor just under the next sky lobby is reached by stairs at [stairX], one floor up from the
 * shaft's top.
 */
class Column(val x: Int) {
    val stairX: Int = if ((x + 3) / Bot.SLOT == x / Bot.SLOT) x + 2 else x - 2
    val left: Boolean get() = x < Bot.FINAL_WIDTH / 2
    override fun toString() = "x=$x"
}

/**
 * Base of the playthrough bots. A bot decides each game hour what to build; this class does
 * the bookkeeping every bot shares: money reserve, cabins, the must-have specials, and above
 * all checking the way from the street before a room is built.
 *
 * Nobody rents a room whose way takes more than [crazytower.core.RoomProblems.MAX_TRAVEL_MINUTES].
 * The bot asks the game's own path search for the way to the spot, the way it will be once
 * the lot has its final width included, and leaves out every spot over [budget].
 */
abstract class Bot(val sim: Simulation) {
    /** Short name for the command line. */
    abstract val id: String

    /** Name in the log. */
    abstract val title: String

    /** One line on how the bot plays. */
    abstract val idea: String

    val built = LinkedHashMap<String, Int>()
    var skippedNoMoney = 0
        private set
    var rejectedFar = 0
        private set
    var noSpotHours = 0
        private set

    /** Most minutes the way to a new room may take; the game allows 90, the rest is for queues. */
    open val budget = 72f

    open val roomsPerHour = 6

    /** Whether the last hour ended because no reachable spot was left (not for lack of money). */
    protected var noSpot = false

    // ---------- waits, sampled by the runner every tick ----------
    val carWaitSum = HashMap<Int, Double>()
    val carWaitN = HashMap<Int, Int>()

    fun sampleWaits() {
        val now = sim.clock.totalMinutes
        for (car in sim.elevators.cars.values) {
            for (since in car.waitingSince.values) {
                carWaitSum[car.id] = (carWaitSum[car.id] ?: 0.0) + (now - since)
                carWaitN[car.id] = (carWaitN[car.id] ?: 0) + 1
            }
        }
    }

    fun avgWait(carId: Int): Double {
        val n = carWaitN[carId] ?: 0
        return if (n == 0) 0.0 else (carWaitSum[carId] ?: 0.0) / n
    }

    // ---------- the hour ----------
    open fun setup() {
        check(sim.build(sim.planLobby(0, 4 * SLOT - 1)).ok)
    }

    fun act() {
        navStamp = -1
        buyCabins()
        specials()
        if (sim.missionStarted && !sim.liftedOff) mission()
        if (spends && sim.stage >= SINK_STAGE && !savingForPart()) spend()
        infrastructure()
        noSpot = false
        for (i in 0 until roomsPerHour) {
            when (buildRoom()) {
                Outcome.BUILT -> continue
                Outcome.NO_SPOT -> { noSpot = true; noSpotHours++; break }
                Outcome.WAIT -> break
            }
        }
        if (sim.clock.hour == 0) { carWaitSum.clear(); carWaitN.clear() }
    }

    /** Shafts, lobbies and stairs. */
    protected abstract fun infrastructure()

    /** What to build next, or null to wait (for buyers, tenants or staff). */
    protected abstract fun chooseType(): RoomType?

    /** Spots on [floor] in the order the bot likes them, as left segments. */
    protected abstract fun slots(floor: Int, type: RoomType): List<Int>

    /** Something narrower to try when [type] fits nowhere. */
    protected open fun fallback(type: RoomType): RoomType? =
        if (type.width > SLOT) (if (sim.unlocked(Catalog.OFFICE)) Catalog.OFFICE else Catalog.HOME) else null

    /** Floors the bot fills, lowest first. */
    protected open fun floors(type: RoomType): IntProgression = maxOf(1, type.minFloor ?: 1) until top

    /**
     * Stairs the bot is willing to build from [floor] - 1 up to [floor], by their left segment.
     * A room covering one of them counts as reachable over the stairs.
     */
    protected open fun stairsUp(floor: Int): List<Int> = emptyList()

    protected enum class Outcome { BUILT, WAIT, NO_SPOT }

    protected fun buildRoom(): Outcome {
        var type = chooseType() ?: return Outcome.WAIT
        if (!afford(sim.listPrice(type))) { skippedNoMoney++; return Outcome.WAIT }
        var spot = findSpot(type)
        if (spot == null) {
            type = fallback(type) ?: return Outcome.NO_SPOT
            if (!afford(sim.listPrice(type))) { skippedNoMoney++; return Outcome.WAIT }
            spot = findSpot(type) ?: return Outcome.NO_SPOT
        }
        return if (place(type, spot)) Outcome.BUILT else Outcome.WAIT
    }

    /** The first spot in the bot's order that fits [type] and has a short enough way. */
    protected fun findSpot(type: RoomType, floors: IntProgression = floors(type)): Spot? {
        for (f in floors) {
            if (f >= top) break
            if (f > 0 && Tower.isLobbyFloor(f)) continue
            if (f < 0 && !type.basementOnly) continue
            for (x in slots(f, type)) {
                if (!roughFit(type, f, x)) continue
                val est = estimate(f, x, type.width) ?: continue
                if (est > budget) { rejectedFar++; continue }
                if (f == 1 && lobbyMissing(x, type.width) > 0) {
                    if (!afford(sim.listPrice(type) + Catalog.LOBBY.cost * lobbyMissing(x, type.width))) continue
                    return Spot(f, x)
                }
                if (sim.checkPlace(type, f, x).ok) return Spot(f, x)
            }
        }
        return null
    }

    protected fun place(type: RoomType, spot: Spot): Boolean {
        if (spot.floor == 1) ensureGroundLobby(spot.x, type.width)
        val ok = sim.build(type, spot.floor, spot.x).ok
        if (ok) {
            note(type.id)
            for (sx in stairsUp(spot.floor)) if (sx >= spot.x && sx + 2 <= spot.x + type.width) buildStairs(spot.floor - 1, sx)
        }
        return ok
    }

    /** Cheap checks before the path search: inside the lot, free, something to stand on. */
    private fun roughFit(type: RoomType, f: Int, x: Int): Boolean {
        if (x < 0 || x + type.width > sim.lotWidth) return false
        for (s in x until x + type.width) {
            if (sim.tower.roomAt(f, s) != null) return false
            if (f > 1 && !sim.tower.isBuilt(f - 1, s)) return false
        }
        return true
    }

    private fun lobbyMissing(x: Int, w: Int) = (x until x + w).count { sim.tower.roomAt(0, it) == null && sim.tower.wayAt(0, it) == null }

    protected fun ensureGroundLobby(x: Int, w: Int) {
        if (lobbyMissing(x, w) == 0) return
        // fill the hole slot by slot, so the ground lobby stays one piece where it can
        for (s in x until x + w) {
            if (sim.tower.roomAt(0, s) != null || sim.tower.wayAt(0, s) != null) continue
            var e = s
            while (e + 1 < x + w && sim.tower.roomAt(0, e + 1) == null && sim.tower.wayAt(0, e + 1) == null) e++
            if (sim.build(sim.planLobby(s, e)).ok) note("lobby")
        }
    }

    // ---------- the way from the street ----------
    private var navStamp = -1
    private var navLot = -1
    private var fromLeft: (Loc) -> Float? = { null }
    private var fromRight: (Loc) -> Float? = { null }

    private fun nav(left: Boolean): (Loc) -> Float? {
        if (navStamp != sim.tower.version || navLot != sim.lotWidth) {
            fromLeft = sim.nav.travelMinutesFrom(Loc(GROUND_FLOOR, 0f))
            fromRight = sim.nav.travelMinutesFrom(Loc(GROUND_FLOOR, sim.lotWidth - 0.01f))
            navStamp = sim.tower.version
            navLot = sim.lotWidth
        }
        return if (left) fromLeft else fromRight
    }

    /** Minutes from the [left] or right end of the street to a room at [x]..[x]+[w] on [f], once it is built. */
    private fun walkIn(f: Int, x: Int, w: Int, left: Boolean): Float? {
        val go = nav(left)
        val cx = x + w / 2f
        var best: Float? = null
        for (s in x - 1..x + w) {
            if (!sim.tower.isWalkable(f, s)) continue
            val t = go(Loc(f, s + 0.5f)) ?: continue
            val total = t + abs(s + 0.5f - cx) / NavCosts.WALK_SPEED
            if (best == null || total < best) best = total
        }
        if (best == null) {
            for (sx in stairsUp(f)) {
                if (sx < x || sx + 2 > x + w) continue
                if (!sim.tower.isWalkable(f - 1, sx) || !sim.tower.isWalkable(f - 1, sx + 1)) continue
                val t = go(Loc(f - 1, sx + 1f)) ?: continue
                val total = t + NavCosts.STAIRS_PER_FLOOR + abs(sx + 1f - cx) / NavCosts.WALK_SPEED
                if (best == null || total < best) best = total
            }
        }
        return best
    }

    /**
     * The longest way to a room at [x]..[x]+[w] on [f], today and for every width the lot will
     * still grow to: people come in from the closer end of the street, and the right end moves
     * when the lot grows. A wider lot means walking the extra sidewalk first.
     */
    fun estimate(f: Int, x: Int, w: Int): Float? {
        val cx = x + w / 2f
        val width = sim.lotWidth
        var left: Float? = null
        var right: Float? = null
        var worst = 0f
        for (step in Lot.steps) {
            if (step.width < width) continue
            val minutes = if (cx < step.width / 2f) {
                if (left == null) left = walkIn(f, x, w, true) ?: return null
                left
            } else {
                if (right == null) right = walkIn(f, x, w, false) ?: return null
                right + (step.width - width) / NavCosts.WALK_SPEED
            }
            worst = max(worst, minutes)
        }
        return worst
    }

    // ---------- money ----------
    fun reserve(): Long {
        val y = sim.finance.day(sim.clock.day - 1)
        val upkeep = -(y[MoneyKind.UPKEEP] ?: 0L)
        return maxOf(5_000L, upkeep * 2)
    }

    fun afford(cost: Long) = sim.money - cost >= reserve()

    protected val top: Int get() = Stages.info(sim.stage).maxFloor

    protected val rooms: List<Room> get() = sim.tower.allRooms.toList()

    fun count(type: RoomType) = sim.tower.allRooms.count { it.type == type }

    fun note(what: String) { built[what] = (built[what] ?: 0) + 1 }

    // ---------- elevators ----------
    protected open fun buyCabins() {
        for (car in sim.elevators.cars.values) {
            val n = carWaitN[car.id] ?: 0
            val waiting = car.waitingCount
            val busy = (n > 30 && avgWait(car.id) > 2.0) || waiting > car.cabins.size * car.capacity / 2
            if (busy && sim.checkCabin(car).ok && afford(Elevators.CABIN_COST)) {
                sim.addCabin(car); note("Kabine")
                carWaitSum[car.id] = 0.0; carWaitN[car.id] = 0
            }
        }
    }

    /** Top floor of the shaft of [type] at [x] that contains floor [near], or null if there is none. */
    protected fun shaftTop(type: RoomType, x: Int, near: Int): Int? {
        val way = sim.tower.wayAt(near, x) ?: return null
        if (way.type != type || way.x != x) return null
        return sim.tower.shaftRange(near, x, type).last
    }

    /** Builds or extends the shaft of [type] at [x] from [bottom] up to [to]. */
    protected fun shaft(type: RoomType, x: Int, bottom: Int, to: Int): Boolean {
        val upTo = minOf(to, top - 1)
        if (upTo < bottom) return false
        val current = shaftTop(type, x, bottom)
        if (current != null && current >= upTo) return true
        val plan = sim.planColumn(type, x, bottom, upTo)
        if (!plan.check.ok || plan.pieces.isEmpty() || !afford(plan.cost)) { if (!afford(plan.cost)) skippedNoMoney++; return false }
        if (!sim.build(plan).ok) return false
        note(if (type.express) "Express" else "Schacht")
        return true
    }

    protected fun buildStairs(f: Int, x: Int): Boolean {
        val t = sim.tower
        if (t.wayAt(f, x) != null || t.wayAt(f, x + 1) != null) return false
        if (!t.isWalkable(f, x) || !t.isWalkable(f, x + 1) || !t.isWalkable(f + 1, x) || !t.isWalkable(f + 1, x + 1)) return false
        if (!afford(Catalog.STAIRS.cost)) return false
        val ok = sim.build(Catalog.STAIRS, f, x).ok
        if (ok) note("Treppe")
        return ok
    }

    /** Sky lobby slots on [floor] wherever something stands below, inside [xs]. */
    protected fun skyLobby(floor: Int, xs: IntRange): Boolean {
        var any = false
        var s = xs.first / SLOT * SLOT
        while (s + SLOT - 1 <= xs.last && s + SLOT <= sim.lotWidth) {
            val free = (s until s + SLOT).all { sim.tower.roomAt(floor, it) == null }
            val held = (s until s + SLOT).all { sim.tower.isBuilt(floor - 1, it) }
            if (free && held) {
                if (!afford(Catalog.LOBBY.cost * SLOT)) { skippedNoMoney++; return any }
                if (sim.build(Catalog.LOBBY, floor, s, SLOT).ok) { note("Sky-Lobby"); any = true }
            }
            s += SLOT
        }
        return any
    }

    protected fun hasLobby(floor: Int, x: Int, w: Int = 2) = (x until x + w).all { sim.tower.roomAt(floor, it)?.type?.role == RoomRole.LOBBY }

    // ---------- specials every bot needs ----------
    protected open val wantsSubway = true
    protected open val wantsKita = false
    protected open val wantsConference = false

    protected open fun specials() {
        if (sim.stage >= 3 && count(Catalog.SECURITY) == 0) placeAnywhere(Catalog.SECURITY)
        if (sim.stage >= 2 && count(Catalog.COPYROOM) == 0) placeAnywhere(Catalog.COPYROOM)
        if (sim.stage >= 5 && count(Catalog.UFO_PAD) == 0 && sim.money > 1_000_000) placeAnywhere(Catalog.UFO_PAD)
        val hotels = count(Catalog.HOTEL)
        if (hotels > 0 && count(Catalog.HOUSEKEEPING) < (hotels + 7) / 8) placeAnywhere(Catalog.HOUSEKEEPING)
        if (wantsKita && sim.unlocked(Catalog.KITA) && count(Catalog.KITA) < minOf(3, 1 + count(Catalog.HOME) / 30)) placeAnywhere(Catalog.KITA)
        if (wantsConference && sim.unlocked(Catalog.CONFERENCE) && count(Catalog.CONFERENCE) == 0 && hotels >= 10) placeAnywhere(Catalog.CONFERENCE)
        if (wantsSubway && sim.unlocked(Catalog.SUBWAY) && count(Catalog.SUBWAY) == 0 && afford(Catalog.SUBWAY.cost)) {
            for (x in listOf(72, 60, 84, 48, 96)) if (sim.build(Catalog.SUBWAY, -1, x).ok) { note("subway"); break }
        }
        parking()
    }

    /** Parking for the luxury offices first; more only when money is plentiful. */
    private fun parking() {
        if (!sim.basementOpen || sim.carsWithoutSpace <= 0) return
        val luxuryNeed = count(Catalog.LUXURY_OFFICE) * Simulation.LUXURY_SPACES - sim.parkingSpaces
        if (luxuryNeed <= 0 && sim.money < 500_000) return
        var n = if (luxuryNeed > 0) luxuryNeed else 4
        for (floor in -1 downTo Tower.MIN_FLOOR) for (x in 0 until sim.lotWidth - 1 step Catalog.PARKING.width) {
            if (n <= 0 || sim.carsWithoutSpace <= 0 || !afford(Catalog.PARKING.cost)) return
            if (sim.build(Catalog.PARKING, floor, x).ok) { note("parking"); n-- }
        }
    }

    protected fun placeAnywhere(type: RoomType): Boolean {
        if (!afford(sim.listPrice(type))) return false
        val spot = findSpot(type) ?: return false
        return place(type, spot)
    }

    // ---------- where the money goes from stage 4 on ----------

    /** False for the miser, who keeps everything and only pays what the mission needs. */
    open val spends = true

    /** Money the bot keeps on top of the reserve before buying extras. */
    private fun spare(cost: Long, times: Int = 3) = sim.money - reserve() >= cost * times

    /**
     * The optional extras: tuning for busy shafts, upgrades for the best-paying rooms, insurance,
     * the weekly party and ads, side entrances, research and, during the mission, decorations.
     */
    protected open fun spend() {
        // shafts where people wait long get tuned first
        for (car in sim.elevators.cars.values.sortedByDescending { avgWait(it.id) }) {
            if ((carWaitN[car.id] ?: 0) < 30 || avgWait(car.id) < 3.0) break
            if (sim.tuningBlocked(car) == null && spare(Tuning.cost(car))) { sim.tuneElevator(car); note("Tuning") ; break }
        }
        // insurance against what costs most; one a day at most
        if (sim.clock.hour == 9) {
            for (insurance in Insurances.all) {
                if (insurance.id in sim.endgame.insurances) continue
                if (spare(Insurances.fee(sim, insurance), 20)) { if (sim.toggleInsurance(insurance.id) == null) note("Versicherung"); break }
            }
        }
        if (sim.partyBlocked() == null && spare(sim.partyCost(), 4)) { sim.throwParty(); note("Turmfest") }
        if (sim.campaignBlocked() == null && spare(sim.campaignCost(), 6)) { sim.startCampaign(); note("Werbung") }
        if (sim.gateBlocked() == null && spare(Gates.all[sim.endgame.gates].cost, 3)) { sim.buyGate(); note("Seiteneingang") }
        Research.all.firstOrNull { sim.researchBlocked(it) == null }?.let { if (spare(sim.researchCost(), 3)) { sim.doResearch(it); note("Forschung") } }
        if (sim.missionStarted) {
            Decor.all.firstOrNull { it.id !in sim.endgame.decor && sim.decorBlocked(it) == null }?.let { if (spare(it.cost, 4)) { sim.buyDecor(it); note("Deko") } }
        }
        // upgrades: the rooms that pay most, a few per hour
        var upgrades = 0
        if (spare(Upgrades.COSTS[0], 10)) {
            val best = sim.tower.allRooms.filter { it.tenant != null && Upgrades.applies(it) && it.upgrade < Upgrades.MAX_LEVEL }
                .sortedByDescending { (it.tenant?.rentPerDay ?: 0L) * (Upgrades.MAX_LEVEL - it.upgrade) / Upgrades.cost(it).coerceAtLeast(1) }
            for (room in best) {
                if (upgrades >= 3 || sim.upgradeBlocked(room) != null || !spare(Upgrades.cost(room), 10)) break
                if (sim.upgradeRoom(room).ok) { upgrades++; note("Ausbau") }
            }
        }
    }

    // ---------- the moon mission ----------
    private var testflightTries = 0

    /** What every bot does for the countdown: the rooms the tasks need, the buttons, the station parts. */
    protected open fun mission() {
        // spectators: a deck up high, then souvenir shops
        if (count(Mission.DECK) == 0) placeAnywhere(Mission.DECK)
        else if (count(Mission.SOUVENIRS) < 2) placeAnywhere(Mission.SOUVENIRS)
        else if (count(Mission.DECK) < 2 && (sim.endgame.parts >= 2 || sim.clock.totalMinutes < sim.endgame.delayedUntil)) placeAnywhere(Mission.DECK)
        // rooms for the open tasks
        if (sim.taskOpen(Mission.SUITES) && sim.unlocked(Catalog.HOTEL) && count(Catalog.HOTEL) < 16) placeAnywhere(Catalog.HOTEL)
        if (sim.taskOpen(Mission.PERMIT) && count(Catalog.COPYROOM) == 0) placeAnywhere(Catalog.COPYROOM)
        if (sim.taskOpen(Mission.COFFEE) && count(Catalog.FOOD) * 8 < rentables().size) placeAnywhere(Catalog.FOOD)
        if (sim.taskOpen(Mission.CONTROL)) missionTenant(Catalog.OFFICE, "rocket")
        if (sim.taskOpen(Mission.ASTRONAUTS)) {
            missionTenant(Catalog.HOME, "astronauts")
            // astronauts want it nice: their flats get done up
            for (room in sim.tower.allRooms.filter { it.tenant?.id == "astronauts" }) {
                if (sim.upgradeBlocked(room) == null && afford(Upgrades.cost(room))) { sim.upgradeRoom(room); note("Ausbau Astronauten") }
            }
        }
        if ((sim.taskOpen(Mission.SABOTAGE)) && count(Catalog.SECURITY) == 0) placeAnywhere(Catalog.SECURITY)
        // Dr. Hämisch: send security straight away
        for (event in sim.activeEvents.toList()) {
            if (event.id == "sabotage" && sim.remedyBlocked(event) == null) { sim.useRemedy(event); note("Sabotage gestoppt") }
        }
        // the test flight only in a good mood, with the party if it helps
        if (sim.testflightBlocked() == null) {
            if (sim.satisfaction < Mission.TESTFLIGHT_MOOD + 8 && sim.partyBlocked() == null && afford(sim.partyCost())) { sim.throwParty(); note("Turmfest") }
            if (sim.satisfaction >= Mission.TESTFLIGHT_MOOD + 6 || ++testflightTries > 24 * 5) { sim.startTestflight(); note("Testflug"); testflightTries = 0 }
        }
        buildPart()
    }

    /**
     * Gets a mission tenant into a room of [type]: an ad for an empty room, a new room, or, in a
     * full tower, the cheapest ordinary room of that kind torn down and built again.
     */
    private fun missionTenant(type: RoomType, tenantId: String) {
        val tenant = Tenants.byId(tenantId)
        val empty = sim.tower.allRooms.firstOrNull { it.type == type && it.tenant == null && sim.recruitBlocked(it) == null }
        if (empty != null) { if (sim.recruit(empty, tenant).ok) note("Anzeige $tenantId"); return }
        if (placeAnywhere(type)) return
        if (sim.clock.hour != 10) return // once a day at most
        val victim = sim.tower.allRooms
            .filter { it.type == type && it.tenant != null && it.tenant?.eventOnly != true && sim.roomProblems[it.id] == null && sim.checkDemolish(it).ok }
            .minByOrNull { it.tenant!!.rentPerDay } ?: return
        val floor = victim.floor
        val x = victim.x
        sim.demolish(victim.id)
        if (sim.build(type, floor, x).ok) note("Umbau für $tenantId")
    }

    /** The tasks of the next station part are done: every dollar goes into the part. */
    private fun savingForPart(): Boolean {
        val part = sim.currentPart ?: return false
        return part.tasks.all { sim.taskDone(it) } && sim.money - reserve() < part.type.cost * 2
    }

    /** The next station part on the top floor, tearing down what stands in the way if needed. */
    private fun buildPart() {
        val part = sim.currentPart ?: return
        val type = part.type
        if (sim.partBlocked(type, Mission.FLOOR) != null || !afford(type.cost)) return
        val floor = Mission.FLOOR
        val xs = (0..sim.lotWidth - type.width)
        for (x in xs) if (sim.checkPlace(type, floor, x).ok && sim.build(type, floor, x).ok) { note(type.id); return }
        // the top floor is full: clear a stretch that stands on solid floors, cheapest rooms first
        for (x in xs step SLOT) {
            if ((x until x + type.width).any { !sim.tower.isBuilt(floor - 1, it) }) continue
            val inWay = sim.tower.allRooms.filter { it.floor == floor && it.x < x + type.width && it.x + it.width > x }
            if (inWay.any { it.type.role.isWay || Mission.partOf(it.type) != null }) continue
            inWay.forEach { sim.demolish(it.id) }
            if (sim.build(type, floor, x).ok) { note(type.id); note("Platz für Station"); return }
        }
    }

    // ---------- what the tower needs ----------
    protected fun rentables() = sim.tower.allRooms.filter { it.type.role.rentable }
    protected fun unsoldHomes() = sim.tower.allRooms.count { it.type.role == RoomRole.HOME && !it.sold && sim.roomProblems[it.id]?.far != true }
    protected fun emptyOffices() = sim.tower.allRooms.count { it.type.role == RoomRole.OFFICE && !it.type.luxury && it.tenant == null && sim.roomProblems[it.id]?.far != true }
    protected fun emptyOf(type: RoomType) = sim.tower.allRooms.count { it.type == type && it.tenant == null && sim.roomProblems[it.id]?.far != true }
    protected fun foodNeeded(per: Int = 15): Boolean {
        val n = rentables().size + 1
        return sim.unlocked(Catalog.FOOD) && count(Catalog.FOOD) * per < n
    }
    protected fun shopNeeded(): Boolean =
        sim.unlocked(Catalog.SHOP) && count(Catalog.SHOP) * Simulation.SHOP_SERVES < count(Catalog.HOME) + count(Catalog.DORM)
    protected fun officeOk() = sim.unlocked(Catalog.OFFICE) && emptyOffices() < 2 && sim.hasWorkersFor(Catalog.OFFICE.capacity)
    /**
     * A luxury office only while its parking fits: each one keeps [Simulation.LUXURY_SPACES]
     * spaces, and the basement holds [MAX_LUXURY] offices' worth next to the subway.
     */
    protected fun luxuryOk() = sim.unlocked(Catalog.LUXURY_OFFICE) && sim.basementOpen &&
        count(Catalog.LUXURY_OFFICE) < MAX_LUXURY && emptyOf(Catalog.LUXURY_OFFICE) < 2

    /** Whether the bot's own part of the lot is built up to the stage's height limit. */
    protected fun reachedTop(xs: IntRange): Boolean = xs.any { sim.tower.roomAt(top - 2, it) != null }

    protected fun dormOk() = sim.unlocked(Catalog.DORM) && emptyOf(Catalog.DORM) == 0 && sim.hasSideJobsFor(Catalog.DORM.capacity)

    companion object {
        const val SLOT = 6

        /** Luxury offices the basement can park: three floors of spaces, a good part for them. */
        const val MAX_LUXURY = 40
        val FINAL_WIDTH = Lot.maxWidth
        const val ZONE = Tower.SKY_LOBBY_EVERY

        /** Floors a local shaft of one zone covers, from the zone's lobby floor. */
        const val SHAFT_FLOORS = ZONE - 1

        fun zoneOf(floor: Int) = floor / ZONE
        fun zoneBottom(z: Int) = z * ZONE

        /** Highest floor a local shaft of zone [z] reaches; the floor above gets stairs. */
        fun zoneShaftTop(z: Int) = z * ZONE + SHAFT_FLOORS - 1
    }
}
