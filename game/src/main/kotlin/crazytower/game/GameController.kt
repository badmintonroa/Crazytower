package crazytower.game

import com.github.quillraven.fleks.Entity
import crazytower.core.Category
import crazytower.core.RoomType
import crazytower.core.Simulation
import kotlin.time.Duration

/** What the mouse does in the tower. */
sealed interface Tool {
    data class Build(val type: RoomType) : Tool
    data object Demolish : Tool
}

/** What the inspector shows. */
sealed interface Selection {
    data class RoomSel(val roomId: Int) : Selection
    data class PersonSel(val entity: Entity) : Selection
}

/**
 * Front-end state that is not part of the simulation: game speed, open build category,
 * active tool, selection and short status messages.
 */
class GameController(val sim: Simulation) {
    var speed: Int = 1
    var category: Category? = Category.WAYS
    var tool: Tool? = null
    var selection: Selection? = null
    var financeOpen = false
    var optionsOpen = false
    var extrasOpen = false
    var missionOpen = false
    var helpOpen = false

    /** Starts a ready-made tower of the given stage; set by the game screen. */
    var onTestTower: (Int) -> Unit = {}

    /** Saves (unless it is a test tower) and goes back to the title screen; set by the game screen. */
    var onMainMenu: (() -> Unit)? = null

    /** Saves (unless it is a test tower) and closes the game; set by the game screen. */
    var onQuit: () -> Unit = {}

    /** The game stands still at the end of the demo; the title screen's tower keeps living. */
    var stopsAtDemoEnd = true
    var toast: String? = null
        private set
    private var toastLeft = 0.0

    fun update(dt: Duration) {
        val seconds = dt.inWholeMilliseconds / 1000.0
        if (toastLeft > 0) {
            toastLeft -= seconds
            if (toastLeft <= 0) toast = null
        }
        if (speed > 0 && !(stopsAtDemoEnd && sim.demoOver)) sim.step(seconds.coerceAtMost(0.25) * GAME_MINUTES_PER_SECOND * speed * nightBoost)
    }

    /** Between 23 and 6 o'clock the clock runs four times faster, if the player wants. */
    var fastNights: Boolean = Options.current.fastNights

    val nightBoost: Int
        get() = if (fastNights && (sim.clock.hour >= 23 || sim.clock.hour < 6)) NIGHT_BOOST else 1

    fun togglePause() {
        speed = if (speed == 0) lastSpeed else 0.also { lastSpeed = speed }
    }

    private var lastSpeed = 1

    /** One step faster ([faster]) or slower, between 1x and the fastest speed; from the pause, 1x. */
    fun faster() = changeSpeed(+1)

    fun slower() = changeSpeed(-1)

    private fun changeSpeed(step: Int) {
        val running = SPEEDS.filter { it > 0 }
        val index = running.indexOf(speed)
        speed = if (index < 0) running.first() else running[(index + step).coerceIn(0, running.lastIndex)]
    }

    fun openCategory(c: Category) {
        if (c == Category.DEMOLISH) {
            category = c
            tool = Tool.Demolish
            return
        }
        // with a room chosen, the category button brings the full list back instead of closing it
        category = if (category == c && tool == null) null else c
        tool = null
    }

    fun cancel() {
        if (tool != null) tool = null else category = null
    }

    fun say(message: String, seconds: Double = 3.0) {
        toast = message
        toastLeft = seconds
    }

    companion object {
        /** At 1x one real second is four game minutes, so a game day lasts 6 minutes. */
        const val GAME_MINUTES_PER_SECOND = 4.0
        const val NIGHT_BOOST = 4
        val SPEEDS = listOf(0, 1, 2, 4, 8)
    }
}
