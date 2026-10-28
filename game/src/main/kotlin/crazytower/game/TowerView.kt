package crazytower.game

import com.github.quillraven.fleks.Entity
import crazytower.core.Loc
import crazytower.core.Room
import crazytower.core.Simulation
import crazytower.core.ecs.Person
import crazytower.core.ecs.Position
import crazytower.core.ecs.Riding
import crazytower.core.ecs.Route
import crazytower.core.nav.Leg
import crazytower.game.ui.chamferPath
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
    private val screenWidth: Double,
    private val screenHeight: Double,
) : Container() {
    /** Everything that moves with the camera. */
    val world = container()
    private val sky = world.solidRect(1.0, 1.0, Theme.SKY)
    private val backdrop = world.container()
    private val dusk = world.solidRect(1.0, 1.0, RGBA(0, 0, 0, 0))
    private val roomsLayer = world.container()
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
    private val ghostFill = ghost.solidRect(10, 10, Theme.alpha(Theme.SELECT, 0.18))
    private val ghostFrame = ghost.container()
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
    private var dragStart: Point? = null
    private var dragOrigin = Point(0.0, 0.0)
    private var dragging = false

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
            }
        }
        backdrop.solidRect(lotWidth + 8000, 3000.0, Theme.GROUND).xy(-4000.0, 0.0)
        backdrop.solidRect(lotWidth + 8000, 20.0, Theme.GROUND_DARK).xy(-4000.0, 6.0)
        backdrop.solidRect(lotWidth + 8000, Metrics.FEET, Theme.STREET).xy(-4000.0, -Metrics.FEET)
        // lot markers
        for (x in listOf(0.0, lotWidth)) backdrop.solidRect(4.0, 24.0, Theme.GOLD).xy(x - 2, -24.0)
    }

    // ---------------------------------------------------------------- sync

    /** Mirrors the simulation into views. Called once per frame. */
    fun sync() {
        val dark = DayLight.isDark(sim.clock.minuteOfDay)
        if (drawnVersion != sim.tower.version || drawnLooks != sim.looksVersion || drawnDark != dark || ++artCheck % 60 == 0) {
            drawnDark = dark
            rebuildRooms()
        }
        syncSky()
        syncCars()
        syncPeople()
        syncOverlay()
    }

    private fun rebuildRooms() {
        drawnVersion = sim.tower.version
        drawnLooks = sim.looksVersion
        val alive = sim.tower.allRooms.associateBy { it.id }
        roomViews.keys.filter { it !in alive }.forEach { roomViews.remove(it)?.removeFromParent() }
        for (room in alive.values) {
            val art = artFor(room)
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

    /** Elevator cars glide over their shafts; the number shows how many people ride along. */
    private fun syncCars() {
        val cars = sim.elevators.cars
        carViews.keys.filter { it !in cars }.forEach {
            carViews.remove(it)?.removeFromParent()
            carLabels.remove(it)
        }
        for (car in cars.values) {
            val view = carViews.getOrPut(car.id) {
                carsLayer.container {
                    graphics {
                        fill(Theme.INK) { chamferPath(64.0, 82.0, 6.0, 0.0) }
                        fill(Theme.GOLD) { chamferPath(64.0, 82.0, 6.0, 3.0) }
                        fill(Theme.alpha(Theme.INK, 0.85)) { rect(10.0, 12.0, 44.0, 54.0) }
                    }
                }.also { carLabels[car.id] = it.label("", 14.0, Theme.GOLD, Theme.pixel).xy(24.0, 30.0) }
            }
            view.xy(Metrics.x(car.x) + 16.0, Metrics.floorTop(0) - car.position * Metrics.FLOOR + 6.0)
            carLabels.getValue(car.id).text =
                if (car.riders.isEmpty()) "" else car.riders.size.toString()
        }
    }

    private fun createRoomView(room: Room): View {
        val view = roomsLayer.container { xy(Metrics.x(room.x), Metrics.floorTop(room.floor)) }
        val art = assets.room(artFor(room))
        val tileSegments = 6
        val tiles = (room.width + tileSegments - 1) / tileSegments
        // rooms without night art are dimmed so they don't glow in the dark
        val dimmed = DayLight.isDark(sim.clock.minuteOfDay) && !artFor(room).endsWith("_night")
        for (i in 0 until tiles) {
            view.image(art) {
                scale = 1.0 / Assets.ROOM_ART_SCALE * 2
                x = Metrics.x(i * tileSegments)
                if (dimmed) colorMul = RGBA(120, 124, 165)
            }
        }
        return view
    }

    private fun syncPeople() {
        val seen = HashSet<Int>()
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
            // people queueing at the same spot stand a little apart
            val queueing = !moving || (entity[Route].legs.firstOrNull() is Leg.Ride)
            view.x = Metrics.x(pos.x) + if (queueing) ((entity.id % 7) - 3) * 5.0 else 0.0
            view.y = Metrics.floorBottom(pos.floor) - Metrics.FEET
            view.scaleX = if (pos.facingLeft) -2.0 else 2.0
            view.scaleY = 2.0
            view.visible = PeopleVisibility.isVisible(sim, entity)
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
        if (h == null || tool == null) {
            ghost.visible = false
            return
        }
        when (tool) {
            is Tool.Build -> {
                val x = BuildRules.snapX(h.segment, tool.type.width, sim.tower.width)
                val check = sim.checkPlace(tool.type, h.floor, x)
                val color = if (check.ok) Theme.SELECT else Theme.ALARM
                ghost.xy(Metrics.x(x), Metrics.floorTop(h.floor))
                ghostFill.size(Metrics.x(tool.type.width), Metrics.FLOOR)
                ghostFill.color = Theme.alpha(color, 0.2)
                frame(ghostFrame, Metrics.x(tool.type.width), Metrics.FLOOR, color)
                ghost.visible = true
                ghostInfo = if (check.ok) {
                    "${tool.type.name} · ${crazytower.game.ui.formatMoney(tool.type.cost)} · Klicken zum Bauen" to true
                } else {
                    check.reason to false
                }
            }
            Tool.Demolish -> {
                val room = sim.tower.roomAt(h.floor, floor(h.segment).toInt())
                val blocked = room?.let { sim.checkDemolish(it) }
                if (room == null) {
                    ghost.visible = false
                } else if (blocked != null && !blocked.ok) {
                    ghost.xy(Metrics.x(room.x), Metrics.floorTop(room.floor))
                    ghostFill.size(Metrics.x(room.width), Metrics.FLOOR)
                    ghostFill.color = Theme.alpha(Theme.ALARM, 0.1)
                    frame(ghostFrame, Metrics.x(room.width), Metrics.FLOOR, Theme.ALARM)
                    ghost.visible = true
                    ghostInfo = blocked.reason to false
                } else {
                    ghost.xy(Metrics.x(room.x), Metrics.floorTop(room.floor))
                    ghostFill.size(Metrics.x(room.width), Metrics.FLOOR)
                    ghostFill.color = Theme.alpha(Theme.ALARM, 0.25)
                    frame(ghostFrame, Metrics.x(room.width), Metrics.FLOOR, Theme.ALARM)
                    ghost.visible = true
                    ghostInfo = "${room.type.name} abreißen · ${crazytower.game.ui.formatMoney(sim.refundFor(room))} zurück" to true
                }
            }
        }
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
        // an invisible full-screen hit area so clicks in empty sky still reach us
        val hit = SolidRect(screenWidth, screenHeight, RGBA(0, 0, 0, 0)).also { addChildAt(it, 0) }
        hit.onMove { updateHover() }
        world.onMove { updateHover() }
        hit.onScroll { e -> zoomAt(e.currentPosGlobal, if (e.scrollDeltaYPixels > 0) 1 / 1.12 else 1.12) }
        for (target in listOf<View>(hit, world)) {
            target.onDown { e ->
                if (isOverUi(e.currentPosGlobal)) return@onDown
                dragStart = e.currentPosGlobal
                dragOrigin = Point(world.x, world.y)
                dragging = false
                dragButton = e.button
            }
        }
        onMove { e ->
            val start = dragStart ?: return@onMove
            val delta = e.currentPosGlobal - start
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

    private fun zoomAt(screenPoint: Point, factor: Double) {
        val newZoom = (zoom * factor).coerceIn(0.35, 2.0)
        val local = world.globalToLocal(screenPoint)
        zoom = newZoom
        world.scale = zoom
        val after = world.localToGlobal(local)
        world.x += screenPoint.x - after.x
        world.y += screenPoint.y - after.y
        clampCamera()
    }

    private fun clampCamera() {
        val lot = Metrics.x(sim.tower.width) * zoom
        world.x = world.x.coerceIn(-lot + 200, screenWidth - 200)
        world.y = world.y.coerceIn(screenHeight * 0.4, 110 * Metrics.FLOOR * zoom)
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
        hover = Hover(floorIndex, (local.x / Metrics.SEG).toFloat(), global)
    }

    private fun clicked() {
        updateHover()
        val h = hover ?: return
        when (val tool = game.tool) {
            is Tool.Build -> {
                val x = BuildRules.snapX(h.segment, tool.type.width, sim.tower.width)
                val result = sim.build(tool.type, h.floor, x)
                if (!result.ok) game.say(result.reason)
            }
            Tool.Demolish -> {
                val room = sim.tower.roomAt(h.floor, floor(h.segment).toInt())
                val blocked = room?.let { sim.checkDemolish(it) }
                if (room != null && blocked != null && !blocked.ok) {
                    game.say(blocked.reason)
                } else if (room != null) {
                    val refund = sim.demolish(room.id)
                    game.say("${room.type.name} abgerissen, ${crazytower.game.ui.formatMoney(refund)} zurück")
                }
            }
            null -> select(h)
        }
    }

    private fun select(h: Hover) {
        val local = world.globalToLocal(h.screen)
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
        val room = sim.tower.roomAt(h.floor, floor(h.segment).toInt())
        game.selection = when {
            best != null -> Selection.PersonSel(best!!)
            room != null -> Selection.RoomSel(room.id)
            else -> null
        }
    }
}

/** Daylight over the day: 1 at noon, 0 at night, with dawn and dusk in between. */
object DayLight {
    fun light(minuteOfDay: Int): Double {
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
