package crazytower.game

import crazytower.core.Room
import crazytower.core.Simulation

/** Chooses the art for a room from its type (and later its tenant). */
object RoomLooks {
    fun art(sim: Simulation, room: Room): String = when {
        room.type.role == crazytower.core.RoomRole.LOBBY -> crazytower.core.Stages.info(sim.stage).lobbyArt
        else -> room.tenant?.art ?: room.type.art
    }
}

/** Decides whether a person is drawn (people riding in a closed elevator car are hidden, for example). */
object PeopleVisibility {
    fun isVisible(sim: Simulation, entity: com.github.quillraven.fleks.Entity): Boolean =
        with(sim.world) { entity hasNo crazytower.core.ecs.Riding && entity hasNo crazytower.core.ecs.Away }
}
