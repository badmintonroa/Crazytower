package crazytower.core

import crazytower.core.ecs.Vip
import crazytower.core.transport.Elevators
import com.github.quillraven.fleks.Entity
import kotlin.math.abs

/**
 * Headless playthrough: a bot plays from an empty lot to stage 5 and logs a line per day.
 * Run: java -cp <test runtime cp> crazytower.core.PlaythroughKt [days] [seed]
 */
fun main(args: Array<String>) {
    val days = args.getOrNull(0)?.toIntOrNull() ?: 120
    val seed = args.getOrNull(1)?.toLongOrNull() ?: 42L
    val wallLimitMin = args.getOrNull(2)?.toIntOrNull() ?: 50
    Playthrough(seed).run(days, wallLimitMin)
}

class Playthrough(seed: Long) {
    val sim = Simulation(seed = seed)

    /**
     * How the bot builds: "mix" uses everything, "office" only offices (with food, subway and the
     * must-have specials), "home" only flats (with food, shops, daycare and penthouses).
     * Set with -Dstrategy=... or STRATEGY=...
     */
    private val strategy = System.getProperty("strategy") ?: System.getenv("STRATEGY") ?: "mix"
    private val started = System.nanoTime()

    // ---------- layout ----------
    private val slotW = 6
    private val evenCols = listOf(16, 58, 100, 142)
    private val oddCols = listOf(30, 72, 114)
    private val expressX = 2
    private val secondExpress = System.getProperty("express2") != null || System.getenv("EXPRESS2") != null
    private val reach = 30f
    private fun zoneOf(floor: Int) = floor / Tower.SKY_LOBBY_EVERY
    private fun zoneBottom(z: Int) = z * Tower.SKY_LOBBY_EVERY
    private fun cols(z: Int) = if (z % 2 == 0) evenCols else oddCols
    private val activeCols = HashMap<Int, MutableList<Int>>() // zone -> xs
    private fun slots() = (0 until sim.lotWidth / slotW)

    // ---------- stats ----------
    private val waitSamples = ArrayList<Double>()
    private var maxWait = 0.0
    private val carWaitSum = HashMap<Int, Double>()
    private val carWaitN = HashMap<Int, Int>()
    private var lastNewsId = 0
    private var vipTipTotal = 0L
    private val vipVisits = ArrayList<String>()
    private var vipGaveUp = 0
    private var vipHappy = 0
    private var vipStingy = 0
    private val vipTrip = HashMap<Entity, Pair<Double, String>>()
    private val vipDurations = ArrayList<Double>()
    private var moveOuts = 0
    private var moveOutsMood = 0
    private val events = HashMap<String, Int>()
    private val stageDay = HashMap<Int, Int>()
    private val starDay = HashMap<Int, Int>()
    private val problemsSeen = HashMap<String, Int>()
    private var exceptions = 0
    private var lastVipMoney = 0L
    private val built = HashMap<String, Int>()
    private var skippedNoMoney = 0
    private val log = StringBuilder()
    private var maxMsPerHour = 0L
    private var peakPop = 0

    fun run(days: Int, wallLimitMin: Int) {
        check(sim.build(sim.planLobby(0, 4 * slotW - 1)).ok)
        check(sim.build(sim.planColumn(Catalog.ELEVATOR, evenCols[0], 0, 1)).ok)
        activeCols[0] = mutableListOf(evenCols[0])
        println("Tag | Std | Geld | Stufe | ★ | Leute | Laune | Räume B/W/I/H/Lux (vermietet) | leer+Problem | Schächte/Kabinen | Wartezeit Ø/p95/max | VIP n/+/−/weg Trinkgeld | Auszüge | Einnahmen | Unterhalt | Bau | ms/Std")
        for (hour in 0 until days * 24) {
            val wall = System.nanoTime()
            repeat(6) {
                try { sim.step(10.0) } catch (e: Throwable) { exceptions++; println("EXCEPTION day ${sim.clock.day}: $e"); e.printStackTrace(); return }
                sample()
            }
            val ms = (System.nanoTime() - wall) / 1_000_000
            if (ms > maxMsPerHour) maxMsPerHour = ms
            act()
            readNews()
            if (sim.stage !in stageDay) stageDay[sim.stage] = sim.clock.day
            if (sim.stars !in starDay) starDay[sim.stars] = sim.clock.day
            peakPop = maxOf(peakPop, sim.population)
            if (sim.clock.hour == 23) dayLine(ms)
            if (sim.stage >= 5 && sim.clock.day > (stageDay[5] ?: 0) + 5) break
            if ((System.nanoTime() - started) / 60e9 > wallLimitMin) { println("Zeitlimit erreicht"); break }
        }
        summary()
    }

