package crazytower.game

import crazytower.core.Edition
import crazytower.core.SaveGame
import crazytower.core.Simulation
import crazytower.core.tr
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The one save slot, in the user's home folder. The demo keeps its own file, so it never
 * overwrites a tower of the full game; the full game picks up the demo tower while it has none.
 * Every save keeps the one before as a backup (`spielstand.bak.json`) and writes to a new file first,
 * so a crash while saving never leaves a half-written game behind. When the save game doesn't
 * load, its backup does.
 */
object SaveSlot {
    private val folder = File(System.getProperty("user.home"), ".crazytower")
    private val demoFile = File(folder, "spielstand_demo.json")
    private val fullFile = File(folder, "spielstand.json")

    /** Where this edition saves. */
    private val ownFile: File get() = if (Edition.demo) demoFile else fullFile

    /** Where this edition loads from: its own save, or for the full game the demo tower. */
    val file: File get() = if (!Edition.demo && !fullFile.exists() && demoFile.exists()) demoFile else ownFile

    /** The save before the last one, next to the file it loads from. */
    val backup: File get() = backupOf(file)

    private fun backupOf(save: File): File = File(save.parentFile, save.nameWithoutExtension + ".bak.json")

    /** Saves and returns a message for the player. */
    fun save(sim: Simulation): String = trySave(sim).second

    /** Saves and returns whether it worked and a message for the player. */
    fun trySave(sim: Simulation): Pair<Boolean, String> = runCatching {
        ownFile.parentFile.mkdirs()
        val fresh = File(folder, ownFile.nameWithoutExtension + ".neu.json")
        fresh.writeText(sim.toSave().toJson())
        // only a save that loads becomes the backup
        if (ownFile.exists() && readable(ownFile)) Files.copy(ownFile.toPath(), backupOf(ownFile).toPath(), StandardCopyOption.REPLACE_EXISTING)
        Files.move(fresh.toPath(), ownFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
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
