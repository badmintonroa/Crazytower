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
            val candidates = Tenants.candidates(room.type.role, sim.stars, stage)
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

    /** Mostly offices and flats, a restaurant every few rooms and the stage's specials now and then. */
    private fun roomFor(stage: Int, floor: Int, i: Int): RoomType {
        if (floor == 1 && i == 4 && stage >= 3) return Catalog.SECURITY
        if (floor == 2 && i == 4 && stage >= 2) return Catalog.COPYROOM
        if (i == 5 && floor % 3 == 1) return Catalog.FOOD
        return if ((floor + i) % 2 == 0) Catalog.OFFICE else Catalog.HOME
    }
}
