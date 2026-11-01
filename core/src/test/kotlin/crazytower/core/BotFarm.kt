package crazytower.core

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Many bot playthroughs at once, one JVM per run, spread over the CPU cores.
 * Run: ./gradlew :core:bots --args="[days] [seeds] [minutes]" -Dbot=twin,hub|all -Djobs=n
 *  - seeds: "1-8", "1,5,9" or a single number (default 1-4)
 *  - jobs: runs at the same time (default: cores - 1); each run uses one thread
 *  - out: folder for the logs, CSVs and the summary (default core/build/bots)
 *  - xmx: heap per run (default 2g)
 * Each run writes its full log to <out>/<bot>-<seed>.log; at the end the comparison of all runs and
 * a summary per bot are printed and saved to <out>/zusammenfassung.txt and <out>/ergebnisse.csv.
 */
fun main(args: Array<String>) {
    val days = args.getOrNull(0)?.toIntOrNull() ?: 200
    val seeds = parseSeeds(args.getOrNull(1) ?: "1-4")
    val minutes = args.getOrNull(2)?.toIntOrNull() ?: 50
    val which = System.getProperty("bot")?.takeIf { it.isNotBlank() } ?: "all"
    val bots = if (which == "all") Playthrough.BOTS.keys.toList() else which.split(",").map { it.trim() }
    bots.forEach { require(it in Playthrough.BOTS) { "Unbekannter Bot '$it', es gibt ${Playthrough.BOTS.keys} und all" } }
    val cores = Runtime.getRuntime().availableProcessors()
    val jobs = (System.getProperty("jobs")?.toIntOrNull() ?: (cores - 1)).coerceAtLeast(1)
    val xmx = System.getProperty("xmx")?.takeIf { it.isNotBlank() } ?: "2g"
    val out = File(System.getProperty("out")?.takeIf { it.isNotBlank() } ?: "build/bots").absoluteFile.apply { mkdirs() }

    val runs = seeds.flatMap { seed -> bots.map { it to seed } }
    println("== ${runs.size} Läufe (${bots.joinToString()} × Seeds ${seeds.joinToString()}), $days Tage, höchstens $minutes min je Lauf ==")
    println("$jobs gleichzeitig auf $cores Kernen, Logs in $out")

    val javaBin = File(System.getProperty("java.home"), "bin/java").path
    val classpath = System.getProperty("java.class.path")
    val children = ConcurrentHashMap.newKeySet<Process>()
    Runtime.getRuntime().addShutdownHook(Thread { children.forEach { it.destroyForcibly() } })

    val started = System.nanoTime()
    val done = AtomicInteger(0)
    val pool = Executors.newFixedThreadPool(jobs)
    val futures = runs.map { (bot, seed) ->
        pool.submit<Playthrough.Result?> {
            val log = File(out, "$bot-$seed.log")
            val result = File(out, "$bot-$seed.json").apply { delete() }
            val t0 = System.nanoTime()
            val pb = ProcessBuilder(
                javaBin, "-Xmx$xmx", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-Dbot=$bot", "-Dcsv=${out.path}", "-Dresult=${result.path}", "-Dcrazytower.threads=1",
                "crazytower.core.PlaythroughKt", "$days", "$seed", "$minutes",
            ).redirectErrorStream(true).redirectOutput(log)
            // the class path goes in the environment: on Windows it is too long for the command line
            pb.environment()["CLASSPATH"] = classpath
            val p = pb.start().also { children += it }
            val code = p.waitFor()
            children -= p
            val r = if (code == 0 && result.exists()) Json.decodeFromString<List<Playthrough.Result>>(result.readText()).firstOrNull() else null
            result.delete()
            val min = (System.nanoTime() - t0) / 60e9
            val what = when {
                r == null -> "FEHLER (Exit-Code $code), siehe ${log.name}"
                r.partDay[4] != null -> "abgehoben an Tag ${r.partDay[4]}"
                else -> "Tag ${r.days}, Stufe ${r.stage}, ${r.stars} ★"
            }
            println("[%d/%d] %s Seed %d: %s (%.1f min)".format(done.incrementAndGet(), runs.size, bot, seed, what, min))
            r
        }
    }
    pool.shutdown()
    pool.awaitTermination(365, TimeUnit.DAYS)
    val results = runs.zip(futures.map { it.get() })

    val report = StringBuilder()
    fun say(s: String = "") { println(s); report.append(s).append('\n') }
    say("\n== Alle Läufe (%.1f min echt) ==".format((System.nanoTime() - started) / 60e9))
    say("Bot | Seed | Tage | Stufe | ★ | Leute (Spitze) | Geld | Laune (tiefste) | Stufe 1/2/3/4/5 an Tag | ★ 3/4/5 an Tag | Auszüge | Schächte/Kabinen | Countdown 0/1/2/3/4 an Tag | Geld in Tagen")
    for ((run, r) in results) {
        val (bot, seed) = run
        if (r == null) { say("$bot | $seed | FEHLER, siehe $bot-$seed.log"); continue }
        say("%s | %d | %d | %d | %d | %d (%d) | %,d | %.0f (%.0f) | %s | %s | %d | %d/%d | %s | %.1f".format(
            bot, seed, r.days, r.stage, r.stars, r.population, r.peak, r.money, r.mood, r.minMood,
            days(r.stageDay, 1..5), days(r.starDay, 3..5), r.moveOuts, r.shafts, r.cabins, days(r.partDay, 0..4), r.moneyDays))
    }

    say("\n== Je Bot (Median, in Klammern min–max) ==")
    say("Bot | Läufe | abgehoben | Abheben an Tag | Stufe 5 an Tag | 5 ★ an Tag | Leute (Spitze) | Laune | Geld am Ende | Fehler")
    for (bot in bots) {
        val mine = results.filter { it.first.first == bot }
        val ok = mine.mapNotNull { it.second }
        say("%s | %d | %d | %s | %s | %s | %s | %s | %s | %d".format(
            bot, mine.size, ok.count { it.partDay[4] != null },
            spread(ok.mapNotNull { it.partDay[4]?.toDouble() }), spread(ok.mapNotNull { it.stageDay[5]?.toDouble() }),
            spread(ok.mapNotNull { it.starDay[5]?.toDouble() }), spread(ok.map { it.peak.toDouble() }),
            spread(ok.map { it.mood.toDouble() }), spread(ok.map { it.money.toDouble() }), mine.size - ok.size))
    }
    say("\nAbheben/Stufe 5/5 ★ zählen nur die Läufe, die es geschafft haben. Die Logs je Lauf liegen in $out")
    File(out, "zusammenfassung.txt").writeText(report.toString())

    val csv = StringBuilder("bot;seed;tage;stufe;sterne;leute;spitze;geld;laune;tiefste_laune;stufe5_tag;sterne5_tag;abgehoben_tag;auszuege;schaechte;kabinen;geld_in_tagen\n")
    for ((run, r) in results) {
        if (r == null) continue
        csv.append(listOf(run.first, run.second, r.days, r.stage, r.stars, r.population, r.peak, r.money,
            "%.1f".format(Locale.ROOT, r.mood), "%.1f".format(Locale.ROOT, r.minMood),
            r.stageDay[5] ?: "", r.starDay[5] ?: "", r.partDay[4] ?: "", r.moveOuts, r.shafts, r.cabins,
            "%.1f".format(Locale.ROOT, r.moneyDays)).joinToString(";")).append('\n')
    }
    File(out, "ergebnisse.csv").writeText(csv.toString())
    if (results.any { it.second == null }) System.exit(1)
}

/** "1-8", "1,5,9", "3" or a mix like "1-4,10". */
private fun parseSeeds(text: String): List<Long> = text.split(",").filter { it.isNotBlank() }.flatMap { part ->
    val p = part.trim()
    val dash = p.indexOf('-', startIndex = 1)
    if (dash > 0) (p.substring(0, dash).toLong()..p.substring(dash + 1).toLong()).toList() else listOf(p.toLong())
}.distinct()

private fun days(map: Map<Int, Int>, range: IntRange) = range.joinToString("/") { map[it]?.toString() ?: "-" }

private fun spread(values: List<Double>): String {
    if (values.isEmpty()) return "-"
    val s = values.sorted()
    val median = if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    fun f(v: Double) = if (kotlin.math.abs(v) >= 10_000) "%,.0f".format(v) else "%.0f".format(v)
    return if (s.size == 1) f(median) else "${f(median)} (${f(s.first())}–${f(s.last())})"
}
