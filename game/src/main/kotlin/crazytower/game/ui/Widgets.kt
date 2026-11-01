package crazytower.game.ui

import crazytower.game.Sfx
import crazytower.game.Theme
import korlibs.image.color.RGBA
import korlibs.image.font.Font
import korlibs.korge.input.onClick
import korlibs.korge.input.onDown
import korlibs.korge.input.onOut
import korlibs.korge.input.onOver
import korlibs.korge.input.onUpAnywhere
import korlibs.korge.view.*
import korlibs.math.geom.Point
import korlibs.math.geom.vector.VectorBuilder

/** Draws a rectangle with the top-left and bottom-right corners cut off (the low-poly panel shape). */
fun Graphics.chamfer(width: Double, height: Double, cut: Double, color: RGBA) {
    updateShape { fill(color) { chamferPath(width, height, cut, 0.0) } }
}

/** A dark UI panel with chamfered corners. */
class Panel(var panelWidth: Double, var panelHeight: Double, color: RGBA = Theme.PANEL, private val cut: Double = 10.0) : Container() {
    private val bg = graphics { }
    var color: RGBA = color
        set(value) { field = value; redraw() }

    init { redraw() }

    fun resize(width: Double, height: Double) {
        panelWidth = width
        panelHeight = height
        redraw()
    }

    private fun redraw() = bg.chamfer(panelWidth, panelHeight, cut, color)
}

fun Container.panel(width: Double, height: Double, color: RGBA = Theme.PANEL, cut: Double = 10.0): Panel =
    Panel(width, height, color, cut).addTo(this)

/** No interface text is smaller than this, so it stays readable; smaller sizes are raised to it. */
const val MIN_TEXT = 13.0

fun Container.label(
    value: String,
    size: Double = 14.0,
    color: RGBA = Theme.TEXT,
    font: Font = Theme.body,
): Text = text(value, textSize = maxOf(size, MIN_TEXT), color = color, font = font)

/**
 * Makes a one-line text at most [maxWidth] wide: a little too long shrinks it, much too long
 * also cuts it with "…". It never shrinks below [MIN_TEXT]; a cut text shows the whole text
 * as a tip when the mouse is on it. Texts that fit stay as they are, in every language.
 */
fun Text.fitWidth(maxWidth: Double): Text {
    scale = 1.0
    if (width <= maxWidth) {
        Tips.uncut(this)
        return this
    }
    val full = text
    val minScale = maxOf(MIN_FIT, MIN_TEXT / textSize).coerceAtMost(1.0)
    var keep = full.length
    while (width * minScale > maxWidth && keep > 1) {
        keep--
        text = full.take(keep).trimEnd() + "…"
    }
    if (width > maxWidth) scale = maxWidth / width
    if (text != full) Tips.cut(this, full) else Tips.uncut(this)
    return this
}

/** Texts are shrunk to this share of their size at most before they are cut. */
private const val MIN_FIT = 0.8

/** What a hover tip says: a title, an optional explanation, the key that does the same, and why it is locked. */
data class Tip(val title: String, val body: String? = null, val key: String? = null, val locked: String? = null)

/**
 * Every view that has a tip, and the texts cut by [fitWidth]. [TipLayer] asks for the tip
 * under the mouse each frame: the smallest view wins, a view's own tip before a cut text.
 */
object Tips {
    private val own = java.util.WeakHashMap<View, () -> Tip?>()
    private val cutTexts = java.util.WeakHashMap<Text, String>()

    /** For screenshots: where the mouse is pretended to be, in window coordinates. */
    var fakeMouse: Point? = null

    fun register(view: View, provider: () -> Tip?) {
        own[view] = provider
    }

    internal fun cut(text: Text, full: String) {
        cutTexts[text] = full
    }

    internal fun uncut(text: Text) {
        cutTexts.remove(text)
    }

    /** The tip of the view under [global] (window coordinates), and that view. */
    fun at(global: Point): Pair<View, Tip>? =
        find(own.entries.map { (view, provider) -> view to provider }, global)
            ?: find(cutTexts.entries.map { (text, full) -> text to { Tip(full) } }, global)

    private fun find(candidates: List<Pair<View, () -> Tip?>>, global: Point): Pair<View, Tip>? {
        var best: Pair<View, Tip>? = null
        var bestArea = Double.MAX_VALUE
        for ((view, provider) in candidates) {
            if (!view.shownOnStage()) continue
            val bounds = view.getGlobalBounds()
            if (!bounds.contains(global)) continue
            val area = bounds.width * bounds.height
            if (area >= bestArea) continue
            val tip = provider() ?: continue
            best = view to tip
            bestArea = area
        }
        return best
    }

