package crazytower.core

import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.World
import com.github.quillraven.fleks.World.Companion.family
import com.github.quillraven.fleks.configureWorld
import crazytower.core.ecs.Activity
import crazytower.core.ecs.Away
import crazytower.core.ecs.Cleaning
import crazytower.core.ecs.EconomySystem
import crazytower.core.ecs.ElevatorSystem
import crazytower.core.ecs.drop
import crazytower.core.ecs.Goal
import crazytower.core.ecs.Grievance
import crazytower.core.ecs.Member
import crazytower.core.ecs.Riding
import crazytower.core.ecs.Schedule
import crazytower.core.ecs.ScheduleSystem
import crazytower.core.ecs.Idle
import crazytower.core.ecs.IdleSystem
import crazytower.core.ecs.HousekeepingSystem
import crazytower.core.ecs.MovementSystem
import crazytower.core.ecs.PathfindingSystem
import crazytower.core.ecs.Person
import crazytower.core.ecs.Position
import crazytower.core.ecs.RouteRefreshSystem
import crazytower.core.ecs.StuckSystem
import crazytower.core.ecs.TenantSystem
import crazytower.core.ecs.Unhurried
import crazytower.core.ecs.Visitor
import crazytower.core.ecs.VisitorSpawnSystem
import crazytower.core.ecs.VisitorSystem
import crazytower.core.nav.NavGraph
import crazytower.core.transport.Car
import crazytower.core.transport.Elevators
import crazytower.core.transport.StairsLinks
import crazytower.core.transport.TeleporterLinks
import crazytower.core.ecs.EventSystem
import crazytower.core.ecs.Vip
import kotlin.random.Random

/**
 * What a drag would build: the pieces, their total price and whether it is allowed. A drag that
 * goes further than money or the rules allow still builds what it can: [blocked] are the pieces
 * past that point, and [blockedReason] says why they are left out.
 */
