package crazytower.core

/**
 * Builds a room on an upper floor the way a player would: a lobby underneath and stairs
 * from the street up to it, running through the room.
 */
fun Simulation.buildReachable(type: RoomType, x: Int, floor: Int = 1): Room {
    if ((x until x + type.width).any { tower.roomAt(GROUND_FLOOR, it) == null }) build(planLobby(x, x + type.width - 1))
    for (f in 1 until floor) if (tower.roomAt(f, x) == null) build(Catalog.CORRIDOR, f, x)
    val room = checkNotNull(build(type, floor, x).room) { checkPlace(type, floor, x).reason }
    val stairs = build(planColumn(Catalog.STAIRS, x, GROUND_FLOOR, floor))
    check(stairs.ok) { stairs.reason }
    return room
}
