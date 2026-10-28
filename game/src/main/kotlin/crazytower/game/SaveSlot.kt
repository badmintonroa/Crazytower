package crazytower.game

import crazytower.core.SaveGame
import crazytower.core.Simulation
import java.io.File

/** The one save slot, in the user's home folder. */
object SaveSlot {
    val file: File = File(System.getProperty("user.home"), ".crazytower/spielstand.json")

    /** Saves and returns a message for the player. */
    fun save(sim: Simulation): String = runCatching {
        file.parentFile.mkdirs()
        file.writeText(sim.toSave().toJson())
        "Gespeichert (${sim.clock.format()})"
    }.getOrElse { "Speichern ging schief: ${it.message}" }

    /** Loads the save game, or returns null and a message why not. */
    fun load(): Pair<Simulation?, String> {
        if (!file.exists()) return null to "Noch kein Spielstand vorhanden"
        return runCatching {
            Simulation.load(SaveGame.fromJson(file.readText()), seed = System.currentTimeMillis()) to ""
        }.getOrElse { null to "Spielstand kaputt: ${it.message}" }
    }
}
