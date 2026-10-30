package crazytower.game

import crazytower.core.Profiler
import crazytower.core.Simulation
import crazytower.core.ecs.Away
import crazytower.core.ecs.Person
import crazytower.core.ecs.Riding
import crazytower.core.ecs.Route
import crazytower.game.ui.label
import crazytower.game.ui.panel
import korlibs.image.color.RGBA
import korlibs.korge.view.*
import kotlin.system.exitProcess

/**
 * F3: where the time of a frame goes. Simulation per ECS system, mirroring the tower into views,
 * the HUD, and KorGE's rendering with its draw calls. Averages over half a second.
 *
 * CRAZYTOWER_PROFILE=<seconds> prints the same numbers every second and quits after that many
 * seconds, for measuring without a screen (CRAZYTOWER_SPEED sets the game speed for it).
 */
class DebugOverlay(private val sim: Simulation, private val tower: TowerView, private val views: Views) : Container() {
    private val background = panel(360.0, 100.0, RGBA(10, 12, 24, 220), cut = 6.0)
    private val text = label("", 13.0, Theme.TEXT).xy(12.0, 10.0)

    private var frames = 0
    private var frameSeconds = 0.0
    private var renderNanos = 0L
    private var renderStart = 0L
    private var drawCalls = 0
    private var maxFrame = 0.0
    private var lastSim: Profiler.Snapshot = sim.profiler.snapshot()
    private val towerParts = LongArray(TowerView.PARTS.size)
    private var elapsed = 0.0
    private val printEvery = System.getenv("CRAZYTOWER_PROFILE")?.toDoubleOrNull()
    private var printed = 0.0
    private var report = ""

    private val listeners = listOf(
        views.onBeforeRender.add { renderStart = System.nanoTime() },
        views.onAfterRender.add { renderNanos += System.nanoTime() - renderStart },
        views.renderContext.batch.beforeFlush.add { drawCalls++ },
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

    /** Called once per frame after everything was updated, with the real seconds since the last frame. */
    fun frame(dtSeconds: Double) {
        frames++
        frameSeconds += dtSeconds
        maxFrame = maxOf(maxFrame, dtSeconds)
        for (i in towerParts.indices) towerParts[i] += tower.partNanos[i]
        if (frameSeconds < WINDOW_SECONDS) return
        report = buildReport()
        text.text = report
        background.resize(text.width + 24.0, text.height + 20.0)
        elapsed += frameSeconds
        if (printEvery != null && elapsed - printed >= 1.0) {
            printed = elapsed
            println("---- ${"%.0f".format(elapsed)} s\n$report")
            if (elapsed >= printEvery) exitProcess(0)
        }
        frames = 0; frameSeconds = 0.0; maxFrame = 0.0
        simNanosSum = 0; towerNanosSum = 0; hudNanosSum = 0; renderNanos = 0; drawCalls = 0
        towerParts.fill(0)
    }

    private var simNanosSum = 0L
    private var towerNanosSum = 0L
    private var hudNanosSum = 0L

    /** Adds this frame's measurements; [frame] averages them. */
    fun add(sim: Long, tower: Long, hud: Long) {
        simNanosSum += sim
        towerNanosSum += tower
        hudNanosSum += hud
    }

    private fun buildReport(): String {
        val n = frames.coerceAtLeast(1)
        fun ms(nanos: Long) = "%5.2f".format(nanos / 1e6 / n)
        val now = sim.profiler.snapshot()
        val d = now - lastSim
        lastSim = now
        var people = 0
        var inside = 0
        var walking = 0
        var riding = 0
        with(sim.world) {
            sim.world.family { all(Person) }.forEach {
                people++
                if (it hasNo Away) inside++
                if (it has Route) walking++
                if (it has Riding) riding++
            }
        }
        val frameMs = frameSeconds * 1000 / n
        val busy = (simNanosSum + towerNanosSum + hudNanosSum + renderNanos) / 1e6 / n
        return buildString {
            appendLine("F3  %.0f fps   Frame %.1f ms (max %.1f)   davon gemessen %.1f ms".format(n / frameSeconds, frameMs, maxFrame * 1000, busy))
            appendLine("Leute $people, im Turm $inside, unterwegs $walking, im Aufzug $riding, gezeichnet ${tower.peopleShown}")
            appendLine()
            appendLine("Simulation   ${ms(simNanosSum)} ms/Frame   ${d.ticks / n} Ticks/Frame")
            val total = d.total.coerceAtLeast(1)
            sim.profiler.names.indices.sortedByDescending { d.nanos[it] }.take(8).forEach { i ->
                appendLine("  %-13s ${ms(d.nanos[i])} ms  %3.0f %%".format(sim.profiler.names[i], 100.0 * d.nanos[i] / total))
            }
            appendLine("  Wegsuche: ${d.pathSearches} Suchen, %.0f µs je Suche".format(d.pathNanos / 1e3 / d.pathSearches.coerceAtLeast(1)))
            appendLine("Turmansicht  ${ms(towerNanosSum)} ms/Frame  (${tower.roomCount} Räume)")
            TowerView.PARTS.forEachIndexed { i, name -> appendLine("  %-13s ${ms(towerParts[i])} ms".format(name)) }
            appendLine("HUD          ${ms(hudNanosSum)} ms/Frame")
            append("Zeichnen     ${ms(renderNanos)} ms/Frame   ${drawCalls / n} Draw Calls")
        }
    }

    companion object {
        const val WINDOW_SECONDS = 0.5
    }
}
