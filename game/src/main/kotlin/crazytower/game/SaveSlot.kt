package crazytower.game

import crazytower.core.SaveGame
import crazytower.core.Simulation
import crazytower.core.tr
import java.io.File

/** The one save slot, in the user's home folder. */
object SaveSlot {
    val file: File = File(System.getProperty("user.home"), ".crazytower/spielstand.json")

    /** Saves and returns a message for the player. */
    fun save(sim: Simulation): String = trySave(sim).second

    /** Saves and returns whether it worked and a message for the player. */
    fun trySave(sim: Simulation): Pair<Boolean, String> = runCatching {
        file.parentFile.mkdirs()
        file.writeText(sim.toSave().toJson())
        true to tr("Gespeichert (${sim.clock.format()})", "Saved (${sim.clock.format()})")
    }.getOrElse { false to tr("Speichern ging schief: ${it.message}", "Saving failed: ${it.message}") }

    /** Loads the save game, or returns null and a message why not. */
    fun load(): Pair<Simulation?, String> {
        if (!file.exists()) return null to tr("Noch kein Spielstand vorhanden", "No saved game yet")
        return runCatching {
            Simulation.load(SaveGame.fromJson(file.readText()), seed = System.currentTimeMillis()) to ""
        }.getOrElse { null to tr("Spielstand kaputt: ${it.message}", "Saved game is broken: ${it.message}") }
    }
}
