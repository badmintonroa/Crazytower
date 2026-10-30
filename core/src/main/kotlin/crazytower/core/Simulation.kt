package crazytower.core

import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.World
import com.github.quillraven.fleks.configureWorld
import crazytower.core.ecs.Activity
import crazytower.core.ecs.Away
import crazytower.core.ecs.Cleaning
import crazytower.core.ecs.EconomySystem
import crazytower.core.ecs.ElevatorSystem
import crazytower.core.ecs.drop
import crazytower.core.ecs.Goal
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

/** What a drag would build: the pieces, their total price and whether it is allowed. */
data class BuildPlan(val type: RoomType, val pieces: List<Piece>, val cost: Long, val check: PlaceCheck) {
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
    var money: Long = startMoney
        internal set // test towers

    val finance = Finance()

    /** Absurd events every hour; tests that count money switch them off. */
    var eventsEnabled: Boolean = true

    /** Changes the money and writes it into the books. */
    fun book(amount: Long, kind: MoneyKind, label: String, where: Loc? = null) {
        if (amount == 0L) return
        money += amount
        finance.record(Payment(clock.totalMinutes, amount, kind, label, where))
    }

    /** Rent and prices grow with the star rating: 20 % per star above the first. */
    /** What the next star still needs, e.g. "ein Imbiss"; empty at five stars. */
    val nextStarMissing: List<String> get() = Rating.missing(stars, population, satisfaction, hasFood)

    private val hasFood: Boolean get() = tower.allRooms.any { it.type.role == RoomRole.FOOD && it.tenant != null }

    val starBonus: Double get() = 1.0 + 0.2 * (stars - 1).coerceAtLeast(0)

