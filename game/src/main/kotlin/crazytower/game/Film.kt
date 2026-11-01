package crazytower.game

import crazytower.core.ActiveEvent
import crazytower.core.Catalog
import crazytower.core.Events
import crazytower.core.Lasting
import crazytower.core.RoomRole
import crazytower.core.startEvent
import crazytower.core.GROUND_FLOOR
import crazytower.core.MoneyKind
import crazytower.core.Simulation
import crazytower.core.Tenants
import crazytower.core.tr
import korlibs.korge.view.SContainer
import korlibs.korge.view.addUpdater
import korlibs.korge.view.renderToBitmap
import korlibs.image.format.PNG
import korlibs.image.format.writeTo
import korlibs.io.file.std.localVfs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds

/**
 * Records trailer footage without a screen: with CRAZYTOWER_FILM=<file.mp4> (or <file.png> for the last frame as a still) the game runs with a
 * fixed time step of 1/[FPS] second per frame and pipes every frame to ffmpeg (video only, no sound),
 * so the clip is smooth however slowly the machine renders. Used for the Steam trailer
 * (tools/steam/film.sh), together with CRAZYTOWER_WINDOW, CRAZYTOWER_LANG and CRAZYTOWER_TEST or CRAZYTOWER_TITLE.
 *
 * - CRAZYTOWER_SECONDS: length of the clip (default 6)
 * - CRAZYTOWER_CAM: camera keyframes "seconds:x:floor:zoom;..." (x in fields, floor may be fractional),
 *   eased between keyframes; without it the camera stays where the game put it
 * - CRAZYTOWER_PREROLL: game minutes to simulate before the first frame (default 30)
 * - CRAZYTOWER_HOUR: start the clip at this hour of the day (e.g. 20.5 for half past eight in the evening)
 * - CRAZYTOWER_NOHUD=1: hides the interface for clean shots of the tower
 * - CRAZYTOWER_TIDY=1: no run-down rooms
 * - CRAZYTOWER_ACTIONS="seconds:what:argument;...": things that happen during the clip:
 *   `morph:<tenant>` the office or flat nearest the middle of the screen gets that tenant (clown, vampire ...)
 *   with the ticker message, `event:<id>` starts an event, `celebrate:star` or `celebrate:stage` the celebration,
 *   `speed:<n>` the game speed, `select:<ROLE>` selects the room of that role nearest the middle (inspector),
 *   `open:finance|extras|mission` opens that window, `close:` closes windows and the selection
 * - CRAZYTOWER_CONSTRUCT=<seconds>: starts from an empty lot and builds a small tower piece by piece, one piece every that many seconds
 */
object Film {
    const val FPS = 30
    val target: String? = System.getenv("CRAZYTOWER_FILM")
    val active: Boolean get() = target != null

    /** CRAZYTOWER_TIDY=1: rooms never look run down (the test towers are not very happy), for key art. */
    val tidy: Boolean = active && System.getenv("CRAZYTOWER_TIDY") != null

    private val frameStep: Duration = (1_000_000 / FPS).microseconds

    /** The time step the game should use this frame: fixed while filming, the real one otherwise. */
    fun step(real: Duration): Duration = if (active) frameStep else real

    /** Counts the frames the game has advanced, so the recorder waits for each new one. */
    @Volatile private var frames = 0L

    private class Action(val t: Double, val what: String, val arg: String)

    private class Key(val t: Double, val x: Double, val floor: Double, val zoom: Double)

    private fun keys(): List<Key> = System.getenv("CRAZYTOWER_CAM").orEmpty().split(";").mapNotNull { part ->
        val v = part.trim().split(":").map { it.toDoubleOrNull() }
        if (v.size == 4 && v.all { it != null }) Key(v[0]!!, v[1]!!, v[2]!!, v[3]!!) else null
    }.sortedBy { it.t }

    private fun ease(f: Double) = (1 - cos(PI * f.coerceIn(0.0, 1.0))) / 2

    private fun camAt(keys: List<Key>, t: Double): Key? {
        if (keys.isEmpty()) return null
        if (t <= keys.first().t) return keys.first()
        if (t >= keys.last().t) return keys.last()
        val i = keys.indexOfLast { it.t <= t }
        val a = keys[i]
        val b = keys[i + 1]
        val f = ease((t - a.t) / (b.t - a.t))
        // zoom is eased in log space so zooming in and out feels even
        val zoom = a.zoom * (b.zoom / a.zoom).pow(f)
        return Key(t, a.x + (b.x - a.x) * f, a.floor + (b.floor - a.floor) * f, zoom)
    }

