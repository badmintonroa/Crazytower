package crazytower.core

import crazytower.core.nav.NavCosts

/**
 * Why nobody moves into an empty room, shown over the room so the player can do something
 * about it. [far] problems are about the way there and get an angry face instead of an
 * exclamation mark.
 */
data class RoomProblem(val title: Tr, val hint: Tr, val far: Boolean = false)

object RoomProblems {
    /** Nobody rents a room whose way from the street takes longer than this, waiting for elevators included. */
    const val MAX_TRAVEL_MINUTES = 90f

    /**
     * What keeps tenants out of [room], or null if it is just a matter of time.
     * Expects an empty rentable room. [minutes] is the way from the street; looking it up is a
     * search, so for many rooms pass it from [crazytower.core.nav.NavGraph.travelMinutesFrom].
     */
    fun of(
        sim: Simulation,
        room: Room,
        // people come in from whichever end of the street is closer
        street: Loc = sim.streetExit(room.centerX),
        minutes: Float? = sim.nav.travelMinutes(street, room.center),
    ): RoomProblem? {
        return when {
            minutes == null && !sim.nav.hasLinksNear(room.center) -> RoomProblem(
                Tr("Kein Weg hierher", "No way here"),
                Tr("Keine Treppe und kein Aufzug, der auf dieser Etage hält. Bau einen bis hierher.", "No stairs and no elevator stop on this floor. Build one up to here."),
                far = true,
            )
            minutes == null && sim.nav.connectedByAnyStairs(street, room.center) -> RoomProblem(
                Tr("Zu viele Treppen", "Too many stairs"),
                Tr(
                    "Niemand steigt mehr als ${NavCosts.MAX_STAIRS_FLOORS} Etagen am Stück zu Fuß. Ein Aufzug hilft.",
                    "Nobody walks more than ${NavCosts.MAX_STAIRS_FLOORS} floors in one go. An elevator helps.",
                ),
                far = true,
            )
            minutes == null -> RoomProblem(
                Tr("Kein Weg hierher", "No way here"),
                Tr("Treppen und Aufzüge führen nicht vom Erdgeschoss bis hierher.", "Stairs and elevators don't lead from the ground floor to here."),
                far = true,
            )
            minutes > MAX_TRAVEL_MINUTES -> RoomProblem(
                Tr("Zu weit weg", "Too far away"),
                Tr(
                    "Der Weg dauert ${minutes.toInt()} Min., mehr als ${MAX_TRAVEL_MINUTES.toInt()} will keiner. " +
                        "Mehr Kabinen, ein Expressaufzug oder ein kürzerer Weg helfen.",
                    "The way takes ${minutes.toInt()} min., nobody wants more than ${MAX_TRAVEL_MINUTES.toInt()}. " +
                        "More cabins, an express elevator or a shorter way help.",
                ),
                far = true,
            )
            room.type.role == RoomRole.OFFICE && !sim.hasWorkersFor(room.type.capacity) -> RoomProblem(
                Tr("Keine Fachkräfte", "No staff"),
                Tr(
                    "Die Firmen finden keine Leute: ${sim.jobs} Stellen, nur ${sim.workforce} Arbeitskräfte. " +
                        "Mehr Wohnungen oder Wohnheime im Turm helfen" + if (sim.stars >= 4) ", oder eine U-Bahn-Station." else ", ab 4 Sternen auch eine U-Bahn-Station.",
                    "The companies can't find people: ${sim.jobs} jobs, only ${sim.workforce} workers. " +
                        "More flats or dorms in the tower help" + if (sim.stars >= 4) ", or a subway station." else ", from 4 stars a subway station too.",
                ),
            )
            room.type.role == RoomRole.DORM && !sim.hasSideJobsFor(room.type.capacity) -> RoomProblem(
                Tr("Keine Nebenjobs", "No side jobs"),
                Tr(
                    "Studis brauchen Nebenjobs: höchstens halb so viele Studis wie Büro-Angestellte (${sim.students} Studis, ${sim.jobs} Angestellte). Mehr Büros helfen.",
                    "Students need side jobs: at most half as many students as office workers (${sim.students} students, ${sim.jobs} workers). More offices help.",
                ),
            )
            room.type.luxuryOffice && !sim.hasParking(room) -> RoomProblem(
                Tr("Keine Parkplätze", "No parking"),
                if (sim.basementOpen) {
                    Tr(
                        "Feine Mieter kommen nur mit ${Simulation.LUXURY_SPACES} eigenen Parkplätzen. Bau mehr Parkplätze im Keller.",
                        "Posh tenants only come with ${Simulation.LUXURY_SPACES} parking spaces of their own. Build more spaces in the basement.",
                    )
                } else {
                    Tr(
                        "Feine Mieter kommen nur mit ${Simulation.LUXURY_SPACES} eigenen Parkplätzen, und der Keller öffnet erst ab ${Lot.BASEMENT_STARS} Sternen.",
                        "Posh tenants only come with ${Simulation.LUXURY_SPACES} parking spaces of their own, and the basement only opens at ${Lot.BASEMENT_STARS} stars.",
                    )
                },
            )
            room.type.role == RoomRole.HOTEL && room.dirty &&
                sim.tower.allRooms.none { it.type.role == RoomRole.HOUSEKEEPING && it.tenant != null } -> RoomProblem(
                Tr("Ungeputzt", "Not cleaned"),
                Tr("Gäste nehmen nur saubere Zimmer, und es gibt keinen Reinigungsdienst im Turm.", "Guests only take clean rooms, and there is no housekeeping in the tower."),
            )
            Tenants.candidates(room.type, sim.stars, sim.stage).isEmpty() -> RoomProblem(
                Tr("Keine Interessenten", "No takers"),
                Tr("Für diesen Raum gibt es noch keine Mieter. Mehr Sterne locken neue an.", "There are no tenants for this room yet. More stars attract new ones."),
            )
            else -> null
        }
    }
}
