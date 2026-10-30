package crazytower.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/** The player's options. Saved next to the save game, not in it. */
@Serializable
data class Settings(
    val fullscreen: Boolean = false,
    /** A window without frame that covers the whole screen; only when not [fullscreen]. */
    val borderless: Boolean = false,
    val windowWidth: Int = 1280,
    val windowHeight: Int = 800,
    /** Size of the interface: 1.0 lays it out for 800 pixels of height, 1.25 makes it a quarter bigger. */
    val uiScale: Double = 1.0,
    val vsync: Boolean = true,
    /** Off keeps the tower in daylight all the time. */
    val dayNight: Boolean = true,
    /** Share of the people that is drawn: 1.0 all, 0.5 every second one. Only the picture, not the simulation. */
    val crowd: Double = 1.0,
    /** The "+1.800 $" texts that rise from rooms that pay. */
    val moneyPopups: Boolean = true,
    val fastNights: Boolean = true,
    /** 0 to 100, for when the game has sound. */
    val volume: Int = 80,
    /** Saves the game every this many game days at midnight; 0 is off. */
    val autosaveDays: Int = 1,
    /** The language of all texts; a new player gets the one of the system. */
    val language: Language = Language.system(),
) {
    /** "Fenster", "Fenster-Vollbild" or "Vollbild". */
    val displayMode: String get() = when {
        fullscreen -> tr("Vollbild", "Fullscreen")
        borderless -> tr("Fenster-Vollbild", "Borderless")
        else -> tr("Fenster", "Window")
    }

    /** The next display mode: window, borderless window, fullscreen, window again. */
    fun nextDisplayMode(): Settings = when {
        fullscreen -> copy(fullscreen = false, borderless = false)
        borderless -> copy(fullscreen = true, borderless = false)
        else -> copy(borderless = true)
    }

    fun toJson(): String = json.encodeToString(serializer(), this)

    /**
     * The virtual screen for a window of [width] x [height] pixels: same aspect ratio as the
     * window, so there are no bars at the sides, with [uiScale] pixels per virtual pixel at
     * 800 high. Narrow windows get a wider virtual screen so the top bar still fits.
     */
    fun virtualSize(width: Int, height: Int): Pair<Int, Int> {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        var vh = BASE_HEIGHT / uiScale.coerceIn(0.5, 2.0)
        var vw = vh * w / h
        if (vw < MIN_WIDTH) {
            vw = MIN_WIDTH
            vh = vw * h / w
        }
        return vw.roundToInt() to vh.roundToInt()
    }

    companion object {
        const val BASE_HEIGHT = 800.0
        /** Narrowest virtual screen the top bar fits into. */
        const val MIN_WIDTH = 1240.0

        val WINDOW_SIZES = listOf(1280 to 720, 1280 to 800, 1600 to 900, 1680 to 1050, 1920 to 1080, 1920 to 1200, 2560 to 1440)
        val UI_SCALES = listOf(0.85, 1.0, 1.15, 1.3, 1.5)
        val CROWDS = listOf(1.0, 0.5, 0.25)
        val AUTOSAVES = listOf(0, 1, 3, 7)

        private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

        /** Reads saved settings; anything broken falls back to the defaults. */
        fun fromJson(text: String): Settings = runCatching { json.decodeFromString(serializer(), text) }.getOrDefault(Settings())
    }
}