    // ---------- sampling ----------
    private fun sample() {
        val now = sim.clock.totalMinutes
        for (car in sim.elevators.cars.values) {
            for (since in car.waitingSince.values) {
                val w = now - since
                waitSamples += w
                if (w > maxWait) maxWait = w
                carWaitSum[car.id] = (carWaitSum[car.id] ?: 0.0) + w
                carWaitN[car.id] = (carWaitN[car.id] ?: 0) + 1
            }
        }
        // VIP trips
        with(sim.world) {
            val present = HashSet<Entity>()
            family { all(Vip) }.forEach { e ->
                present += e
                vipTrip.getOrPut(e) { e[Vip].since to e[Vip].guestId }
            }
            val gone = vipTrip.keys.filter { it !in present }
            for (e in gone) {
                val (since, id) = vipTrip.remove(e)!!
                vipDurations += now - since
                vipVisits += id
            }
        }
        for (kind in listOf(MoneyKind.VIP)) {
            val total = sim.finance.day(sim.clock.day)[kind] ?: 0L
            if (total > lastVipMoney) { vipTipTotal += total - lastVipMoney; if (total - lastVipMoney > 1000) vipHappy++ else vipStingy++ }
            lastVipMoney = total
        }
        if (sim.clock.hour == 0 && sim.clock.minute < 10) lastVipMoney = sim.finance.day(sim.clock.day)[MoneyKind.VIP] ?: 0L
    }

    private val givesUp = Vips.all.map { it.givesUp.toString() }.toSet()

    private fun readNews() {
        for (item in sim.news.latest.reversed()) {
            if (item.id <= lastNewsId) continue
            lastNewsId = item.id
            val t = item.text
            when {
                t in givesUp -> vipGaveUp++
                t.contains("zieht aus: zu schlechte Laune") || t.contains("moves out: the mood") -> { moveOuts++; moveOutsMood++ }
                (t.contains("zieht aus. Grund") || t.contains("moves out. Reason")) -> moveOuts++
            }
            for ((key, words) in EVENT_KEYS) if (words.any { t.contains(it) }) events[key] = (events[key] ?: 0) + 1
        }
    }

    // ---------- the bot ----------
    private fun reserve(): Long {
        val y = sim.finance.day(sim.clock.day - 1)
        val upkeep = -(y[MoneyKind.UPKEEP] ?: 0L)
        return maxOf(5_000L, upkeep * 2)
    }

    private fun afford(cost: Long) = sim.money - cost >= reserve()

    private fun count(type: RoomType) = sim.tower.allRooms.count { it.type == type }

    private fun note(what: String) { built[what] = (built[what] ?: 0) + 1 }

    private fun act() {
        buyCabins()
        specials()
        repeat(6) { if (!buildNext()) return }
    }

    private fun buyCabins() {
        for (car in sim.elevators.cars.values) {
            val n = carWaitN[car.id] ?: 0
            val avg = if (n == 0) 0.0 else (carWaitSum[car.id] ?: 0.0) / n
            if (avg > 2.0 && n > 30 && sim.checkCabin(car).ok && afford(Elevators.CABIN_COST)) {
                sim.addCabin(car); note("Kabine")
                carWaitSum[car.id] = 0.0; carWaitN[car.id] = 0
            }
        }
        if (sim.clock.hour == 0) { carWaitSum.clear(); carWaitN.clear() }
    }

    private fun tryPlace(type: RoomType, floors: IntProgression = 1 until topLimit()): Boolean {
        if (!afford(type.cost)) return false
        for (f in floors) for (s in slots()) {
            if (type.width > slotW && s % 2 == 1) continue
            if (!covered(f, s * slotW + type.width / 2f) || !linked(f, s)) continue
            if (sim.build(type, f, s * slotW).ok) { note(type.id); extendShafts(f); return true }
        }
        return false
    }

