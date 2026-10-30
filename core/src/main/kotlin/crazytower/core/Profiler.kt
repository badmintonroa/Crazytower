package crazytower.core

import crazytower.core.nav.NavGraph

/**
 * Measures how long each ECS system takes, for the debug overlay and the benchmark.
 * The totals only grow; a reader takes two snapshots and looks at the difference.
 * Timing costs two clock reads per system and tick, so it is always on.
 */
class Profiler(val names: List<String>, private val nav: NavGraph) {
    /** Nanoseconds spent in each system since the start, same order as [names]. */
    val nanos = LongArray(names.size)

    /** Simulation ticks since the start. */
    var ticks: Long = 0
        internal set

    val totalNanos: Long get() = nanos.sum()

    fun snapshot(): Snapshot = Snapshot(nanos.copyOf(), ticks, nav.searchNanos, nav.searches)

    /** [pathNanos] and [pathSearches] count every path search, also those outside the systems (building, tests). */
    data class Snapshot(val nanos: LongArray, val ticks: Long, val pathNanos: Long, val pathSearches: Long) {
        /** What happened between [earlier] and this snapshot. */
        operator fun minus(earlier: Snapshot) = Snapshot(
            LongArray(nanos.size) { nanos[it] - earlier.nanos[it] },
            ticks - earlier.ticks,
            pathNanos - earlier.pathNanos,
            pathSearches - earlier.pathSearches,
        )

        val total: Long get() = nanos.sum()
    }
}
