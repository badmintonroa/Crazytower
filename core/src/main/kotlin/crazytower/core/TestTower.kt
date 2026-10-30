package crazytower.core

/**
 * Ready-made towers for trying out a stage without playing up to it: the stage and as many
 * stars, full floors with tenants, elevators with all the cabins the stars allow, and money.
 */
object TestTower {
    /** Floors above the lobby for each stage: fewer people than the next stage needs, and few enough to stay smooth. */
    val FLOORS = listOf(4, 5, 18, 40, 55, 70)

    const val MONEY = 2_000_000L

    /**
     * Elevator columns, each made of shafts that hand over to the next one up, spaced so they
     * keep [Tower.ELEVATOR_GAP] apart. The second column serves the lower half.
     */
    private val COLUMNS = listOf(listOf(2, 16, 30), listOf(44, 58))
    private const val SHAFT_FLOORS = 25

    fun build(stage: Int, seed: Long = 1L): Simulation {
        val sim = Simulation(seed = seed, startMoney = 100_000_000)
        sim.eventsEnabled = false
        sim.stage = stage
        sim.stars = stage.coerceIn(0, 5)
        // only the first lot is filled, so the tower stays smooth and the plots bought later are free to try out
        val width = Lot.steps.first().width
        sim.build(sim.planLobby(0, width - 1))
        val floors = FLOORS[stage.coerceIn(0, FLOORS.lastIndex)].coerceAtMost(Stages.info(stage).maxFloor - 1)
        for (floor in 1..floors) {
            // every 25th floor is a sky lobby, the floors above need it
            if (floor > GROUND_FLOOR && Tower.isLobbyFloor(floor)) {
                sim.build(sim.planLobby(0, width - 1, floor))
                continue
            }
            var x = 0
            var i = 0
            while (x + 6 <= width) {
                sim.build(roomFor(stage, floor, i), floor, x)
                x += 6
                i++
            }
            if (x < width) sim.build(Catalog.CORRIDOR, floor, x, width - x)
        }
        // elevators: each shaft covers SHAFT_FLOORS floors, the next one starts where it ends
        for (column in COLUMNS) {
            column.forEachIndexed { k, x ->
                val bottom = k * (SHAFT_FLOORS - 1)
                if (bottom >= floors) return@forEachIndexed
                val top = minOf(bottom + SHAFT_FLOORS - 1, floors)
                sim.build(sim.planColumn(Catalog.ELEVATOR, x, bottom, top))
            }
        }
        for (car in sim.elevators.cars.values) {
            while (sim.checkCabin(car).ok) sim.addCabin(car)
        }
        for (room in sim.tower.allRooms) {
            val candidates = Tenants.candidates(room.type, sim.stars, stage)
            if (candidates.isEmpty() || room.type.capacity == 0) continue
            val tenant = candidates[sim.random.nextInt(candidates.size)]
            sim.moveIn(room, tenant, announce = false)
            if (room.type.role == RoomRole.HOME) room.sold = true
        }
        // car parks once the tenants are in and want spaces
        var x = 8
        while (sim.basementOpen && sim.build(Catalog.PARKING, -1, x).ok) x += 6
        if (stage >= 5) sim.vipSurvived = true
        sim.money = MONEY
        sim.eventsEnabled = true
        sim.news.post("Testturm Stufe $stage: ${sim.population} Leute, ${sim.stars} Sterne. Wird nicht gespeichert.", important = true)
        return sim
    }

    /** The number that stands for the maximum tower, one past the last stage. */
    val MAXIMUM: Int get() = Stages.last + 1

    /** The stage tower for [number] 1 to 5, or the maximum tower for [MAXIMUM]. */
    fun create(number: Int, seed: Long = 1L): Simulation = if (number >= MAXIMUM) buildMaximum(seed) else build(number, seed)

    /** Elevator slots for the maximum tower, [Tower.ELEVATOR_GAP] apart: express at the edges, locals in between. */
    private val EXPRESS_X = listOf(2, 142)
    private val LOCAL_X = listOf(listOf(30, 58, 86, 114), listOf(44, 72, 100, 128))