    private fun specials() {
        if (sim.stage >= 2 && count(Catalog.COPYROOM) == 0) tryPlace(Catalog.COPYROOM)
        if (sim.stage >= 3 && count(Catalog.SECURITY) == 0) tryPlace(Catalog.SECURITY)
        if (sim.stage >= 5 && count(Catalog.UFO_PAD) == 0) tryPlace(Catalog.UFO_PAD)
        val homes = count(Catalog.HOME)
        if (strategy != "office" && sim.unlocked(Catalog.KITA) && count(Catalog.KITA) < minOf(3, 1 + homes / 40)) tryPlace(Catalog.KITA)
        if (strategy != "home" && sim.unlocked(Catalog.SUBWAY) && count(Catalog.SUBWAY) == 0 && afford(Catalog.SUBWAY.cost)) {
            for (s in 0 until sim.lotWidth / slotW step 2) if (sim.build(Catalog.SUBWAY, -1, s * slotW).ok) { note("subway"); break }
        }
        if (strategy == "mix" && sim.unlocked(Catalog.CONFERENCE) && count(Catalog.CONFERENCE) == 0 && count(Catalog.HOTEL) >= 10) tryPlace(Catalog.CONFERENCE)
        if (sim.basementOpen && afford(Catalog.PARKING.cost) && (strategy == "home" || count(Catalog.SUBWAY) > 0 || sim.money > 400_000)) {
            for (s in 0 until sim.lotWidth / slotW) if (sim.build(Catalog.PARKING, -1, s * slotW).ok) { note("parking"); break }
        }
        val hotels = count(Catalog.HOTEL)
        if (hotels > 0 && count(Catalog.HOUSEKEEPING) < (hotels + 7) / 8) tryPlace(Catalog.HOUSEKEEPING)
    }

    private fun topLimit() = Stages.info(sim.stage).maxFloor

    /** Whether a room centred at [cx] on [floor] is within walking reach of an active local shaft. */
    private fun covered(floor: Int, cx: Float): Boolean {
        val z = zoneOf(floor)
        return activeCols[z].orEmpty().any { abs(it + 1f - cx) <= reach }
    }

    /** What to build next on [floor], or null to wait (for buyers, tenants or staff). */
    private fun chooseType(floor: Int): RoomType? {
        val rooms = sim.tower.allRooms.filter { it.type.role.rentable }
        val n = rooms.size + 1
        val food = rooms.count { it.type.role == RoomRole.FOOD }
        if (sim.unlocked(Catalog.FOOD) && food * 15 < n) return Catalog.FOOD
        // flats nobody will ever buy (too far away) don't count as waiting for a buyer
        val unsoldHomes = rooms.count { it.type.role == RoomRole.HOME && !it.sold && sim.roomProblems[it.id]?.far != true }
        val homes = rooms.count { it.type == Catalog.HOME }
        val emptyOffices = rooms.count { it.type == Catalog.OFFICE && it.tenant == null && sim.roomProblems[it.id]?.far != true }
        val housing = rooms.count { it.type.role.housing }
        fun shopNeeded() = sim.unlocked(Catalog.SHOP) && rooms.count { it.type == Catalog.SHOP } * Simulation.SHOP_SERVES < housing
        fun penthouse() = sim.unlocked(Catalog.PENTHOUSE) && floor >= Simulation.PENTHOUSE_FLOOR && sim.money > 400_000 &&
            rooms.count { it.type == Catalog.PENTHOUSE } * 10 < homes
        if (!sim.unlocked(Catalog.OFFICE) || strategy == "home") {
            if (strategy == "home" && shopNeeded()) return Catalog.SHOP
            if (unsoldHomes >= 2) return null
            return if (strategy == "home" && penthouse()) Catalog.PENTHOUSE else Catalog.HOME
        }
        if (strategy == "office") {
            if (sim.stage >= 4 && sim.money > 600_000 && rooms.count { it.type == Catalog.LUXURY_OFFICE } * 10 < n) return Catalog.LUXURY_OFFICE
            return if (emptyOffices < 2) Catalog.OFFICE else null
        }
        if (sim.stage >= 3) {
            val hotels = rooms.count { it.type == Catalog.HOTEL }
            if (hotels * 12 < n) return Catalog.HOTEL
        }
        if (shopNeeded()) return Catalog.SHOP
        if (sim.stage >= 4 && sim.money > 600_000) {
            val lux = rooms.count { it.type == Catalog.LUXURY_OFFICE }
            if (lux * 10 < n) return Catalog.LUXURY_OFFICE
        }
        if (penthouse() && unsoldHomes == 0) return Catalog.PENTHOUSE
        val emptyDorms = rooms.count { it.type == Catalog.DORM && it.tenant == null }
        if (sim.unlocked(Catalog.DORM) && emptyDorms == 0 && sim.hasSideJobsFor(Catalog.DORM.capacity)) return Catalog.DORM
        if (emptyOffices < 2 && sim.hasWorkersFor(Catalog.OFFICE.capacity) && (homes * 4 >= n || unsoldHomes >= 2)) return Catalog.OFFICE
        if (unsoldHomes < 2) return Catalog.HOME
        return if (emptyOffices < 2 && sim.hasWorkersFor(Catalog.OFFICE.capacity)) Catalog.OFFICE else null
    }

