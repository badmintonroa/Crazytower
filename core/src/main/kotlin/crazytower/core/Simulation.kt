package crazytower.core

import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.World
import com.github.quillraven.fleks.configureWorld
import crazytower.core.ecs.Idle
import crazytower.core.ecs.IdleSystem
import crazytower.core.ecs.MovementSystem
import crazytower.core.ecs.PathfindingSystem
import crazytower.core.ecs.Person
import crazytower.core.ecs.Position
import crazytower.core.ecs.StuckSystem
import crazytower.core.ecs.Visitor
import crazytower.core.ecs.VisitorSpawnSystem
import crazytower.core.ecs.VisitorSystem
import crazytower.core.nav.NavGraph
import kotlin.random.Random

/** Outcome of a build command, shown to the player when it fails. */
data class BuildResult(val room: Room?, val reason: String = "") {
    val ok: Boolean get() = room != null
}

/**
 * The whole game state and rules, independent of rendering. The game front end only
 * sends commands (build, demolish) and calls [step]; everything else happens in the Fleks world.
 */
class Simulation(seed: Long = 1L, startMoney: Long = START_MONEY) {
    val random = Random(seed)
    val clock = GameClock()
    val tower = Tower()
    val nav = NavGraph(tower)
    val news = NewsFeed()
    var money: Long = startMoney
        private set

    val world: World = configureWorld(entityCapacity = 2048) {
        injectables { add(this@Simulation) }
        systems {
            add(IdleSystem())
            add(VisitorSpawnSystem())
            add(VisitorSystem())
            add(PathfindingSystem())
            add(MovementSystem())
            add(StuckSystem())
        }
    }

    /** Advances the simulation by [minutes] game minutes in fixed steps. */
    fun step(minutes: Double) {
        var left = minutes
        while (left > 0) {
            val dt = minOf(left, STEP_MINUTES)
            clock.advance(dt)
            world.update(dt.toFloat())
            left -= dt
        }
    }

    fun checkPlace(type: RoomType, floor: Int, x: Int): PlaceCheck {
        if (money < type.cost) return PlaceCheck.no("Zu wenig Geld")
        return tower.canPlace(type, floor, x)
    }

    fun build(type: RoomType, floor: Int, x: Int): BuildResult {
        val check = checkPlace(type, floor, x)
        if (!check.ok) return BuildResult(null, check.reason)
        money -= type.cost
        val room = tower.place(type, floor, x)
        if (type.role == RoomRole.LOBBY && tower.allRooms.count { it.type.role == RoomRole.LOBBY } == 1) {
            news.post("Die erste Lobby ist offen. Ein Mann fragt, ob es hier zum Bahnhof geht.", Loc(floor, room.centerX))
        }
        return BuildResult(room)
    }

    /** Money back when a room is torn down. */
    fun refundFor(room: Room): Long = room.type.cost / 4

    /** Tears a room down and returns the refund. */
    fun demolish(roomId: Int): Long {
        val room = tower.remove(roomId) ?: return 0
        val refund = refundFor(room)
        money += refund
        return refund
    }

    /** People who live or work in the tower. Visitors don't count. */
    val population: Int get() = 0

    /** Current tower stage, 1 to 5. */
    val stage: Int get() = 1

    /** Progress towards the next stage, 0 to 1. */
    val stageProgress: Double get() = population / 300.0

    /** Street exit closest to [x]: people leave the tower to the left or right edge. */
    fun streetExit(x: Float): Loc = Loc(GROUND_FLOOR, if (x < tower.width / 2f) 0f else tower.width - 0.01f)

    fun spawnVisitor(): Entity {
        val fromLeft = random.nextBoolean()
        val start = if (fromLeft) 0f else tower.width - 0.01f
        val look = VISITOR_LOOKS[random.nextInt(VISITOR_LOOKS.size)]
        return world.entity {
            it += Position(GROUND_FLOOR, start, facingLeft = !fromLeft)
            it += Person(Names.random(random), look)
            it += Visitor(stopsLeft = 1 + random.nextInt(3))
        }
    }

    /** Called by the movement system when a person reached its goal. */
    fun onArrived(entity: Entity) = with(world) {
        val visitor = entity.getOrNull(Visitor)
        when {
            visitor != null && visitor.leaving -> entity.remove()
            visitor != null -> entity.configure { it += Idle(clock.totalMinutes + 3 + random.nextInt(8)) }
        }
    }

    /** Called when a person could not find a way to its goal for a while. */
    fun onGaveUp(entity: Entity) = with(world) {
        val visitor = entity.getOrNull(Visitor) ?: return@with
        if (visitor.leaving) entity.remove() else visitor.stopsLeft = 0
    }

    companion object {
        const val START_MONEY = 250_000L
        const val STEP_MINUTES = 0.1
        val VISITOR_LOOKS = listOf("tourist", "suit", "courier", "casual")
    }
}
