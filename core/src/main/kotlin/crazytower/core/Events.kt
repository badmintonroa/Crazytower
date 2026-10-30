package crazytower.core

import crazytower.core.ecs.Away
import crazytower.core.ecs.Member
import crazytower.core.ecs.Position
import crazytower.core.ecs.Schedule

/**
 * Absurd things that happen to the tower. The player can't prevent them, only live with them
 * (a security service makes the bad ones rarer). Each event returns false when it doesn't fit
 * the current tower, e.g. a clown takeover without offices.
 */
class TowerEvent(
    val id: String,
    val weight: Int,
    val minStage: Int = 1,
    val bad: Boolean = false,
    val apply: Simulation.() -> Boolean,
)

object Events {
    val all: List<TowerEvent> = listOf(
        TowerEvent("clown", 4) {
            val office = rentedRooms(RoomRole.OFFICE).filter { it.tenant?.id != "clown" }.randomOrNull(random) ?: return@TowerEvent false
            val old = office.tenant!!.name
            val clowns = Tenants.byId("clown")
            moveIn(office, clowns, announce = false)
            news.post(tr("$old ist über Nacht zur $clowns geworden. Die Hupen sind beglaubigt.", "Overnight, $old has turned into the $clowns. The horns are certified."), office.center, important = true)
            true
        },
        TowerEvent("ghost", 2) {
            val home = rentedRooms(RoomRole.HOME).filter { it.tenant?.id != "ghost" }.randomOrNull(random) ?: return@TowerEvent false
            val old = home.tenant!!.name
            moveIn(home, Tenants.byId("ghost"), announce = false)
            news.post(tr("Bei $old spukt es. $old ist ausgezogen, das Gespenst bleibt und zahlt keine Miete.", "$old's flat is haunted. $old moved out, the ghost stays and pays no rent."), home.center, important = true)
            true
        },
        TowerEvent("wedding", 3, bad = true) {
            val car = elevators.cars.values.filter { it.bottom != it.top }.randomOrNull(random) ?: return@TowerEvent false
            car.blockedUntil = clock.totalMinutes + 45
            news.post(tr("Im Aufzug wird spontan eine Hochzeit gefeiert. Er fährt erst nach dem Brautstrauß wieder.", "A spontaneous wedding in the elevator. It won't move until the bouquet is thrown."),
                Loc(car.position.toInt(), car.centerX), important = true)
            true
        },
        TowerEvent("pigeons", 3, bad = true) {
            val top = tower.builtFloors.maxOrNull() ?: return@TowerEvent false
            if (top < 2) return@TowerEvent false
            changeMood(-10f) { it.floor == top }
            news.post(tr("Tauben haben die ${Simulation.floorLabel(top)} besetzt. Sie wollen Mitspracherecht.", "Pigeons have occupied ${Simulation.floorLabel(top)}. They want a say."), Loc(top, lotWidth / 2f), important = true)
            true
        },
        TowerEvent("pizza", 3) {
            if (population == 0) return@TowerEvent false
            changeMood(8f) { true }
            news.post(tr("Gratis-Pizza für alle! Keiner weiß, wer sie bestellt hat. Alle essen trotzdem.", "Free pizza for everyone! Nobody knows who ordered it. Everyone eats anyway."))
            true
        },
        TowerEvent("refund", 2) {
            val amount = 5_000L + random.nextLong(15_000)
            book(amount, MoneyKind.LUCK, tr("Finanzamt-Fehler", "Tax office error"))
            news.post(tr("Das Finanzamt hat sich verrechnet. Zu deinen Gunsten. Einmalig: ${formatMoney(amount)}.", "The tax office got its sums wrong. In your favour. Just this once: ${formatMoney(amount)}."))
            true
        },
        TowerEvent("fine", 2, bad = true) {
            book(-3_000, MoneyKind.FINE, tr("Bußgeld", "Fine"))
            news.post(tr("Bußgeld über ${formatMoney(3_000)}: Der Turm war drei Zentimeter zu hoch. Er wurde nicht kleiner.", "A ${formatMoney(3_000)} fine: the tower was three centimetres too tall. It did not get any shorter."))
            true
        },
        TowerEvent("influencer", 2) {
            if (tower.allRooms.none { it.type.role == RoomRole.LOBBY }) return@TowerEvent false
            repeat(12) { spawnVisitor() }
            news.post(tr("Ein Influencer hat den Turm gepostet. Busladungen voller Fans stehen in der Lobby.", "An influencer posted the tower. Busloads of fans are standing in the lobby."), important = true)
            true
        },
        TowerEvent("smell", 2, bad = true) {
            val office = rentedRooms(RoomRole.OFFICE).randomOrNull(random) ?: return@TowerEvent false
            changeMood(-15f) { it.roomId == office.id }
            news.post(tr("Bei ${office.tenant!!.name} riecht es nach Fisch. Keiner gibt es zu.", "Something smells fishy at ${office.tenant!!.name}. Nobody admits it."), office.center, important = true)
            true
        },
        TowerEvent("tourist", 2) {
            news.post(tr("Ein Tourist sucht seit drei Tagen den Ausgang. Er findet es schön hier.", "A tourist has been looking for the exit for three days. He likes it here."))
            true
        },
        TowerEvent("cat", 2) {
            if (elevators.cars.isEmpty()) return@TowerEvent false
            news.post(tr("Eine Katze fährt seit Stunden Aufzug. Sie drückt alle Knöpfe. Jeden einzeln.", "A cat has been riding the elevator for hours. It presses every button. One at a time."))
            true
        },
        TowerEvent("blackout", 2, minStage = 2, bad = true) {
            if (elevators.cars.isEmpty()) return@TowerEvent false
            elevators.cars.values.forEach { it.blockedUntil = clock.totalMinutes + 25 }
            news.post(tr("Stromausfall! Jemand hat den Wasserkocher und den Teilchenbeschleuniger gleichzeitig angemacht.", "Power cut! Someone switched on the kettle and the particle accelerator at the same time."), important = true)
            true
        },
        TowerEvent("timetraveler", 2, minStage = 3) {
            news.post(tr("Ein Mann aus dem Jahr 2087 fragt, ob der Turm schon steht. Er steht.", "A man from the year 2087 asks whether the tower has been built yet. It has."))
            true
        },
        TowerEvent("penguins", 2, minStage = 3) {
            changeMood(5f) { true }
            news.post(tr("Eine Pinguin-Parade zieht durch die Lobby. Die Laune steigt, die Raumtemperatur fällt.", "A penguin parade marches through the lobby. Moods go up, the room temperature goes down."))
            true
        },
        TowerEvent("vip", 4, minStage = 4) {
            if (vipSurvived || vipOnTheWay) return@TowerEvent false
            spawnVip(Vips.byId("wichtig"))
        },
        TowerEvent("ufo", 2, minStage = 5) {
            val pad = tower.allRooms.firstOrNull { it.type == Catalog.UFO_PAD }
            if (pad != null) {
                book(50_000, MoneyKind.LUCK, tr("Ufo-Parkgebühr", "UFO parking fee"), pad.center)
                news.post(tr("Ein Ufo ist gelandet und zahlt brav ${formatMoney(50_000)} Parkgebühr. In Gold. Von Gliese.", "A UFO has landed and dutifully pays ${formatMoney(50_000)} in parking fees. In gold. From Gliese."), pad.center, important = true)
            } else {
                news.post(tr("Ein Ufo kreist über dem Turm und findet keinen Parkplatz.", "A UFO is circling the tower and can't find a parking space."))
            }
            true
        },
    )

