package crazytower.core.nav

import crazytower.core.Loc
import crazytower.core.Tower
import kotlin.math.abs
import kotlin.math.floor

/**
 * A connection between two floors that people can use, e.g. a staircase or an elevator stop pair.
 * [cost] is evaluated at search time so waiting times can depend on the current queues.
 */
class Link(
    val from: Loc,
    val to: Loc,
    val leg: Leg,
    val cost: () -> Float,
)

/** Supplies the vertical links of a tower. Stairs and elevators register themselves here. */
fun interface LinkProvider {
    fun links(tower: Tower): List<Link>
}

/**
 * Navigation graph of the tower. Nodes are the ends of vertical links; two nodes on the same
 * floor are connected when they lie in the same walkable span. Paths are found with A*.
 * The graph is rebuilt lazily whenever the tower changes.
 */
class NavGraph(private val tower: Tower, private val providers: List<LinkProvider> = emptyList()) {
    private class Node(val id: Int, val loc: Loc, val spanKey: Long)

    private var builtVersion = -1
    private val nodes = ArrayList<Node>()
    private val nodesBySpan = HashMap<Long, MutableList<Node>>()
    // filled lazily, also by helper threads during [findPaths]
    private val spansByFloor = java.util.concurrent.ConcurrentHashMap<Int, List<IntRange>>()

    // The graph again in flat arrays, so the search does without maps and boxed numbers.
    // Links leaving node n are outTo/outLink[outStart[n] until outStart[n + 1]].
    private var outStart = IntArray(1)
    private var outTo = IntArray(0)
    private var outLink = emptyArray<Link>()

    /** For every node, the other nodes in its walkable span. */
    private var peers = emptyArray<IntArray>()

    private fun ensureBuilt() {
        if (builtVersion == tower.version) return
        builtVersion = tower.version
        nodes.clear(); nodesBySpan.clear(); spansByFloor.clear()
        val edges = ArrayList<Triple<Int, Int, Link>>()
        for (provider in providers) {
            for (link in provider.links(tower)) {
                val a = nodeFor(link.from) ?: continue
                val b = nodeFor(link.to) ?: continue
                edges += Triple(a.id, b.id, link)
            }
        }
        // order by start node, keeping the providers' order within a node
        edges.sortBy { it.first }
        outStart = IntArray(nodes.size + 1)
        for ((a, _, _) in edges) outStart[a + 1]++
        for (i in 1..nodes.size) outStart[i] += outStart[i - 1]
        outTo = IntArray(edges.size) { edges[it].second }
        outLink = Array(edges.size) { edges[it].third }
        peers = Array(nodes.size) { n -> nodesBySpan.getValue(nodes[n].spanKey).map { it.id }.toIntArray() }
        stateCount = nodes.size * CLIMBS + 1
    }

    /** Search states of the current graph: node * CLIMBS + climbed, and one for the goal. */
    private var stateCount = 1

    private fun spans(floor: Int) = spansByFloor.computeIfAbsent(floor) { tower.walkableSpans(floor) }

    private fun spanKey(loc: Loc): Long? {
        val segment = floor(loc.x).toInt().coerceIn(0, tower.width - 1)
        val index = spans(loc.floor).indexOfFirst { segment in it }
        if (index < 0) return null
        return (loc.floor.toLong() shl 32) or index.toLong()
    }

    private fun nodeFor(loc: Loc): Node? {
        val key = spanKey(loc) ?: return null
        val list = nodesBySpan.getOrPut(key) { ArrayList() }
        list.firstOrNull { it.loc == loc }?.let { return it }
        val node = Node(nodes.size, loc, key)
        nodes += node
        list += node
        return node
    }

    /** Returns true when [loc] stands on walkable ground. */
    fun isWalkable(loc: Loc): Boolean {
        ensureBuilt()
        return spanKey(loc) != null
    }

    /** Nanoseconds spent in path searches (summed over all threads) and how many there were, for the profiler. */
    val searchNanos: Long get() = main.nanos + helpers.sumOf { it?.nanos ?: 0L }
    val searches: Long get() = main.count + helpers.sumOf { it?.count ?: 0L }