    /**
     * Everything at once, as a stress test: the widest lot, every floor up to the top with sky
     * lobbies, a full basement, every special room, express and local elevators with all
     * cabins, five stars and a tenant in every room.
     */
    fun buildMaximum(seed: Long = 1L): Simulation {
        val sim = Simulation(seed = seed, startMoney = 1_000_000_000)
        sim.eventsEnabled = false
        sim.stage = Stages.last
        sim.stars = 5
        sim.vipSurvived = true
        val width = Lot.maxWidth
        val top = Stages.info(Stages.last).maxFloor - 1
        sim.build(sim.planLobby(0, width - 1))
        for (floor in 1..top) {
            if (Tower.isLobbyFloor(floor)) {
                sim.build(sim.planLobby(0, width - 1, floor))
                continue
            }
            fillFloor(sim, floor, width, teleporter = floor == 10 || floor == 60) { i ->
                when {
                    floor == top && i == 0 -> Catalog.UFO_PAD
                    floor == 1 && i == 4 -> Catalog.SECURITY
                    floor == 2 && i == 4 -> Catalog.COPYROOM
                    else -> roomFor(Stages.last, floor, i)
                }
            }
        }
        // express shafts through the whole tower, local shafts per 25-floor zone (alternating slots,
        // so the zones above and below never stack into one shaft), and one into the basement
        for (x in EXPRESS_X) sim.build(sim.planColumn(Catalog.EXPRESS, x, GROUND_FLOOR, top))
        for (zone in 0..top / Tower.SKY_LOBBY_EVERY) {
            val bottom = zone * Tower.SKY_LOBBY_EVERY
            val zoneTop = minOf(bottom + Tower.SKY_LOBBY_EVERY - 1, top)
            for (x in LOCAL_X[zone % 2]) sim.build(sim.planColumn(Catalog.ELEVATOR, x, bottom, zoneTop))
        }
        moveEveryoneIn(sim, Stages.last)
        // the basement: car parks as long as the tenants want spaces, offices in the rest
        for (floor in -1 downTo Tower.MIN_FLOOR) {
            fillFloor(sim, floor, width) { _ -> if (sim.checkPlace(Catalog.PARKING, floor, 0).ok) Catalog.PARKING else Catalog.OFFICE }
        }
        sim.build(sim.planColumn(Catalog.ELEVATOR, LOCAL_X[1][0], Tower.MIN_FLOOR, GROUND_FLOOR))
        moveEveryoneIn(sim, Stages.last)
        for (car in sim.elevators.cars.values) {
            while (sim.checkCabin(car).ok) sim.addCabin(car)
        }
        sim.money = MONEY
        sim.eventsEnabled = true
        sim.news.post("Maximum-Testturm: ${sim.population} Leute, alles voll. Wird nicht gespeichert.", important = true)
        return sim
    }

    /**
     * Rooms of six from the left, [pick] chooses each one. The narrow rest at the right end
     * becomes corridor, so the floor above has something to stand on, with a [teleporter] in it.
     */
    private fun fillFloor(sim: Simulation, floor: Int, width: Int, teleporter: Boolean = false, pick: (Int) -> RoomType) {
        var x = 0
        var i = 0
        while (x + 6 <= width) {
            val type = pick(i)
            if (!sim.build(type, floor, x).ok) sim.build(Catalog.OFFICE, floor, x)
            x += 6
            i++
        }
        if (teleporter && x + Catalog.TELEPORTER.width <= width && sim.build(Catalog.TELEPORTER, floor, x).ok) x += Catalog.TELEPORTER.width
        if (x < width) sim.build(Catalog.CORRIDOR, floor, x, width - x)
    }

    private fun moveEveryoneIn(sim: Simulation, stage: Int) {
        for (room in sim.tower.allRooms) {
            if (room.tenant != null || room.type.capacity == 0) continue
            val candidates = Tenants.candidates(room.type.role, sim.stars, stage)
            if (candidates.isEmpty()) continue
            sim.moveIn(room, candidates[sim.random.nextInt(candidates.size)], announce = false)
            if (room.type.role == RoomRole.HOME) room.sold = true
        }
    }

    /** Mostly offices and flats, a restaurant every few rooms and the stage's specials now and then. */
    private fun roomFor(stage: Int, floor: Int, i: Int): RoomType {
        if (floor == 1 && i == 4 && stage >= 3) return Catalog.SECURITY
        if (floor == 2 && i == 4 && stage >= 2) return Catalog.COPYROOM
        if (i == 5 && floor % 3 == 1) return Catalog.FOOD
        return if ((floor + i) % 2 == 0) Catalog.OFFICE else Catalog.HOME
    }
}
