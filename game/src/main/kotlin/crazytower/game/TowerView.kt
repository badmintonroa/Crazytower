package crazytower.game

import com.github.quillraven.fleks.Entity
import crazytower.core.BuildPlan
import crazytower.core.GROUND_FLOOR
import crazytower.core.Loc
import crazytower.core.PlaceCheck
import crazytower.core.Room
import crazytower.core.RoomRole
import crazytower.core.RoomType
import crazytower.core.Simulation
import crazytower.core.ecs.Person
import crazytower.core.ecs.Position
import crazytower.core.ecs.Riding
import crazytower.core.ecs.Route
import crazytower.core.nav.Leg
import crazytower.game.ui.chamferPath
import crazytower.game.ui.formatMoney
import crazytower.game.ui.label
import korlibs.event.Key
import korlibs.event.MouseButton
import korlibs.image.color.RGBA
import korlibs.korge.input.keys
import korlibs.korge.input.onDown
import korlibs.korge.input.onMove
import korlibs.korge.input.onScroll
import korlibs.korge.input.onUpAnywhere
import korlibs.korge.view.*
import korlibs.math.geom.Anchor
import korlibs.math.geom.Point
import korlibs.math.geom.Size
import korlibs.math.interpolation.toRatio
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Screen metrics of the tower at zoom 1: the native art is shown at twice its size. */
object Metrics {
    const val SEG = 48.0
    const val FLOOR = 96.0
    /** Height of the floor slab at the bottom of each floor; people stand on top of it. */
    const val FEET = 8.0

    fun x(segments: Number) = segments.toDouble() * SEG
    fun floorTop(floor: Int) = -(floor + 1) * FLOOR
    fun floorBottom(floor: Int) = -floor * FLOOR
}

/** What a click in the tower would do at the current mouse position. */
data class Hover(val floor: Int, val segment: Float, val screen: Point)

/**
 * The tower world: backdrop, rooms and people, plus the camera (pan and zoom).
 * It mirrors the simulation every frame; it never changes the simulation itself
 * except through [GameController] commands.
 */
