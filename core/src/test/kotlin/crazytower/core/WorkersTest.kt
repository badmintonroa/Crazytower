package crazytower.core

import crazytower.core.ecs.Goal
import crazytower.core.ecs.Person
import crazytower.core.ecs.Riding
import crazytower.core.ecs.Route
import crazytower.core.ecs.Schedule
import crazytower.core.ecs.Stuck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkersTest {
    /** Everything that the helper threads could get wrong, as one long text. */
    private fun fingerprint(sim: Simulation): String = buildString {
        append("money=${sim.money} population=${sim.population} next=${sim.random.nextLong()}\n")
        with(sim.world) {
            family { all(Person) }.forEach { e ->
                val pos = e[crazytower.core.ecs.Position]
                append("${e.id}:${pos.floor}:${pos.x.toRawBits()}:${pos.facingLeft}")
                e.getOrNull(Route)?.let { append(":r${it.legs.size}/${it.legProgress.toRawBits()}") }
                e.getOrNull(Goal)?.let { append(":g${it.target}") }
                e.getOrNull(Riding)?.let { append(":c${it.carId}") }
                if (e has Stuck) append(":s")
                e.getOrNull(Schedule)?.let { append(":m${it.mood.toRawBits()}") }
                append('\n')
            }
        }
        for (car in sim.elevators.cars.values) {
            for (cabin in car.cabins) append("car${car.id}:${cabin.position.toRawBits()}:${cabin.direction}:${cabin.riders.size}\n")
        }
    }

    private fun run(threads: Int, number: Int, hours: Int): String {
        val before = Workers.threads
        Workers.threads = threads
        try {
            val sim = TestTower.create(number, seed = 7L)
            repeat(hours) { sim.step(60.0) }
            return fingerprint(sim)
        } finally {
            Workers.threads = before
        }
    }

    @Test
    fun theGameIsTheSameWithAnyNumberOfThreads() {
        // the maximum tower through the morning rush, when elevators, paths and walking are busiest
        val one = run(1, TestTower.MAXIMUM, 4)
        for (threads in listOf(2, 4, 7)) {
            assertEquals(one, run(threads, TestTower.MAXIMUM, 4), "$threads threads")
        }
    }

    @Test
    fun chunksCoverEverythingOnce() {
        val before = Workers.threads
        Workers.threads = 4
        try {
            for (count in listOf(0, 1, 3, 17, 1000)) {
                val hits = IntArray(count)
                val workers = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
                Workers.chunks(count, 2) { worker, from, until ->
                    workers += worker
                    for (i in from until until) hits[i]++
                }
                assertTrue(hits.all { it == 1 }, "count $count")
                assertTrue(workers.all { it in 0 until 4 })
            }
        } finally {
            Workers.threads = before
        }
    }

    @Test
    fun aFailureOnAHelperReachesTheCaller() {
        val before = Workers.threads
        Workers.threads = 4
        try {
            val thrown = runCatching {
                Workers.chunks(100, 1) { _, from, _ -> if (from >= 50) error("kaputt") }
            }.exceptionOrNull()
            assertEquals("kaputt", thrown?.message)
        } finally {
            Workers.threads = before
        }
    }
}
