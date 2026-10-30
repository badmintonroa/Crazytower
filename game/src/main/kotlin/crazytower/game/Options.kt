package crazytower.game

import crazytower.core.I18n
import crazytower.core.Language
import crazytower.core.Settings
import java.io.File

/** The player's settings, kept in the user's home folder next to the save game. */
object Options {
    val file: File = File(System.getProperty("user.home"), ".crazytower/einstellungen.json")

    var current: Settings = load().also { I18n.language = it.language }
        private set

    /** Called after every change, with the old and the new settings. */
    var onChange: (old: Settings, new: Settings) -> Unit = { _, _ -> }

    fun update(change: (Settings) -> Settings) {
        val old = current
        current = change(old)
        I18n.language = current.language
        if (!Autopilot.active) runCatching {
            file.parentFile.mkdirs()
            file.writeText(current.toJson())
        }
        onChange(old, current)
    }

    private fun load(): Settings {
        // screenshots run with fixed settings, a window size from CRAZYTOWER_WINDOW (e.g. 1920x1080)
        // and the language from CRAZYTOWER_LANG (de or en, German if not set)
        if (Autopilot.active) {
            val size = System.getenv("CRAZYTOWER_WINDOW")?.split("x")?.mapNotNull { it.toIntOrNull() }
            val language = if (System.getenv("CRAZYTOWER_LANG").equals("en", ignoreCase = true)) Language.EN else Language.DE
            val base = Settings(language = language)
            return if (size?.size == 2) base.copy(windowWidth = size[0], windowHeight = size[1]) else base
        }
        return if (file.exists()) runCatching { Settings.fromJson(file.readText()) }.getOrDefault(Settings()) else Settings()
    }
}