    private fun View.shownOnStage(): Boolean {
        var v: View? = this
        while (v != null) {
            // not the alpha: an invisible hit area is a rectangle in a see-through colour, and that is its alpha
            if (!v.visible) return false
            if (v is Stage) return true
            v = v.parent
        }
        return false
    }
}

/** Gives this view a hover tip; [provider] is asked every frame the mouse is on it and may return null. */
fun <T : View> T.tip(provider: () -> Tip?): T {
    Tips.register(this, provider)
    return this
}

fun <T : View> T.tip(title: String, body: String? = null, key: String? = null): T = tip { Tip(title, body, key) }

/**
 * Draws the tip of whatever the mouse rests on, after a short moment, below it (or above
 * when there is no room). Add it last, so it is in front of everything; [update] once a frame.
 */
class TipLayer(var screenWidth: Double, var screenHeight: Double) : Container() {
    private var source: View? = null
    private var shown: Tip? = null
    private var waited = 0.0
    private var boxWidth = 0.0
    private var boxHeight = 0.0

    init {
        visible = false
        mouseEnabled = false
    }

    fun update(dt: Double) {
        val stage = stage ?: return
        val mouse = Tips.fakeMouse ?: stage.views.globalMousePos
        val hit = Tips.at(mouse)
        if (hit == null) {
            source = null
            visible = false
            return
        }
        val (view, tip) = hit
        if (view !== source) {
            source = view
            waited = 0.0
        }
        waited += dt
        if (waited < DELAY && Tips.fakeMouse == null) {
            visible = false
            return
        }
        if (tip != shown) rebuild(tip)
        place(view)
        visible = true
    }

    private fun rebuild(tip: Tip) {
        shown = tip
        removeChildren()
        val content = Container()
        val title = Text(tip.title, 14.0, Theme.TEXT, Theme.bodyBold)
        title.addTo(content).xy(0.0, 0.0)
        var width = title.width
        var y = title.height + 2
        if (tip.key != null) {
            val key = Text(tip.key, MIN_TEXT, Theme.GOLD, Theme.bodyBold)
            key.addTo(content).xy(title.width + 14.0, 1.0)
            width = key.x + key.width
        }
        if (tip.body != null) {
            val body = Text(wrapText(tip.body, MAX_WIDTH, MIN_TEXT), MIN_TEXT, Theme.TEXT_DIM, Theme.body)
            body.addTo(content).xy(0.0, y + 2)
            width = maxOf(width, body.width)
            y += body.height + 4
        }
        if (tip.locked != null) {
            val lock = Icons.lock(content, Theme.ALARM).xy(0.0, y + 4)
            val reason = Text(wrapText(tip.locked, MAX_WIDTH - 18.0, MIN_TEXT), MIN_TEXT, Theme.ALARM, Theme.bodyBold)
            reason.addTo(content).xy(18.0, y + 2)
            width = maxOf(width, reason.x + reason.width)
            y += maxOf(reason.height, lock.height) + 4
        }
        boxWidth = width + 24
        boxHeight = y + 18
        Panel(boxWidth, boxHeight, Theme.SELECT, 6.0).addTo(this)
        Panel(boxWidth - 4, boxHeight - 4, Theme.INK, 5.0).addTo(this).xy(2.0, 2.0)
        content.addTo(this).xy(12.0, 9.0)
    }

    /** Below [view], centred on it, inside the screen; above it when the bottom is too close. */
    private fun place(view: View) {
        val bounds = view.getGlobalBounds()
        val topLeft = parent?.globalToLocal(Point(bounds.left, bounds.top)) ?: return
        val bottomRight = parent!!.globalToLocal(Point(bounds.right, bounds.bottom))
        val below = bottomRight.y + 6
        val y = if (below + boxHeight > screenHeight - 54) topLeft.y - boxHeight - 6 else below
        val x = (topLeft.x + bottomRight.x) / 2 - boxWidth / 2
        xy(x.coerceIn(4.0, (screenWidth - boxWidth - 4).coerceAtLeast(4.0)), y.coerceAtLeast(4.0))
    }

    private companion object {
        /** Seconds the mouse rests on a view before its tip shows. */
        const val DELAY = 0.35
        const val MAX_WIDTH = 300.0
    }
}

/**
 * Breaks [value] into lines of at most [maxWidth] at [size] in [font], at the spaces.
 * Line breaks already in [value] are kept.
 */