    private fun lobbyFloor(f: Int) = f > 0 && Tower.isLobbyFloor(f)

    /** Builds one room (or shaft), lowest floor first. False when nothing was built. */
    private fun buildNext(): Boolean {
        val top = topLimit()
        for (f in 1 until top) {
            val z = zoneOf(f)
            if (lobbyFloor(f)) {
                // sky lobby under everything built below
                var any = false
                for (s in slots()) {
                    if (sim.tower.roomAt(f, s * slotW) != null) continue
                    if (sim.tower.roomAt(f - 1, s * slotW) == null) continue
                    if (!afford(Catalog.LOBBY.cost * slotW)) { skippedNoMoney++; return false }
                    if (sim.build(Catalog.LOBBY, f, s * slotW, slotW).ok) { note("skylobby"); any = true }
                }
                if (any) { extendExpress(f); return true }
                continue
            }
            if (z > 0 && activeCols[z] == null) {
                // a new zone: express must reach the sky lobby, then the first local shaft starts there
                if (sim.stage < 2) return false
                if (!extendExpress(zoneBottom(z))) return false
                activeCols[z] = mutableListOf(cols(z)[0])
            }
            val free = slots().filter { s ->
                sim.tower.roomAt(f, s * slotW) == null && (sim.tower.roomAt(f - 1, s * slotW) != null ||
                    (f == 1 && (s == 0 || sim.tower.roomAt(0, (s - 1) * slotW) != null)))
            }
            if (free.isEmpty()) continue
            val reachable = free.filter { covered(f, it * slotW + 3f) && linked(f, it) }
                .sortedBy { s -> activeCols[z].orEmpty().minOf { abs(it + 1f - (s * slotW + 3f)) } }
            if (reachable.isEmpty()) {
                // open the next column of this zone
                val next = cols(z).firstOrNull { x -> x !in activeCols[z]!! && x + 2 <= sim.lotWidth && free.any { abs(x + 1f - (it * slotW + 3f)) <= reach } }
                    ?: continue
                val plan = sim.planColumn(Catalog.ELEVATOR, next, zoneBottom(z), f - 1)
                if (!plan.check.ok || !afford(plan.cost)) { skippedNoMoney++; return false }
                if (!sim.build(plan).ok) return false
                note("Schacht"); activeCols[z]!! += next
                return true
            }
            val type = chooseType(f) ?: return false
            for (s in reachable) {
                if (type.width > slotW && !(s + 1 in reachable)) continue
                val cost = type.cost + shaftCostTo(f) + lobbyCost(f, s)
                if (!afford(cost)) { skippedNoMoney++; return false }
                ensureLobby(f, s)
                if (sim.build(type, f, s * slotW).ok) {
                    note(type.id)
                    extendShafts(f)
                    return true
                }
            }
            // wide type did not fit, try a narrow one
            val narrow = if (strategy == "home" || !sim.unlocked(Catalog.OFFICE)) Catalog.HOME else Catalog.OFFICE
            if (type.width <= slotW) return false
            for (s in reachable) {
                if (!afford(narrow.cost + shaftCostTo(f) + lobbyCost(f, s))) { skippedNoMoney++; return false }
                ensureLobby(f, s)
                if (sim.build(narrow, f, s * slotW).ok) { note(narrow.id); extendShafts(f); return true }
            }
            return false
        }
        return false
    }