    /** Search scratch space of the calling thread, and of each helper of [findPaths]. */
    private val main = Searcher()
    private val helpers = arrayOfNulls<Searcher>(crazytower.core.Workers.MAX_THREADS)

    /**
     * Finds the cheapest route from [from] to [to], or null if there is none.
     * The result never contains a zero-length walk. Nobody climbs more than
     * [NavCosts.MAX_STAIRS_FLOORS] floors on foot in a row; an elevator ride in
     * between lets them catch their breath.
     */
    fun findPath(from: Loc, to: Loc): List<Leg>? {
        ensureBuilt()
        return main.timed { search(from, to) }
    }

    /**
     * [findPath] for `0 until count` at once, from `from(i)` to `to(i)` into `out[i]`, spread
     * over the [crazytower.core.Workers]. [from] and [to] are called on helper threads, so they
     * may only read. Every result is the same as from [findPath].
     */
    fun findPaths(count: Int, from: (Int) -> Loc, to: (Int) -> Loc, out: Array<List<Leg>?>) {
        ensureBuilt()
        crazytower.core.Workers.chunks(count, PATHS_PER_CHUNK) { worker, a, b ->
            val searcher = if (worker == 0) main else helpers[worker] ?: Searcher().also { helpers[worker] = it }
            for (i in a until b) out[i] = searcher.timed { search(from(i), to(i)) }
        }
    }

    /**
     * Minutes the cheapest route from [from] to [to] takes, waiting for elevators included,
     * or null if there is none. Rooms whose way from the street takes too long find no tenants.
     */
    fun travelMinutes(from: Loc, to: Loc): Float? = findPath(from, to)?.let { main.lastCost }

    /**
     * [travelMinutes] from [from] to many goals with one search instead of one per goal: the
     * returned function answers for any goal. Use it right away, it does not notice when the
     * tower changes or the queues move.
     */
    fun travelMinutesFrom(from: Loc): (Loc) -> Float? {
        ensureBuilt()
        val startKey = spanKey(from) ?: return main.timed { { null } }
        val reached = main.timed { explore(from, startKey) }
        return { to ->
            val goalKey = spanKey(to)
            when (goalKey) {
                null -> null
                startKey -> walkCost(from.x, to.x)
                else -> nodesBySpan[goalKey].orEmpty().minOfOrNull { reached[it.id] + walkCost(it.loc.x, to.x) }?.takeIf { it.isFinite() }
            }
        }
    }

