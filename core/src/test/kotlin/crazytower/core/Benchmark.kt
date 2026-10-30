package crazytower.core

import crazytower.core.ecs.Away
import crazytower.core.ecs.Person
import crazytower.core.ecs.Riding
import crazytower.core.ecs.Route

/**
 * Runs the maximum test tower without graphics and prints the time per game hour and per system.
 * `./gradlew :core:bench` (optional arguments: game hours, tower number).
 * At 1x a game hour lasts 15 real seconds, so 15 000 ms per game hour is the limit for 1x.
 */
fun main(args: Array<String>) {
    val hours = args.getOrNull(0)?.toIntOrNull() ?: 24
    val number = args.getOrNull(1)?.toIntOrNull() ?: TestTower.MAXIMUM
    val built = System.nanoTime()
    val sim = TestTower.create(number)
    println("Turm gebaut in ${(System.nanoTime() - built) / 1_000_000} ms: ${sim.population} Leute, ${sim.elevators.cars.size} Schächte")
    // warm up the JIT, then start at the same hour every run
    sim.step(60.0)
    val start = sim.profiler.snapshot()
    println("Stunde  ms/Std  Ticks  Pfade  im Turm  unterwegs  Aufzug")
    var hourStart = start
    repeat(hours) {
        val wall = System.nanoTime()
        sim.step(60.0)
        val ms = (System.nanoTime() - wall) / 1_000_000
        val now = sim.profiler.snapshot()
        val d = now - hourStart
        hourStart = now
        var inside = 0
        var moving = 0
        var riding = 0
        with(sim.world) {
            family { all(Person) }.forEach {
                if (it hasNo Away) inside++
                if (it has Route) moving++
                if (it has Riding) riding++
            }
        }
        println("%5d  %6d  %5d  %5d  %7d  %9d  %6d".format(sim.clock.hour, ms, d.ticks, d.pathSearches, inside, moving, riding))
    }
    val total = sim.profiler.snapshot() - start
    val sum = total.total.coerceAtLeast(1)
    println()
    println("Je System über $hours Spielstunden (ms pro Spielstunde, Anteil):")
    sim.profiler.names.indices.sortedByDescending { total.nanos[it] }.forEach { i ->
        println("  %-14s %8.1f ms  %5.1f %%".format(sim.profiler.names[i], total.nanos[i] / 1e6 / hours, 100.0 * total.nanos[i] / sum))
    }
    println("  %-14s %8.1f ms".format("Summe", sum / 1e6 / hours))
    println("Pfadsuche (in den Systemen enthalten): %.1f ms pro Spielstunde, %d Suchen, %.1f µs je Suche".format(
        total.pathNanos / 1e6 / hours, total.pathSearches, total.pathNanos / 1e3 / total.pathSearches.coerceAtLeast(1)))
    println("Bei 1x stehen 15 000 ms pro Spielstunde zur Verfügung: %.0f %% davon braucht die Simulation".format(sum / 1e6 / hours / 150))
}