class TowerView(
    private val sim: Simulation,
    private val game: GameController,
    private val assets: Assets,
    private var screenWidth: Double,
    private var screenHeight: Double,
) : Container() {
    /** Everything that moves with the camera. */
    val world = container()
    private val sky = world.solidRect(1.0, 1.0, Theme.SKY)
    private val backdrop = world.container()
    private val dusk = world.solidRect(1.0, 1.0, RGBA(0, 0, 0, 0))
    private val roomsLayer = world.container()
    private val waysLayer = world.container()
    private val carsLayer = world.container()
    private val peopleLayer = world.container()
    private val overlay = world.container()

    private val roomViews = HashMap<Int, View>()
    private val roomArt = HashMap<Int, String>()
    private val carViews = HashMap<Int, Container>()
    private val carLabels = HashMap<Int, Text>()
    private val personViews = HashMap<Int, Image>()
    private var drawnVersion = -1
    private var drawnLooks = -1
    private var drawnDark = false
    private var artCheck = 0

    private val ghost = overlay.container { visible = false }
    private val selectionFrame = overlay.container { visible = false }
    private val personMarker = overlay.graphics {
        fill(Theme.SELECT) {
            moveTo(Point(-6.0, -10.0)); lineTo(Point(6.0, -10.0)); lineTo(Point(0.0, 0.0)); close()
        }
    }.also { it.visible = false }

    var hover: Hover? = null
        private set

    /** Set by the HUD: true when the mouse is over an interface element. */
    var isOverUi: (Point) -> Boolean = { false }

    /** Placement feedback for the HUD tooltip: text and whether the spot is valid. */
    var ghostInfo: Pair<String, Boolean>? = null
        private set

    private var zoom = 1.0

    /** Where the left button went down with a build tool: the anchor of a lobby or shaft drag. */
    private var pressHover: Hover? = null

    /** The end module of a shaft or staircase the player grabbed to shorten it. */
    private var grabbedEnd: Room? = null
    private var dragStart: Point? = null
    private var dragOrigin = Point(0.0, 0.0)
    private var dragging = false

    /** An invisible full-screen hit area so clicks in empty sky still reach us. */
    private val hit = SolidRect(screenWidth, screenHeight, RGBA(0, 0, 0, 0)).also { addChildAt(it, 0) }

    init {
        drawBackdrop()
        world.x = screenWidth / 2 - Metrics.x(sim.tower.width / 2)
        world.y = screenHeight - 170.0
        setupInput()
    }

    // ---------------------------------------------------------------- backdrop

    private fun syncSky() {
        val light = DayLight.light(sim.clock.minuteOfDay)
        sky.color = DayLight.skyColor(light)
        dusk.color = RGBA(10, 12, 30, ((1 - light) * 110).toInt())
    }

    private fun drawBackdrop() {
        val lotWidth = Metrics.x(sim.tower.width)
        sky.size(lotWidth + 20000, 20000.0).xy(-10000.0, -18000.0)
        dusk.size(lotWidth + 20000, 18000.0).xy(-10000.0, -18000.0 + 0.0)
        val random = Random(42)
        // low-poly skyline in two layers
        for ((color, maxHeight) in listOf(Theme.SKY_FAR to 900.0, Theme.SKY_NEAR to 520.0)) {
            // drawn once at its own size: scaled to window pixels the skyline would be a texture wider than the GPU allows
            backdrop.graphics {
                fill(color) {
                    moveTo(Point(-4000.0, 0.0))
                    var x = -4000.0
                    while (x < lotWidth + 4000) {
                        val h = 120 + random.nextDouble() * maxHeight
                        val w = 140 + random.nextDouble() * 260
                        lineTo(Point(x, -h))
                        lineTo(Point(x + w * 0.5, -h - random.nextDouble() * 80))
                        lineTo(Point(x + w, -h * (0.7 + random.nextDouble() * 0.3)))
                        x += w
                    }
                    lineTo(Point(x, 0.0))
                    close()
                }
            }.also { it.autoScaling = false }
        }
        backdrop.solidRect(lotWidth + 8000, 3000.0, Theme.GROUND).xy(-4000.0, 0.0)
        backdrop.solidRect(lotWidth + 8000, 20.0, Theme.GROUND_DARK).xy(-4000.0, 6.0)
        backdrop.solidRect(lotWidth + 8000, Metrics.FEET, Theme.STREET).xy(-4000.0, -Metrics.FEET)
        // lot markers
        for (x in listOf(0.0, lotWidth)) backdrop.solidRect(4.0, 24.0, Theme.GOLD).xy(x - 2, -24.0)
    }

    // ---------------------------------------------------------------- sync

    /** Mirrors the simulation into views. Called once per frame with the real seconds since the last one. */
    fun sync(dtSeconds: Double = 0.0) {
        val dark = DayLight.isDark(sim.clock.minuteOfDay)
        if (drawnVersion != sim.tower.version || drawnLooks != sim.looksVersion || drawnDark != dark || ++artCheck % 60 == 0) {
            drawnDark = dark
            rebuildRooms()
        }
        syncSky()
        syncCars()
        syncPeople()
        syncOverlay()
        syncMoney(dtSeconds)
    }

    private class Floater(val view: Text, var age: Double)

    private val floaters = ArrayList<Floater>()

    /** Payments rise as "+1.800 $" from the room that paid, and fade away. */
    private fun syncMoney(dt: Double) {
        val fresh = sim.finance.fresh
        while (fresh.isNotEmpty()) {
            val payment = fresh.removeFirst()
            val where = payment.where ?: continue
            if (payment.amount > 0) Sfx.play(Sfx.CASH, gapMillis = 400)
            if (!Options.current.moneyPopups) continue
            val text = (if (payment.amount > 0) "+" else "") + formatMoney(payment.amount)
            val view = overlay.text(text, textSize = 22.0, color = if (payment.amount > 0) Theme.GOOD else Theme.ALARM, font = Theme.pixel)
            view.xy(Metrics.x(where.x) - view.width / 2, Metrics.floorTop(where.floor) + 18.0)
            floaters += Floater(view, 0.0)
        }
        val it = floaters.iterator()
        while (it.hasNext()) {
            val f = it.next()
            f.age += dt
            f.view.y -= dt * 30
            f.view.alpha = (1.0 - (f.age - 1.2) / 0.8).coerceIn(0.0, 1.0)
            if (f.age > 2.0) {
                f.view.removeFromParent()
                it.remove()
            }
        }
    }

    private fun rebuildRooms() {
        drawnVersion = sim.tower.version
        drawnLooks = sim.looksVersion
        val alive = sim.tower.allRooms.associateBy { it.id }
        roomViews.keys.filter { it !in alive }.forEach { roomViews.remove(it)?.removeFromParent() }
        for (room in alive.values) {
            // the key includes position and width: a lobby that grew by merging keeps its id and art
            val art = "${artFor(room)}@${room.x}+${room.width}"
            if (room.id !in roomViews || roomArt[room.id] != art) {
                roomViews.remove(room.id)?.removeFromParent()
                roomViews[room.id] = createRoomView(room)
                roomArt[room.id] = art
            }
        }
    }

    /**
     * Day art, the run-down version when its people are unhappy, or the night version
     * when it's dark, whenever the atlases have one.
     */
    private fun artFor(room: Room): String {
        val key = RoomLooks.art(sim, room)
        val mood = sim.moodOf(room)
        return when {
            mood != null && mood < Simulation.UNHAPPY && assets.hasRoom(key + "_rundown") -> key + "_rundown"
            DayLight.isDark(sim.clock.minuteOfDay) && assets.hasRoom(key + "_night") -> key + "_night"
            else -> key
        }
    }

    /** Elevator cabins glide over their shafts; the number shows how many people ride along. */
    private fun syncCars() {
        val alive = HashSet<Int>()
        for (car in sim.elevators.cars.values) {
            car.cabins.forEachIndexed { i, cabin ->
                val key = car.id * 16 + i
                alive += key
                val view = carViews.getOrPut(key) {
                    carsLayer.container {
                        graphics {
                            fill(Theme.INK) { chamferPath(64.0, 82.0, 6.0, 0.0) }
                            fill(Theme.GOLD) { chamferPath(64.0, 82.0, 6.0, 3.0) }
                            fill(Theme.alpha(Theme.INK, 0.85)) { rect(10.0, 12.0, 44.0, 54.0) }
                        }
                    }.also { carLabels[key] = it.label("", 14.0, Theme.GOLD, Theme.pixel).xy(20.0, 30.0) }
                }
                view.xy(Metrics.x(car.x) + 16.0, Metrics.floorTop(0) - cabin.position * Metrics.FLOOR + 6.0)
                carLabels.getValue(key).text = if (cabin.riders.isEmpty()) "" else cabin.riders.size.toString()
            }
        }
        carViews.keys.filter { it !in alive }.forEach {
            carViews.remove(it)?.removeFromParent()
            carLabels.remove(it)
        }
    }

    private fun createRoomView(room: Room): View {
        val layer = if (room.type.role.isWay) waysLayer else roomsLayer
        val view = layer.container { xy(Metrics.x(room.x), Metrics.floorTop(room.floor)) }
        val key = artFor(room)
        val art = assets.room(key)
        val tileSegments = 6
        val tiles = (room.width + tileSegments - 1) / tileSegments
        // rooms without night art are dimmed so they don't glow in the dark
        val dimmed = DayLight.isDark(sim.clock.minuteOfDay) && !key.endsWith("_night")
        // a long lobby gets a door at each end; the right one is the left one mirrored
        val entrance = if (room.type.role == RoomRole.LOBBY && tiles >= 2) entranceArt(key) else null
        val clip = view.clipContainer(Size(Metrics.x(room.width), Metrics.FLOOR))
        for (i in 0 until tiles) {
            val last = i == tiles - 1
            val tile = when {
                entrance != null && (i == 0 || last) -> entrance
                else -> art
            }
            clip.image(tile) {
                scale = 1.0 / Assets.ROOM_ART_SCALE * 2
                x = Metrics.x(i * tileSegments)
                if (entrance != null && last) {
                    // mirrored and pushed against the right end of the lobby
                    scaleX = -scaleX
                    x = Metrics.x(room.width)
                }
                if (dimmed) colorMul = RGBA(120, 124, 165)
            }
        }
        return view
    }

    /** The entrance art matching a lobby art key (day, night or run-down), if the atlas has it. */
    private fun entranceArt(lobbyKey: String) =
        listOf("_night", "_rundown").firstOrNull { lobbyKey.endsWith(it) }.orEmpty()
            .let { suffix -> "lobby_entrance$suffix" }
            .takeIf { assets.hasRoom(it) }
            ?.let { assets.room(it) }

    /**
     * Where the people waiting for an elevator stand: in a line in front of the doors, the
     * first one closest, like in SimTower. Long lines continue on the other side of the shaft.
     * Maps entity id to x in segments and whether they face left.
     */
    private fun elevatorQueues(): Map<Int, Pair<Float, Boolean>> {
        val spots = HashMap<Int, Pair<Float, Boolean>>()
        for (car in sim.elevators.cars.values) {
            for (line in car.waiting.values) {
                line.sortedBy { car.waitingSince[it] ?: 0.0 }.forEachIndexed { i, entity ->
                    spots[entity.id] = if (i < QUEUE_SIDE) {
                        car.x - QUEUE_GAP * (i + 0.6f) to false
                    } else {
                        car.x + 2 + QUEUE_GAP * (i - QUEUE_SIDE + 0.6f) to true
                    }
                }
            }
        }
        return spots
    }

    private fun syncPeople() {
        val seen = HashSet<Int>()
        val queues = elevatorQueues()
        val family = sim.world.family { all(Person, Position) }
        family.forEach { entity ->
            val pos = entity[Position]
            val person = entity[Person]
            seen += entity.id
            val view = personViews.getOrPut(entity.id) {
                peopleLayer.image(assets.person(person.look, 0), Anchor.BOTTOM_CENTER) { smoothing = false }
            }
            val moving = entity has Route
            val frame = if (moving) 1 + (floor(pos.x * 3).toInt() and 1) else 0
            view.bitmap = assets.person(person.look, frame)
            val queued = queues[entity.id]
            val facingLeft = queued?.second ?: pos.facingLeft
            if (queued != null) {
                view.bitmap = assets.person(person.look, 0)
                view.x = Metrics.x(queued.first)
            } else {
                // people standing at the same spot stand a little apart
                val queueing = !moving || (entity[Route].legs.firstOrNull() is Leg.Ride)
                view.x = Metrics.x(pos.x) + if (queueing) ((entity.id % 7) - 3) * 5.0 else 0.0
            }
            view.y = Metrics.floorBottom(pos.floor) - Metrics.FEET
            val scale = assets.personScale(person.look, 2.0)
            // native 12x24 pixel art stays crisp, the 4x art is shrunk smoothly
            view.smoothing = scale < 1.0
            view.scaleX = if (facingLeft) -scale else scale
            view.scaleY = scale
            // with a thinner crowd only every second or fourth person is drawn, but never the selected one
            val shown = (entity.id % 4) < Options.current.crowd * 4 || (game.selection as? Selection.PersonSel)?.entity == entity
            view.visible = shown && PeopleVisibility.isVisible(sim, entity)
        }
        personViews.keys.filter { it !in seen }.forEach { personViews.remove(it)?.removeFromParent() }
    }

    private fun syncOverlay() {
        val sel = game.selection
        selectionFrame.visible = false
        personMarker.visible = false
        when (sel) {
            is Selection.RoomSel -> sim.tower.room(sel.roomId)?.let { room ->
                frame(selectionFrame, Metrics.x(room.width), Metrics.FLOOR, Theme.SELECT)
                selectionFrame.xy(Metrics.x(room.x), Metrics.floorTop(room.floor))
                selectionFrame.visible = true
            } ?: run { game.selection = null }
            is Selection.PersonSel -> {
                val view = personViews[sel.entity.id]
                if (view == null || !sim.world.contains(sel.entity)) {
                    game.selection = null
                } else {
                    personMarker.xy(view.x, view.y - 54.0)
                    personMarker.visible = view.visible
                }
            }
            null -> {}
        }
        updateGhost()
    }

    private fun updateGhost() {
        val h = hover
        val tool = game.tool
        ghostInfo = null
        ghost.visible = false
        if (h == null || tool == null) return
        when (tool) {
            is Tool.Build -> when {
                grabbedEnd != null -> {
                    val modules = sim.planRetract(grabbedEnd!!, h.floor)
                    val end = grabbedEnd!!
                    if (modules.isEmpty()) {
                        showGhost(listOf(Ghost(end.floor, end.x, end.width)), Theme.SELECT, 0.1)
                        ghostInfo = "Ende gegriffen · ziehen zum Verlängern oder Kürzen" to true
                    } else {
                        showGhost(modules.map { Ghost(it.floor, it.x, it.width) }, Theme.ALARM, 0.25)
                        val refund = modules.sumOf { sim.refundFor(it) }
                        ghostInfo = "${modules.size} ${if (modules.size == 1) "Etage" else "Etagen"} abbauen · ${formatMoney(refund)} zurück" to true
                    }
                }
                tool.type.stretchable || tool.type.role.isWay -> {
                    val plan = planFor(tool.type, h)
                    val color = if (plan.check.ok) Theme.SELECT else Theme.ALARM
                    val pieces = plan.pieces.ifEmpty { listOf(BuildPlan.Piece(h.floor, BuildRules.snapX(h.segment, tool.type.width, sim.tower.width), tool.type.width)) }
                    showGhost(pieces.map { Ghost(it.floor, it.x, it.width) }, color, 0.2)
                    ghostInfo = if (plan.check.ok) planText(plan) to true else plan.check.reason to false
                }
                else -> {
                    val x = BuildRules.snapX(h.segment, tool.type.width, sim.tower.width)
                    val check = sim.checkPlace(tool.type, h.floor, x)
                    showGhost(listOf(Ghost(h.floor, x, tool.type.width)), if (check.ok) Theme.SELECT else Theme.ALARM, 0.2)
                    ghostInfo = if (check.ok) {
                        "${tool.type.name} · ${formatMoney(tool.type.cost)} · Klicken zum Bauen" to true
                    } else {
                        check.reason to false
                    }
                }
            }
            Tool.Demolish -> {
                val room = sim.tower.topAt(h.floor, floor(h.segment).toInt()) ?: return
                val blocked = sim.checkDemolish(room)
                if (!blocked.ok) {
                    showGhost(listOf(Ghost(room.floor, room.x, room.width)), Theme.ALARM, 0.1)
                    ghostInfo = blocked.reason to false
                } else {
                    showGhost(listOf(Ghost(room.floor, room.x, room.width)), Theme.ALARM, 0.25)
                    ghostInfo = "${room.type.name} abreißen · ${formatMoney(sim.refundFor(room))} zurück" to true
                }
            }
        }
    }

    private data class Ghost(val floor: Int, val x: Int, val width: Int)

    private var drawnGhost: Pair<List<Ghost>, RGBA>? = null

    private fun showGhost(parts: List<Ghost>, color: RGBA, fill: Double) {
        ghost.visible = true
        if (drawnGhost == parts to color) return
        drawnGhost = parts to color
        ghost.removeChildren()
        for (part in parts) {
            val box = ghost.container { xy(Metrics.x(part.x), Metrics.floorTop(part.floor)) }
            box.solidRect(Metrics.x(part.width), Metrics.FLOOR, Theme.alpha(color, fill))
            frame(box.container(), Metrics.x(part.width), Metrics.FLOOR, color)
        }
    }

    /**
     * What the build tool would place for the current mouse position. Lobbies stretch along
     * the ground floor and stairs and elevators along a column, from where the button went down.
     */
    private fun planFor(type: RoomType, h: Hover): BuildPlan {
        val anchor = pressHover ?: h
        return if (type.stretchable) {
            if (h.floor != GROUND_FLOOR) return BuildPlan(type, emptyList(), 0, PlaceCheck.no("Lobby nur im Erdgeschoss"))
            val from = floor(anchor.segment).toInt()
            val to = floor(h.segment).toInt()
            if (pressHover == null || from == to) {
                val x = BuildRules.snapX(h.segment, type.width, sim.tower.width)
                sim.planLobby(x, x + type.width - 1)
            } else {
                sim.planLobby(from, to)
            }
        } else {
            val x = BuildRules.snapX(anchor.segment, type.width, sim.tower.width)
            sim.planColumn(type, x, anchor.floor, h.floor)
        }
    }

    private fun planText(plan: BuildPlan): String {
        val size = if (plan.type.stretchable) {
            "${plan.pieces.sumOf { it.width }} Felder"
        } else {
            "${plan.pieces.size} ${if (plan.pieces.size == 1) "Etage" else "Etagen"}"
        }
        val hint = if (pressHover == null) "Ziehen zum Bauen" else "Loslassen zum Bauen"
        return "${plan.type.name} · $size · ${formatMoney(plan.cost)} · $hint"
    }

    private fun frame(target: Container, w: Double, h: Double, color: RGBA, t: Double = 2.0) {
        target.removeChildren()
        target.solidRect(w, t, color)
        target.solidRect(w, t, color).y = h - t
        target.solidRect(t, h, color)
        target.solidRect(t, h, color).x = w - t
    }

    // ---------------------------------------------------------------- camera and input

    private fun setupInput() {
        hit.onMove { updateHover() }
        world.onMove { updateHover() }
        // on the whole view, not only the empty sky: the rooms are in front of the hit area
        onScroll { e ->
            val at = screen(e.currentPosGlobal)
            if (!isOverUi(at)) zoomAt(at, if (e.scrollDeltaYPixels > 0) 1 / 1.12 else 1.12)
        }
        for (target in listOf<View>(hit, world)) {
            target.onDown { e ->
                if (isOverUi(screen(e.currentPosGlobal))) return@onDown
                dragStart = screen(e.currentPosGlobal)
                dragOrigin = Point(world.x, world.y)
                dragging = false
                dragButton = e.button
                if (e.button == MouseButton.LEFT) pressBuildTool()
            }
        }
        onMove { e ->
            val start = dragStart ?: return@onMove
            val delta = screen(e.currentPosGlobal) - start
            val panButton = dragButton != MouseButton.LEFT || game.tool == null
            if (!dragging && panButton && (abs(delta.x) > 4 || abs(delta.y) > 4)) dragging = true
            if (dragging) {
                world.x = dragOrigin.x + delta.x
                world.y = dragOrigin.y + delta.y
                clampCamera()
            }
            updateHover()
        }
        onUpAnywhere { e ->
            if (dragStart == null) return@onUpAnywhere
            val wasDrag = dragging
            dragStart = null
            dragging = false
            if (!wasDrag && e.button == MouseButton.LEFT) clicked()
            if (!wasDrag && e.button == MouseButton.RIGHT) game.cancel()
            pressHover = null
            grabbedEnd = null
        }
        keys {
            downFrame(Key.LEFT) { pan(14.0, 0.0) }
            downFrame(Key.A) { pan(14.0, 0.0) }
            downFrame(Key.RIGHT) { pan(-14.0, 0.0) }
            downFrame(Key.D) { pan(-14.0, 0.0) }
            downFrame(Key.UP) { pan(0.0, 14.0) }
            downFrame(Key.W) { pan(0.0, 14.0) }
            downFrame(Key.DOWN) { pan(0.0, -14.0) }
            downFrame(Key.S) { pan(0.0, -14.0) }
            down(Key.PLUS) { zoomAt(Point(screenWidth / 2, screenHeight / 2), 1.12) }
            down(Key.KP_ADD) { zoomAt(Point(screenWidth / 2, screenHeight / 2), 1.12) }
            down(Key.MINUS) { zoomAt(Point(screenWidth / 2, screenHeight / 2), 1 / 1.12) }
            down(Key.KP_SUBTRACT) { zoomAt(Point(screenWidth / 2, screenHeight / 2), 1 / 1.12) }
        }
    }

    private var dragButton: MouseButton? = null

    private fun pan(dx: Double, dy: Double) {
        world.x += dx
        world.y += dy
        clampCamera()
    }

    /** A window point in screen layout units (what [world]'s position and the HUD use). */
    private fun screen(global: Point): Point = globalToLocal(global)

    /** Zooms around [screenPoint] (screen layout units), keeping the spot under it in place. */
    private fun zoomAt(screenPoint: Point, factor: Double) {
        val newZoom = (zoom * factor).coerceIn(0.35, 2.0)
        val localX = (screenPoint.x - world.x) / zoom
        val localY = (screenPoint.y - world.y) / zoom
        zoom = newZoom
        world.scale = zoom
        world.x = screenPoint.x - localX * zoom
        world.y = screenPoint.y - localY * zoom
        clampCamera()
    }

    private fun clampCamera() {
        val lot = Metrics.x(sim.tower.width) * zoom
        world.x = world.x.coerceIn(-lot + 200, screenWidth - 200)
        world.y = world.y.coerceIn(screenHeight * 0.4, 110 * Metrics.FLOOR * zoom)
    }

    /** The window changed size: keep the middle of the view in the middle and the ground at the same distance from the bottom. */
    fun resize(width: Double, height: Double) {
        world.x += (width - screenWidth) / 2
        world.y += height - screenHeight
        screenWidth = width
        screenHeight = height
        hit.size(width, height)
        clampCamera()
    }

    /** Moves the camera so that [loc] is in the middle of the screen. */
    fun lookAt(loc: Loc) {
        world.x = screenWidth / 2 - Metrics.x(loc.x) * zoom
        world.y = screenHeight / 2 - (Metrics.floorBottom(loc.floor) - Metrics.FLOOR / 2) * zoom
        clampCamera()
    }

    private fun updateHover() {
        val stage = stage ?: return
        val global = stage.views.globalMousePos
        val local = world.globalToLocal(global)
        val floorIndex = floor(-local.y / Metrics.FLOOR).toInt()
        hover = Hover(floorIndex, (local.x / Metrics.SEG).toFloat(), screen(global))
    }

    /**
     * The left button went down with a build tool: remember the anchor of the drag, and
     * whether the player grabbed the end of a shaft or staircase (to shorten it).
     */
    private fun pressBuildTool() {
        updateHover()
        val tool = game.tool as? Tool.Build ?: return
        val h = hover ?: return
        pressHover = h
        if (tool.type.role.isWay) {
            val module = sim.tower.wayAt(h.floor, floor(h.segment).toInt())?.takeIf { it.type == tool.type }
            val isEnd = module != null && listOf(module.floor + 1, module.floor - 1).any { f ->
                sim.tower.wayAt(f, module.x)?.let { it.type == module.type && it.x == module.x } != true
            }
            grabbedEnd = if (isEnd) module else null
        }
    }

    private fun clicked() {
        updateHover()
        val h = hover ?: return
        when (val tool = game.tool) {
            is Tool.Build -> when {
                grabbedEnd != null -> {
                    val modules = sim.planRetract(grabbedEnd!!, h.floor)
                    if (modules.isNotEmpty()) {
                        val refund = sim.demolishAll(modules)
                        Sfx.play(Sfx.DEMOLISH)
                        game.say("${tool.type.name} um ${modules.size} ${if (modules.size == 1) "Etage" else "Etagen"} gekürzt, ${formatMoney(refund)} zurück")
                    } else {
                        val plan = planFor(tool.type, h)
                        if (plan.pieces.isNotEmpty()) build(plan)
                    }
                }
                tool.type.stretchable || tool.type.role.isWay -> build(planFor(tool.type, h))
                else -> {
                    val x = BuildRules.snapX(h.segment, tool.type.width, sim.tower.width)
                    val result = sim.build(tool.type, h.floor, x)
                    feedback(result.ok, result.reason)
                }
            }
            Tool.Demolish -> {
                val room = sim.tower.topAt(h.floor, floor(h.segment).toInt())
                val blocked = room?.let { sim.checkDemolish(it) }
                if (room != null && blocked != null && !blocked.ok) {
                    game.say(blocked.reason)
                    Sfx.play(Sfx.ERROR)
                } else if (room != null) {
                    val refund = sim.demolish(room.id)
                    Sfx.play(Sfx.DEMOLISH)
                    game.say("${room.type.name} abgerissen, ${formatMoney(refund)} zurück")
                }
            }
            null -> select(h)
        }
    }

    private fun build(plan: BuildPlan) {
        val result = sim.build(plan)
        feedback(result.ok, result.reason)
    }

    private fun feedback(ok: Boolean, reason: String) {
        if (ok) {
            Sfx.play(Sfx.BUILD)
        } else {
            game.say(reason)
            Sfx.play(Sfx.ERROR)
        }
    }

    /** Every tenant sounds different when clicked; ways ding, empty rooms sound hollow. */
    private fun roomSound(room: Room): String {
        val tenant = room.tenant?.id
        return when {
            tenant != null && Sfx.has(tenant) -> tenant
            room.type.role.isWay || room.type.role == RoomRole.LOBBY -> Sfx.DING
            else -> Sfx.EMPTY
        }
    }

    private fun select(h: Hover) {
        val local = world.globalToLocal(localToGlobal(h.screen))
        // people first: they stand in front of the rooms
        var best: Entity? = null
        var bestDistance = Double.MAX_VALUE
        sim.world.family { all(Person, Position) }.forEach { entity ->
            val view = personViews[entity.id] ?: return@forEach
            if (!view.visible) return@forEach
            val dx = abs(local.x - view.x)
            val dy = view.y - local.y
            if (dx < 14 && dy in 0.0..50.0 && dx < bestDistance) {
                best = entity
                bestDistance = dx
            }
        }
        val room = sim.tower.topAt(h.floor, floor(h.segment).toInt())
        game.selection = when {
            best != null -> Selection.PersonSel(best!!)
            room != null -> Selection.RoomSel(room.id)
            else -> null
        }
        when {
            best != null -> Sfx.play(Sfx.CLICK)
            room != null -> Sfx.play(roomSound(room))
        }
    }
}

