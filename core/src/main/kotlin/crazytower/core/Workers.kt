package crazytower.core

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport

/**
 * Helper threads for the simulation, so a big tower uses more than one CPU core.
 *
 * The systems split only work into chunks that does not touch anything shared: path searches,
 * and the elevator shafts, one each. Everything that changes the world (components,
 * money, random numbers, news) happens afterwards on the calling thread, in the same order as
 * without helpers. The game therefore plays exactly the same with any number of threads.
 *
 * The number of threads follows the CPU cores; `-Dcrazytower.threads=1` (or the environment
 * variable CRAZYTOWER_THREADS) switches the helpers off.
 */
object Workers {
    /** Threads that work on a [chunks] call, the calling thread included. Change it only between ticks. */
    @Volatile
    var threads: Int = defaultThreads()
        set(value) {
            field = value.coerceIn(1, MAX_THREADS)
        }

    /** Worker numbers in [chunks] stay below this; scratch space per worker can be sized with it. */
    const val MAX_THREADS = 16

    private fun defaultThreads(): Int {
        val wanted = System.getProperty("crazytower.threads")?.toIntOrNull()
            ?: System.getenv("CRAZYTOWER_THREADS")?.toIntOrNull()
        // one core stays free for the rest of the game (sound, garbage collection); more than 8 hardly helps
        return (wanted ?: (Runtime.getRuntime().availableProcessors() - 1).coerceAtMost(8)).coerceIn(1, MAX_THREADS)
    }

    /**
     * One [chunks] call. Helpers take chunks until none are left; the caller counts the
     * finished ones, so it doesn't wait for a helper that came too late to get one.
     */
    private class Job(
        val count: Int,
        val size: Int,
        val chunkCount: Int,
        val work: (worker: Int, from: Int, until: Int) -> Unit,
    ) {
        val next = AtomicInteger(0)
        val finished = AtomicInteger(0)
        val failure = AtomicReference<Throwable?>(null)

        fun drain(worker: Int) {
            while (true) {
                val c = next.getAndIncrement()
                if (c >= chunkCount) return
                try {
                    if (failure.get() == null) {
                        val from = c * size
                        work(worker, from, minOf(count, from + size))
                    }
                } catch (t: Throwable) {
                    failure.compareAndSet(null, t)
                } finally {
                    finished.incrementAndGet()
                }
            }
        }
    }

    /**
     * A helper thread. A tick hands out work every few hundred microseconds, too often for
     * waking sleeping threads each time, so a helper keeps looking for new work for a moment
     * ([SPIN_NANOS]) and only then goes to sleep until the next [chunks] call wakes it.
     */
    private class Helper(val worker: Int) : Thread("sim-worker-$worker") {
        @Volatile
        var sleeping = false

        init {
            isDaemon = true
        }

        override fun run() {
            var seen = generation.get()
            while (true) {
                var now = generation.get()
                if (now == seen) {
                    val spinUntil = System.nanoTime() + SPIN_NANOS
                    while (now == seen && System.nanoTime() < spinUntil) {
                        onSpinWait()
                        now = generation.get()
                    }
                    while (now == seen) {
                        sleeping = true
                        now = generation.get()
                        if (now == seen) LockSupport.park(this)
                        sleeping = false
                        now = generation.get()
                    }
                }
                seen = now
                val published = current ?: continue
                if (worker < published.threads) published.job.drain(worker)
            }
        }
    }

    /** The job of the latest [chunks] call, and how many threads work on it (helpers 1 until [threads]). */
    private class Published(val job: Job, val threads: Int)

    @Volatile
    private var current: Published? = null
    private val generation = AtomicInteger(0)
    private val helperThreads = ArrayList<Helper>()

    private fun ensureHelpers(count: Int) {
        while (helperThreads.size < count) helperThreads += Helper(helperThreads.size + 1).also { it.start() }
    }

    /**
     * Splits `0 until count` into consecutive chunks of at least [minPerChunk] items and runs
     * [work] (worker, from, until) for each of them. The calling thread works along with the
     * helpers; whoever is free takes the next chunk, so uneven chunks even out. [worker] runs
     * from 0 until [threads] and is the same for all chunks one thread does in this call, for
     * scratch space per thread; the calling thread is always worker 0. Returns when all chunks
     * are done. Small jobs stay on the calling thread. The calling thread could do all of the
     * work alone, so a call from a second thread at the same time is still right, only slower.
     *
     * [work] may only change data that belongs to its own items, and must not depend on which
     * worker does a chunk or in which order the chunks are done.
     */
    fun chunks(count: Int, minPerChunk: Int, work: (worker: Int, from: Int, until: Int) -> Unit) {
        if (count <= 0) return
        val min = minPerChunk.coerceAtLeast(1)
        val helpers = minOf(threads, (count + min - 1) / min) - 1
        if (helpers <= 0) {
            work(0, 0, count)
            return
        }
        ensureHelpers(helpers)
        // a few chunks per thread, so a thread that got the quick ones takes over more
        val parts = CHUNKS_PER_THREAD * (helpers + 1)
        val size = maxOf(min, (count + parts - 1) / parts)
        val job = Job(count, size, (count + size - 1) / size, work)
        current = Published(job, helpers + 1)
        generation.incrementAndGet()
        for (i in 0 until helpers) {
            val helper = helperThreads[i]
            if (helper.sleeping) LockSupport.unpark(helper)
        }
        job.drain(0)
        // the last chunks may still be running on helpers
        while (job.finished.get() < job.chunkCount) Thread.onSpinWait()
        current = null
        job.failure.get()?.let { throw it }
    }

    /** How long a helper keeps looking for work before it sleeps: a little longer than a tick of a big tower. */
    private const val SPIN_NANOS = 1_000_000L

    private const val CHUNKS_PER_THREAD = 4
}
