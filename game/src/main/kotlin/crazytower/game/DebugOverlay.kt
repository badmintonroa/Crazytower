package crazytower.game

import crazytower.core.Profiler
import crazytower.core.Simulation
import crazytower.core.Workers
import crazytower.game.ui.label
import crazytower.game.ui.panel
import korlibs.image.color.RGBA
import korlibs.korge.view.*
import kotlin.system.exitProcess

/**
 * F7 (only in [DevMode]): frames per second and how many milliseconds each system needs per
 * frame, the slowest first. Every ECS system of the simulation, the tower view, the HUD and
 * KorGE's drawing. Averages over half a second.
 *
 * CRAZYTOWER_PROFILE=<seconds> prints the same numbers every second and quits after that many
 * seconds, for measuring without a screen (CRAZYTOWER_SPEED sets the game speed for it).
 */
class DebugOverlay(private val sim: Simulation, private val views: Views) : Container() {
    private val background = panel(240.0, 100.0, RGBA(10, 12, 24, 245), cut = 6.0)
    private val header = label("", 13.0, Theme.TEXT).xy(12.0, 10.0)
    private val names = label("", 13.0, Theme.TEXT_DIM).xy(12.0, 30.0)
    private val times = label("", 13.0, Theme.TEXT).xy(140.0, 30.0)

    private var frames = 0
    private var frameSeconds = 0.0
    private var maxFrame = 0.0
    private var simNanosSum = 0L
    private var towerNanosSum = 0L
    private var hudNanosSum = 0L
    private var renderNanos = 0L
    private var renderStart = 0L
    private var lastSim: Profiler.Snapshot = sim.profiler.snapshot()
    private var elapsed = 0.0
    private val printEvery = System.getenv("CRAZYTOWER_PROFILE")?.toDoubleOrNull()
    private var printed = 0.0

    /**
     * Draw calls: the batches KorGE hands to the graphics card. Every change of texture set,
     * clipping or smoothing starts a new one, so this is the number to keep low.
     */
    private var drawCalls = 0L
    private var maxDrawCalls = 0
    private var batchesBefore = 0

    private val listeners = listOf(
        views.onBeforeRender.add {
            renderStart = System.nanoTime()
            batchesBefore = views.renderContext.batch.batchCount
        },
        views.onAfterRender.add {
            renderNanos += System.nanoTime() - renderStart
            val calls = views.renderContext.batch.batchCount - batchesBefore
            drawCalls += calls
            maxDrawCalls = maxOf(maxDrawCalls, calls)
        },
    )

    init {
        visible = printEvery != null
    }

    fun toggle() {
        visible = !visible
    }

    /** Stops listening to the renderer; the overlay goes away with its screen. */
    fun dispose() {
        for (listener in listeners) listener.close()
    }

    /** Adds this frame's measurements; [frame] averages them. */
    fun add(sim: Long, tower: Long, hud: Long) {
        simNanosSum += sim
        towerNanosSum += tower
        hudNanosSum += hud
    }

    /** Called once per frame after everything was updated, with the real seconds since the last frame. */
    fun frame(dtSeconds: Double) {
        frames++
        frameSeconds += dtSeconds
        maxFrame = maxOf(maxFrame, dtSeconds)
        if (frameSeconds < WINDOW_SECONDS) return
        val (title, rows) = measure()
        if (visible) {
            header.text = title
            names.text = rows.joinToString("\n") { it.first }
            times.text = rows.joinToString("\n") { it.second }
            times.x = names.x + names.width + 16.0
            background.resize(maxOf(header.width, times.x + times.width - 12.0) + 24.0, names.y + names.height + 10.0)
        }
        elapsed += frameSeconds
        if (printEvery != null && elapsed - printed >= 1.0) {
            printed = elapsed
            println("---- ${"%.0f".format(elapsed)} s\n$title\n" + rows.joinToString("\n") { "  %-16s %s".format(it.first, it.second) })
            if (elapsed >= printEvery) exitProcess(0)
        }
        frames = 0; frameSeconds = 0.0; maxFrame = 0.0
        drawCalls = 0; maxDrawCalls = 0
        simNanosSum = 0; towerNanosSum = 0; hudNanosSum = 0; renderNanos = 0
    }

    /** The title line and one row per system: name and milliseconds per frame, slowest first. */
    private fun measure(): Pair<String, List<Pair<String, String>>> {
        val n = frames.coerceAtLeast(1)
        val now = sim.profiler.snapshot()
        val d = now - lastSim
        lastSim = now
        val systems = sim.profiler.names.mapIndexed { i, name -> name to d.nanos[i] }
        // game logic outside the ECS systems: commands, events, the clock
        val rest = (simNanosSum - d.total).coerceAtLeast(0)
        val rows = (systems + listOf("Spiel sonst" to rest, "Turmansicht" to towerNanosSum, "HUD" to hudNanosSum, "Zeichnen" to renderNanos))
            .sortedByDescending { it.second }
            .map { (name, nanos) -> name to "%.2f ms".format(nanos / 1e6 / n) }
        val title = "%.0f FPS   %.1f ms/Frame (max %.1f)   %d Draw Calls (max %d)   %d Sim-Threads"
            .format(n / frameSeconds, frameSeconds * 1000 / n, maxFrame * 1000, drawCalls / n, maxDrawCalls, Workers.threads)
        return title to rows
    }

    companion object {
        const val WINDOW_SECONDS = 0.5
    }
}
