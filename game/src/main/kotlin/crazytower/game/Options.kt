package crazytower.game

import crazytower.core.Settings
import java.io.File

/** The player's settings, kept in the user's home folder next to the save game. */
object Options {
    val file: File = File(System.getProperty("user.home"), ".crazytower/einstellungen.json")

    var current: Settings = load()
        private set

    /** Called after every change, with the old and the new settings. */
    var onChange: (old: Settings, new: Settings) -> Unit = { _, _ -> }

    fun update(change: (Settings) -> Settings) {
        val old = current
        current = change(old)
        if (!Autopilot.active) runCatching {
            file.parentFile.mkdirs()
            file.writeText(current.toJson())
        }
        onChange(old, current)
    }

    private fun load(): Settings {
        // screenshots run with fixed settings and a window size from CRAZYTOWER_WINDOW (e.g. 1920x1080)
        if (Autopilot.active) {
            val size = System.getenv("CRAZYTOWER_WINDOW")?.split("x")?.mapNotNull { it.toIntOrNull() }
            return if (size?.size == 2) Settings(windowWidth = size[0], windowHeight = size[1]) else Settings()
        }
        return if (file.exists()) runCatching { Settings.fromJson(file.readText()) }.getOrDefault(Settings()) else Settings()
    }
}