    /**
     * Whether [to] could be reached from [from] if people climbed any number of stairs, for
     * telling the player why [findPath] found nothing.
     */
    fun connectedByAnyStairs(from: Loc, to: Loc): Boolean {
        ensureBuilt()
        val startKey = spanKey(from) ?: return false
        val goalKey = spanKey(to) ?: return false
        if (startKey == goalKey) return true
        val visited = BooleanArray(nodes.size)
        val queue = ArrayDeque<Int>()
        for (n in nodesBySpan[startKey].orEmpty()) { visited[n.id] = true; queue += n.id }
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (nodes[id].spanKey == goalKey) return true
            for (e in outStart[id] until outStart[id + 1]) {
                for (other in peers[outTo[e]]) if (!visited[other]) { visited[other] = true; queue += other }
            }
        }
        return false
    }

    /** Whether any staircase or elevator stop can be walked to from [loc]. */
    fun hasLinksNear(loc: Loc): Boolean {
        ensureBuilt()
        val key = spanKey(loc) ?: return false
        return nodesBySpan[key].orEmpty().isNotEmpty()
    }

    /**
     * Scratch space of the searches, reused between them. One thread uses one searcher at a
     * time; the graph itself is only read while searching.
     *
     * A state is a node plus the floors climbed on foot since the last ride:
     * node * CLIMBS + climbed; the last state stands for the goal. An entry only counts when
     * its [seen] stamp is the current [generation], so nothing has to be cleared.
     */
    private inner class Searcher {
        var nanos = 0L
        var count = 0L

        /** Cost of the path the last successful [search] found. */
        var lastCost = 0f

        private var best = FloatArray(1)
        private var cameFrom = IntArray(1) // previous state, or START
        private var cameBy = arrayOfNulls<Link>(1) // link used to get here, null for walking
        private var seen = IntArray(1)
        private var closed = IntArray(1)
        private var generation = 0
        private val open = StateHeap()

        inline fun <T> timed(block: Searcher.() -> T): T {
            val started = System.nanoTime()
            try {
                return block()
            } finally {
                nanos += System.nanoTime() - started
                count++
            }
        }

        /** Starts a new search: sizes the arrays to the graph and moves on the [generation]. */
        private fun begin(): Int {
            if (best.size != stateCount) {
                best = FloatArray(stateCount)
                cameFrom = IntArray(stateCount)
                cameBy = arrayOfNulls(stateCount)
                seen = IntArray(stateCount)
                closed = IntArray(stateCount)
                generation = 0
            }
            if (++generation == Int.MAX_VALUE) {
                seen.fill(0); closed.fill(0); generation = 1
            }
            open.clear()
            return generation
        }

        /** Dijkstra from [from] to every node; the cheapest cost per node, infinite where there is no way. */
        fun explore(from: Loc, startKey: Long): FloatArray {
            val gen = begin()
            // the graph in locals: the search loop reads them all the time
            val nodes = nodes
            val outStart = outStart
            val outTo = outTo
            val outLink = outLink
            val peers = peers
            fun relax(state: Int, cost: Float) {
                if (seen[state] != gen || cost < best[state]) {
                    seen[state] = gen
                    best[state] = cost
                    open.push(state, cost)
                }
            }
            for (n in nodesBySpan[startKey].orEmpty()) relax(n.id * CLIMBS, walkCost(from.x, n.loc.x))
            val reached = FloatArray(nodes.size) { Float.POSITIVE_INFINITY }
            while (open.isNotEmpty()) {
                val current = open.pop()
                if (closed[current] == gen) continue
                closed[current] = gen
                val cost = best[current]
                val nodeId = current / CLIMBS
                if (cost < reached[nodeId]) reached[nodeId] = cost
                val climbed = current % CLIMBS
                for (e in outStart[nodeId] until outStart[nodeId + 1]) {
                    val link = outLink[e]
                    val next = outTo[e]
                    val leg = link.leg
                    val nowClimbed = if (leg is Leg.Stairs) climbed + abs(leg.toFloor - leg.fromFloor) else 0
                    if (nowClimbed > NavCosts.MAX_STAIRS_FLOORS) continue
                    val arrival = nodes[next]
                    val afterLink = cost + link.cost()
                    relax(next * CLIMBS + nowClimbed, afterLink)
                    for (other in peers[next]) {
                        if (other != next && other != nodeId) relax(other * CLIMBS + nowClimbed, afterLink + walkCost(arrival.loc.x, nodes[other].loc.x))
                    }
                }
            }
            return reached
        }

        fun search(from: Loc, to: Loc): List<Leg>? {
            val startKey = spanKey(from) ?: return null
            val goalKey = spanKey(to) ?: return null
            if (startKey == goalKey) {
                lastCost = walkCost(from.x, to.x)
                return listOfNotNull(walk(from, to.x))
            }

            val gen = begin()
            // the graph in locals: the search loop reads them all the time
            val nodes = nodes
            val outStart = outStart
            val outTo = outTo
            val outLink = outLink
            val peers = peers
            val goal = nodes.size * CLIMBS

            fun h(floor: Int) = abs(floor - to.floor) * NavCosts.FASTEST_PER_FLOOR
            fun relax(state: Int, previous: Int, link: Link?, cost: Float, floor: Int) {
                if (seen[state] != gen || cost < best[state]) {
                    seen[state] = gen
                    best[state] = cost
                    cameFrom[state] = previous
                    cameBy[state] = link
                    open.push(state, cost + h(floor))
                }
            }

            for (n in nodesBySpan[startKey].orEmpty()) relax(n.id * CLIMBS, START, null, walkCost(from.x, n.loc.x), n.loc.floor)

            while (open.isNotEmpty()) {
                val current = open.pop()
                if (current == goal) break
                if (closed[current] == gen) continue // already expanded with a better cost
                closed[current] = gen
                val cost = best[current]
                val nodeId = current / CLIMBS
                val node = nodes[nodeId]
                val climbed = current % CLIMBS
                if (node.spanKey == goalKey) relax(goal, current, null, cost + walkCost(node.loc.x, to.x), to.floor)
                for (e in outStart[nodeId] until outStart[nodeId + 1]) {
                    val link = outLink[e]
                    val next = outTo[e]
                    val leg = link.leg
                    val nowClimbed = if (leg is Leg.Stairs) climbed + abs(leg.toFloor - leg.fromFloor) else 0
                    if (nowClimbed > NavCosts.MAX_STAIRS_FLOORS) continue
                    val arrival = nodes[next]
                    val afterLink = cost + link.cost()
                    val arrived = next * CLIMBS + nowClimbed
                    relax(arrived, current, link, afterLink, arrival.loc.floor)
                    // walking from the arrival point to the other nodes of that span
                    for (other in peers[next]) {
                        if (other != next && other != nodeId) {
                            val loc = nodes[other].loc
                            relax(other * CLIMBS + nowClimbed, arrived, null, afterLink + walkCost(arrival.loc.x, loc.x), loc.floor)
                        }
                    }
                }
            }
            if (seen[goal] != gen) return null
            lastCost = best[goal]

            // Rebuild the chain of states, then turn it into legs.
            val chain = ArrayList<Int>()
            var cursor = goal
            while (cursor != START) {
                chain += cursor
                cursor = cameFrom[cursor]
            }
            chain.reverse()
            val legs = ArrayList<Leg>()
            var position = from
            for (state in chain) {
                val loc = if (state == goal) to else nodes[state / CLIMBS].loc
                val link = cameBy[state]
                if (link != null) {
                    legs += link.leg
                } else {
                    walk(position, loc.x)?.let { legs += it }
                }
                position = loc
            }
            return legs
        }
    }

    private fun walk(from: Loc, toX: Float): Leg? =
        if (abs(from.x - toX) < 0.01f) null else Leg.Walk(from.floor, toX)

    private fun walkCost(fromX: Float, toX: Float) = abs(fromX - toX) / NavCosts.WALK_SPEED

    private companion object {
        const val CLIMBS = NavCosts.MAX_STAIRS_FLOORS + 1
        const val START = -1

        /** A search takes tens of microseconds, so even a single one is worth handing to a helper. */
        const val PATHS_PER_CHUNK = 1
    }
}

/** A binary min-heap of search states by priority, without boxing. */
private class StateHeap {
    private var states = IntArray(256)
    private var priorities = FloatArray(256)
    private var size = 0

    fun clear() { size = 0 }
    fun isNotEmpty() = size > 0

    fun push(state: Int, priority: Float) {
        if (size == states.size) {
            states = states.copyOf(size * 2)
            priorities = priorities.copyOf(size * 2)
        }
        var i = size++
        while (i > 0) {
            val parent = (i - 1) / 2
            if (priorities[parent] <= priority) break
            states[i] = states[parent]
            priorities[i] = priorities[parent]
            i = parent
        }
        states[i] = state
        priorities[i] = priority
    }

    fun pop(): Int {
        val top = states[0]
        size--
        if (size > 0) {
            val state = states[size]
            val priority = priorities[size]
            var i = 0
            while (true) {
                var child = 2 * i + 1
                if (child >= size) break
                if (child + 1 < size && priorities[child + 1] < priorities[child]) child++
                if (priorities[child] >= priority) break
                states[i] = states[child]
                priorities[i] = priorities[child]
                i = child
            }
            states[i] = state
            priorities[i] = priority
        }
        return top
    }
}
