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
    private val spansByFloor = HashMap<Int, List<IntRange>>()

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
        val states = nodes.size * CLIMBS + 1
        best = FloatArray(states)
        cameFrom = IntArray(states)
        cameBy = arrayOfNulls(states)
        seen = IntArray(states)
        closed = IntArray(states)
        generation = 0
    }

    private fun spans(floor: Int) = spansByFloor.getOrPut(floor) { tower.walkableSpans(floor) }

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

    /** Nanoseconds spent in [findPath] and how many searches there were, for the profiler. */
    var searchNanos: Long = 0
        private set
    var searches: Long = 0
        private set

    /**
     * Finds the cheapest route from [from] to [to], or null if there is none.
     * The result never contains a zero-length walk. Nobody climbs more than
     * [NavCosts.MAX_STAIRS_FLOORS] floors on foot in a row; an elevator or teleporter ride in
     * between lets them catch their breath.
     */
    fun findPath(from: Loc, to: Loc): List<Leg>? {
        val started = System.nanoTime()
        try {
            return search(from, to)
        } finally {
            searchNanos += System.nanoTime() - started
            searches++
        }
    }

    // Search state, reused between searches. A state is a node plus the floors climbed on foot
    // since the last ride: node * CLIMBS + climbed; the last state stands for the goal. An entry
    // only counts when its [seen] stamp is the current [generation], so nothing has to be cleared.
    private var best = FloatArray(1)
    private var cameFrom = IntArray(1) // previous state, or START
    private var cameBy = arrayOfNulls<Link>(1) // link used to get here, null for walking
    private var seen = IntArray(1)
    private var closed = IntArray(1)
    private var generation = 0
    private val open = StateHeap()

    private fun search(from: Loc, to: Loc): List<Leg>? {
        ensureBuilt()
        val startKey = spanKey(from) ?: return null
        val goalKey = spanKey(to) ?: return null
        if (startKey == goalKey) return listOfNotNull(walk(from, to.x))

        val goal = nodes.size * CLIMBS
        if (++generation == Int.MAX_VALUE) {
            seen.fill(0); closed.fill(0); generation = 1
        }
        val gen = generation
        open.clear()

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

    private fun walk(from: Loc, toX: Float): Leg? =
        if (abs(from.x - toX) < 0.01f) null else Leg.Walk(from.floor, toX)

    private fun walkCost(fromX: Float, toX: Float) = abs(fromX - toX) / NavCosts.WALK_SPEED

    private companion object {
        const val CLIMBS = NavCosts.MAX_STAIRS_FLOORS + 1
        const val START = -1
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