    /** Hooks the recorder into the game screen. Does nothing unless CRAZYTOWER_FILM is set. */
    fun attachIfRequested(root: SContainer, sim: Simulation, game: GameController, tower: TowerView, hud: Hud?) {
        if (!active) return
        if (System.getenv("CRAZYTOWER_NOHUD") != null) hud?.visible = false
        // a clean screen: no build menu (unless CRAZYTOWER_CATEGORY opens one), no greeting
        game.category = null
        System.getenv("CRAZYTOWER_CATEGORY")?.let { name -> crazytower.core.Category.entries.firstOrNull { it.name == name }?.let { game.openCategory(it) } }
        game.say("", 0.001)
        // the ticker shows a few of the tower's sillier moments instead of the test tower note
        listOf(
            tr("Im Aufzug wurde geheiratet. Die Trauzeugen sind im 7. Stock ausgestiegen.", "Someone got married in the elevator. The best man got off on floor 7."),
            tr("Das Zentralarchiv für einzelne Socken sucht weiterhin den linken.", "The Central Archive of Odd Socks is still looking for the left one."),
            tr("Tauben haben die oberste Etage besetzt und fordern Brot.", "Pigeons have occupied the top floor and are demanding bread."),
        ).forEach { sim.news.post(it) }
        val construct = System.getenv("CRAZYTOWER_CONSTRUCT")?.toDoubleOrNull()
        System.getenv("CRAZYTOWER_HOUR")?.toDoubleOrNull()?.let { hour ->
            val now = sim.clock.totalMinutes % 1440
            val wanted = (hour * 60).toInt()
            sim.step(((wanted - now + 1440) % 1440).toDouble())
        }
        if (construct == null) sim.step(System.getenv("CRAZYTOWER_PREROLL")?.toDoubleOrNull() ?: 30.0)
        val pieces = if (construct != null) Construction.plan(sim) else mutableListOf()
        val actions = System.getenv("CRAZYTOWER_ACTIONS").orEmpty().split(";").mapNotNull { part ->
            val bits = part.trim().split(":", limit = 3).takeIf { it.size >= 2 } ?: return@mapNotNull null
            bits[0].toDoubleOrNull()?.let { Action(it, bits[1], bits.getOrElse(2) { "" }) }
        }.sortedBy { it.t }.toMutableList()
        val keys = keys()
        camAt(keys, 0.0)?.let { tower.filmCamera(it.x, it.floor, it.zoom) }
        root.addUpdater { frames++ }
        record(root, keys, actions, sim, tower, game, hud, construct, pieces)
    }

    /** Records the title screen as it is (its own camera drifts up and down). */
    fun attachToTitle(root: SContainer) {
        if (!active) return
        root.addUpdater { frames++ }
        record(root, emptyList(), mutableListOf(), null, null, null, null, null, mutableListOf())
    }

    private fun record(
        root: SContainer,
        keys: List<Key>,
        actions: MutableList<Action>,
        sim: Simulation?,
        tower: TowerView?,
        game: GameController?,
        hud: Hud?,
        construct: Double?,
        pieces: MutableList<(Simulation) -> Unit>,
    ) {
        val out = target ?: return
        val seconds = System.getenv("CRAZYTOWER_SECONDS")?.toDoubleOrNull() ?: 6.0
        val total = (seconds * FPS).toInt().coerceAtLeast(1)
        val still = out.endsWith(".png", ignoreCase = true)
        val stage = root.stage ?: return
        val views = stage.views
        CoroutineScope(views.coroutineContext).launch {
            // a few frames to let the scene settle (fonts, first layout)
            var seen = frames
            repeat(10) { while (frames == seen) delay(1); seen = frames }
            var ffmpeg: Process? = null
            var buffer: ByteBuffer? = null
            var nextPiece = 0.0
            for (i in 0 until total) {
                val t = i.toDouble() / FPS
                if (sim != null && tower != null) {
                    camAt(keys, t)?.let { tower.filmCamera(it.x, it.floor, it.zoom) }
                    while (actions.isNotEmpty() && actions.first().t <= t) perform(actions.removeAt(0), sim, tower, game!!, hud)
                    if (construct != null && t >= nextPiece && pieces.isNotEmpty()) {
                        pieces.removeAt(0)(sim)
                        nextPiece = t + construct
                    }
                }
                val bmp = stage.renderToBitmap(views, includeBackground = true)
                if (still) {
                    // a .png target keeps only the last frame, for key art and screenshots
                    if (i == total - 1) bmp.writeTo(localVfs(out), PNG)
                } else {
                    if (ffmpeg == null) {
                        ffmpeg = ProcessBuilder(
                            "ffmpeg", "-y", "-loglevel", "error",
                            "-f", "rawvideo", "-pix_fmt", "rgba", "-s", "${bmp.width}x${bmp.height}", "-r", "$FPS", "-i", "-",
                            "-c:v", "libx264", "-preset", "medium", "-crf", "14", "-pix_fmt", "yuv420p", out,
                        ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.INHERIT).start()
                        buffer = ByteBuffer.allocate(bmp.width * bmp.height * 4).order(ByteOrder.LITTLE_ENDIAN)
                    }
                    val buf = buffer!!
                    buf.clear()
                    buf.asIntBuffer().put(bmp.ints)
                    ffmpeg!!.outputStream.write(buf.array())
                }
                seen = frames
                while (frames == seen) delay(1)
            }
            ffmpeg?.outputStream?.close()
            ffmpeg?.waitFor()
            exitProcess(0)
        }
    }