    private fun lobbyCost(f: Int, s: Int) = if (f == 1 && sim.tower.roomAt(0, s * slotW) == null) Catalog.LOBBY.cost * slotW else 0L

    private fun ensureLobby(f: Int, s: Int) {
        if (f == 1 && sim.tower.roomAt(0, s * slotW) == null) { sim.build(Catalog.LOBBY, 0, s * slotW, slotW); note("lobby") }
    }

    /** A slot people can walk into: next to a built slot on its floor or holding a shaft. */
    private fun linked(f: Int, s: Int): Boolean {
        if (activeCols[zoneOf(f)].orEmpty().any { it / slotW == s }) return true
        return (s > 0 && sim.tower.roomAt(f, (s - 1) * slotW) != null) || sim.tower.roomAt(f, (s + 1) * slotW) != null
    }

    private fun shaftCostTo(f: Int): Long = activeCols[zoneOf(f)].orEmpty().sumOf { x ->
        if (sim.tower.wayAt(f, x) == null) sim.priceAt(Catalog.ELEVATOR, f, x) else 0L
    }

    private fun extendShafts(f: Int) {
        val z = zoneOf(f)
        for (x in activeCols[z].orEmpty()) {
            if (sim.tower.wayAt(f, x) != null) continue
            val plan = sim.planColumn(Catalog.ELEVATOR, x, zoneBottom(z), f)
            if (plan.check.ok) sim.build(plan)
        }
        if (sim.stage >= 2 && f >= 10) extendExpress(f)
    }

    private fun extendExpress(f: Int): Boolean {
        if (secondExpress && sim.stage >= 3) extendExpressAt(86, f)
        return extendExpressAt(expressX, f)
    }

    private fun extendExpressAt(expressX: Int, f: Int): Boolean {
        if (sim.stage < 2) return false
        if (sim.tower.wayAt(f, expressX)?.type == Catalog.EXPRESS) return true
        val plan = sim.planColumn(Catalog.EXPRESS, expressX, 0, f)
        if (!plan.check.ok || !afford(plan.cost)) return false
        val ok = sim.build(plan).ok
        if (ok) note("express")
        return ok
    }

    // ---------- output ----------
    private fun pct(list: List<Double>, p: Double): Double = if (list.isEmpty()) 0.0 else list.sorted()[((list.size - 1) * p).toInt()]

    private fun dayLine(ms: Long) {
        val rooms = sim.tower.allRooms
        fun c(t: RoomType) = "${rooms.count { it.type == t }}(${rooms.count { it.type == t && it.tenant != null }})"
        val probs = sim.roomProblems.values.groupingBy { it.title.toString() }.eachCount()
        for ((k, v) in probs) problemsSeen[k] = maxOf(problemsSeen[k] ?: 0, v)
        val empty = rooms.count { it.type.role.rentable && it.tenant == null }
        val d = sim.finance.day(sim.clock.day)
        val inc = d.filterKeys { it.income }.values.sum()
        val avgW = if (waitSamples.isEmpty()) 0.0 else waitSamples.average()
        val line = "%d | %d | %,d | %d | %d | %d | %.0f | %s/%s/%s/%s/%s D%s S%s P%s | %d %s | %d/%d | %.1f/%.1f/%.0f | %d/%d/%d/%d %,d | %d | %,d | %,d | %,d | %d".format(
            sim.clock.day, sim.clock.hour, sim.money, sim.stage, sim.stars, sim.population, sim.satisfaction,
            c(Catalog.OFFICE), c(Catalog.HOME), c(Catalog.FOOD), c(Catalog.HOTEL), c(Catalog.LUXURY_OFFICE), c(Catalog.DORM), c(Catalog.SHOP), c(Catalog.PENTHOUSE),
            empty, probs.toString(), sim.elevators.cars.size, sim.elevators.cars.values.sumOf { it.cabins.size },
            avgW, pct(waitSamples, 0.95), maxWait,
            vipVisits.size, vipHappy, vipStingy, vipGaveUp, vipTipTotal, moveOuts,
            inc, -(d[MoneyKind.UPKEEP] ?: 0L), -(d[MoneyKind.BUILD] ?: 0L), ms,
        )
        val kinds = listOf(MoneyKind.RENT, MoneyKind.FOOD, MoneyKind.SALE, MoneyKind.FEES, MoneyKind.HOTEL, MoneyKind.PARKING, MoneyKind.VIP, MoneyKind.GUESTS, MoneyKind.REFUND)
        println(line + " | " + kinds.joinToString(" ") { "${it.name.lowercase()}=${(d[it] ?: 0L) / 1000}k" })
        waitSamples.clear(); maxWait = 0.0
    }

