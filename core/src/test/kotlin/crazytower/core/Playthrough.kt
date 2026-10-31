package crazytower.core

import crazytower.core.bot.Bot
import crazytower.core.bot.HubBot
import crazytower.core.bot.MiserBot
import crazytower.core.bot.TwinBot
import crazytower.core.bot.WideBot
import crazytower.core.ecs.Vip
import com.github.quillraven.fleks.Entity
import java.io.File

/**
 * Headless playthrough: a bot plays from an empty lot through stage 5 to the lift-off of the moon
 * mission (or [days]) and logs a line per day.
 * Run: ./gradlew :core:playthrough --args="[days] [seed] [minutes]" -Dbot=twin|wide|hub|miser|all
 * With -Dcsv=<dir> each run also writes one CSV line per day to <dir>/<bot>-<seed>.csv.
 */
fun main(args: Array<String>) {
    val days = args.getOrNull(0)?.toIntOrNull() ?: 200
    val seed = args.getOrNull(1)?.toLongOrNull() ?: 42L
    val wallLimitMin = args.getOrNull(2)?.toIntOrNull() ?: 50
    val which = System.getProperty("bot") ?: System.getenv("BOT") ?: "all"
    val csv = (System.getProperty("csv") ?: System.getenv("CSV"))?.let { File(it).apply { mkdirs() } }
    val ids = if (which == "all") Playthrough.BOTS.keys.toList() else which.split(",")
    val results = ids.map { id ->
        val make = Playthrough.BOTS[id] ?: error("Unbekannter Bot '$id', es gibt ${Playthrough.BOTS.keys} und all")
        Playthrough(seed, make, csv).run(days, wallLimitMin)
    }
    if (results.size > 1) Playthrough.compare(results)
}

class Playthrough(private val seed: Long, makeBot: (Simulation) -> Bot, private val csvDir: File? = null) {
    val sim = Simulation(seed = seed)
    val bot = makeBot(sim)
    private val started = System.nanoTime()

    /** What a run ended with, for the comparison table. */
    data class Result(
        val bot: String, val days: Int, val stage: Int, val stars: Int, val population: Int, val peak: Int,
        val money: Long, val mood: Float, val stageDay: Map<Int, Int>, val starDay: Map<Int, Int>,
        val far: Int, val empty: Int, val moveOuts: Int, val shafts: Int, val cabins: Int, val p95Wait: Double,
        val minMood: Float, val minMoney: Long, val rejectedFar: Int, val noMoneyHours: Int,
        val partDay: Map<Int, Int>, val sinks: Map<MoneyKind, Long>, val moneyDays: Double,
    )

    // ---------- stats ----------
    private val waitSamples = ArrayList<Double>()
    private val allWaits = ArrayList<Double>()
    private var maxWait = 0.0
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
    private val moveOutReasons = HashMap<String, Int>()
    private val events = HashMap<String, Int>()
    private val stageDay = HashMap<Int, Int>()
    private val starDay = HashMap<Int, Int>()
    private val problemsSeen = HashMap<String, Int>()
    private var lastVipMoney = 0L
    private var maxMsPerHour = 0L
    private var peakPop = 0
    private var minMood = 100f
    private var minMoney = Long.MAX_VALUE
    private val partDay = HashMap<Int, Int>()
    private val sinkTotals = HashMap<MoneyKind, Long>()
    private var moneyDays = 0.0
    private val csv = StringBuilder("tag;geld;stufe;sterne;leute;laune;bueros;wohnungen;wohnheime;hotels;luxus;leer;zu_weit;wartezeit_p95;auszuege;einnahmen;unterhalt;bau;geld_in_tagen;ausbau;tuning;versicherung;werbung;extras;mission;countdown;aufgaben;schaulustige;verargert\n")

    fun run(days: Int, wallLimitMin: Int): Result {
        bot.setup()
        println("== ${bot.title} (${bot.id}), Seed $seed: ${bot.idea} ==")
        println("Tag | Std | Geld | Stufe | ★ | Leute | Laune | Räume B/W/I/H/Lux (vermietet) | leer+Problem | Schächte/Kabinen | Wartezeit Ø/p95/max | VIP n/+/−/weg Trinkgeld | Auszüge | Einnahmen | Unterhalt | Bau | ms/Std")
        for (hour in 0 until days * 24) {
            val wall = System.nanoTime()
            repeat(6) {
                try { sim.step(10.0) } catch (e: Throwable) { println("EXCEPTION day ${sim.clock.day}: $e"); e.printStackTrace(); return summary() }
                sample()
            }
            val ms = (System.nanoTime() - wall) / 1_000_000
            if (ms > maxMsPerHour) maxMsPerHour = ms
            bot.act()
            readNews()
            if (sim.stage !in stageDay) stageDay[sim.stage] = sim.clock.day
            if (sim.stars !in starDay) starDay[sim.stars] = sim.clock.day
            peakPop = maxOf(peakPop, sim.population)
            if (sim.clock.hour == 23) dayLine(ms)
            if (sim.missionStarted && sim.endgame.parts !in partDay) partDay[sim.endgame.parts] = sim.clock.day
            if (sim.liftedOff) { println("ABGEHOBEN an Tag ${sim.clock.day}"); break }
            if ((System.nanoTime() - started) / 60e9 > wallLimitMin) { println("Zeitlimit erreicht"); break }
        }
        return summary()
    }