fun wrapText(value: String, maxWidth: Double, textSize: Double, font: Font = Theme.body): String {
    val size = maxOf(textSize, MIN_TEXT)
    val probe = Text("", size, font = font)
    fun widthOf(s: String): Double { probe.text = s; return probe.width }
    return value.split('\n').joinToString("\n") { paragraph ->
        val lines = ArrayList<String>()
        var line = ""
        for (word in paragraph.split(' ')) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && widthOf(candidate) > maxWidth) {
                lines += line
                line = word
            } else {
                line = candidate
            }
        }
        lines += line
        lines.joinToString("\n")
    }
}

/** A label broken into lines of at most [maxWidth], see [wrapText]. */
fun Container.wrappedLabel(
    value: String,
    maxWidth: Double,
    size: Double = 14.0,
    color: RGBA = Theme.TEXT,
    font: Font = Theme.body,
): Text = label(wrapText(value, maxWidth, size, font), size, color, font)

/**
 * The game's button: hover lifts it with a teal frame, pressed pushes it down,
 * selected fills it teal, disabled shows it greyed out.
 */
open class Button(val buttonWidth: Double, val buttonHeight: Double, private val onPress: () -> Unit) : Container() {
    private val shadow = graphics { }
    private val bg = graphics { }
    val content = Container().addTo(this)
    private var hovered = false
    private var pressed = false

    var selected = false
        set(value) { if (field != value) { field = value; redraw() } }
    var enabled = true
        set(value) { if (field != value) { field = value; redraw() } }

    /** Colour for icons and labels that follow the button state. */
    val ink: RGBA get() = when {
        !enabled -> Theme.TEXT_LOCKED
        selected -> Theme.ON_SELECT
        else -> Theme.TEXT
    }

    /** Called after every state change so subclasses can recolour their content. */
    var onRestyle: (Button) -> Unit = {}

    init {
        onOver { hovered = true; redraw() }
        onOut { hovered = false; pressed = false; redraw() }
        onDown { if (enabled) { pressed = true; redraw() } }
        onUpAnywhere { pressed = false; redraw() }
        onClick { if (enabled) { Sfx.play(Sfx.CLICK); onPress() } }
        redraw()
    }

    protected fun redraw() {
        val fill = when {
            !enabled -> Theme.LOCKED
            selected -> Theme.SELECT
            pressed -> Theme.BUTTON_DOWN
            hovered -> Theme.BUTTON_HOVER
            else -> Theme.BUTTON
        }
        val framed = hovered && enabled && !selected && !pressed
        val lift = when {
            pressed -> 2.0
            framed -> -1.0
            else -> 0.0
        }
        val faceHeight = buttonHeight - 3.0
        shadow.updateShape {
            fill(if (selected) Theme.SELECT_DARK else Theme.INK) { chamferPath(buttonWidth, faceHeight, 8.0, 0.0) }
        }
        shadow.y = 3.0
        shadow.visible = enabled
        bg.updateShape {
            if (framed) {
                fill(Theme.SELECT) { chamferPath(buttonWidth, faceHeight, 8.0, 0.0) }
                fill(fill) { chamferPath(buttonWidth, faceHeight, 8.0, 2.0) }
            } else {
                fill(fill) { chamferPath(buttonWidth, faceHeight, 8.0, 0.0) }
            }
        }
        bg.y = lift
        content.y = lift
        onRestyle(this)
    }
}

/** Outline of a chamfered rectangle, shrunk by [inset] on every side. */
fun VectorBuilder.chamferPath(w: Double, h: Double, cut: Double, inset: Double) {
    moveTo(Point(cut + inset, inset))
    lineTo(Point(w - inset, inset))
    lineTo(Point(w - inset, h - cut))
    lineTo(Point(w - cut, h - inset))
    lineTo(Point(inset, h - inset))
    lineTo(Point(inset, cut))
    close()
}

/** A button with a single centred text label. */
class TextButton(width: Double, height: Double, title: String, size: Double = 15.0, font: Font = Theme.bodyBold, onPress: () -> Unit) :
    Button(width, height, onPress) {
    val text: Text = content.text(title, textSize = maxOf(size, MIN_TEXT), color = Theme.TEXT, font = font)

    init {
        onRestyle = { text.color = it.ink }
        centre()
        onRestyle(this)
    }

    fun setTitle(title: String) {
        text.text = title
        centre()
    }

    private fun centre() {
        // long titles (some languages need more letters) shrink instead of spilling over the edge
        text.fitWidth(buttonWidth - 12.0)
        text.x = (buttonWidth - text.width * text.scaleX) / 2
        text.y = (buttonHeight - 3.0 - text.height * text.scaleY) / 2
    }
}

/** Numbers in the current language: 48250 is "48.250" or "48,250". */
fun formatNumber(value: Long): String = crazytower.core.formatNumber(value)

/** Money in the current language: "48.250 $" or "$48,250". */
fun formatMoney(value: Long): String = crazytower.core.formatMoney(value)
