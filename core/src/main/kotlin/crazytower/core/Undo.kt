package crazytower.core

/**
 * Taking back the last build or demolition (Ctrl+Z). Only the last step counts and only for
 * [Undo.MINUTES] game minutes. A build comes back with its whole price as long as nobody has
 * moved in; a demolition only when the rooms were empty, and then costs the refund again.
 * Rented rooms are protected by a question before the wrecking ball, not by undo: their
 * tenants, people and running events can't simply be put back.
 */
object Undo {
    /** How long a step can be taken back, in game minutes. */
    const val MINUTES = 60.0
}

/** A room as it was before it was torn down, enough to put it back. */
data class RemovedRoom(val type: RoomType, val floor: Int, val x: Int, val width: Int, val paid: Long, val cabins: Int, val refund: Long)

/** The last step: rooms built (by id, with their total [cost]) or rooms torn down. */
data class UndoStep(
    val label: String,
    val at: Double,
    val built: List<Int> = emptyList(),
    val cost: Long = 0,
    val removed: List<RemovedRoom> = emptyList(),
)

/**
 * Builds with [block] and remembers what was added, so [undo] can take it back. Lobbies and
 * station parts can't be taken back: lobbies join the old lobby, station parts start the countdown.
 */
fun Simulation.undoableBuild(block: () -> BuildResult): BuildResult {
    val before = tower.allRooms.mapTo(HashSet()) { it.id }
    val lobbiesBefore = tower.allRooms.filter { it.type.role == RoomRole.LOBBY }.map { it.x to it.width }
    val moneyBefore = money
    val result = block()
    val added = tower.allRooms.filter { it.id !in before }
    if (added.isEmpty()) return result
    val lobbyChanged = tower.allRooms.filter { it.type.role == RoomRole.LOBBY }.map { it.x to it.width } != lobbiesBefore
    undoStep = if (lobbyChanged || added.any { Mission.partOf(it.type) != null }) null
    else UndoStep(added.first().type.name.toString(), clock.totalMinutes, built = added.map { it.id }, cost = moneyBefore - money)
    return result
}

/**
 * Tears down [rooms] (in this order) and remembers them, so [undo] can put empty ones back.
 * Returns the refund.
 */
fun Simulation.undoableDemolish(rooms: List<Room>): Long {
    val snapshot = rooms.map { RemovedRoom(it.type, it.floor, it.x, it.width, it.paid, it.cabins, refundFor(it)) }
    val empty = rooms.all { it.tenant == null && !it.sold && it.type.role != RoomRole.LOBBY && Mission.partOf(it.type) == null }
    val gone = rooms.takeWhile { demolish(it.id) >= 0 && tower.room(it.id) == null }
    val refund = snapshot.take(gone.size).sumOf { it.refund }
    if (gone.isNotEmpty()) undoStep = if (empty) UndoStep(rooms.first().type.name.toString(), clock.totalMinutes, removed = snapshot.take(gone.size)) else null
    return refund
}

/** Why the last step can't be taken back now, or null if [undo] would work. */
fun Simulation.undoBlocked(): String? {
    val step = undoStep ?: return tr("Nichts zum Rückgängigmachen", "Nothing to undo")
    if (clock.totalMinutes - step.at > Undo.MINUTES) return tr("Zu spät: Rückgängig geht nur eine Spielstunde lang", "Too late: undo only works for one game hour")
    if (step.built.isNotEmpty()) {
        val rooms = step.built.map { tower.room(it) ?: return tr("${step.label} steht nicht mehr", "${step.label} is no longer there") }
        if (rooms.any { it.tenant != null || it.sold }) return tr("Dort ist schon jemand eingezogen", "Somebody has already moved in")
        // the last built first: a shaft comes down from its end
        val others = tower.allRooms.filter { it.id !in step.built }
        if (rooms.any { r -> tower.dependents(r).any { it in others } }) return tr("Darauf steht schon etwas Neues", "Something new stands on it")
    } else {
        val refunds = step.removed.sumOf { it.refund }
        if (money < refunds) return Simulation.NO_MONEY
        for (r in step.removed.asReversed()) {
            val check = checkRules(r.type, r.floor, r.x, r.width)
            if (!check.ok) return check.reason
        }
        val first = step.removed.last()
        tower.canPlace(first.type, first.floor, first.x, first.width).takeUnless { it.ok }?.let { return it.reason }
    }
    return null
}

/** Takes the last step back if it can; returns what happened, for the player. */
fun Simulation.undo(): PlaceCheck {
    undoBlocked()?.let { return PlaceCheck.no(it) }
    val step = undoStep!!
    undoStep = null
    if (step.built.isNotEmpty()) {
        var refund = 0L
        for (id in step.built.asReversed()) refund += demolish(id)
        // demolish gave a quarter back, undo gives the rest of what the build cost
        book(step.cost - refund, MoneyKind.BUILD, tr("Rückgängig: ${step.label}", "Undone: ${step.label}"))
        return PlaceCheck(true, tr("${step.label} rückgängig gemacht, ${formatMoney(step.cost)} zurück", "${step.label} undone, ${formatMoney(step.cost)} back"))
    }
    var restored = 0
    for (r in step.removed.asReversed()) {
        if (!tower.canPlace(r.type, r.floor, r.x, r.width).ok) continue
        val room = tower.place(r.type, r.floor, r.x, r.width)
        room.paid = r.paid
        room.cabins = r.cabins
        room.since = clock.totalMinutes
        book(-r.refund, MoneyKind.BUILD, tr("Rückgängig: Abriss ${r.type.name}", "Undone: demolished ${r.type.name}"), Loc(r.floor, r.x + r.width / 2f))
        restored++
    }
    if (restored == 0) return PlaceCheck.no(tr("Dort ist kein Platz mehr", "There is no room for it any more"))
    return PlaceCheck(true, tr("${step.label} steht wieder", "${step.label} is back"))
}
