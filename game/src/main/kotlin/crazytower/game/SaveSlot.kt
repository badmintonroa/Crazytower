package crazytower.game

import crazytower.core.SaveGame
import crazytower.core.Simulation
import crazytower.core.tr
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The one save slot, in the user's home folder. Every save keeps the one before as
 * [backup], and writes to a temporary file first, so a crash while saving never leaves
 * a half-written game behind. When the save game doesn't load, the backup does.
 */
object SaveSlot {
    val file: File = File(System.getProperty("user.home"), ".crazytower/spielstand.json")

    /** The save before the last one. */
    val backup: File get() = File(file.parentFile, "spielstand.bak.json")

    /** Saves and returns a message for the player. */
    fun save(sim: Simulation): String = trySave(sim).second

    /** Saves and returns whether it worked and a message for the player. */
    fun trySave(sim: Simulation): Pair<Boolean, String> = runCatching {
        file.parentFile.mkdirs()
        val fresh = File(file.parentFile, "spielstand.neu.json")
        fresh.writeText(sim.toSave().toJson())
        // only a save that loads becomes the backup
        if (file.exists() && readable(file)) Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
        Files.move(fresh.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        true to tr("Gespeichert (${sim.clock.format()})", "Saved (${sim.clock.format()})")
    }.getOrElse { false to tr("Speichern ging schief: ${it.message}", "Saving failed: ${it.message}") }

    /** Loads the save game (or, if it is broken, the backup), or returns null and a message why not. */
    fun load(): Pair<Simulation?, String> {
        if (!file.exists() && !backup.exists()) return null to tr("Noch kein Spielstand vorhanden", "No saved game yet")
        val main = runCatching { read(file) }
        main.getOrNull()?.let { return it to "" }
        val fallback = runCatching { read(backup) }.getOrNull()
            ?: return null to tr("Spielstand kaputt: ${main.exceptionOrNull()?.message}", "Saved game is broken: ${main.exceptionOrNull()?.message}")
        return fallback to tr("Spielstand kaputt, die Sicherung davor ist geladen", "Saved game is broken, the backup before it is loaded")
    }

    private fun read(from: File): Simulation =
        Simulation.load(SaveGame.fromJson(from.readText()), seed = System.currentTimeMillis())

    private fun readable(from: File): Boolean = runCatching { SaveGame.fromJson(from.readText()) }.isSuccess
}
