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
     * The result never contains a zero-length walk. Nobody climbs more than
     * [NavCosts.MAX_STAIRS_FLOORS] floors on foot in a row; an elevator or teleporter ride in
     * between lets them catch their breath.
     */
    fun findPath(from: Loc, to: Loc): List<Leg>? {
        ensureBuilt()
        val startKey = spanKey(from) ?: return null
        val goalKey = spanKey(to) ?: return null
        if (startKey == goalKey) return listOfNotNull(walk(from, to.x))

        // A search state is a node plus the floors climbed on foot since the last ride.
        // Node ids >= 0 are graph nodes; GOAL is a virtual node standing for `to`.
        val climbs = NavCosts.MAX_STAIRS_FLOORS + 1
        fun state(node: Int, climbed: Int) = node * climbs + climbed
        fun nodeOf(state: Int) = state / climbs
        fun climbedOf(state: Int) = state % climbs
        val goal = -1
        val start = -2
        val best = HashMap<Int, Float>()
        val cameFrom = HashMap<Int, Pair<Int, Link?>>() // state -> (previous state or START, link used)
        val open = PriorityQueue<Pair<Int, Float>>(compareBy { it.second })

        fun h(loc: Loc) = abs(loc.floor - to.floor) * NavCosts.FASTEST_PER_FLOOR
        fun relax(state: Int, previous: Int, link: Link?, cost: Float, loc: Loc) {
            if (cost < (best[state] ?: Float.MAX_VALUE)) {
                best[state] = cost
                cameFrom[state] = previous to link
                open += state to cost + h(loc)
            }
        }

        for (n in nodesBySpan[startKey].orEmpty()) relax(state(n.id, 0), start, null, walkCost(from.x, n.loc.x), n.loc)

        while (open.isNotEmpty()) {
            val (current, _) = open.poll()
            if (current == goal) break
            val cost = best.getValue(current)
            val node = nodes[nodeOf(current)]
            val climbed = climbedOf(current)
            if (node.spanKey == goalKey) relax(goal, current, null, cost + walkCost(node.loc.x, to.x), to)
            for ((next, link) in outgoing[node.id].orEmpty()) {
                val leg = link.leg
                val nowClimbed = if (leg is Leg.Stairs) climbed + abs(leg.toFloor - leg.fromFloor) else 0
                if (nowClimbed > NavCosts.MAX_STAIRS_FLOORS) continue
                val arrival = nodes[next]
                val afterLink = cost + link.cost()
                val arrived = state(next, nowClimbed)
                relax(arrived, current, link, afterLink, arrival.loc)
                // walking from the arrival point to the other nodes of that span
                for (other in nodesBySpan[arrival.spanKey].orEmpty()) {
                    if (other.id != next && other.id != node.id) {
                        relax(state(other.id, nowClimbed), arrived, null, afterLink + walkCost(arrival.loc.x, other.loc.x), other.loc)
                    }
                }
            }
        }
        if (goal !in cameFrom) return null

        // Rebuild the chain of states, then turn it into legs.
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
        for ((state, link) in chain) {
            val loc = if (state == goal) to else nodes[nodeOf(state)].loc
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