    private fun perform(action: Action, sim: Simulation, tower: TowerView, game: GameController, hud: Hud?) {
        when (action.what) {
            "morph" -> morph(sim, tower, action.arg)
            "event" -> startEvent(sim, action.arg)
            "celebrate" -> hud?.celebrate(stage = action.arg == "stage")
            "speed" -> action.arg.toIntOrNull()?.let { game.speed = it }
            "select" -> {
                val role = RoomRole.entries.firstOrNull { it.name == action.arg }
                val (cx, cf) = tower.filmCenter()
                game.selection = sim.tower.allRooms.filter { role == null || it.type.role == role }
                    .minByOrNull { (it.centerX - cx).let { d -> d * d } + ((it.floor - cf) * 4).let { d -> d * d } }
                    ?.let { Selection.RoomSel(it.id) }
            }
            "open" -> when (action.arg) {
                "finance" -> game.financeOpen = true
                "extras" -> game.extrasOpen = true
                "mission" -> game.missionOpen = true
            }
            "close" -> {
                game.selection = null
                game.financeOpen = false
                game.extrasOpen = false
                game.missionOpen = false
            }
        }
    }

    /** Starts the event [id] like the hourly roll would (events that need no place start anyway). */
    private fun startEvent(sim: Simulation, id: String) {
        val started = Events.all.firstOrNull { it.id == id }?.apply?.invoke(sim) == true
        if (!started && Lasting.exists(id)) {
            val now = sim.clock.totalMinutes
            sim.startEvent(ActiveEvent(id, now, now + 120), Lasting.kind(id).title.toString())
        }
    }

    /** The office or flat nearest the middle of the screen gets [tenantId], with a ticker message like the real events. */
    private fun morph(sim: Simulation, tower: TowerView, tenantId: String) {
        val kind = Tenants.byId(tenantId)
        val (cx, cf) = tower.filmCenter()
        val room = sim.tower.allRooms
            .filter { it.type.role == kind.role && it.tenant != null && it.tenant?.id != kind.id }
            .minByOrNull { (it.centerX - cx).let { d -> d * d } + ((it.floor - cf) * 4).let { d -> d * d } } ?: return
        val old = room.tenant!!.name
        sim.moveIn(room, kind, announce = false)
        sim.news.post(tr("$old ist über Nacht zur ${kind.name} geworden.", "Overnight, $old has turned into the ${kind.name}."), room.center, important = true)
    }
}

/** A small tower that grows piece by piece for the building shot of the trailer. */
private object Construction {
    fun plan(sim: Simulation): MutableList<(Simulation) -> Unit> {
        sim.book(5_000_000, MoneyKind.LUCK, tr("Demo-Zuschuss", "Demo grant"))
        sim.bestStars = maxOf(sim.bestStars, 2)
        sim.stars = maxOf(sim.stars, 2)
        val mid = sim.lotWidth / 2 - 16
        val steps = mutableListOf<(Simulation) -> Unit>()
        steps += { it.build(it.planLobby(mid, mid + 31)) }
        steps += { it.build(it.planColumn(Catalog.ELEVATOR, mid + 14, GROUND_FLOOR, 1)) }
        val rows = listOf(
            listOf(Catalog.OFFICE, Catalog.OFFICE, Catalog.FOOD, Catalog.OFFICE),
            listOf(Catalog.HOME, Catalog.OFFICE, Catalog.OFFICE, Catalog.HOME),
            listOf(Catalog.HOME, Catalog.HOME, Catalog.HOME, Catalog.HOME),
            listOf(Catalog.OFFICE, Catalog.OFFICE, Catalog.OFFICE, Catalog.OFFICE),
            listOf(Catalog.HOME, Catalog.HOME, Catalog.OFFICE, Catalog.HOME),
        )
        for ((i, row) in rows.withIndex()) {
            val floor = i + 1
            steps += { it.build(it.planColumn(Catalog.ELEVATOR, mid + 14, GROUND_FLOOR, floor)) }
            // four rooms in a row, the shaft runs through the second like in the demo tower
            val xs = listOf(mid + 4, mid + 10, mid + 16, mid + 22)
            row.forEachIndexed { j, type -> steps += { it.build(type, floor, xs[j]) } }
        }
        return steps
    }
}
