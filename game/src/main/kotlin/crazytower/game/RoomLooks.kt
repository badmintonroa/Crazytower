package crazytower.game

import crazytower.core.Room
import crazytower.core.Simulation

/** Chooses the art for a room from its type (and later its tenant). */
object RoomLooks {
    fun art(sim: Simulation, room: Room): String = when {
        room.type.role == crazytower.core.RoomRole.LOBBY -> crazytower.core.Stages.info(sim.stage).lobbyArt
        room.type.role == crazytower.core.RoomRole.PARKING ->
            if (sim.isTaken(room)) room.type.art + CAR_COLORS[room.id % CAR_COLORS.size] else room.type.art + "_empty"
        else -> room.tenant?.art ?: room.type.art
    }

    /** Art suffixes for the cars in the parking spaces. */
    private val CAR_COLORS = listOf("", "_blue", "_yellow")
}

/** Decides whether a person is drawn (people riding in a closed elevator car are hidden, for example). */
object PeopleVisibility {
    fun isVisible(sim: Simulation, entity: com.github.quillraven.fleks.Entity): Boolean =
        with(sim.world) { entity hasNo crazytower.core.ecs.Riding && entity hasNo crazytower.core.ecs.Away }
}