data class BuildPlan(
    val type: RoomType,
    val pieces: List<Piece>,
    val cost: Long,
    val check: PlaceCheck,
    val blocked: List<Piece> = emptyList(),
    val blockedReason: String = "",
) {
    data class Piece(val floor: Int, val x: Int, val width: Int)
}

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
    val nav = NavGraph(tower, listOf(StairsLinks, TeleporterLinks, elevators))
    val news = NewsFeed()

    /** Empty rooms nobody will move into, and why. Kept up to date by the tenant system. */
    val roomProblems = HashMap<Int, RoomProblem>()
    var money: Long = startMoney
        internal set // test towers

    val finance = Finance()

    /** Absurd events every hour; tests that count money switch them off. */
    var eventsEnabled: Boolean = true

    /** Events that last a while, see [Lasting]. */
    val activeEvents = ArrayList<ActiveEvent>()

    /** When the player last held an open day; once a week is allowed. */
    var lastOpenDay: Double = -Lasting.OPEN_DAY_COOLDOWN

    /** Money sinks and the moon mission, see [Endgame]. */
    var endgame: Endgame = Endgame()
        internal set

    /** Spectators per observation deck, so it gets dirty now and then. */
    internal val deckVisits = HashMap<Int, Int>()

    /** Changes the money and writes it into the books. */
    fun book(amount: Long, kind: MoneyKind, label: String, where: Loc? = null) {
        if (amount == 0L) return
        money += amount
        finance.record(Payment(clock.totalMinutes, amount, kind, label, where))
    }

    /** What the next star still needs, e.g. "ein Imbiss"; empty at five stars. */
    val nextStarMissing: List<String> get() = Rating.missing(stars, population, satisfaction, hasFood)

    private val hasFood: Boolean get() = tower.allRooms.any { it.type.role == RoomRole.FOOD && it.tenant != null }

    /** Rent and fees grow with the star rating: [STAR_BONUS] per star above the first. */
    val starBonus: Double get() = 1.0 + STAR_BONUS * (stars - 1).coerceAtLeast(0)

    /** Who belongs to which room, kept up to date by the family hooks below, so asking is cheap. */
    private val roomMembers = HashMap<Int, MutableList<Entity>>()
    private val memberRoom = HashMap<Entity, Int>()

    val world: World = configureWorld(entityCapacity = 2048) {
        injectables { add(this@Simulation) }
        families {
            val members = family { all(Member) }
            onAdd(members) { entity ->
                val roomId = entity[Member].roomId
                memberRoom[entity] = roomId
                roomMembers.getOrPut(roomId) { ArrayList() } += entity
            }
            onRemove(members) { entity ->
                val roomId = memberRoom.remove(entity) ?: return@onRemove
                val list = roomMembers[roomId] ?: return@onRemove
                list -= entity
                if (list.isEmpty()) roomMembers.remove(roomId)
            }
        }
        systems {
            add(IdleSystem())
            add(TenantSystem())
            add(HousekeepingSystem())
            add(EconomySystem())
            add(EventSystem())
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

    /** Time per system, for the debug overlay. */
    val profiler = Profiler(world.systems.map { it::class.simpleName.orEmpty().removeSuffix("System") }, nav)

    init {
        // the systems are run one by one in [step], so each can be timed
        for (system in world.systems) system.enabled = false
    }

    /** Advances the simulation by [minutes] game minutes in fixed steps. */
    fun step(minutes: Double) {
        var left = minutes
        val systems = world.systems
        val nanos = profiler.nanos
        while (left > 0) {
            val dt = minOf(left, STEP_MINUTES)
            clock.advance(dt)
            world.update(dt.toFloat()) // sets the delta time; every system is switched off there
            val unhurriedTurn = profiler.ticks % UNHURRIED_EVERY == 0L
            for (i in systems.indices) {
                if (!unhurriedTurn && systems[i] is Unhurried) continue
                val started = System.nanoTime()
                systems[i].onUpdate()
                nanos[i] += System.nanoTime() - started
            }
            profiler.ticks++
            left -= dt
        }
    }

    /** Increases whenever a room changes its looks without the tower changing, e.g. a new tenant. */
    var looksVersion: Int = 0
        private set

    /** A room changed its looks or its effects without the tower changing (an upgrade, a dirty deck). */
    fun bumpLooks() {
        looksVersion++
    }

    fun checkPlace(type: RoomType, floor: Int, x: Int, width: Int = type.width): PlaceCheck {
        val rules = checkRules(type, floor, x, width)
        if (!rules.ok) return rules
        if (money < priceAt(type, floor, x, width)) return PlaceCheck.no(NO_MONEY)
        return tower.canPlace(type, floor, x, width)
    }

    /** Width of the building lot in segments; it grows with the best stars, see [Lot]. */
    val lotWidth: Int get() = Lot.width(bestStars)

    /** Whether the basement may be dug. */
    val basementOpen: Boolean get() = bestStars >= Lot.BASEMENT_STARS

    /** Whether stage and stars allow [type] at all; the build menu only shows these. */
    fun unlocked(type: RoomType): Boolean = stage >= type.minStage && bestStars >= type.minStars

    /**
     * Room types the player has already seen in the build menu. Types unlocked since are marked
     * as new there until their category is opened. A new tower has seen what it starts with.
     */
    val seenTypes: MutableSet<String> = Catalog.roomTypes.filter { unlocked(it) }.mapTo(HashSet()) { it.id }

    /** Unlocked room types the player has not seen in the build menu yet. */
    fun isNew(type: RoomType): Boolean = unlocked(type) && type.id !in seenTypes

    /** What stage, stars and lot allow, before money and the tower itself are asked. */
    private fun checkRules(type: RoomType, floor: Int, x: Int, width: Int): PlaceCheck {
        if (type.minStage > stage) return PlaceCheck.no(tr("Erst ab Stufe ${type.minStage}", "Only from stage ${type.minStage}"))
        if (type.minStars > bestStars) return PlaceCheck.no(tr("Erst ab ${type.minStars} Sternen", "Only from ${type.minStars} stars"))
        val maxFloor = Stages.info(stage).maxFloor
        if (floor >= maxFloor) return PlaceCheck.no(floorLimit(maxFloor))
        if (floor < GROUND_FLOOR && !basementOpen) return PlaceCheck.no(tr("Keller erst ab ${Lot.BASEMENT_STARS} Sternen", "Basement only from ${Lot.BASEMENT_STARS} stars"))
        Tower.skyLobbiesBelow(floor).firstOrNull { sky -> tower.allRooms.none { it.floor == sky && it.type.role == RoomRole.LOBBY } }?.let {
            return PlaceCheck.no(tr("Erst eine Sky-Lobby auf der $it. Etage bauen", "Build a sky lobby on floor $it first"))
        }
        if (type.basementOnly && floor >= GROUND_FLOOR) return PlaceCheck.no(tr("${type.name} nur im Keller", "${type.name}: basement only"))
        type.minFloor?.let { min ->
            if (floor < min) return PlaceCheck.no(tr("${type.name} erst ab der $min. Etage", "${type.name} only from floor $min up"))
        }
        val max = type.maxCount
        if (max != null && tower.allRooms.count { it.type == type } >= max) {
            return PlaceCheck.no(
                if (max == 1) tr("${type.name} gibt es schon, nur einmal pro Turm", "There already is a ${type.name}, only one per tower")
                else tr("Schon $max ${type.name}, mehr gehen nicht", "Already $max of ${type.name}, no more allowed"),
            )
        }
        if (type.role == RoomRole.PARKING && carsWithoutSpace <= 0) return NO_CAR
        partBlocked(type, floor)?.let { return PlaceCheck.no(it) }
        if (x >= 0 && x + width > lotWidth) {
            val next = Lot.next(bestStars)
            return PlaceCheck.no(if (next != null) tr("Grundstück wird erst ab ${next.stars} Sternen breiter", "The lot grows at ${next.stars} stars") else tr("Außerhalb des Grundstücks", "Outside the lot"))
        }
        return PlaceCheck.OK
    }

    /**
     * Elevator shafts in the tower besides the one a module at [x] on [floor] would belong to.
     * Every one of them makes the next shaft dearer, so elevators are not built everywhere.
     */
    fun otherShafts(type: RoomType, floor: Int, x: Int, along: Collection<Int> = emptyList()): Int = elevators.cars.values.count { car ->
        val near = car.bottom - 1..car.top + 1
        !(car.x == x && car.express == type.express && (floor in near || along.any { it in near }))
    }

    /** What [type] costs right now, before any elevator surcharge: offices get dearer with the stars. */
    fun listPrice(type: RoomType, width: Int = type.width): Long =
        (type.price(width) * (1 + type.pricePerStar * (stars - 2).coerceAtLeast(0))).toLong()

    /**
     * What building [type] at [x] on [floor] costs right now. Elevators get dearer with each shaft;
     * [along] are floors of the same drag built before this one, which join it to a shaft.
     */
    fun priceAt(type: RoomType, floor: Int, x: Int, width: Int = type.width, along: Collection<Int> = emptyList()): Long {
        val base = listPrice(type, width)
        if (type.role != RoomRole.ELEVATOR) return base
        return (base * (1 + SHAFT_SURCHARGE * otherShafts(type, floor, x, along))).toLong()
    }

    fun build(type: RoomType, floor: Int, x: Int, width: Int = type.width): BuildResult {
        val check = checkPlace(type, floor, x, width)
        if (!check.ok) return BuildResult(null, check.reason)
        val firstLobby = type.role == RoomRole.LOBBY && tower.allRooms.none { it.type.role == RoomRole.LOBBY }
        val price = priceAt(type, floor, x, width)
        var room = tower.place(type, floor, x, width)
        book(-price, if (Mission.partOf(type) != null) MoneyKind.MISSION else MoneyKind.BUILD, type.name.toString(), Loc(floor, x + width / 2f))
        if (type.pricePerStar > 0) room.paid = price
        room.since = clock.totalMinutes
        if (type.role == RoomRole.LOBBY) room = tower.mergeLobbies() ?: room
        if (Mission.partOf(type) != null) partBuilt(room)
        if (firstLobby) {
            news.post(tr("Die erste Lobby ist offen. Ein Mann fragt, ob es hier zum Bahnhof geht.", "The first lobby is open. A man asks if this is the way to the station."), Loc(floor, room.centerX))
        }
        return BuildResult(room)
    }

    /**
     * The lobby pieces a drag along [floor] (the ground floor or a sky lobby floor) from segment
     * [from] to [to] would add. Segments that already belong to a lobby are skipped, so dragging
     * over the old lobby is free.
     */
    fun planLobby(from: Int, to: Int, floor: Int = GROUND_FLOOR): BuildPlan {
        val lo = minOf(from, to).coerceIn(0, lotWidth - 1)
        val hi = maxOf(from, to).coerceIn(0, lotWidth - 1)
        val pieces = ArrayList<IntRange>()
        var start = -1
        for (s in lo..hi + 1) {
            val free = s <= hi && tower.roomAt(floor, s)?.type?.role != RoomRole.LOBBY
            if (free && start < 0) start = s
            if (!free && start >= 0) {
                pieces += start until s
                start = -1
            }
        }
        val cost = pieces.sumOf { Catalog.LOBBY.price(it.last - it.first + 1) }
        val maxFloor = Stages.info(stage).maxFloor
        var check = if (floor >= maxFloor) PlaceCheck.no(floorLimit(maxFloor)) else PlaceCheck.OK
        for (piece in pieces) {
            if (!check.ok) break
            val c = tower.canPlace(Catalog.LOBBY, floor, piece.first, piece.last - piece.first + 1)
            if (!c.ok) { check = c; break }
        }
        if (check.ok && cost > money) check = PlaceCheck.no(NO_MONEY)
        if (check.ok && pieces.isEmpty()) check = PlaceCheck.no(tr("Hier ist schon Lobby", "There is lobby here already"))
        return BuildPlan(Catalog.LOBBY, pieces.map { BuildPlan.Piece(floor, it.first, it.last - it.first + 1) }, cost, check)
    }

    /**
     * The gap filler for a click at [segment] on [floor]: the whole empty stretch around it,
     * between the neighbouring rooms or the lot edge, if it is at most [MAX_GAP] wide.
     */
    fun planGap(floor: Int, segment: Int): BuildPlan {
        val type = Catalog.FILLER
        fun empty(s: Int) = s in 0 until lotWidth && tower.roomAt(floor, s) == null
        if (!empty(segment)) return BuildPlan(type, emptyList(), 0, PlaceCheck.no(tr("Hier ist keine Lücke", "There is no gap here")))
        var lo = segment
        var hi = segment
        while (empty(lo - 1) && hi - lo < MAX_GAP) lo--
        while (empty(hi + 1) && hi - lo < MAX_GAP) hi++
        val width = hi - lo + 1
        val piece = BuildPlan.Piece(floor, lo, width)
        val cost = type.price(width)
        val check = when {
            width > MAX_GAP -> PlaceCheck.no(tr("Zu große Lücke, hier passt ein ganzer Raum", "Gap too big, a whole room fits here"))
            else -> checkRules(type, floor, lo, width).takeUnless { it.ok }
                ?: tower.canPlace(type, floor, lo, width).takeUnless { it.ok }
                ?: if (cost > money) PlaceCheck.no(NO_MONEY) else PlaceCheck.OK
        }
        return BuildPlan(type, listOf(piece), cost, check)
    }

    /**
     * The rooms of [type] a sideways drag along [floor] from segment [from] to [to] would add,
     * side by side, stepping over what is already built there. Parking spaces stop at the
     * number of cars that still look for one.
     */
    fun planRow(type: RoomType, floor: Int, from: Int, to: Int): BuildPlan {
        val lo = minOf(from, to).coerceIn(0, lotWidth - 1)
        val hi = maxOf(from, to).coerceIn(0, lotWidth - 1)
        val limit = if (type.role == RoomRole.PARKING) carsWithoutSpace else Int.MAX_VALUE
        var check = checkRules(type, floor, lo, type.width)
        val pieces = ArrayList<BuildPlan.Piece>()
        var x = lo
        while (check.ok && x + type.width - 1 <= hi && pieces.size < limit) {
            if (tower.canPlace(type, floor, x).ok) {
                pieces += BuildPlan.Piece(floor, x, type.width)
                x += type.width
            } else {
                x++
            }
        }
        if (check.ok && pieces.isEmpty()) check = tower.canPlace(type, floor, lo).takeUnless { it.ok } ?: PlaceCheck.no(tr("Hier ist kein Platz", "There is no room here"))
        // as many as the money pays for, from where the drag started
        if (from > to) pieces.reverse()
        val affordable = ArrayList<BuildPlan.Piece>()
        var cost = 0L
        for (piece in pieces) {
            val price = type.price(piece.width)
            if (cost + price > money) break
            cost += price
            affordable += piece
        }
        val blocked = pieces.drop(affordable.size)
        if (check.ok && affordable.isEmpty()) check = PlaceCheck.no(NO_MONEY)
        return BuildPlan(type, affordable, cost, check, blocked, if (blocked.isEmpty()) "" else NO_MONEY)
    }

    /**
     * The stairs or elevator modules a vertical drag at [x] from floor [from] to floor [to]
     * would add. Floors that already have a module of this type at [x] are skipped, so grabbing
     * the end of a shaft and dragging on extends it. Stairs lead up to the upper floor, so they
     * stop one floor below it.
     */
    fun planColumn(type: RoomType, x: Int, from: Int, to: Int): BuildPlan {
        require(type.role.isWay)
        var floors = if (from <= to) (from..to).toList() else (from downTo to).toList()
        if (type.role == RoomRole.STAIRS && floors.size > 1) floors = floors.map { minOf(it, maxOf(from, to) - 1) }.distinct()
        // checked outward from the floor that is carried: up from below, down from the ground in the basement
        floors = floors.sortedBy { if (it >= GROUND_FLOOR) it else -it }
        val fresh = floors.filter { f -> tower.wayAt(f, x)?.let { it.type == type && it.x == x } != true }
        if (fresh.isEmpty()) return BuildPlan(type, emptyList(), 0, PlaceCheck.no(tr("Hier ist schon ${type.name}", "There is ${type.name} here already")))
        fun rules(f: Int, assumed: Set<Int>) = checkRules(type, f, x, type.width).takeUnless { it.ok } ?: tower.canPlace(type, f, x, assumed = assumed)
        // the rules: everything up to the first floor that may not be built
        var stop = PlaceCheck.OK
        val valid = ArrayList<Int>()
        for (f in fresh) {
            stop = rules(f, valid.toSet())
            if (!stop.ok) break
            valid += f
        }
        // Then the money. A drag that goes further than the money builds the floors next to
        // where it started and leaves out the rest; dragging away from what carries the column
        // that is the same order as the rules, dragging towards it each floor must stand on its own.
        val fromStart = fresh.sortedBy { kotlin.math.abs(it - from) }
        val pieces = ArrayList<Int>()
        var cost = 0L
        fun take(f: Int): Boolean {
            val price = priceAt(type, f, x, along = pieces)
            if (cost + price > money) { stop = PlaceCheck.no(NO_MONEY); return false }
            cost += price
            pieces += f
            return true
        }
        val allFine = valid.size == fresh.size && fresh.indices.sumOf { priceAt(type, fresh[it], x, along = fresh.take(it)) } <= money
        when {
            allFine || fromStart.first() == fresh.first() -> for (f in valid) if (!take(f)) break
            else -> for (f in fromStart) {
                val c = rules(f, pieces.toSet())
                if (!c.ok) { stop = c; break }
                if (!take(f)) break
            }
        }
        val blocked = fresh.filter { it !in pieces }.map { BuildPlan.Piece(it, x, type.width) }
        val check = if (pieces.isEmpty()) stop else PlaceCheck.OK
        return BuildPlan(type, pieces.map { BuildPlan.Piece(it, x, type.width) }, cost, check, blocked, if (blocked.isEmpty()) "" else stop.reason)
    }

    /** Builds everything in [plan] if it is still valid. */
    fun build(plan: BuildPlan): BuildResult {
        if (!plan.check.ok) return BuildResult(null, plan.check.reason)
        var last: Room? = null
        for (piece in plan.pieces) {
            val result = build(plan.type, piece.floor, piece.x, piece.width)
            if (!result.ok) return BuildResult(last, result.reason)
            last = result.room
        }
        return BuildResult(last)
    }

    /**
     * The modules of the shaft or staircase whose end is [end] that a drag from [end] towards
     * floor [to] would remove: only from that end inwards, and at least one module stays.
     */
    fun planRetract(end: Room, to: Int): List<Room> {
        if (!end.type.role.isWay) return emptyList()
        fun module(f: Int) = tower.wayAt(f, end.x)?.takeIf { it.type == end.type && it.x == end.x }
        val removed = ArrayList<Room>()
        when {
            module(end.floor + 1) == null && to < end.floor -> {
                var f = end.floor
                while (f > to && module(f - 1) != null) removed += module(f--)!!
            }
            module(end.floor - 1) == null && to > end.floor -> {
                var f = end.floor
                while (f < to && module(f + 1) != null) removed += module(f++)!!
            }
        }
        return removed
    }

    /** Tears down [modules] in order (from the end of their column), returns the refund. */
    fun demolishAll(modules: List<Room>): Long = modules.sumOf { demolish(it.id) }

    /** Whether the shaft [car] may get another cabin now, and why not. */
    fun checkCabin(car: Car): PlaceCheck = when {
        car.cabins.size >= Elevators.MAX_CABINS -> PlaceCheck(false, tr("Mehr Kabinen passen nicht in den Schacht", "No more cabins fit into the shaft"))
        car.cabins.size >= Elevators.maxCabins(bestStars) -> PlaceCheck(false, tr("Mehr Kabinen ab ${bestStars + 1} Sternen", "More cabins at ${bestStars + 1} stars"))
        money < Elevators.CABIN_COST -> PlaceCheck(false, NO_MONEY)
        else -> PlaceCheck(true)
    }

    /** Buys one more cabin for [car]; the shaft's modules remember it for the save game. */
    fun addCabin(car: Car): PlaceCheck {
        val check = checkCabin(car)
        if (!check.ok) return check
        book(-Elevators.CABIN_COST, MoneyKind.BUILD, tr("Aufzugkabine", "Elevator cabin"), Loc(car.bottom, car.centerX))
        setCabins(car, car.cabins.size + 1)
        return check
    }

    /** Takes the last empty cabin out of [car] (no refund, the old cabin goes to scrap). */
    fun removeCabin(car: Car): Boolean {
        if (car.cabins.size <= 1) return false
        setCabins(car, car.cabins.size - 1)
        return true
    }

    private fun setCabins(car: Car, count: Int) {
        for (f in car.floors) tower.wayAt(f, car.x)?.cabins = count
        car.setCabins(count)
    }

    /**
     * Money back when a room is torn down: a quarter of the price. Nothing for a flat that was
     * sold once, or building, selling and tearing down flats would print money.
     */
    fun refundFor(room: Room): Long = if (room.sold) 0 else (room.paid.takeIf { it > 0 } ?: room.type.price(room.width)) / 4

    fun checkDemolish(room: Room): PlaceCheck = tower.canRemove(room)

    /** Tears a room down and returns the refund. */
    fun demolish(roomId: Int): Long {
        val room = tower.room(roomId) ?: return 0
        if (!tower.canRemove(room).ok) return 0
        room.tenant?.let { news.post(tr("${it.name} zieht aus. Der Abrissbagger war sehr überzeugend.", "${it.name} moves out. The wrecking ball was very convincing."), room.center) }
        removeMembers(roomId)
        tower.remove(roomId)
        val refund = refundFor(room)
        book(refund, MoneyKind.BUILD, tr("Abriss ${room.type.name}", "Demolished ${room.type.name}"), room.center)
        // the owners get their whole purchase price back: the flat is gone
        if (room.tenant != null && room.salePaid > 0) book(-room.salePaid, MoneyKind.REFUND, tr("Kaufpreis zurück", "Purchase price back"), room.center)
        return refund
    }

    /**
     * A tenant moves in. The first residents of a flat buy it right away; later ones buy it from
     * the people who left, so the tower sees that money only once per flat. Offices and
     * restaurants pay at the end of each working day from now on. [announce] is false when loading a save.
     */
    fun moveIn(room: Room, tenant: TenantKind, announce: Boolean = true) {
        removeMembers(room.id)
        room.tenant = tenant
        room.since = clock.totalMinutes
        room.paidUntil = clock.totalMinutes
        if (room.type.role == RoomRole.HOTEL) room.paidUntil = checkoutAfter(clock.totalMinutes)
        looksVersion++
        if (announce && room.type.role == RoomRole.HOME && tenant.salePrice > 0 && !room.sold) {
            room.sold = true
            // the price is fixed, no star bonus: a flat sells for what it cost to build, never more
            room.salePaid = tenant.salePrice
            flatsSoldToday++
            book(room.salePaid, MoneyKind.SALE, tenant.name.toString(), room.center)
        }
        val resident = room.type.role.residential
        val firstLook = room.id // rooms of the same tenant start their crew at different people
        repeat(room.type.capacity) { i ->
            world.entity {
                it += Position(GROUND_FLOOR, 0f)
                it += Person(Names.random(random), tenant.lookFor(firstLook + i))
                it += Member(room.id, resident)
                it += Schedule(tenant.plan, offset = random.nextInt(-40, 41), crew = i % tenant.plan.crews, seed = random.nextInt(1_000_000))
                it += Away(clock.totalMinutes)
            }
        }
        if (announce && room.type.role == RoomRole.HOTEL) {
            if (random.nextInt(4) == 0) news.post(tr("Check-in: ${tenant.name} (${floorLabel(room.floor)}). ${tenant.quirk}", "Check-in: ${tenant.name} (${floorLabel(room.floor)}). ${tenant.quirk}"), room.center)
        } else if (announce) news.post(tr("Neu im Turm: ${tenant.name} (${floorLabel(room.floor)}). ${tenant.quirk}", "New in the tower: ${tenant.name} (${floorLabel(room.floor)}). ${tenant.quirk}"), room.center)
    }

    /** Unhappy tenants leave; the first owners of a flat get half their price back, once. */
    fun moveOut(room: Room) {
        removeMembers(room.id)
        if (room.salePaid > 0) book(-room.salePaid / 2, MoneyKind.REFUND, tr("Kaufpreis zurück", "Purchase price back"), room.center)
        room.salePaid = 0
        // guests leave a mess behind, however long they stayed
        if (room.type.role == RoomRole.HOTEL) room.dirty = true
        room.tenant = null
        room.since = clock.totalMinutes
        looksVersion++
    }

    private val members = world.family { all(Member) }

    /** Everybody who belongs to [roomId]. */
    fun membersOf(roomId: Int): List<Entity> = roomMembers[roomId]?.toList().orEmpty()

    private fun removeMembers(roomId: Int) = with(world) {
        for (e in membersOf(roomId)) e.remove()
    }

    /** Average mood of the members of [room], or null when nobody belongs to it. */
    fun moodOf(room: Room): Float? = with(world) {
        val list = roomMembers[room.id] ?: return@with null
        var sum = 0.0
        for (e in list) sum += e[Schedule].mood
        (sum / list.size).toFloat()
    }

    private var extrasVersion = -1
    private var extrasLooks = -1
    private var hasCopyroom = false
    private var hasUfoPad = false
    private var shopCover = 0f
    private val dormFloors = HashSet<Int>()

    /**
     * The mood a member of [room] settles to: [ScheduleSystem.MOOD_BASE], more after a meal
     * today or yesterday, more with the Copy Room (office workers), corner shops (residents)
     * and the UFO pad (everybody). Flats next to a dorm hear the parties.
     */
    fun moodTarget(schedule: Schedule, resident: Boolean, room: Room? = null): Float {
        if (extrasVersion != tower.version || extrasLooks != looksVersion) {
            extrasVersion = tower.version
            extrasLooks = looksVersion
            hasCopyroom = tower.allRooms.any { it.type == Catalog.COPYROOM }
            hasUfoPad = tower.allRooms.any { it.type == Catalog.UFO_PAD }
            val shops = tower.allRooms.count { it.type.role == RoomRole.SHOP && it.tenant != null }
            val homes = tower.allRooms.count { it.type.role.housing && it.tenant != null }
            shopCover = if (homes == 0) 0f else minOf(1f, shops * SHOP_SERVES / homes.toFloat())
            dormFloors.clear()
            tower.allRooms.filter { it.type.role == RoomRole.DORM && it.tenant != null }.mapTo(dormFloors) { it.floor }
        }
        var target = ScheduleSystem.MOOD_BASE
        if (schedule.fedOnDay >= clock.day - 1) target += MEAL_MOOD
        if (hasCopyroom && !resident) target += COPYROOM_MOOD * (if (researched("coffee")) 2 else 1)
        if (hasUfoPad) target += UFO_MOOD
        if (room != null && room.type.role.housing) target += SHOP_MOOD * shopCover
        if (room != null && room.type.role == RoomRole.HOME && room.floor in dormFloors && !researched("wallpaper")) target -= DORM_NOISE
        if (room != null) target += Upgrades.MOOD_BONUS * room.upgrade
        if (partyRunning) target += Promotion.PARTY_MOOD
        return target
    }

    private var workVersion = -1
    private var workLooks = -1
    private var cachedJobs = 0
    private var cachedResidents = 0
    private var cachedStudents = 0

    private fun countWork() {
        if (workVersion == tower.version && workLooks == looksVersion) return
        workVersion = tower.version
        workLooks = looksVersion
        cachedJobs = 0
        cachedResidents = 0
        cachedStudents = 0
        for (room in tower.allRooms) {
            if (room.tenant == null) continue
            when (room.type.role) {
                RoomRole.OFFICE -> cachedJobs += room.type.capacity
                RoomRole.HOME -> cachedResidents += room.type.capacity
                RoomRole.DORM -> { cachedResidents += room.type.capacity; cachedStudents += room.type.capacity }
                else -> Unit
            }
        }
    }

    /** Office workers of all rented offices. */
    val jobs: Int get() = countWork().let { cachedJobs }

    /** People who live in flats, penthouses and dorms; hotel guests don't count. */
    val residents: Int get() = countWork().let { cachedResidents }

    /** Students in the dorms. */
    val students: Int get() = countWork().let { cachedStudents }

    /** Whether offices need workers from the city and the tower, see [workforce]. */
    val workforceMatters: Boolean get() = stars >= WORKFORCE_STARS

    /**
     * How many office workers the tower can find, from [WORKFORCE_STARS] on: commuters from the
     * city, more with every star and with the subway, and everybody who lives in the tower.
     * New office tenants only move in while their people fit in.
     */
    val workforce: Int get() {
        if (!workforceMatters) return Int.MAX_VALUE
        val subway = if (tower.allRooms.any { it.type.role == RoomRole.SUBWAY }) SUBWAY_COMMUTERS else 0
        return commuters(stars) + subway + residents
    }

    /** Whether the people of an office with [capacity] workers would still find enough workers. */
    fun hasWorkersFor(capacity: Int): Boolean = !workforceMatters || jobs + capacity <= workforce

    /** Whether a dorm with [capacity] students would find side jobs for them: half the office workers at most. */
    fun hasSideJobsFor(capacity: Int): Boolean = (students + capacity) * STUDENT_JOBS <= jobs

    /** Average mood of everybody in the tower, 0..100. */
    val satisfaction: Float
        get() = with(world) {
            var sum = 0f
            var n = 0
            members.forEach { sum += it[Schedule].mood; n++ }
            if (n == 0) 70f else sum / n
        }

    /** Called by the elevator system when somebody boards after waiting [minutes]. */
    fun onBoarded(entity: Entity, minutes: Double, car: Car? = null) = with(world) {
        val schedule = entity.getOrNull(Schedule) ?: return@with
        val patience = PATIENCE_MINUTES +
            (if ((car?.tuning ?: 0) >= Tuning.QUARTET) Tuning.QUARTET_PATIENCE else 0.0) +
            (if (researched("patience")) Research.PATIENCE else 0.0)
        if (minutes > patience) {
            schedule.mood = (schedule.mood - ((minutes - patience) * 2).toFloat()).coerceAtLeast(0f)
            schedule.grievance = Grievance.WAITING
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
    val earnedToday: Long get() = finance.income(clock.day)

    fun earn(amount: Long) = book(amount, MoneyKind.GUESTS, MoneyKind.GUESTS.label)

    /**
     * Offices and restaurants pay at the end of every working day of their tenant, the
     * vampires in the morning. Unhappy tenants pay half. Called every game minute.
     */
    fun collectRent() {
        val now = clock.totalMinutes
        for (room in tower.allRooms) {
            val tenant = room.tenant ?: continue
            if (room.type.role == RoomRole.HOME || tenant.rentPerDay <= 0) continue
            if (room.type.role == RoomRole.HOTEL) {
                if (now >= room.paidUntil) checkOut(room, tenant)
                continue
            }
            val end = tenant.plan.lastEnd(now) ?: continue
            if (end <= room.paidUntil) continue
            room.paidUntil = end.toDouble()
            // buried under socks: no work, no rent
            if (rentBlocked(room)) continue
            book(rentFor(room, tenant), if (room.type.role == RoomRole.FOOD) MoneyKind.FOOD else MoneyKind.RENT, tenant.name.toString(), room.center)
        }
    }

    /** Hotel guests who checked in at [minute] leave the next morning at [CHECKOUT_HOUR]. */
    private fun checkoutAfter(minute: Double): Double =
        ((minute / GameClock.MINUTES_PER_DAY).toInt() + 1) * GameClock.MINUTES_PER_DAY + CHECKOUT_HOUR * 60.0

    /** The guests pay for their night and go; the room stays dirty until somebody cleans it. */
    private fun checkOut(room: Room, tenant: TenantKind) {
        book(rentFor(room, tenant), MoneyKind.HOTEL, tenant.name.toString(), room.center)
        guestsLeft(room, tenant)
        moveOut(room)
    }

    /** Cleaning staff finished [room]: the next guests may come. */
    fun cleaned(room: Room) {
        if (!room.dirty) return
        room.dirty = false
        room.since = clock.totalMinutes
        looksVersion++
    }

    /** Hotel rooms that wait for the cleaning staff. */
    val dirtyRooms: List<Room> get() = tower.allRooms.filter { it.dirty }

    private val hasConference: Boolean get() = tower.allRooms.any { it.type.role == RoomRole.CONFERENCE }

    internal fun rentFor(room: Room, tenant: TenantKind): Long {
        val mood = moodOf(room) ?: 70f
        val full = (tenant.rentPerDay * starBonus * Upgrades.rentFactor(room)).toLong()
        if (room.type.luxuryOffice && !hasParking(room)) return full / 2
        val fair = if (room.type.role == RoomRole.HOTEL && hasConference) (full * (1 + CONFERENCE_HOTEL_BONUS)).toLong() else full
        return if (mood < (if (room.type.luxuryOffice) LUXURY_MOOD else UNHAPPY)) fair / 2 else fair
    }

    /**
     * Payments expected in the next [hours] game hours, soonest first. Upkeep is negative. The
     * same payer at the same time is one entry with the amounts summed.
     */
    fun upcoming(hours: Int = 36): List<Upcoming> {
        val now = clock.totalMinutes
        val horizon = now + hours * 60
        val list = ArrayList<Upcoming>()
        for (room in tower.allRooms) {
            val tenant = room.tenant ?: continue
            if (room.type.role == RoomRole.HOME || tenant.rentPerDay <= 0) continue
            val end = if (room.type.role == RoomRole.HOTEL) room.paidUntil.toInt() else tenant.plan.nextEnd(now) ?: continue
            if (end <= horizon) list += Upcoming(end, rentFor(room, tenant), tenant.name.toString(), room.center)
        }
        var midnight = (clock.day + 1) * GameClock.MINUTES_PER_DAY
        while (midnight <= horizon) {
            val day = midnight / GameClock.MINUTES_PER_DAY
            if (day % FEE_EVERY_DAYS == 0) {
                val fees = homeFees()
                if (fees > 0) list += Upcoming(midnight, fees, MoneyKind.FEES.label)
            }
            list += Upcoming(midnight, -upkeep(), MoneyKind.UPKEEP.label)
            midnight += GameClock.MINUTES_PER_DAY
        }
        return list.groupBy { it.minute to it.label }.values
            .map { same -> same.first().copy(amount = same.sumOf { it.amount }, count = same.sumOf { it.count }) }
            .sortedBy { it.minute }
    }

    /** Every sold flat pays its service charge, a penthouse [PENTHOUSE_FEES] times as much. */
    private fun homeFees(): Long = tower.allRooms.sumOf {
        if (it.type.role != RoomRole.HOME || it.tenant == null || it.tenant!!.salePrice <= 0) 0L
        else (HOME_FEE * starBonus * Upgrades.rentFactor(it)).toLong() * (if (it.type.luxury) PENTHOUSE_FEES else 1)
    }

    private fun upkeep(): Long = tower.allRooms.sumOf { it.type.upkeep ?: ((UPKEEP[it.type.role] ?: 0L) * (if (it.type.perSegment) it.width else 6) / 6) } +
        elevators.cars.values.sumOf { it.cabins.size } * CABIN_UPKEEP + shaftSurcharge() + runningCosts()

    /** Rented offices and snack bars cost a share of their rent every day, weekends too, more with every star. */
    private fun runningCosts(): Long {
        val percent = RUNNING_COST_PERCENT * (stars - 2).coerceAtLeast(0)
        if (percent == 0L) return 0
        return tower.allRooms.sumOf {
            val tenant = it.tenant
            if (tenant == null || (it.type.role != RoomRole.OFFICE && it.type.role != RoomRole.FOOD)) 0L
            else tenant.rentPerDay * percent / 100
        }
    }

    /** Each elevator shaft beyond the first makes every shaft's upkeep dearer. */
    private fun shaftSurcharge(): Long {
        val shafts = elevators.cars.size
        if (shafts < 2) return 0
        val floors = elevators.cars.values.sumOf { it.top - it.bottom + 1 }
        return (floors * UPKEEP.getValue(RoomRole.ELEVATOR) * UPKEEP_SURCHARGE * (shafts - 1)).toLong()
    }

    /**
     * Midnight: maintenance, every few days the home fees, unhappy tenants move out, new star
     * rating. Stars only go up; a gloomy tower loses tenants instead.
     */
    fun closeDay() {
        flatsSoldToday = 0
        val leaving = ArrayList<Room>()
        for (room in tower.allRooms) {
            if (room.tenant == null) continue
            val mood = moodOf(room) ?: 70f
            if (mood < MOVE_OUT && clock.totalMinutes - room.since > MIN_STAY_MINUTES) leaving += room
        }
        val ufoPad = tower.allRooms.firstOrNull { it.type == Catalog.UFO_PAD }
        if (ufoPad != null) book(UFO_FEE, MoneyKind.GUESTS, tr("Ufo-Landegebühr", "UFO landing fee"), ufoPad.center)
        val yesterday = clock.day - 1
        book(-upkeep(), MoneyKind.UPKEEP, MoneyKind.UPKEEP.label, tower.allRooms.firstOrNull { it.type.role == RoomRole.LOBBY }?.center)
        payInsurances()
        missionMidnight()
        if (clock.day % FEE_EVERY_DAYS == 0) book(homeFees(), MoneyKind.FEES, MoneyKind.FEES.label)
        val income = finance.income(yesterday)
        news.post(
            tr("Tagesabschluss: Einnahmen ${formatMoney(income)}, Unterhalt ${formatMoney(upkeep())}. ", "End of day: income ${formatMoney(income)}, upkeep ${formatMoney(upkeep())}. ") +
                if (income >= upkeep()) tr("Der Turm lebt.", "The tower lives.") else tr("Der Steuerberater weint leise.", "The tax adviser weeps quietly."),
        )
        for (room in leaving) {
            val name = room.tenant?.name ?: continue
            val reason = moveOutReason(room)
            moveOut(room)
            news.post(tr("$name zieht aus. Grund: $reason", "$name moves out. Reason: $reason"), room.center, important = true)
        }
        val newStars = Rating.stars(population, satisfaction, hasFood)
        if (newStars > stars) {
            news.post(tr("Der Turm hat jetzt $newStars Sterne! Neue Mieter werden neugierig.", "The tower now has $newStars stars! New tenants are getting curious."), important = true)
            val oldLot = lotWidth
            val hadBasement = basementOpen
            val before = Catalog.roomTypes.filter { unlocked(it) }.toSet()
            stars = newStars
            val fresh = Catalog.roomTypes.filter { unlocked(it) && it !in before }
            if (fresh.isNotEmpty()) {
                news.post(tr("Neu im Baumenü: ", "New in the build menu: ") + fresh.joinToString(", ") { it.name.toString() }, important = true)
            }
            if (newStars >= WORKFORCE_STARS && stars - 1 < WORKFORCE_STARS) {
                news.post(
                    tr("Ab jetzt suchen Firmen Fachkräfte: Neue Büros brauchen Leute aus der Stadt oder Bewohner im Turm. Wohnungen und Wohnheime helfen.",
                        "From now on companies look for staff: new offices need people from the city or residents of the tower. Flats and dorms help."),
                    important = true,
                )
            }
            if (lotWidth > oldLot) {
                news.post(
                    tr("Die Stadt verkauft dir das Nachbargrundstück. Der Turm darf jetzt $lotWidth Felder breit werden. Der Nachbar zieht murrend mit seinem Gartenzwerg um.",
                        "The city sells you the plot next door. The tower may now be $lotWidth tiles wide. The neighbour moves out grumbling, garden gnome in hand."),
                    Loc(GROUND_FLOOR, oldLot.toFloat()), important = true,
                )
            }
            if (basementOpen && !hadBasement) {
                news.post(
                    tr("Der Keller ist frei! Bis zur ${-Tower.MIN_FLOOR}. Untergeschoss darf gegraben werden, zum Beispiel für ein Parkhaus. Die Maulwürfe sind informiert.",
                        "The basement is open! You may dig down to basement ${-Tower.MIN_FLOOR}, for a car park for example. The moles have been informed."),
                    important = true,
                )
            }
        }
        val fees = parkingFees()
        if (fees > 0) book(fees, MoneyKind.PARKING, MoneyKind.PARKING.label, tower.allRooms.firstOrNull { it.type.role == RoomRole.PARKING }?.center)
        moveOutUnhappiest(leaving)
    }

    /**
     * Stars are never taken away. Instead, while the mood is below what the current stars ask
     * for, the unhappiest tenants give up their rooms: one in twenty rooms each night, at least one.
     */
    private fun moveOutUnhappiest(alreadyLeaving: List<Room>) {
        val needed = Rating.steps.firstOrNull { it.stars == stars }?.satisfaction ?: return
        if (satisfaction >= needed) return
        val rented = tower.allRooms.filter { it.tenant != null && it !in alreadyLeaving && clock.totalMinutes - it.since > MIN_STAY_MINUTES }
        // those who left tonight anyway count towards it
        val count = maxOf(1, (rented.size + alreadyLeaving.size) / 20) - alreadyLeaving.size
        for (room in rented.sortedBy { moodOf(it) ?: 70f }.take(count.coerceAtLeast(0))) {
            val name = room.tenant?.name ?: continue
            val reason = moveOutReason(room)
            moveOut(room)
            news.post(tr("$name zieht aus: zu schlechte Laune im Turm. $reason", "$name moves out: the mood in the tower is too bad. $reason"), room.center, important = true)
        }
    }

    /**
     * Why the tenant of [room] leaves: what spoilt the mood of most of its people. Must be asked
     * before [moveOut]. A tenant without a real complaint makes something up.
     */
    private fun moveOutReason(room: Room): String = with(world) {
        val grievance = membersOf(room.id).mapNotNull { it[Schedule].grievance }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        (grievance?.let { GRIEVANCE_REASONS.getValue(it) } ?: MOVE_OUT_REASONS[random.nextInt(MOVE_OUT_REASONS.size)]).toString()
    }

    private fun floorLimit(maxFloor: Int): String = tr("Stufe $stage erlaubt nur $maxFloor Etagen", "Stage $stage only allows $maxFloor floors")

    /**
     * Cars that want a space: those of the rented offices and occupied flats, plus the spaces
     * every luxury office keeps for itself, rented or not, so they can be built in advance.
     */
    val carsWanted: Int get() = tower.allRooms.sumOf {
        when {
            it.type.luxuryOffice -> LUXURY_SPACES
            it.tenant == null -> 0
            it.type.role == RoomRole.OFFICE -> CARS_PER_OFFICE
            it.type == Catalog.PENTHOUSE -> CARS_PER_PENTHOUSE
            it.type.role == RoomRole.HOME -> CARS_PER_HOME
            else -> 0
        }
    }

    /** One space per parking room. */
    val parkingSpaces: Int get() = tower.allRooms.count { it.type.role == RoomRole.PARKING }

    /** How many more parking spaces may be built: one for every car that has none yet. */
    val carsWithoutSpace: Int get() = carsWanted - parkingSpaces

    /**
     * Cars that pay for their space. The luxury offices get theirs first, rented before empty
     * ones, see [hasParking]; spaces kept for an empty luxury office earn nothing.
     */
    val parkedCars: Int get() {
        val luxury = tower.allRooms.filter { it.type.luxuryOffice }
        val spaces = parkingSpaces
        val forLuxury = minOf(spaces, luxury.size * LUXURY_SPACES)
        val rentedLuxury = minOf(forLuxury, luxury.count { it.tenant != null } * LUXURY_SPACES)
        return rentedLuxury + minOf(spaces - forLuxury, carsWanted - luxury.size * LUXURY_SPACES)
    }

    /** Whether a car stands in the parking space [space]: the oldest spaces fill up first. */
    fun isTaken(space: Room): Boolean =
        tower.allRooms.count { it.type.role == RoomRole.PARKING && it.id < space.id } < parkedCars

    /**
     * Where the people of [room] get out of their car in the morning and back in at night: a
     * parking space, for luxury offices that have theirs and can be reached from the basement.
     * Everybody else takes the street.
     */
    fun carSpot(room: Room): Loc? {
        if (!room.type.luxuryOffice || !hasParking(room)) return null
        val spaces = tower.allRooms.filter { it.type.role == RoomRole.PARKING }
        if (spaces.isEmpty()) return null
        val spot = spotIn(spaces[random.nextInt(spaces.size)])
        return spot.takeIf { nav.travelMinutes(it, room.center) != null }
    }

    /**
     * Whether the luxury office [room] has its [LUXURY_SPACES] spaces. They go to the luxury
     * offices first, rented ones before empty ones, the oldest first; the rest of the cars share what is left.
     */
    fun hasParking(room: Room): Boolean {
        if (!room.type.luxuryOffice) return true
        val order = tower.allRooms.filter { it.type.luxuryOffice }.sortedWith(compareBy({ it.tenant == null }, { it.id }))
        return parkingSpaces >= (order.indexOf(room) + 1) * LUXURY_SPACES
    }

    /** Daily parking fees for [parkedCars]. */
    fun parkingFees(): Long = (parkedCars * PARKING_FEE * starBonus).toLong()


    /** Flats sold since midnight; buyers are scarce, see [flatBuyersPerDay]. */
    var flatsSoldToday: Int = 0
        private set

    /**
     * How many flats can be sold in one day. A flat pays back its building cost when it is
     * sold, so without this limit flats could be built and sold endlessly in one go. More
     * stars, daycare and people working in the tower bring more buyers.
     */
    val flatBuyersPerDay: Int get() =
        FLAT_BUYERS_BASE + stars + KITA_BUYERS * tower.allRooms.count { it.type.role == RoomRole.DAYCARE } + jobs / JOBS_PER_BUYER

    /** Tower rating, 0 to 5 stars. Updated every midnight; it only goes up. */
    var stars: Int = 0
        set(value) {
            field = value
            if (value > bestStars) bestStars = value
        }

    /**
     * The most stars the tower ever had. Elevator cabins go by this, so a tower that loses a
     * star over a jam can still buy the cabins that end the jam.
     */
    var bestStars: Int = 0

    /** People who live or work in the tower. Visitors don't count. */
    val population: Int get() = members.numEntities

    /** Current tower stage, 1 to 5. */
    var stage: Int = 0
        internal set // tests jump ahead

    /** Progress towards the next stage, 0 to 1. */
    val stageProgress: Double get() = Stages.progress(this)

    /** Set when a VIP made it to her goal; needed for stage 5. */
    var vipSurvived: Boolean = false
        internal set

    val vipOnTheWay: Boolean get() = world.family { all(Vip) }.numEntities > 0

    /** When the last VIP arrived or gave up, so they don't come one after the other. */
    var lastVipVisit: Double = -Vips.COOLDOWN_MINUTES
        internal set

    /** Checks whether the tower reached the next stage. Called every game hour. */
    fun checkStage() {
        if (stage >= Stages.last || !Stages.reached(this, stage + 1)) return
        stage++
        looksVersion++ // the lobbies get a new look
        val info = Stages.info(stage)
        news.post(
            tr("STUFE $stage: Der Turm ist jetzt ein ${info.name}! Bis zur ${info.maxFloor}. Etage darf gebaut werden. ${info.unlocks}",
                "STAGE $stage: The tower is now a ${info.name}! You may build up to floor ${info.maxFloor}. ${info.unlocks}"),
            important = true,
        )
    }

    fun toSave(): SaveGame = SaveGame(
        minutes = clock.totalMinutes,
        money = money,
        stage = stage,
        stars = stars,
        bestStars = bestStars,
        vipSurvived = vipSurvived,
        earnedToday = earnedToday,
        rooms = tower.allRooms.map {
            SavedRoom(
                it.id, it.type.id, it.floor, it.x, it.since, it.tenant?.id, moodOf(it),
                it.width.takeIf { w -> w != it.type.width }, it.paidUntil, it.salePaid, it.cabins, it.sold, it.dirty, it.paid,
                it.upgrade, it.tuning,
            )
        },
        news = news.latest.map { SavedNews(it.text, it.where?.floor, it.where?.x, it.important) },
        activeEvents = activeEvents.toList(),
        lastOpenDay = lastOpenDay,
        seenTypes = seenTypes.sorted(),
        endgame = endgame,
    )

    private fun restore(save: SaveGame) {
        clock.set(save.minutes)
        money = save.money
        stage = save.stage
        stars = save.stars
        bestStars = maxOf(save.bestStars, save.stars)
        vipSurvived = save.vipSurvived
        val oldCarParks = save.rooms.filter { it.type == Catalog.OLD_CAR_PARK }
        for (saved in save.rooms - oldCarParks.toSet()) {
            val room = tower.placeUnchecked(Catalog.type(saved.type), saved.floor, saved.x, saved.id, saved.width ?: Catalog.type(saved.type).width)
            val tenant = saved.tenant?.let { Tenants.byId(it) }
            if (tenant != null) {
                moveIn(room, tenant, announce = false)
                saved.mood?.let { mood -> with(world) { membersOf(room.id).forEach { it[Schedule].mood = mood } } }
            }
            room.since = saved.since
            room.paidUntil = saved.paidUntil ?: save.minutes
            room.salePaid = saved.salePaid
            room.sold = saved.sold || saved.salePaid > 0
            room.cabins = saved.cabins
            room.dirty = saved.dirty
            room.paid = saved.paid
            room.upgrade = saved.upgrade
            room.tuning = saved.tuning
        }
        // the old car parks for 40 cars become rows of single spaces, once all saved ids are taken
        for (saved in oldCarParks) {
            for (x in saved.x until saved.x + (saved.width ?: 6) step Catalog.PARKING.width) {
                tower.placeUnchecked(Catalog.PARKING, saved.floor, x).since = saved.since
            }
        }
        for (item in save.news.asReversed()) {
            news.post(item.text, item.floor?.let { Loc(it, item.x ?: 0f) }, item.important)
        }
        lastOpenDay = save.lastOpenDay ?: lastOpenDay
        endgame = save.endgame ?: Endgame()
        // older saves did not keep track: everything the tower already allows counts as seen
        seenTypes.clear()
        save.seenTypes?.let { seenTypes += it } ?: Catalog.roomTypes.filter { unlocked(it) }.mapTo(seenTypes) { it.id }
        for (event in save.activeEvents.filter { Lasting.exists(it.id) }) {
            activeEvents += event
            event.kind.resume(this, event)
        }
    }

    /** A teleporter sent [entity] to the wrong place. */
    fun onMisfire(entity: Entity, landed: Room) {
        if (random.nextInt(3) == 0) {
            val name = with(world) { entity[Person].name }
            news.post(tr("$name wollte teleportieren und ist in der ${floorLabel(landed.floor)} gelandet. Nimmt es gelassen.", "$name tried to teleport and ended up ${onFloor(landed.floor)}. Takes it calmly."), landed.center)
        }
    }

    /** Street exit closest to [x]: people leave the tower to the left or right edge. */
    fun streetExit(x: Float): Loc {
        if (endgame.gates == 0) return Loc(GROUND_FLOOR, if (x < lotWidth / 2f) 0f else lotWidth - 0.01f)
        return Loc(GROUND_FLOOR, entrances.minBy { kotlin.math.abs(it - x) })
    }

    /**
     * Where people of [room] come in from the street: either end at random, as always, or the
     * nearest side entrance once the tower has some.
     */
    fun arrivalX(room: Room): Float =
        if (endgame.gates == 0) (if (random.nextBoolean()) 0f else lotWidth - 0.01f) else streetExit(room.centerX).x

    fun spawnVisitor(spectator: Boolean = false): Entity {
        val doors = entrances
        val start = doors[random.nextInt(doors.size)]
        val fromLeft = start < lotWidth / 2f
        val looks = if (spectator) SPECTATOR_LOOKS else VISITOR_LOOKS
        val look = looks[random.nextInt(looks.size)]
        return world.entity {
            it += Position(GROUND_FLOOR, start, facingLeft = !fromLeft)
            it += Person(Names.random(random), look)
            it += Visitor(stopsLeft = if (spectator) 3 else 1 + random.nextInt(3), spectator = spectator, since = clock.totalMinutes)
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
                    schedule.fedOnDay = clock.day
                    earn(MEAL_PRICE * visitorBoost)
                    mealServed()
                    entity.configure { it += Idle(clock.totalMinutes + 15 + random.nextInt(15)) }
                }
                Activity.TO_CLEAN -> {
                    val target = entity.getOrNull(Cleaning)?.let { tower.room(it.roomId) }
                    entity.configure { drop(it, Cleaning) }
                    if (target != null && target.dirty) {
                        schedule.activity = Activity.CLEANING
                        entity.configure { it += Idle(clock.totalMinutes + CLEAN_MINUTES / (if (researched("towels")) 2 else 1)) }
                        cleaned(target)
                    }
                }
                else -> Unit
            }
            return@with
        }
        entity.getOrNull(Vip)?.let { vip ->
            entity.configure { drop(it, Vip) }
            vipArrived(vip, Loc(entity[Position].floor, entity[Position].x))
        }
        if (visitor != null && !visitor.leaving) {
            val room = tower.roomAt(entity[Position].floor, entity[Position].x.toInt())
            if (room?.type?.role == RoomRole.FOOD && room.tenant != null) {
                earn(VISITOR_MEAL_PRICE * visitorBoost)
                mealServed()
            }
            if (visitor.spectator && room != null) {
                when (room.type) {
                    Mission.DECK -> if (!visitor.sawDeck) {
                        visitor.sawDeck = true
                        spectatorAtDeck(room, clock.totalMinutes - visitor.since)
                    }
                    Mission.SOUVENIRS -> book(Spectators.SOUVENIR, MoneyKind.SPECTATORS, tr("Souvenirs", "Souvenirs"), room.center)
                    else -> Unit
                }
            }
        }
        when {
            visitor != null && visitor.leaving -> entity.remove()
            visitor != null -> entity.configure { it += Idle(clock.totalMinutes + 3 + random.nextInt(8)) }
        }
    }

    /** A VIP got to her room: she judges the tower and tips, a lot or very little. */
    private fun vipArrived(vip: Vip, where: Loc) {
        val guest = Vips.byId(vip.guestId)
        val room = tower.room(vip.roomId)
        lastVipVisit = clock.totalMinutes
        if (guest.id == "wichtig") {
            vipSurvived = true
            news.post(tr("$VIP_NAME ist angekommen und nickt anerkennend. Der Turm ist bereit für Größeres.", "$VIP_NAME has arrived and nods approvingly. The tower is ready for bigger things."), where, important = true)
        }
        val verdict = Vips.judge(this, room, clock.totalMinutes - vip.since)
        vipJudged(guest.id, verdict.happy, where)
        val tip = Vips.tip(stars, verdict, random, finance.income(clock.day - 1))
        book(tip, MoneyKind.VIP, guest.name.toString(), where)
        val text = Vips.line(if (verdict.happy) guest.generous else guest.stingy, room, tip)
        news.post(if (verdict.happy || verdict.complaint == null) text else "$text ${verdict.complaint}", where, important = true)
    }

    /** Called when a person could not find a way to its goal for a while. */
    fun onGaveUp(entity: Entity) = with(world) {
        entity.getOrNull(Schedule)?.let {
            it.mood = (it.mood - STUCK_MOOD).coerceAtLeast(0f)
            it.grievance = Grievance.STUCK
            // nobody can reach their room any more: they simply go home
            if (it.activity != Activity.LEAVING) it.activity = Activity.AWAY
            entity.configure { e -> e += Away(clock.totalMinutes) }
            return@with
        }
        entity.getOrNull(Vip)?.let { vip ->
            entity.configure { drop(it, Vip) }
            lastVipVisit = clock.totalMinutes
            news.post(Vips.byId(vip.guestId).givesUp.toString(), important = true)
        }
        val visitor = entity.getOrNull(Visitor) ?: return@with
        if (visitor.spectator && !visitor.sawDeck && !visitor.leaving) {
            visitor.sawDeck = true
            spectatorGaveUp()
        }
        if (visitor.leaving) entity.remove() else visitor.stopsLeft = 0
    }

    companion object {
        /** Rebuilds a simulation from a save game. */
        fun load(save: SaveGame, seed: Long = 1L): Simulation {
            require(save.version <= SaveGame.VERSION) { tr("Spielstand ist von einer neueren Version", "The save game is from a newer version") }
            return Simulation(seed, startMoney = 0).also { it.restore(save) }
        }

        /** "5. Etage" or "floor 5"; English sentences that need "on" use [onFloor]. */
        fun floorLabel(floor: Int): String = when {
            floor == 0 -> tr("Erdgeschoss", "ground floor")
            floor < 0 -> tr("${-floor}. Untergeschoss", "basement ${-floor}")
            else -> tr("$floor. Etage", "floor $floor")
        }

        /** English "on floor 5", "on the ground floor", "in basement 2", for sentences. */
        fun onFloor(floor: Int): String = when {
            floor == 0 -> "on the ground floor"
            floor < 0 -> "in basement ${-floor}"
            else -> "on floor $floor"
        }

        /** The visitor whose visit unlocks the last stage. */
        val VIP_NAME = Tr("Frau Dr. Wichtig", "Dr. Bigwig")

        private val NO_MONEY: String get() = tr("Zu wenig Geld", "Not enough money")
        private val NO_CAR: PlaceCheck get() = PlaceCheck.no(tr("Jedes Auto hat schon einen Parkplatz. Mehr Büros, Wohnungen oder Luxusbüros!", "Every car has a space already. More offices, flats or luxury offices!"))

        const val START_MONEY = 120_000L

        /** Rent and fees grow by this share for every star above the first. */
        const val STAR_BONUS = 0.15

        /** Rented offices and snack bars cost this share of their rent every day for every star above two. */
        const val RUNNING_COST_PERCENT = 5L

        /** Offices get dearer by this share of their price for every star above two. */
        const val OFFICE_PRICE_PER_STAR = 0.5

        /** Waiting longer than this many minutes for an elevator makes people grumpy. */
        const val PATIENCE_MINUTES = 3.0
        const val MEAL_MOOD = 15f
        const val STUCK_MOOD = 15f
        const val MEAL_PRICE = 12L
        /** Sightseers order the menu with dessert; there are fewer of them than residents. */
        const val VISITOR_MEAL_PRICE = 40L

        /** Every this many days each sold flat pays its fees. */
        const val FEE_EVERY_DAYS = 1
        const val HOME_FEE = 300L
        const val FLAT_BUYERS_BASE = 2
        const val UNHAPPY = 40f
        const val MOVE_OUT = 25f
        const val MIN_STAY_MINUTES = 2 * 1440.0

        /** Daily cost per room role. */
        /** Every elevator shaft already standing adds this share to the price of a new one. */
        const val SHAFT_SURCHARGE = 0.5

        /** Every shaft beyond the first adds this share to the upkeep of all elevator floors. */
        const val UPKEEP_SURCHARGE = 0.25

        val UPKEEP = mapOf(
            RoomRole.ELEVATOR to 60L, RoomRole.STAIRS to 10L, RoomRole.LOBBY to 40L, RoomRole.CORRIDOR to 5L,
            RoomRole.SPECIAL to 300L, RoomRole.SECURITY to 800L, RoomRole.TELEPORTER to 1_000L,
            RoomRole.PARKING to 400L, RoomRole.HOTEL to HOTEL_UPKEEP, RoomRole.HOUSEKEEPING to HOUSEKEEPING_UPKEEP,
        )
        const val HOTEL_UPKEEP = 400L
        const val HOUSEKEEPING_UPKEEP = 1_500L

        /** Hotel guests check in from [CHECK_IN_FROM] to [CHECK_IN_UNTIL] o'clock and leave the next morning. */
        const val CHECK_IN_FROM = 14
        const val CHECK_IN_UNTIL = 22
        const val CHECKOUT_HOUR = 10

        /** How long cleaning one hotel room takes, in game minutes. */
        const val CLEAN_MINUTES = 45

        /** Luxury tenants pay only half below this mood. */
        const val LUXURY_MOOD = 65f

        /** Parking spaces a luxury office needs before posh tenants move in and pay the full rent. */
        const val LUXURY_SPACES = 4

        /** The widest gap the gap filler closes; anything wider takes a room. */
        const val MAX_GAP = 5
        const val CARS_PER_OFFICE = 3
        const val CARS_PER_HOME = 1
        const val CARS_PER_PENTHOUSE = 2

        /** From this many stars offices need workers, see [workforce]. */
        const val WORKFORCE_STARS = 3

        /** Commuters the city sends for the offices of a tower with [stars] stars. */
        fun commuters(stars: Int): Int = when {
            stars < WORKFORCE_STARS -> 0
            stars == 3 -> 600
            stars == 4 -> 1_000
            else -> 1_600
        }

        /** Extra commuters a subway station brings. */
        const val SUBWAY_COMMUTERS = 1_500

        /** Students need side jobs: there are at most 1 / [STUDENT_JOBS] as many students as office workers. */
        const val STUDENT_JOBS = 2

        /** Every this many office workers bring one more flat buyer a day. */
        const val JOBS_PER_BUYER = 150

        /** Flat buyers a day each daycare brings. */
        const val KITA_BUYERS = 3

        /** Flats and dorms one corner shop serves. */
        const val SHOP_SERVES = 30

        /** Mood residents get when every flat and dorm is served by a shop. */
        const val SHOP_MOOD = 6f

        /** Mood flats lose on a floor with a dorm. */
        const val DORM_NOISE = 8f

        /** The lowest floor for a penthouse. */
        const val PENTHOUSE_FLOOR = 20

        /** A penthouse pays this many times the service charge of a flat. */
        const val PENTHOUSE_FEES = 4

        /** Share hotel guests pay on top while there is a conference centre. */
        const val CONFERENCE_HOTEL_BONUS = 0.5


        /** What the special rooms add to the mood people settle to. */
        const val COPYROOM_MOOD = 5f
        const val UFO_MOOD = 3f
        const val UFO_FEE = 15_000L

        /** Daily maintenance of one elevator cabin, on top of the shaft's per-floor upkeep. */
        const val CABIN_UPKEEP = 200L
        const val PARKING_FEE = 60L

        /** What tenants say when they leave over a real complaint. */
        val GRIEVANCE_REASONS = mapOf(
            Grievance.WAITING to Tr("Der Aufzug kam nie.", "The elevator never came."),
            Grievance.STUCK to Tr("Den Weg ins Büro hat keiner gefunden. Auch das Navi nicht.", "Nobody could find the way in. Not even the satnav."),
            Grievance.NO_FOOD to Tr("Kein Mittagessen in Sicht.", "No lunch in sight."),
        )

        /** What tenants make up when nothing in particular went wrong. */
        val MOVE_OUT_REASONS = listOf(
            Tr("Die Nachbarn haben geklatscht. Ständig.", "The neighbours kept clapping. Constantly."),
            Tr("Das Horoskop hat abgeraten.", "The horoscope advised against it."),
            Tr("Der Feng-Shui-Berater hat die Tür verboten.", "The feng shui consultant banned the door."),
        )
        const val STEP_MINUTES = 0.1

        /** [crazytower.core.ecs.Unhurried] systems run every this many ticks, half a game minute. */
        const val UNHURRIED_EVERY = 5
        val VISITOR_LOOKS = listOf("npc_tourist", "npc_tourist", "npc_courier", "npc_pizza", "npc_janitor", "npc_office_blazer", "npc_office_sleeves")
        val SPECTATOR_LOOKS = listOf("npc_tourist", "npc_tourist", "npc_kid", "npc_family_mom", "npc_family_dad", "npc_retiree", "npc_student_gamer", "npc_timetraveller")
    }
}
