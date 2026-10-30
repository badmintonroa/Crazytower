package crazytower.core

import crazytower.core.nav.NavCosts

/**
 * Why nobody moves into an empty room, shown over the room so the player can do something
 * about it. [far] problems are about the way there and get an angry face instead of an
 * exclamation mark.
 */
data class RoomProblem(val title: String, val hint: String, val far: Boolean = false)

object RoomProblems {
    /** Nobody rents a room whose way from the street takes longer than this, waiting for elevators included. */
    const val MAX_TRAVEL_MINUTES = 90f

    /**
     * What keeps tenants out of [room], or null if it is just a matter of time.
     * Expects an empty rentable room; the way there is looked up, so call it sparingly.
     */
    fun of(sim: Simulation, room: Room): RoomProblem? {
        // people come in from whichever end of the street is closer
        val street = sim.streetExit(room.centerX)
        val minutes = sim.nav.travelMinutes(street, room.center)
        return when {
            minutes == null && !sim.nav.hasLinksNear(room.center) -> RoomProblem(
                "Kein Weg hierher",
                "Keine Treppe und kein Aufzug, der auf dieser Etage hält. Bau einen bis hierher.",
                far = true,
            )
            minutes == null && sim.nav.connectedByAnyStairs(street, room.center) -> RoomProblem(
                "Zu viele Treppen",
                "Niemand steigt mehr als ${NavCosts.MAX_STAIRS_FLOORS} Etagen am Stück zu Fuß. Ein Aufzug hilft.",
                far = true,
            )
            minutes == null -> RoomProblem(
                "Kein Weg hierher",
                "Treppen und Aufzüge führen nicht vom Erdgeschoss bis hierher.",
                far = true,
            )
            minutes > MAX_TRAVEL_MINUTES -> RoomProblem(
                "Zu weit weg",
                "Der Weg dauert ${minutes.toInt()} Min., mehr als ${MAX_TRAVEL_MINUTES.toInt()} will keiner. " +
                    "Mehr Kabinen, ein Expressaufzug oder ein kürzerer Weg helfen.",
                far = true,
            )
            room.type.role == RoomRole.HOTEL && room.dirty &&
                sim.tower.allRooms.none { it.type.role == RoomRole.HOUSEKEEPING && it.tenant != null } -> RoomProblem(
                "Ungeputzt",
                "Gäste nehmen nur saubere Zimmer, und es gibt keinen Reinigungsdienst im Turm.",
            )
            Tenants.candidates(room.type, sim.stars, sim.stage).isEmpty() -> RoomProblem(
                "Keine Interessenten",
                "Für diesen Raum gibt es noch keine Mieter. Mehr Sterne locken neue an.",
            )
            else -> null
        }
    }
}