    // ---------- sampling ----------
    private fun sample() {
        val now = sim.clock.totalMinutes
        bot.sampleWaits()
        for (car in sim.elevators.cars.values) {
            for (since in car.waitingSince.values) {
                val w = now - since
                waitSamples += w
                if (w > maxWait) maxWait = w
            }
        }
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
        val total = sim.finance.day(sim.clock.day)[MoneyKind.VIP] ?: 0L
        if (total > lastVipMoney) { vipTipTotal += total - lastVipMoney; if (total - lastVipMoney > 1000) vipHappy++ else vipStingy++ }
        lastVipMoney = total
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
                t.contains("zieht aus. Grund") || t.contains("moves out. Reason") -> {
                    moveOuts++
                    val reason = t.substringAfter("Grund").substringAfter(":").trim().take(40)
                    moveOutReasons[reason] = (moveOutReasons[reason] ?: 0) + 1
                }
            }
            for ((key, words) in EVENT_KEYS) if (words.any { t.contains(it) }) events[key] = (events[key] ?: 0) + 1
        }
    }

    // ---------- output ----------
    private fun pct(list: List<Double>, p: Double): Double = if (list.isEmpty()) 0.0 else list.sorted()[((list.size - 1) * p).toInt()]

    private fun dayLine(ms: Long) {
        val rooms = sim.tower.allRooms
        fun c(t: RoomType) = "${rooms.count { it.type == t }}(${rooms.count { it.type == t && it.tenant != null }})"
        val probs = sim.roomProblems.values.groupingBy { it.title.toString() }.eachCount()
        for ((k, v) in probs) problemsSeen[k] = maxOf(problemsSeen[k] ?: 0, v)
        val empty = rooms.count { it.type.role.rentable && it.tenant == null }
        val far = sim.roomProblems.values.count { it.far }
        val d = sim.finance.day(sim.clock.day)
        val inc = d.filterKeys { it.income }.values.sum()
        val avgW = if (waitSamples.isEmpty()) 0.0 else waitSamples.average()
        val p95 = pct(waitSamples, 0.95)
        minMood = minOf(minMood, sim.satisfaction)
        minMoney = minOf(minMoney, sim.money)
        val line = "%d | %d | %,d | %d | %d | %d | %.0f | %s/%s/%s/%s/%s D%s S%s P%s | %d %s | %d/%d | %.1f/%.1f/%.0f | %d/%d/%d/%d %,d | %d | %,d | %,d | %,d | %d".format(
            sim.clock.day, sim.clock.hour, sim.money, sim.stage, sim.stars, sim.population, sim.satisfaction,
            c(Catalog.OFFICE), c(Catalog.HOME), c(Catalog.FOOD), c(Catalog.HOTEL), c(Catalog.LUXURY_OFFICE), c(Catalog.DORM), c(Catalog.SHOP), c(Catalog.PENTHOUSE),
            empty, probs.toString(), sim.elevators.cars.size, sim.elevators.cars.values.sumOf { it.cabins.size },
            avgW, p95, maxWait,
            vipVisits.size, vipHappy, vipStingy, vipGaveUp, vipTipTotal, moveOuts,
            inc, -(d[MoneyKind.UPKEEP] ?: 0L), -(d[MoneyKind.BUILD] ?: 0L), ms,
        )
        val kinds = listOf(MoneyKind.RENT, MoneyKind.FOOD, MoneyKind.SALE, MoneyKind.FEES, MoneyKind.HOTEL, MoneyKind.PARKING, MoneyKind.VIP, MoneyKind.GUESTS, MoneyKind.REFUND)
        val sinks = MoneyKind.SINKS.associateWith { -(d[it] ?: 0L) }
        sinks.forEach { (k, v) -> sinkTotals[k] = (sinkTotals[k] ?: 0L) + v }
        moneyDays = if (inc > 0) sim.money.toDouble() / inc else 0.0
        val part = sim.currentPart
        val spent = sinks.filterValues { it > 0 }.map { "${it.key.name.lowercase()}=${it.value / 1000}k" }
        val mission = when {
            sim.missionStarted -> " | Höhe ${rooms.maxOf { it.floor }} Plattformen ${rooms.count { it.type == Mission.DECK }} Läden ${rooms.count { it.type == Mission.SOUVENIRS }}" +
                (if (sim.clock.totalMinutes < sim.endgame.delayedUntil) " PRESSE" else "") +
                rooms.filter { it.tenant?.id == "astronauts" }.let { a -> if (a.isEmpty()) "" else " Astronauten ${a.size}x Laune ${a.map { sim.moodOf(it)?.toInt() }}" } +
                rooms.count { it.tenant?.id == "rocket" }.let { if (it == 0) "" else " Raketenbüros $it" } +
                " freie Fachkräfte ${sim.workforce - sim.jobs}" + " | Countdown ${sim.endgame.parts}/4 Aufgaben %.0f %% (%s) Schaulustige %d/%d verärgert (spät %d, schmutzig %d, kein Weg %d, Ø %.0f min), Senken %s".format(
            sim.missionProgress * 100, part?.tasks?.joinToString(" ") { "${it.id}=${sim.taskProgress(it)}/${it.goal}" } ?: "-",
                sim.spectatorsPerDay, sim.endgame.spectatorsUnhappy, sim.endgame.spectatorsLate, sim.endgame.spectatorsDirty, sim.endgame.spectatorsStuck, sim.endgame.spectatorMinutes / (sim.endgame.spectatorsToday - sim.endgame.spectatorsStuck).coerceAtLeast(1), spent)
            sim.stage >= SINK_STAGE -> " | Senken $spent"
            else -> ""
        }
        println(line + " | " + kinds.joinToString(" ") { "${it.name.lowercase()}=${(d[it] ?: 0L) / 1000}k" } + " | Geld = %.1f Tage".format(moneyDays) + mission)
        fun n(t: RoomType) = rooms.count { it.type == t }
        csv.append(listOf(
            sim.clock.day, sim.money, sim.stage, sim.stars, sim.population, "%.1f".format(java.util.Locale.ROOT, sim.satisfaction),
            n(Catalog.OFFICE), n(Catalog.HOME), n(Catalog.DORM), n(Catalog.HOTEL), n(Catalog.LUXURY_OFFICE), empty, far,
            "%.1f".format(java.util.Locale.ROOT, p95), moveOuts, inc, -(d[MoneyKind.UPKEEP] ?: 0L), -(d[MoneyKind.BUILD] ?: 0L),
            "%.1f".format(java.util.Locale.ROOT, moneyDays), sinks[MoneyKind.UPGRADE], sinks[MoneyKind.TUNING], sinks[MoneyKind.INSURANCE],
            sinks[MoneyKind.PROMOTION], sinks[MoneyKind.EXTRAS], sinks[MoneyKind.MISSION], sim.endgame.parts,
            "%.2f".format(java.util.Locale.ROOT, if (sim.missionStarted) sim.missionProgress else 0.0), sim.endgame.spectatorsToday, sim.endgame.spectatorsUnhappy,
        ).joinToString(";")).append('\n')
        allWaits += p95
        waitSamples.clear(); maxWait = 0.0
    }

    private fun summary(): Result {
        println("\n== Zusammenfassung ${bot.title} (${bot.id}), Seed $seed ==")
        println("Laufzeit: %.1f min echt, %d Spieltage, Geld %,d, Stufe %d, Sterne %d, Leute %d (Spitze %d), Laune %.0f %%".format(
            (System.nanoTime() - started) / 60e9, sim.clock.day, sim.money, sim.stage, sim.stars, sim.population, peakPop, sim.satisfaction))
        println("Stufen erreicht an Tag: ${stageDay.toSortedMap()}")
        println("Sterne erreicht an Tag: ${starDay.toSortedMap()}")
        println("Gebaut: ${bot.built}")
        println("Countdown-Schritte an Tag: ${partDay.toSortedMap()} (0 = Mission begonnen, 4 = abgehoben)")
        val steps = partDay.toSortedMap().entries.zipWithNext { a, b -> "${a.key}→${b.key}: ${b.value - a.value} Tage" }
        if (steps.isNotEmpty()) println("Dauer je Schritt: $steps")
        println("Ausgaben für Senken gesamt: ${sinkTotals.filterValues { it > 0 }.mapValues { "%,d".format(it.value) }}, Geld am Ende = %.1f Tage Einnahmen".format(moneyDays))
        val e = sim.endgame
        if (sim.missionStarted) println("Mission: Versicherungen ${e.insurances}, Deko ${e.decor}, Eingänge ${e.gates}, Forschung ${e.research}, Schaulustige/Tag ${sim.spectatorsPerDay}")
        println("Stunden ohne Geld: ${bot.skippedNoMoney}, Stunden ohne Platz: ${bot.noSpotHours}, Plätze verworfen (Weg zu lang): ${bot.rejectedFar}")
        println("VIPs: ${vipVisits.groupingBy { it }.eachCount()} | großzügig $vipHappy, knausrig $vipStingy, aufgegeben $vipGaveUp, Trinkgeld %,d".format(vipTipTotal))
        if (vipDurations.isNotEmpty()) println("VIP-Wege: Ø %.0f min, max %.0f min".format(vipDurations.average(), vipDurations.max()))
        println("Auszüge: $moveOuts (davon wegen Turm-Laune $moveOutsMood), Gründe: $moveOutReasons")
        println("Ereignisse (Ticker): $events")
        println("Leerstand-Gründe (Spitze gleichzeitig): $problemsSeen")
        println("Max ms pro Spielstunde (6 Ticks à 10 min): $maxMsPerHour")
        println("Schächte (x, express, Etagen, Kabinen, Ø Wartezeit heute):")
        for (car in sim.elevators.cars.values.sortedBy { it.bottom * 1000 + it.x }) {
            println("  x=%3d %s %2d..%2d Kabinen %2d Ø %.1f min (%d Proben)".format(car.x, if (car.express) "EXPRESS" else "lokal  ", car.bottom, car.top, car.cabins.size, bot.avgWait(car.id), bot.carWaitN[car.id] ?: 0))
        }
        val byFloor = sim.roomProblems.keys.mapNotNull { sim.tower.room(it) }.groupingBy { it.floor / 5 * 5 }.eachCount().toSortedMap()
        println("Leerstand nach Etagen (5er-Gruppen): $byFloor")
        println("Letzte Ticker-Meldungen:")
        sim.news.latest.take(12).forEach { println("  - ${it.text}") }
        csvDir?.let { File(it, "${bot.id}-$seed.csv").writeText(csv.toString()) }
        val rooms = sim.tower.allRooms
        return Result(
            bot.id, sim.clock.day, sim.stage, sim.stars, sim.population, peakPop, sim.money, sim.satisfaction,
            stageDay.toSortedMap(), starDay.toSortedMap(), sim.roomProblems.values.count { it.far },
            rooms.count { it.type.role.rentable && it.tenant == null }, moveOuts, sim.elevators.cars.size,
            sim.elevators.cars.values.sumOf { it.cabins.size }, pct(allWaits, 0.95), minMood, minMoney,
            bot.rejectedFar, bot.skippedNoMoney, partDay.toSortedMap(), sinkTotals.toMap(), moneyDays,
        )
    }

    companion object {
        val BOTS: Map<String, (Simulation) -> Bot> = linkedMapOf(
            "twin" to ::TwinBot,
            "wide" to ::WideBot,
            "hub" to ::HubBot,
            "miser" to ::MiserBot,
        )

        fun compare(results: List<Result>) {
            println("\n== Vergleich ==")
            println("Bot | Tage | Stufe | ★ | Leute (Spitze) | Geld | Laune (tiefste) | Stufe 1/2/3/4/5 an Tag | ★ 3/4/5 an Tag | leer / zu weit | Auszüge | Schächte/Kabinen | Countdown 0/1/2/3/4 an Tag | Senken | Geld in Tagen")
            for (r in results) {
                val st = (1..5).joinToString("/") { r.stageDay[it]?.toString() ?: "-" }
                val sr = (3..5).joinToString("/") { r.starDay[it]?.toString() ?: "-" }
                val parts = (0..4).joinToString("/") { r.partDay[it]?.toString() ?: "-" }
                println("%s | %d | %d | %d | %d (%d) | %,d | %.0f (%.0f) | %s | %s | %d / %d | %d | %d/%d | %s | %,d | %.1f".format(
                    r.bot, r.days, r.stage, r.stars, r.population, r.peak, r.money, r.mood, r.minMood, st, sr, r.empty, r.far, r.moveOuts, r.shafts, r.cabins,
                    parts, r.sinks.values.sum(), r.moneyDays))
            }
        }

        val EVENT_KEYS = mapOf(
            "clowns" to listOf("Clown"), "spuk" to listOf("spukt", "Geist"), "hochzeit" to listOf("heirat"),
            "tauben" to listOf("Tauben"), "influencer" to listOf("Influencer"), "stromausfall" to listOf("Stromausfall"),
            "wichtig" to listOf("Frau Dr. Wichtig"), "security" to listOf("Sicherheitsdienst hat"),
        )
    }
}