    val world: World = configureWorld(entityCapacity = 2048) {
        injectables { add(this@Simulation) }
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

    fun checkPlace(type: RoomType, floor: Int, x: Int, width: Int = type.width): PlaceCheck {
        val rules = checkRules(type, floor, x, width)
        if (!rules.ok) return rules
        if (money < priceAt(type, floor, x, width)) return PlaceCheck.no("Zu wenig Geld")
        return tower.canPlace(type, floor, x, width)
    }

    /** Width of the building lot in segments; it grows with the best stars, see [Lot]. */
    val lotWidth: Int get() = Lot.width(bestStars)

    /** Whether the basement may be dug. */
    val basementOpen: Boolean get() = bestStars >= Lot.BASEMENT_STARS

    /** What stage, stars and lot allow, before money and the tower itself are asked. */
    private fun checkRules(type: RoomType, floor: Int, x: Int, width: Int): PlaceCheck {
        if (type.minStage > stage) return PlaceCheck.no("Erst ab Stufe ${type.minStage}")
        if (type.minStars > bestStars) return PlaceCheck.no("Erst ab ${type.minStars} Sternen")
        val maxFloor = Stages.info(stage).maxFloor
        if (floor >= maxFloor) return PlaceCheck.no("Stufe $stage erlaubt nur $maxFloor Etagen")
        if (floor < GROUND_FLOOR && !basementOpen) return PlaceCheck.no("Keller erst ab ${Lot.BASEMENT_STARS} Sternen")
        Tower.skyLobbiesBelow(floor).firstOrNull { sky -> tower.allRooms.none { it.floor == sky && it.type.role == RoomRole.LOBBY } }?.let {
            return PlaceCheck.no("Erst eine Sky-Lobby auf der $it. Etage bauen")
        }
        if (type.basementOnly && floor >= GROUND_FLOOR) return PlaceCheck.no("${type.name} nur im Keller")
        val max = type.maxCount
        if (max != null && tower.allRooms.count { it.type == type } >= max) {
            return PlaceCheck.no(if (max == 1) "${type.name} gibt es schon, nur einmal pro Turm" else "Schon $max ${type.name}, mehr gehen nicht")
        }
        if (type.role == RoomRole.PARKING) {
            val missing = parkingSpaces + MIN_BOOKINGS - carsWanted
            if (missing > 0) return PlaceCheck.no("Noch $missing Autos zu wenig für ein weiteres Parkhaus. Mehr Büros und Wohnungen!")
        }
        if (x >= 0 && x + width > lotWidth) {
            val next = Lot.next(bestStars)
            return PlaceCheck.no(if (next != null) "Grundstück wird erst ab ${next.stars} Sternen breiter" else "Außerhalb des Grundstücks")
        }
        return PlaceCheck.OK
    }

    /**
     * Elevator shafts in the tower besides the one a module at [x] on [floor] would belong to.
     * Every one of them makes the next shaft dearer, so elevators are not built everywhere.
     */
    fun otherShafts(type: RoomType, floor: Int, x: Int): Int = elevators.cars.values.count {
        !(it.x == x && it.express == type.express && floor in it.bottom - 1..it.top + 1)
    }

    /** What building [type] at [x] on [floor] costs right now. Elevators get dearer with each shaft. */
    fun priceAt(type: RoomType, floor: Int, x: Int, width: Int = type.width): Long {
        val base = type.price(width)
        if (type.role != RoomRole.ELEVATOR) return base
        return (base * (1 + SHAFT_SURCHARGE * otherShafts(type, floor, x))).toLong()
    }

    fun build(type: RoomType, floor: Int, x: Int, width: Int = type.width): BuildResult {
        val check = checkPlace(type, floor, x, width)
        if (!check.ok) return BuildResult(null, check.reason)
        val firstLobby = type.role == RoomRole.LOBBY && tower.allRooms.none { it.type.role == RoomRole.LOBBY }
        var room = tower.place(type, floor, x, width)
        book(-priceAt(type, floor, x, width), MoneyKind.BUILD, type.name, Loc(floor, x + width / 2f))
        room.since = clock.totalMinutes
        if (type.role == RoomRole.LOBBY) room = tower.mergeLobbies() ?: room
        if (firstLobby) {
            news.post("Die erste Lobby ist offen. Ein Mann fragt, ob es hier zum Bahnhof geht.", Loc(floor, room.centerX))
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
        var check = if (floor >= maxFloor) PlaceCheck.no("Stufe $stage erlaubt nur $maxFloor Etagen") else PlaceCheck.OK
        for (piece in pieces) {
            if (!check.ok) break
            val c = tower.canPlace(Catalog.LOBBY, floor, piece.first, piece.last - piece.first + 1)
            if (!c.ok) { check = c; break }
        }
        if (check.ok && cost > money) check = PlaceCheck.no("Zu wenig Geld")
        if (check.ok && pieces.isEmpty()) check = PlaceCheck.no("Hier ist schon Lobby")
        return BuildPlan(Catalog.LOBBY, pieces.map { BuildPlan.Piece(floor, it.first, it.last - it.first + 1) }, cost, check)
    }

    /**
     * The gap filler for a click at [segment] on [floor]: the whole empty stretch around it,
     * between the neighbouring rooms or the lot edge, if it is at most [MAX_GAP] wide.
     */
    fun planGap(floor: Int, segment: Int): BuildPlan {
        val type = Catalog.FILLER
        fun empty(s: Int) = s in 0 until lotWidth && tower.roomAt(floor, s) == null
        if (!empty(segment)) return BuildPlan(type, emptyList(), 0, PlaceCheck.no("Hier ist keine Lücke"))
        var lo = segment
        var hi = segment
        while (empty(lo - 1) && hi - lo < MAX_GAP) lo--
        while (empty(hi + 1) && hi - lo < MAX_GAP) hi++
        val width = hi - lo + 1
        val piece = BuildPlan.Piece(floor, lo, width)
        val cost = type.price(width)
        val check = when {
            width > MAX_GAP -> PlaceCheck.no("Zu große Lücke, hier passt ein ganzer Raum")
            else -> checkRules(type, floor, lo, width).takeUnless { it.ok }
                ?: tower.canPlace(type, floor, lo, width).takeUnless { it.ok }
                ?: if (cost > money) PlaceCheck.no("Zu wenig Geld") else PlaceCheck.OK
        }
        return BuildPlan(type, listOf(piece), cost, check)
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
        // build outward from the floor that is carried: up from below, down from the ground in the basement
        floors = floors.sortedBy { if (it >= GROUND_FLOOR) it else -it }
        val fresh = floors.filter { f -> tower.wayAt(f, x)?.let { it.type == type && it.x == x } != true }
        val cost = fresh.sumOf { priceAt(type, it, x) }
        var check = PlaceCheck.OK
        val assumed = HashSet<Int>()
        for (f in fresh) {
            if (!check.ok) break
            check = checkRules(type, f, x, type.width).takeUnless { it.ok }
                ?: tower.canPlace(type, f, x, assumed = assumed)
            assumed += f
        }
        if (check.ok && cost > money) check = PlaceCheck.no("Zu wenig Geld")
        if (check.ok && fresh.isEmpty()) check = PlaceCheck.no("Hier ist schon ${type.name}")
        return BuildPlan(type, fresh.map { BuildPlan.Piece(it, x, type.width) }, cost, check)
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
        car.cabins.size >= Elevators.MAX_CABINS -> PlaceCheck(false, "Mehr Kabinen passen nicht in den Schacht")
        car.cabins.size >= Elevators.maxCabins(bestStars) -> PlaceCheck(false, "Mehr Kabinen ab ${bestStars + 1} Sternen")
        money < Elevators.CABIN_COST -> PlaceCheck(false, "Zu wenig Geld")
        else -> PlaceCheck(true)
    }

    /** Buys one more cabin for [car]; the shaft's modules remember it for the save game. */
    fun addCabin(car: Car): PlaceCheck {
        val check = checkCabin(car)
        if (!check.ok) return check
        book(-Elevators.CABIN_COST, MoneyKind.BUILD, "Aufzugkabine", Loc(car.bottom, car.centerX))
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
    fun refundFor(room: Room): Long = if (room.sold) 0 else room.type.price(room.width) / 4

    fun checkDemolish(room: Room): PlaceCheck = tower.canRemove(room)

    /** Tears a room down and returns the refund. */
    fun demolish(roomId: Int): Long {
        val room = tower.room(roomId) ?: return 0
        if (!tower.canRemove(room).ok) return 0
        room.tenant?.let { news.post("${it.name} zieht aus. Der Abrissbagger war sehr überzeugend.", room.center) }
        removeMembers(roomId)
        tower.remove(roomId)
        val refund = refundFor(room)
        book(refund, MoneyKind.BUILD, "Abriss ${room.type.name}", room.center)
        // the owners get their whole purchase price back: the flat is gone
        if (room.tenant != null && room.salePaid > 0) book(-room.salePaid, MoneyKind.REFUND, "Kaufpreis zurück", room.center)
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
            book(room.salePaid, MoneyKind.SALE, tenant.name, room.center)
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
            if (random.nextInt(4) == 0) news.post("Check-in: ${tenant.name} (${floorLabel(room.floor)}). ${tenant.quirk}", room.center)
        } else if (announce) news.post("Neu im Turm: ${tenant.name} (${floorLabel(room.floor)}). ${tenant.quirk}", room.center)
    }

    /** Unhappy tenants leave; the first owners of a flat get half their price back, once. */
    fun moveOut(room: Room) {
        removeMembers(room.id)
        if (room.salePaid > 0) book(-room.salePaid / 2, MoneyKind.REFUND, "Kaufpreis zurück", room.center)
        room.salePaid = 0
        // guests leave a mess behind, however long they stayed
        if (room.type.role == RoomRole.HOTEL) room.dirty = true
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
    val earnedToday: Long get() = finance.income(clock.day)

    fun earn(amount: Long) = book(amount, MoneyKind.GUESTS, "Gäste")

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
            book(rentFor(room, tenant), if (room.type.role == RoomRole.FOOD) MoneyKind.FOOD else MoneyKind.RENT, tenant.name, room.center)
        }
    }

    /** Hotel guests who checked in at [minute] leave the next morning at [CHECKOUT_HOUR]. */
    private fun checkoutAfter(minute: Double): Double =
        ((minute / GameClock.MINUTES_PER_DAY).toInt() + 1) * GameClock.MINUTES_PER_DAY + CHECKOUT_HOUR * 60.0

    /** The guests pay for their night and go; the room stays dirty until somebody cleans it. */
    private fun checkOut(room: Room, tenant: TenantKind) {
        book(rentFor(room, tenant), MoneyKind.HOTEL, tenant.name, room.center)
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

    private fun rentFor(room: Room, tenant: TenantKind): Long {
        val mood = moodOf(room) ?: 70f
        val full = (tenant.rentPerDay * starBonus).toLong()
        return if (mood < (if (room.type.luxury) LUXURY_MOOD else UNHAPPY)) full / 2 else full
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
            if (end <= horizon) list += Upcoming(end, rentFor(room, tenant), tenant.name, room.center)
        }
        var midnight = (clock.day + 1) * GameClock.MINUTES_PER_DAY
        while (midnight <= horizon) {
            val day = midnight / GameClock.MINUTES_PER_DAY
            if (day % FEE_EVERY_DAYS == 0) {
                val fees = homeFees()
                if (fees > 0) list += Upcoming(midnight, fees, "Hausgeld")
            }
            list += Upcoming(midnight, -upkeep(), "Unterhalt")
            midnight += GameClock.MINUTES_PER_DAY
        }
        return list.groupBy { it.minute to it.label }.values
            .map { same -> same.first().copy(amount = same.sumOf { it.amount }, count = same.sumOf { it.count }) }
            .sortedBy { it.minute }
    }

    private fun homeFees(): Long = tower.allRooms.count { it.type.role == RoomRole.HOME && it.tenant != null && it.tenant!!.salePrice > 0 } * (HOME_FEE * starBonus).toLong()

    private fun upkeep(): Long = tower.allRooms.sumOf { it.type.upkeep ?: ((UPKEEP[it.type.role] ?: 0L) * (if (it.type.perSegment) it.width else 6) / 6) } +
        elevators.cars.values.sumOf { it.cabins.size } * CABIN_UPKEEP + shaftSurcharge()

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
        // the special rooms do their bit every night
        val hasCopyroom = tower.allRooms.any { it.type == Catalog.COPYROOM }
        val ufoPad = tower.allRooms.firstOrNull { it.type == Catalog.UFO_PAD }
        if (hasCopyroom || ufoPad != null) members.forEach {
            val schedule = it[Schedule]
            var lift = if (ufoPad != null) UFO_MOOD else 0f
            if (hasCopyroom && !it[Member].resident) lift += COPYROOM_MOOD
            schedule.mood = (schedule.mood + lift).coerceAtMost(100f)
        }
        if (ufoPad != null) book(UFO_FEE, MoneyKind.GUESTS, "Ufo-Landegebühr", ufoPad.center)
        val yesterday = clock.day - 1
        book(-upkeep(), MoneyKind.UPKEEP, "Unterhalt", tower.allRooms.firstOrNull { it.type.role == RoomRole.LOBBY }?.center)
        if (clock.day % FEE_EVERY_DAYS == 0) book(homeFees(), MoneyKind.FEES, "Hausgeld")
        val income = finance.income(yesterday)
        news.post("Tagesabschluss: Einnahmen ${income.fmt()} $, Unterhalt ${upkeep().fmt()} $. " +
            if (income >= upkeep()) "Der Turm lebt." else "Der Steuerberater weint leise.")
        for (room in leaving) {
            val name = room.tenant?.name ?: continue
            moveOut(room)
            news.post("$name zieht aus. Grund: ${MOVE_OUT_REASONS[random.nextInt(MOVE_OUT_REASONS.size)]}", room.center, important = true)
        }
        val newStars = Rating.stars(population, satisfaction, hasFood)
        if (newStars > stars) {
            news.post("Der Turm hat jetzt $newStars Sterne! Neue Mieter werden neugierig.", important = true)
            val oldLot = lotWidth
            val hadBasement = basementOpen
            stars = newStars
            if (lotWidth > oldLot) {
                news.post("Die Stadt verkauft dir das Nachbargrundstück. Der Turm darf jetzt $lotWidth Felder breit werden. Der Nachbar zieht murrend mit seinem Gartenzwerg um.", Loc(GROUND_FLOOR, oldLot.toFloat()), important = true)
            }
            if (basementOpen && !hadBasement) {
                news.post("Der Keller ist frei! Bis zur ${-Tower.MIN_FLOOR}. Untergeschoss darf gegraben werden, zum Beispiel für ein Parkhaus. Die Maulwürfe sind informiert.", important = true)
            }
        }
        val fees = parkingFees()
        if (fees > 0) book(fees, MoneyKind.PARKING, "Parkgebühren", tower.allRooms.firstOrNull { it.type.role == RoomRole.PARKING }?.center)
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
            moveOut(room)
            news.post("$name zieht aus: zu schlechte Laune im Turm. ${MOVE_OUT_REASONS[random.nextInt(MOVE_OUT_REASONS.size)]}", room.center, important = true)
        }
    }

    /** Parking spaces the rented offices and occupied flats want to book. */
    val carsWanted: Int get() = tower.allRooms.sumOf {
        when {
            it.tenant == null -> 0
            it.type.role == RoomRole.OFFICE -> CARS_PER_OFFICE
            it.type.role == RoomRole.HOME -> CARS_PER_HOME
            else -> 0
        }
    }

    /** Spaces in all car parks. */
    val parkingSpaces: Int get() = tower.allRooms.count { it.type.role == RoomRole.PARKING } * CARS_PER_PARKING

    /** Booked spaces: as many as are wanted, as long as there are spaces. */
    val parkedCars: Int get() = minOf(parkingSpaces, carsWanted)

    /** Daily parking fees for [parkedCars]. */
    fun parkingFees(): Long = (parkedCars * PARKING_FEE * starBonus).toLong()

    private fun Long.fmt(): String = "%,d".format(this).replace(',', '.')

    /** Flats sold since midnight; buyers are scarce, see [flatBuyersPerDay]. */
    var flatsSoldToday: Int = 0
        private set

    /**
     * How many flats can be sold in one day. A flat pays back its building cost when it is
     * sold, so without this limit flats could be built and sold endlessly in one go.
     */
    val flatBuyersPerDay: Int get() = FLAT_BUYERS_BASE + stars

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

    /** Checks whether the tower reached the next stage. Called every game hour. */
    fun checkStage() {
        if (stage >= Stages.last || !Stages.reached(this, stage + 1)) return
        stage++
        looksVersion++ // the lobbies get a new look
        val info = Stages.info(stage)
        news.post("STUFE $stage: Der Turm ist jetzt ein ${info.name}! Bis zur ${info.maxFloor}. Etage darf gebaut werden. ${info.unlocks}", important = true)
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
                it.width.takeIf { w -> w != it.type.width }, it.paidUntil, it.salePaid, it.cabins, it.sold, it.dirty,
            )
        },
        news = news.latest.map { SavedNews(it.text, it.where?.floor, it.where?.x, it.important) },
    )

    private fun restore(save: SaveGame) {
        clock.set(save.minutes)
        money = save.money
        stage = save.stage
        stars = save.stars
        bestStars = maxOf(save.bestStars, save.stars)
        vipSurvived = save.vipSurvived
        for (saved in save.rooms) {
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
        }
        for (item in save.news.asReversed()) {
            news.post(item.text, item.floor?.let { Loc(it, item.x ?: 0f) }, item.important)
        }
    }

    /** A teleporter sent [entity] to the wrong place. */
    fun onMisfire(entity: Entity, landed: Room) {
        if (random.nextInt(3) == 0) {
            val name = with(world) { entity[Person].name }
            news.post("$name wollte teleportieren und ist in der ${floorLabel(landed.floor)} gelandet. Nimmt es gelassen.", landed.center)
        }
    }

    /** Street exit closest to [x]: people leave the tower to the left or right edge. */
    fun streetExit(x: Float): Loc = Loc(GROUND_FLOOR, if (x < lotWidth / 2f) 0f else lotWidth - 0.01f)

    fun spawnVisitor(): Entity {
        val fromLeft = random.nextBoolean()
        val start = if (fromLeft) 0f else lotWidth - 0.01f
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
                Activity.TO_CLEAN -> {
                    val target = entity.getOrNull(Cleaning)?.let { tower.room(it.roomId) }
                    entity.configure { drop(it, Cleaning) }
                    if (target != null && target.dirty) {
                        schedule.activity = Activity.CLEANING
                        entity.configure { it += Idle(clock.totalMinutes + CLEAN_MINUTES) }
                        cleaned(target)
                    }
                }
                else -> Unit
            }
            return@with
        }
        if (entity has Vip) {
            vipSurvived = true
            news.post("Frau Dr. Wichtig ist angekommen und nickt anerkennend. Der Turm ist bereit für Größeres.", Loc(entity[Position].floor, entity[Position].x), important = true)
            entity.configure { drop(it, Vip) }
        }
        if (visitor != null && !visitor.leaving) {
            val room = tower.roomAt(entity[Position].floor, entity[Position].x.toInt())
            if (room?.type?.role == RoomRole.FOOD && room.tenant != null) earn(VISITOR_MEAL_PRICE)
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
        if (entity has Vip) {
            news.post("Frau Dr. Wichtig hat den Weg nicht gefunden und reist beleidigt ab. Vielleicht ein Aufzug mehr?", important = true)
            entity.configure { drop(it, Vip) }
        }
        val visitor = entity.getOrNull(Visitor) ?: return@with
        if (visitor.leaving) entity.remove() else visitor.stopsLeft = 0
    }

    companion object {
        /** Rebuilds a simulation from a save game. */
        fun load(save: SaveGame, seed: Long = 1L): Simulation {
            require(save.version <= SaveGame.VERSION) { "Spielstand ist von einer neueren Version" }
            return Simulation(seed, startMoney = 0).also { it.restore(save) }
        }

        fun floorLabel(floor: Int): String = when {
            floor == 0 -> "Erdgeschoss"
            floor < 0 -> "${-floor}. Untergeschoss"
            else -> "$floor. Etage"
        }

        const val START_MONEY = 120_000L

        /** Waiting longer than this many minutes for an elevator makes people grumpy. */
        const val PATIENCE_MINUTES = 3.0
        const val MEAL_MOOD = 8f
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
        const val LUXURY_MOOD = 70f

        /** Cars one car park holds, people per car, and the daily fee per parked car. */
        const val CARS_PER_PARKING = 40

        /** The widest gap the gap filler closes; anything wider takes a room. */
        const val MAX_GAP = 5
        const val CARS_PER_OFFICE = 3
        const val CARS_PER_HOME = 1

        /** A new car park needs at least this many cars that find no space yet. */
        const val MIN_BOOKINGS = CARS_PER_PARKING / 2

        /** What the special rooms do every night. */
        const val COPYROOM_MOOD = 5f
        const val UFO_MOOD = 3f
        const val UFO_FEE = 15_000L

        /** Daily maintenance of one elevator cabin, on top of the shaft's per-floor upkeep. */
        const val CABIN_UPKEEP = 200L
        const val PARKING_FEE = 60L

        val MOVE_OUT_REASONS = listOf(
            "Der Aufzug kam nie.",
            "Zu viel Treppe, zu wenig Leben.",
            "Die Nachbarn haben geklatscht. Ständig.",
            "Kein Mittagessen in Sicht.",
            "Das Horoskop hat abgeraten.",
        )
        const val STEP_MINUTES = 0.1
        val VISITOR_LOOKS = listOf("npc_tourist", "npc_tourist", "npc_courier", "npc_pizza", "npc_janitor", "npc_office_blazer", "npc_office_sleeves")
    }
}