    /** Rolls the dice for one hour: maybe something absurd happens. */
    fun roll(sim: Simulation) {
        if (sim.random.nextFloat() > CHANCE_PER_HOUR) return
        val secured = sim.tower.allRooms.any { it.type.role == RoomRole.SECURITY }
        val pool = all.filter { it.minStage <= sim.stage }
        val weights = pool.map { if (it.bad && secured) it.weight / 2.0 else it.weight.toDouble() }
        var pick = sim.random.nextDouble() * weights.sum()
        for ((event, weight) in pool.zip(weights)) {
            pick -= weight
            if (pick <= 0) {
                if (event.bad && secured && sim.random.nextFloat() < 0.3f) {
                    sim.news.post(tr("Der Sicherheitsdienst hat etwas Schlimmes verhindert. Er sagt nicht, was.", "Security has prevented something terrible. They won't say what."))
                } else {
                    event.apply(sim)
                }
                return
            }
        }
    }

    const val CHANCE_PER_HOUR = 0.15f
}

private fun Simulation.rentedRooms(role: RoomRole): List<Room> = tower.allRooms.filter { it.type.role == role && it.tenant != null }

/** Changes the mood of every member who is in the tower and matches [where]. */
private fun Simulation.changeMood(delta: Float, where: (MoodTarget) -> Boolean) = with(world) {
    family { all(Member, Schedule, Position) }.forEach { e ->
        if (e has Away) return@forEach
        if (where(MoodTarget(e[Position].floor, e[Member].roomId))) {
            e[Schedule].mood = (e[Schedule].mood + delta).coerceIn(0f, 100f)
        }
    }
}

private class MoodTarget(val floor: Int, val roomId: Int)
