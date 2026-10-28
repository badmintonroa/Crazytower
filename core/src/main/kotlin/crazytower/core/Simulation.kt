package crazytower.core

import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.World
import com.github.quillraven.fleks.configureWorld
import crazytower.core.ecs.Activity
import crazytower.core.ecs.Away
import crazytower.core.ecs.EconomySystem
import crazytower.core.ecs.ElevatorSystem
import crazytower.core.ecs.Goal
import crazytower.core.ecs.Member
import crazytower.core.ecs.Riding
import crazytower.core.ecs.Schedule
import crazytower.core.ecs.ScheduleSystem
import crazytower.core.ecs.Idle
import crazytower.core.ecs.IdleSystem
import crazytower.core.ecs.MovementSystem
import crazytower.core.ecs.PathfindingSystem
import crazytower.core.ecs.Person
import crazytower.core.ecs.Position
import crazytower.core.ecs.RouteRefreshSystem
import crazytower.core.ecs.StuckSystem
import crazytower.core.ecs.TenantSystem
import crazytower.core.ecs.Visitor
import crazytower.core.ecs.VisitorSpawnSystem
import crazytower.core.ecs.VisitorSystem
import crazytower.core.nav.NavGraph
import crazytower.core.transport.Elevators
import crazytower.core.transport.StairsLinks
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
    val elevators = Elevators(tower)
    val nav = NavGraph(tower, listOf(StairsLinks, elevators))
    val news = NewsFeed()
    var money: Long = startMoney
        private set

    val world: World = configureWorld(entityCapacity = 2048) {
        injectables { add(this@Simulation) }
        systems {
            add(IdleSystem())
            add(TenantSystem())
            add(EconomySystem())
            add(ScheduleSystem())
            add(VisitorSpawnSystem())
            add(VisitorSystem())
            add(RouteRefreshSystem())
            add(PathfindingSystem())
            add(ElevatorSystem())
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

    /** Increases whenever a room changes its looks without the tower changing, e.g. a new tenant. */
    var looksVersion: Int = 0
        private set

    fun checkPlace(type: RoomType, floor: Int, x: Int): PlaceCheck {
        if (type.minStage > stage) return PlaceCheck.no("Erst ab Stufe ${type.minStage}")
        val maxFloor = Stages.info(stage).maxFloor
        if (floor >= maxFloor) return PlaceCheck.no("Stufe $stage erlaubt nur $maxFloor Etagen")
        if (money < type.cost) return PlaceCheck.no("Zu wenig Geld")
        return tower.canPlace(type, floor, x)
    }

    fun build(type: RoomType, floor: Int, x: Int): BuildResult {
        val check = checkPlace(type, floor, x)
        if (!check.ok) return BuildResult(null, check.reason)
        money -= type.cost
        val room = tower.place(type, floor, x)
        room.since = clock.totalMinutes
        if (type.role == RoomRole.LOBBY && tower.allRooms.count { it.type.role == RoomRole.LOBBY } == 1) {
            news.post("Die erste Lobby ist offen. Ein Mann fragt, ob es hier zum Bahnhof geht.", Loc(floor, room.centerX))
        }
        return BuildResult(room)
    }

    /** Money back when a room is torn down. */
    fun refundFor(room: Room): Long = room.type.cost / 4

    fun checkDemolish(room: Room): PlaceCheck = tower.canRemove(room)

    /** Tears a room down and returns the refund. */
    fun demolish(roomId: Int): Long {
        val room = tower.room(roomId) ?: return 0
        if (!tower.canRemove(room).ok) return 0
        room.tenant?.let { news.post("${it.name} zieht aus. Der Abrissbagger war sehr überzeugend.", room.center) }
        removeMembers(roomId)
        tower.remove(roomId)
        val refund = refundFor(room)
        money += refund
        return refund
    }

    fun moveIn(room: Room, tenant: TenantKind, announce: Boolean = true) {
        removeMembers(room.id)
        room.tenant = tenant
        room.since = clock.totalMinutes
        looksVersion++
        val resident = room.type.role == RoomRole.HOME
        repeat(room.type.capacity) {
            world.entity {
                it += Position(GROUND_FLOOR, 0f)
                it += Person(Names.random(random), tenant.look)
                it += Member(room.id, resident)
                it += Schedule(tenant.shift, offset = random.nextInt(-40, 41))
                it += Away(clock.totalMinutes)
            }
        }
        if (announce) news.post("Neu im Turm: ${tenant.name} (${floorLabel(room.floor)}). ${tenant.quirk}", room.center)
    }

    fun moveOut(room: Room) {
        removeMembers(room.id)
        room.tenant = null
        room.since = clock.totalMinutes
        looksVersion++
    }

    private val members = world.family { all(Member) }

    /** Everybody who belongs to [roomId]. */
    fun membersOf(roomId: Int): List<Entity> {
        val result = ArrayList<Entity>()
        members.forEach { if (it[Member].roomId == roomId) result += it }
        return result
    }

    private fun removeMembers(roomId: Int) = with(world) {
        for (e in membersOf(roomId)) e.remove()
    }

    /** Average mood of the members of [room], or null when nobody belongs to it. */
    fun moodOf(room: Room): Float? = with(world) {
        val list = membersOf(room.id)
        if (list.isEmpty()) null else list.map { it[Schedule].mood }.average().toFloat()
    }

    /** Average mood of everybody in the tower, 0..100. */
    val satisfaction: Float
        get() = with(world) {
            var sum = 0f
            var n = 0
            members.forEach { sum += it[Schedule].mood; n++ }
            if (n == 0) 70f else sum / n
        }

    /** Called by the elevator system when somebody boards after waiting [minutes]. */
    fun onBoarded(entity: Entity, minutes: Double) = with(world) {
        val schedule = entity.getOrNull(Schedule) ?: return@with
        if (minutes > PATIENCE_MINUTES) {
            schedule.mood = (schedule.mood - ((minutes - PATIENCE_MINUTES) * 2).toFloat()).coerceAtLeast(0f)
        }
    }

    /** Sends a member on its way and remembers when the trip started, to measure how long it takes. */
    fun goTo(entity: Entity, target: Loc, activity: Activity) = with(world) {
        val schedule = entity[Schedule]
        schedule.activity = activity
        schedule.tripStart = clock.totalMinutes
        entity.configure { it += Goal(target) }
    }

    /** A spot inside [room], not too close to the walls. */
    fun spotIn(room: Room): Loc = Loc(room.floor, room.x + 0.5f + random.nextFloat() * (room.width - 1f))

    /** The rented restaurant closest to a floor, or null. */
    fun nearestOpenFood(floor: Int, x: Float): Room? = tower.allRooms
        .filter { it.type.role == RoomRole.FOOD && it.tenant != null }
        .minByOrNull { kotlin.math.abs(it.floor - floor) * 10 + kotlin.math.abs(it.centerX - x) }

    /** Money earned since the last midnight, for the daily report. */
    var earnedToday: Long = 0
        private set

    fun earn(amount: Long) {
        money += amount
        earnedToday += amount
    }

    /** Midnight: rent, maintenance, unhappy tenants move out, new star rating. */
    fun closeDay() {
        var rent = 0L
        val leaving = ArrayList<Room>()
        for (room in tower.allRooms) {
            val tenant = room.tenant ?: continue
            val mood = moodOf(room) ?: 70f
            rent += if (mood < UNHAPPY) tenant.rentPerDay / 2 else tenant.rentPerDay
            if (mood < MOVE_OUT && clock.totalMinutes - room.since > MIN_STAY_MINUTES) leaving += room
        }
        val upkeep = tower.allRooms.sumOf { UPKEEP[it.type.role] ?: 0L }
        money += rent - upkeep
        val total = rent - upkeep + earnedToday
        news.post("Tagesabschluss: Miete ${rent.fmt()} $, Unterhalt ${upkeep.fmt()} $, Umsatz ${earnedToday.fmt()} $. " +
            if (total >= 0) "Der Turm lebt." else "Der Steuerberater weint leise.")
        earnedToday = 0
        for (room in leaving) {
            val name = room.tenant?.name ?: continue
            moveOut(room)
            news.post("$name zieht aus. Grund: ${MOVE_OUT_REASONS[random.nextInt(MOVE_OUT_REASONS.size)]}", room.center, important = true)
        }
        val newStars = Rating.stars(population, satisfaction, tower.allRooms.any { it.type.role == RoomRole.FOOD && it.tenant != null })
        if (newStars != stars) {
            news.post(if (newStars > stars) "Der Turm hat jetzt $newStars Sterne! Neue Mieter werden neugierig." else "Ein Stern ist weg. Jemand hat ihn im Aufzug verloren.", important = true)
            stars = newStars
        }
    }

    private fun Long.fmt(): String = "%,d".format(this).replace(',', '.')

    /** Tower rating, 1 to 5 stars. Updated every midnight. */
    var stars: Int = 1

    /** People who live or work in the tower. Visitors don't count. */
    val population: Int get() = members.numEntities

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
        val schedule = entity.getOrNull(Schedule)
        if (schedule != null) {
            when (schedule.activity) {
                Activity.LEAVING -> entity.configure { it += Away(clock.totalMinutes) }
                Activity.TO_MEAL -> {
                    schedule.activity = Activity.EATING
                    schedule.mood = (schedule.mood + MEAL_MOOD).coerceAtMost(100f)
                    earn(MEAL_PRICE)
                    entity.configure { it += Idle(clock.totalMinutes + 15 + random.nextInt(15)) }
                }
                else -> Unit
            }
            return@with
        }
        if (visitor != null && !visitor.leaving) {
            val room = tower.roomAt(entity[Position].floor, entity[Position].x.toInt())
            if (room?.type?.role == RoomRole.FOOD && room.tenant != null) earn(MEAL_PRICE)
        }
        when {
            visitor != null && visitor.leaving -> entity.remove()
            visitor != null -> entity.configure { it += Idle(clock.totalMinutes + 3 + random.nextInt(8)) }
        }
    }

    /** Called when a person could not find a way to its goal for a while. */
    fun onGaveUp(entity: Entity) = with(world) {
        entity.getOrNull(Schedule)?.let {
            it.mood = (it.mood - STUCK_MOOD).coerceAtLeast(0f)
            // nobody can reach their room any more: they simply go home
            if (it.activity != Activity.LEAVING) it.activity = Activity.AWAY
            entity.configure { e -> e += Away(clock.totalMinutes) }
            return@with
        }
        val visitor = entity.getOrNull(Visitor) ?: return@with
        if (visitor.leaving) entity.remove() else visitor.stopsLeft = 0
    }

    companion object {
        fun floorLabel(floor: Int): String = when {
            floor == 0 -> "Erdgeschoss"
            floor < 0 -> "${-floor}. Untergeschoss"
            else -> "$floor. Etage"
        }

        const val START_MONEY = 250_000L

        /** Waiting longer than this many minutes for an elevator makes people grumpy. */
        const val PATIENCE_MINUTES = 3.0
        const val MEAL_MOOD = 8f
        const val STUCK_MOOD = 15f
        const val MEAL_PRICE = 12L
        const val UNHAPPY = 40f
        const val MOVE_OUT = 25f
        const val MIN_STAY_MINUTES = 2 * 1440.0

        /** Daily cost per room role. */
        val UPKEEP = mapOf(RoomRole.ELEVATOR to 60L, RoomRole.STAIRS to 10L, RoomRole.LOBBY to 40L, RoomRole.CORRIDOR to 5L)

        val MOVE_OUT_REASONS = listOf(
            "Der Aufzug kam nie.",
            "Zu viel Treppe, zu wenig Leben.",
            "Die Nachbarn haben geklatscht. Ständig.",
            "Kein Mittagessen in Sicht.",
            "Das Horoskop hat abgeraten.",
        )
        const val STEP_MINUTES = 0.1
        val VISITOR_LOOKS = listOf("tourist", "suit", "courier", "casual")
    }
}