/** People in an elevator line stand this many segments apart; this many on the near side, the rest on the far side. */
private const val QUEUE_GAP = 0.55f
private const val QUEUE_SIDE = 8

/** Daylight over the day: 1 at noon, 0 at night, with dawn and dusk in between. */
object DayLight {
    fun light(minuteOfDay: Int): Double {
        if (!Options.current.dayNight) return 1.0
        val h = minuteOfDay / 60.0
        return when {
            h < 5 || h >= 21.5 -> 0.0
            h < 7 -> (h - 5) / 2
            h < 19 -> 1.0
            else -> 1 - (h - 19) / 2.5
        }
    }

    fun isDark(minuteOfDay: Int): Boolean = light(minuteOfDay) < 0.35

    private val day = RGBA(70, 104, 160)
    private val dusk = RGBA(150, 92, 130)
    private val night = RGBA(16, 20, 44)

    fun skyColor(light: Double): RGBA = when {
        light >= 1 -> day
        light >= 0.5 -> RGBA.interpolate(dusk, day, ((light - 0.5) * 2).toRatio())
        else -> RGBA.interpolate(night, dusk, (light * 2).toRatio())
    }
}

/** Snapping of the build ghost: the room is centred on the mouse and kept on the lot. */
object BuildRules {
    fun snapX(segment: Float, width: Int, lotWidth: Int): Int =
        floor(segment - width / 2f + 0.5f).toInt().coerceIn(0, max(0, lotWidth - width)).let { min(it, lotWidth - width) }
}
