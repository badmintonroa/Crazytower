package crazytower.game

import crazytower.core.Edition
import crazytower.core.SaveGame
import crazytower.core.Simulation
import crazytower.core.tr
import java.io.File

/**
 * The one save slot, in the user's home folder. The demo keeps its own file, so it never
 * overwrites a tower of the full game; the full game picks up the demo tower while it has none.
 */
object SaveSlot {
    private val folder = File(System.getProperty("user.home"), ".crazytower")
    private val demoFile = File(folder, "spielstand_demo.json")
    private val fullFile = File(folder, "spielstand.json")

    /** Where this edition saves. */
    private val ownFile: File get() = if (Edition.demo) demoFile else fullFile

    /** Where this edition loads from: its own save, or for the full game the demo tower. */
    val file: File get() = if (!Edition.demo && !fullFile.exists() && demoFile.exists()) demoFile else ownFile

    /** Saves and returns a message for the player. */
    fun save(sim: Simulation): String = trySave(sim).second

    /** Saves and returns whether it worked and a message for the player. */
    fun trySave(sim: Simulation): Pair<Boolean, String> = runCatching {
        ownFile.parentFile.mkdirs()
        ownFile.writeText(sim.toSave().toJson())
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
