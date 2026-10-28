package crazytower.core.nav

import crazytower.core.Loc
import crazytower.core.Tower
import java.util.PriorityQueue
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
    private val outgoing = HashMap<Int, MutableList<Pair<Int, Link>>>()
    private val nodesBySpan = HashMap<Long, MutableList<Node>>()
    private val spansByFloor = HashMap<Int, List<IntRange>>()

    private fun ensureBuilt() {
        if (builtVersion == tower.version) return
        builtVersion = tower.version
        nodes.clear(); outgoing.clear(); nodesBySpan.clear(); spansByFloor.clear()
        for (provider in providers) {
            for (link in provider.links(tower)) {
                val a = nodeFor(link.from) ?: continue
                val b = nodeFor(link.to) ?: continue
                outgoing.getOrPut(a.id) { ArrayList() } += b.id to link
            }
        }
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

    /**
     * Finds the cheapest route from [from] to [to], or null if there is none.
     * The result never contains a zero-length walk.
     */
    fun findPath(from: Loc, to: Loc): List<Leg>? {
        ensureBuilt()
        val startKey = spanKey(from) ?: return null
        val goalKey = spanKey(to) ?: return null
        if (startKey == goalKey) return listOfNotNull(walk(from, to.x))

        // Node ids >= 0 are graph nodes; GOAL is a virtual node standing for `to`.
        val goal = -1
        val best = HashMap<Int, Float>()
        val cameFrom = HashMap<Int, Pair<Int, Link?>>() // node -> (previous node or START, link used)
        val start = -2
        val open = PriorityQueue<Pair<Int, Float>>(compareBy { it.second })

        fun h(loc: Loc) = abs(loc.floor - to.floor) * NavCosts.FASTEST_PER_FLOOR
        fun relax(node: Int, previous: Int, link: Link?, cost: Float, loc: Loc) {
            if (cost < (best[node] ?: Float.MAX_VALUE)) {
                best[node] = cost
                cameFrom[node] = previous to link
                open += node to cost + h(loc)
            }
        }

        for (n in nodesBySpan[startKey].orEmpty()) relax(n.id, start, null, walkCost(from.x, n.loc.x), n.loc)

        while (open.isNotEmpty()) {
            val (current, _) = open.poll()
            if (current == goal) break
            val cost = best.getValue(current)
            val node = nodes[current]
            if (node.spanKey == goalKey) relax(goal, current, null, cost + walkCost(node.loc.x, to.x), to)
            for ((next, link) in outgoing[current].orEmpty()) {
                val arrival = nodes[next]
                val afterLink = cost + link.cost()
                relax(next, current, link, afterLink, arrival.loc)
                // walking from the arrival point to the other nodes of that span
                for (other in nodesBySpan[arrival.spanKey].orEmpty()) {
                    if (other.id != next && other.id != current) {
                        relax(other.id, next, null, afterLink + walkCost(arrival.loc.x, other.loc.x), other.loc)
                    }
                }
            }
        }
        if (goal !in cameFrom) return null

        // Rebuild the chain of nodes, then turn it into legs.
        val chain = ArrayList<Pair<Int, Link?>>()
        var cursor = goal
        while (cursor != start) {
            val (previous, link) = cameFrom.getValue(cursor)
            chain += cursor to link
            cursor = previous
        }
        chain.reverse()
        val legs = ArrayList<Leg>()
        var position = from
        for ((node, link) in chain) {
            val loc = if (node == goal) to else nodes[node].loc
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
}