    private fun summary() {
        println("\n== Zusammenfassung (Strategie $strategy) ==")
        println("Laufzeit: %.1f min echt, %d Spieltage, Geld %,d, Stufe %d, Sterne %d, Leute %d (Spitze %d)".format(
            (System.nanoTime() - started) / 60e9, sim.clock.day, sim.money, sim.stage, sim.stars, sim.population, peakPop))
        println("Stufen erreicht an Tag: ${stageDay.toSortedMap()}")
        println("Sterne erreicht an Tag: ${starDay.toSortedMap()}")
        println("Gebaut: $built")
        println("Stunden ohne Geld: $skippedNoMoney")
        println("VIPs: ${vipVisits.groupingBy { it }.eachCount()} | großzügig $vipHappy, knausrig $vipStingy, aufgegeben $vipGaveUp, Trinkgeld %,d".format(vipTipTotal))
        if (vipDurations.isNotEmpty()) println("VIP-Wege: Ø %.0f min, max %.0f min".format(vipDurations.average(), vipDurations.max()))
        println("Auszüge: $moveOuts (davon wegen Turm-Laune $moveOutsMood)")
        println("Ereignisse (Ticker): $events")
        println("Leerstand-Gründe (Spitze gleichzeitig): $problemsSeen")
        println("Max ms pro Spielstunde (6 Ticks à 10 min): $maxMsPerHour")
        val total = sim.profiler.snapshot()
        val sum = total.total.coerceAtLeast(1)
        sim.profiler.names.indices.sortedByDescending { total.nanos[it] }.take(6).forEach { i ->
            println("  %-14s %5.1f %%".format(sim.profiler.names[i], 100.0 * total.nanos[i] / sum))
        }
        println("Schächte (x, express, Etagen, Kabinen, Ø Wartezeit heute):")
        for (car in sim.elevators.cars.values.sortedBy { it.bottom * 1000 + it.x }) {
            val n = carWaitN[car.id] ?: 0
            println("  x=%3d %s %2d..%2d Kabinen %2d Ø %.1f min (%d Proben)".format(car.x, if (car.express) "EXPRESS" else "lokal  ", car.bottom, car.top, car.cabins.size, if (n == 0) 0.0 else carWaitSum[car.id]!! / n, n))
        }
        val far = sim.roomProblems.keys.mapNotNull { sim.tower.room(it) }.groupingBy { it.floor / 5 * 5 }.eachCount().toSortedMap()
        println("Leerstand nach Etagen (5er-Gruppen): $far")
        println("Letzte Ticker-Meldungen:")
        sim.news.latest.take(25).forEach { println("  - ${it.text}") }
    }

    companion object {
        val EVENT_KEYS = mapOf(
            "clowns" to listOf("Clown"), "spuk" to listOf("spukt", "Geist"), "hochzeit" to listOf("heirat"),
            "tauben" to listOf("Tauben"), "influencer" to listOf("Influencer"), "stromausfall" to listOf("Stromausfall"),
            "wichtig" to listOf("Frau Dr. Wichtig"), "security" to listOf("Sicherheitsdienst hat"),
        )
    }
}
