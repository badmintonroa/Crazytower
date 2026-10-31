package crazytower.core

import crazytower.core.ecs.Away
import crazytower.core.ecs.Member
import crazytower.core.ecs.Position
import crazytower.core.ecs.Schedule

/**
 * Absurd things that happen to the tower. Most just happen; a security service makes the bad
 * ones rarer. [lasting] events run for a while (see [Lasting]) and often have a remedy. Each
 * event returns false when it doesn't fit the current tower, e.g. a clown takeover without offices.
 */
class TowerEvent(
    val id: String,
    val weight: Int,
    val minStage: Int = 1,
    val bad: Boolean = false,
    val lasting: Boolean = false,
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
            // the owners fled without a word: nobody is owed the purchase price any more
            home.salePaid = 0
            news.post(tr("Bei $old spukt es. $old ist ausgezogen, das Gespenst bleibt und zahlt keine Miete.", "$old's flat is haunted. $old moved out, the ghost stays and pays no rent."), home.center, important = true)
            true
        },
        TowerEvent("wedding", 3, bad = true, lasting = true) {
            val car = elevators.cars.values.filter { it.bottom != it.top }.randomOrNull(random) ?: return@TowerEvent false
            val now = clock.totalMinutes
            startEvent(
                ActiveEvent("wedding", now, now + 45, floor = car.bottom, carX = car.x),
                tr("Im Aufzug wird spontan eine Hochzeit gefeiert. Er fährt erst nach dem Brautstrauß wieder.", "A spontaneous wedding in the elevator. It won't move until the bouquet is thrown."),
                Loc(car.position.toInt(), car.centerX),
            )
        },
        TowerEvent("karaoke", 2, bad = true, lasting = true) {
            val car = elevators.cars.values.filter { it.bottom != it.top && it.blockedUntil <= clock.totalMinutes }.randomOrNull(random) ?: return@TowerEvent false
            val now = clock.totalMinutes
            startEvent(
                ActiveEvent("karaoke", now, now + 180, floor = car.bottom, carX = car.x),
                tr("Jemand hat eine Karaoke-Anlage in den Aufzug gebaut. Er fährt jetzt im Takt. Sehr langsamer Takt.", "Somebody built a karaoke machine into the elevator. It now runs in time with the music. Very slow music."),
                Loc(car.position.toInt(), car.centerX),
            )
        },
        TowerEvent("blooddrive", 2, minStage = 2, bad = true, lasting = true) {
            if (!clock.isNight) return@TowerEvent false
            val office = rentedBy("vampire").filter { o -> tower.allRooms.none { it.type.role == RoomRole.FOOD && it.floor == o.floor } }.randomOrNull(random)
                ?: return@TowerEvent false
            val now = clock.totalMinutes
            startEvent(
                ActiveEvent("blooddrive", now, now + 360, roomId = office.id, floor = office.floor),
                tr("Die Vampir-Versicherung lädt zur Blutspende. Freiwillig. Sagen sie. Auf der ${Simulation.floorLabel(office.floor)} sinkt die Laune.",
                    "The vampire insurers invite everyone to a blood drive. Voluntary, they say. Moods drop ${Simulation.onFloor(office.floor)}."),
            )
        },
        TowerEvent("clownriot", 2, bad = true, lasting = true) {
            val clowns = rentedBy("clown").randomOrNull(random) ?: return@TowerEvent false
            if (rentedRooms(RoomRole.OFFICE).none { it.tenant?.id != "clown" && !it.type.luxuryOffice }) return@TowerEvent false
            val now = clock.totalMinutes
            startEvent(
                ActiveEvent("clownriot", now, now + 3 * GameClock.MINUTES_PER_DAY, roomId = clowns.id),
                tr("Clown-Aufstand! Die Clownsagentur will expandieren. Jede Nacht kann ein Nachbarbüro rote Nasen bekommen.",
                    "Clown uprising! The clown agency wants to expand. Every night a neighbouring office may get red noses."),
            )
        },
        TowerEvent("socks", 2, minStage = 2, bad = true, lasting = true) {
            val archive = rentedBy("socks").filter { o -> tower.allRooms.any { it.floor == o.floor - 1 && it.tenant != null } }.randomOrNull(random)
                ?: return@TowerEvent false
            val now = clock.totalMinutes
            val housekeeping = tower.allRooms.any { it.type.role == RoomRole.HOUSEKEEPING && it.tenant != null }
            startEvent(
                ActiveEvent("socks", now, now + if (housekeeping) 60 else 120, floor = archive.floor - 1, x = archive.centerX),
                tr("Im Sockenarchiv ist ein Regal umgekippt. Die ${Simulation.floorLabel(archive.floor - 1)} steht bis zur Decke voller einzelner Socken.",
                    "A shelf fell over in the sock archive. ${Simulation.floorLabel(archive.floor - 1).replaceFirstChar { it.uppercase() }} is full of odd socks up to the ceiling."),
            )
        },
        TowerEvent("blackmail", 2, minStage = 4, bad = true, lasting = true) {
            if (stars < 3) return@TowerEvent false
            val lair = rentedBy("supervillain").randomOrNull(random) ?: return@TowerEvent false
            val now = clock.totalMinutes
            startEvent(
                ActiveEvent("blackmail", now, now + GameClock.MINUTES_PER_DAY, roomId = lair.id),
                tr("Die Weltherrschaft Holding fordert ${formatMoney(Lasting.BLACKMAIL)}. Sonst schaltet der Mondlaser alle Aufzüge ab. Frist: morgen.",
                    "World Domination Holdings demands ${formatMoney(Lasting.BLACKMAIL)}. Or the moon laser shuts down every elevator. Deadline: tomorrow."),
            )
        },
        TowerEvent("firealarm", 2, bad = true, lasting = true) {
            val food = rentedRooms(RoomRole.FOOD).randomOrNull(random) ?: return@TowerEvent false
            val now = clock.totalMinutes
            val secured = tower.allRooms.any { it.type.role == RoomRole.SECURITY }
            startEvent(
                ActiveEvent("firealarm", now, now + if (secured) 15 else 30, roomId = food.id),
                tr("Feueralarm! Bei ${food.tenant!!.name} ist ein Toast verbrannt. Alle raus auf die Straße.",
                    "Fire alarm! A piece of toast burnt at ${food.tenant!!.name}. Everybody out onto the street."),
            )
        },
        TowerEvent("termites", 2, bad = true, lasting = true) {
            if (clock.day < 10) return@TowerEvent false
            val room = tower.allRooms.filter { (it.type.role == RoomRole.OFFICE || it.type.role == RoomRole.HOME) && it.tenant == null }.randomOrNull(random)
                ?: return@TowerEvent false
            val now = clock.totalMinutes
            startEvent(
                ActiveEvent("termites", now, now + GameClock.MINUTES_PER_DAY, roomId = room.id),
                tr("Termiten mit Architekturdiplom bauen ein leeres Zimmer um. Sie haben Pläne. Große Pläne.",
                    "Termites with a degree in architecture are remodelling an empty room. They have plans. Big plans."),
            )
        },
        TowerEvent("exchange", 2, minStage = 5, lasting = true) {
            if (rentedBy("alien").isEmpty()) return@TowerEvent false
            val home = rentedRooms(RoomRole.HOME).filter { it.tenant?.id !in setOf("ghost", "exchange") }.randomOrNull(random) ?: return@TowerEvent false
            val old = home.tenant!!
            val now = clock.totalMinutes
            val event = ActiveEvent("exchange", now, now + GameClock.MINUTES_PER_DAY, roomId = home.id, note = old.id)
            if (activeEvents.any { it.id == "exchange" }) return@TowerEvent false
            moveIn(home, Tenants.byId("exchange"), announce = false)
            val fee = (Lasting.EXCHANGE_FEE * starBonus).toLong()
            book(fee, MoneyKind.LUCK, tr("Austauschgebühr", "Exchange fee"), home.center)
            startEvent(
                event,
                tr("Alien-Austauschprogramm: ${old.name} verbringt einen Tag auf Gliese, dafür wohnen Aliens hier. Die Botschaft zahlt ${formatMoney(fee)}.",
                    "Alien exchange programme: ${old.name} spends a day on Gliese, aliens live here instead. The embassy pays ${formatMoney(fee)}."),
            )
        },
        TowerEvent("giantcat", 2, minStage = 4, lasting = true) {
            val top = tower.builtFloors.maxOrNull() ?: return@TowerEvent false
            if (top < 5) return@TowerEvent false
            val right = tower.allRooms.filter { it.floor == top }.maxOfOrNull { it.right } ?: return@TowerEvent false
            val now = clock.totalMinutes
            startEvent(
                ActiveEvent("giantcat", now, now + 120, floor = top, x = right - 2f),
                tr("Eine Riesenkatze sitzt auf dem Dach! Sie schnurrt, die ${Simulation.floorLabel(top)} vibriert. Die Presse ist schon da.",
                    "A giant cat is sitting on the roof! She purrs and ${Simulation.floorLabel(top)} vibrates. The press is already here."),
            )
        },
        TowerEvent("moon", 1, minStage = 5, lasting = true) {
            if (tower.allRooms.none { it.type.role == RoomRole.TELEPORTER }) return@TowerEvent false
            val now = clock.totalMinutes
            startEvent(
                ActiveEvent("moon", now, now + 120),
                tr("Der Teleporter hat sich verwählt: Der ganze Turm steht jetzt auf dem Mond. Die Aussicht ist toll, die Besucher bleiben aus.",
                    "The teleporter dialled the wrong number: the whole tower is on the moon now. Great view, no visitors."),
            )
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
        TowerEvent("ministry", 2) {
            val office = rentedBy("ministry").randomOrNull(random) ?: return@TowerEvent false
            book(-MINISTRY_FINE, MoneyKind.FINE, tr("Selbstprüfung", "Self-audit"), office.center)
            book(MINISTRY_FINE, MoneyKind.LUCK, tr("Selbstprüfung erstattet", "Self-audit refunded"), office.center)
            news.post(tr("Das Ministerium für Zuständigkeit hat sich selbst geprüft: ${formatMoney(MINISTRY_FINE)} Bußgeld. Und gleich wieder erstattet. Vier Formulare, Netto null.",
                "The Ministry of Responsibility audited itself: a ${formatMoney(MINISTRY_FINE)} fine. Refunded at once. Four forms, net zero."), office.center, important = true)
            true
        },
        TowerEvent("excuses", 2) {
            val office = rentedBy("excuses").randomOrNull(random) ?: return@TowerEvent false
            val tenant = office.tenant!!
            // the next working day is not paid for
            office.paidUntil = tenant.plan.nextEnd(clock.totalMinutes)?.toDouble() ?: return@TowerEvent false
            val excuse = EXCUSES[random.nextInt(EXCUSES.size)]
            news.post(tr("Die ${tenant.name} hat sich heute krankgemeldet und zahlt keine Miete. Begründung: $excuse", "The ${tenant.name} called in sick today and pays no rent. Reason: $excuse"), office.center, important = true)
            true
        },
        TowerEvent("prepaid", 2, minStage = 3) {
            val office = rentedBy("timetravel").randomOrNull(random) ?: return@TowerEvent false
            val tenant = office.tenant!!
            val next = tenant.plan.nextEnd(clock.totalMinutes) ?: return@TowerEvent false
            // tomorrow's rent comes today, and tomorrow nothing comes
            book(rentFor(office, tenant), MoneyKind.RENT, tenant.name.toString(), office.center)
            office.paidUntil = next.toDouble()
            news.post(tr("Das Zeitreisebüro hat die Miete von morgen schon gestern überwiesen. Heute ist sie da. Morgen kommt dafür nichts.",
                "The time travel agency paid tomorrow's rent yesterday. It arrived today. Tomorrow nothing comes."), office.center, important = true)
            true
        },
        TowerEvent("band", 2) {
            val room = tower.allRooms.firstOrNull { it.tenant?.id == "band" } ?: return@TowerEvent false
            changeMood(-15f) { it.floor in room.floor - 1..room.floor + 1 && it.roomId != room.id }
            val tip = (room.tenant!!.rentPerDay * starBonus / 2).toLong()
            book(tip, MoneyKind.HOTEL, tr("Trinkgeld der Band", "The band's tip"), room.center)
            news.post(tr("Die Lärmschutzwände proben im Hotelzimmer. Die Nachbarn leiden, die Band gibt ${formatMoney(tip)} Trinkgeld. Für die Ohrstöpsel.",
                "The Noise Barriers are rehearsing in their hotel room. The neighbours suffer, the band tips ${formatMoney(tip)}. For the earplugs."), room.center, important = true)
            true
        },
        TowerEvent("aircon", 2, minStage = 3) {
            val home = rentedBy("penguins").randomOrNull(random) ?: return@TowerEvent false
            book(-AIRCON_COST, MoneyKind.UPKEEP, tr("Klimaanlage auf Antarktis", "Air conditioning set to Antarctica"), home.center)
            changeMood(30f) { it.roomId == home.id }
            news.post(tr("Die Pinguine haben die Klimaanlage auf Antarktis gestellt. Strom: ${formatMoney(AIRCON_COST)}. Die Pinguine: begeistert.",
                "The penguins set the air conditioning to Antarctica. Power bill: ${formatMoney(AIRCON_COST)}. The penguins: delighted."), home.center, important = true)
            true
        },
        TowerEvent("scale", 2, minStage = 4) {
            val bank = rentedBy("dragonbank").randomOrNull(random) ?: return@TowerEvent false
            val amount = 8_000L + random.nextLong(12_001)
            book(amount, MoneyKind.LUCK, tr("Drachenschuppe", "Dragon scale"), bank.center)
            news.post(tr("Der Drache der Drachenbank hat eine Schuppe verloren. Aus purem Gold. Der Hausmeister hat sie gefunden: ${formatMoney(amount)}.",
                "The dragon at the dragon bank lost a scale. Solid gold. The caretaker found it: ${formatMoney(amount)}."), bank.center, important = true)
            true
        },
        TowerEvent("roofcar", 2) {
            val park = tower.allRooms.firstOrNull { it.type.role == RoomRole.PARKING } ?: return@TowerEvent false
            if (tower.allRooms.none { it.type.role == RoomRole.LOBBY }) return@TowerEvent false
            repeat(4) { spawnVisitor() }
            news.post(tr("Ein Auto aus dem Parkhaus steht plötzlich auf dem Dach. Keiner weiß wie. Schaulustige kommen in Scharen.",
                "A car from the car park is suddenly standing on the roof. Nobody knows how. Onlookers flock in."), park.center, important = true)
            true
        },
        TowerEvent("robots", 2) {
            if (tower.allRooms.none { it.tenant?.id == "robots" }) return@TowerEvent false
            val dirty = dirtyRooms
            if (dirty.isEmpty()) return@TowerEvent false
            dirty.forEach { cleaned(it) }
            news.post(tr("Der Saugroboter-Schwarm hat alle ${dirty.size} schmutzigen Hotelzimmer auf einmal geputzt. Jetzt will er eine Gewerkschaft gründen.",
                "The robot vacuum swarm cleaned all ${dirty.size} dirty hotel rooms at once. Now it wants to form a union."), dirty.first().center, important = true)
            true
        },
        TowerEvent("leak", 2, minStage = 5, bad = true) {
            val consulate = rentedBy("atlantis").randomOrNull(random) ?: return@TowerEvent false
            val below = consulate.floor - 1
            if (tower.allRooms.none { it.floor == below && it.tenant != null }) return@TowerEvent false
            changeMood(-10f) { it.floor == below }
            news.post(tr("Atlantis meldet einen Wasserschaden. In der ${Simulation.floorLabel(below)} tropft es. Atlantis findet es gemütlich.",
                "Atlantis reports water damage. It is dripping ${Simulation.onFloor(below)}. Atlantis finds it cosy."), Loc(below, consulate.centerX), important = true)
            true
        },
        TowerEvent("ghostparty", 2) {
            val ghosts = rentedBy("ghost")
            if (ghosts.size < 2) return@TowerEvent false
            val floors = ghosts.map { it.floor }.toSet()
            changeMood(-20f) { it.floor in floors }
            if (tower.allRooms.any { it.type.role == RoomRole.LOBBY }) repeat(5) { spawnVisitor() }
            news.post(tr("Gespenster-Treffen im Turm! Die Nachbarn schlafen schlecht, dafür kommen Leute zur Spukführung.",
                "Ghost meeting in the tower! The neighbours sleep badly, but people come for the haunted tour."), ghosts.first().center, important = true)
            true
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
        val busy = sim.activeEvents.size >= Lasting.MAX_ACTIVE
        val pool = all.filter { e -> e.minStage <= sim.stage && (!e.lasting || !busy) && sim.activeEvents.none { it.id == e.id } }
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
    const val MINISTRY_FINE = 2_000L
    const val AIRCON_COST = 3_000L

    private val EXCUSES = listOf(
        Tr("Der Hund hat den Wecker gefressen.", "The dog ate the alarm clock."),
        Tr("Akute Montagsallergie, ärztlich bestätigt.", "Acute Monday allergy, confirmed by a doctor."),
        Tr("Im Aufzug stand die falsche Musik.", "The elevator was playing the wrong music."),
        Tr("Ein Horoskop, das ausdrücklich vor Büros warnt.", "A horoscope that expressly warns against offices."),
        Tr("Die Ausrede für heute ist noch in Arbeit.", "Today's excuse is still being worked on."),
    )
}

internal fun Simulation.rentedBy(tenantId: String): List<Room> = tower.allRooms.filter { it.tenant?.id == tenantId }

internal fun Simulation.rentedRooms(role: RoomRole): List<Room> = tower.allRooms.filter { it.type.role == role && it.tenant != null }

/** Changes the mood of every member who is in the tower and matches [where]. */
internal fun Simulation.changeMood(delta: Float, where: (MoodTarget) -> Boolean) = with(world) {
    family { all(Member, Schedule, Position) }.forEach { e ->
        if (e has Away) return@forEach
        if (where(MoodTarget(e[Position].floor, e[Member].roomId))) {
            e[Schedule].mood = (e[Schedule].mood + delta).coerceIn(0f, 100f)
        }
    }
}

internal class MoodTarget(val floor: Int, val roomId: Int)
