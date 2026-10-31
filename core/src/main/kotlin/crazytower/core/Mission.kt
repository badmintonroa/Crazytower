package crazytower.core

/**
 * The moon mission after stage 5. Stage 5 stays the last stage; the tower then builds a moon
 * station on its top floor, part by part, as a countdown: 3, 2, 1, lift-off. Every part costs
 * money and needs two tasks done in the tower first. With every part more spectators come.
 */
class MissionTask(
    val id: String,
    val title: Tr,
    /** What to do, for the mission window. */
    val hint: Tr,
    val goal: Long,
    /** The player starts it with a button (the test flight). */
    val button: Tr? = null,
)

class StationPart(
    val index: Int,
    /** "Countdown 3" and so on. */
    val step: Tr,
    val type: RoomType,
    val tasks: List<MissionTask>,
    /** Spectators a day once this part stands. */
    val spectators: Int,
)

object Mission {
    /** The mission starts at this stage. */
    const val STAGE = 5

    /** Floor the station is built on: the top floor of the CrazyTower. */
    val FLOOR: Int get() = Stages.info(STAGE).maxFloor - 1

    /** Spectators a day before the first part stands. */
    const val FIRST_SPECTATORS = 50

    /** Mood the astronauts need. */
    const val ASTRONAUT_MOOD = 75f

    /** The tower mood must not drop below this during the test flight. */
    const val TESTFLIGHT_MOOD = 65f
    const val TESTFLIGHT_HOURS = 24

    /** Copies the copy room makes per office hour. */
    const val COPIES_PER_HOUR = 250L

    /** Chance per hour that Dr. Hämisch tries something while the sabotage task is open. */
    const val SABOTAGE_CHANCE = 0.08f
    const val SABOTAGE_MINUTES = 120.0

    /** Share of the money Dr. Hämisch makes off with when nobody stops him. */
    const val SABOTAGE_LOOT = 0.01

    /** A spectator whose trip to the deck takes longer than this goes home grumbling. */
    const val SPECTATOR_PATIENCE = 60.0

    /** When more than this share of the day's spectators grumble, the press laughs and the countdown waits a day. */
    const val PRESS_SHARE = 0.3

    /** Mission tenants take this share of the new tenants while their task is open. */
    const val MISSION_TENANT_CHANCE = 0.35f

    // ---- tasks
    val SUITES = MissionTask("suites", Tr("Regierungs-Suiten", "Government suites"),
        Tr("20 Nächte lang Beamte vom Ministerium für Formulare im Hotel unterbringen. Sie zählen nur, wenn sie gut gelaunt abreisen.",
            "Put up officials from the Ministry of Forms in the hotel for 20 nights. They only count if they leave in a good mood."), 20)
    val PERMIT = MissionTask("permit", Tr("Baugenehmigung", "Building permit"),
        Tr("Der Kopierraum der Ewigkeit kopiert 10.000 Antragsformulare, aber nur, wenn Büroleute im Turm sind.",
            "The Copy Room of Eternity copies 10,000 application forms, but only while office workers are in the tower."), 10_000)
    val COFFEE = MissionTask("coffee", Tr("Raketentreibstoff ist Kaffee", "Rocket fuel is coffee"),
        Tr("Imbisse und Cafés im Turm schenken 40.000 Becher aus. Jede Mahlzeit zählt.", "The snack bars and cafés in the tower pour 40,000 cups. Every meal counts."), 40_000)
    val CONTROL = MissionTask("control", Tr("Bodenkontrolle", "Ground control"),
        Tr("5 Büros an die Raketen für Anfänger GmbH vermieten. Sie arbeiten rund um die Uhr in Schichten.",
            "Rent 5 offices to Rockets for Beginners Ltd. They work around the clock in shifts."), 5)
    val ASTRONAUTS = MissionTask("astronauts", Tr("Astronauten-WG", "Astronaut flatshare"),
        Tr("10 Astronauten wohnen im Turm, und ihre Laune liegt über ${ASTRONAUT_MOOD.toInt()} %.", "10 astronauts live in the tower, in a mood above ${ASTRONAUT_MOOD.toInt()} %."), 10)
    val TESTFLIGHT = MissionTask("testflight", Tr("Testflug", "Test flight"),
        Tr("Einen Tag lang wird probegezündet und der Turm wackelt. Die Laune darf nicht unter ${TESTFLIGHT_MOOD.toInt()} % fallen.",
            "One day of test firing, and the tower shakes. The mood must not drop below ${TESTFLIGHT_MOOD.toInt()} %."), 1,
        button = Tr("Probezünden", "Test fire"))
    val ZORG = MissionTask("zorg", Tr("Landeerlaubnis", "Landing permit"),
        Tr("Botschafter Zorg kommt zu Besuch und muss zufrieden gehen. Dann unterschreibt er mit allen Tentakeln.",
            "Ambassador Zorg comes to visit and has to leave happy. Then he signs with every tentacle."), 1)
    val SABOTAGE = MissionTask("sabotage", Tr("Sabotage", "Sabotage"),
        Tr("Dr. Hämisch will die Kapsel klauen. Der Sicherheitsdienst muss ihn dreimal aufhalten.", "Dr. Gloat wants to steal the capsule. Security has to stop him three times."), 3)

    private fun part(id: String, name: Tr, cost: Long, description: Tr, line: Tr) = RoomType(
        id = id, name = name, category = Category.SPECIAL, role = RoomRole.SPECIAL, width = 12, cost = cost,
        minStage = STAGE, art = id, maxCount = 1, description = description,
        effects = listOf(line, Tr("nur auf der obersten Etage", "top floor only"), Tr("die Aufgaben der Mondmission müssen erledigt sein", "the moon mission's tasks must be done")),
    )

    val RAMP = part("station_ramp", Tr("Startrampe", "Launch pad"), 5_000_000,
        Tr("Eine Rampe Richtung Mond. Für den Anfang reicht die Richtung.", "A ramp towards the moon. For a start, the direction will do."),
        Tr("Countdown 3", "Countdown 3"))
    val TANK = part("station_tank", Tr("Treibstofflager", "Fuel depot"), 15_000_000,
        Tr("40.000 Becher Kaffee in einem Tank. Riecht nach Montagmorgen.", "40,000 cups of coffee in one tank. Smells like Monday morning."),
        Tr("Countdown 2", "Countdown 2"))
    val CAPSULE = part("station_capsule", Tr("Kommandokapsel", "Command capsule"), 40_000_000,
        Tr("Drei Sitze, ein Lenkrad und ein Wunderbaum.", "Three seats, a steering wheel and an air freshener."),
        Tr("Countdown 1", "Countdown 1"))
    val LIFT = part("station_lift", Tr("Mondlift", "Moon lift"), 100_000_000,
        Tr("Der Aufzug bis zum Mond. Bitte nicht alle Knöpfe drücken.", "The elevator to the moon. Please don't press all the buttons."),
        Tr("Abheben!", "Lift-off!"))

    val parts = listOf(
        StationPart(0, Tr("Countdown 3", "Countdown 3"), RAMP, listOf(SUITES, PERMIT), 200),
        StationPart(1, Tr("Countdown 2", "Countdown 2"), TANK, listOf(COFFEE, CONTROL), 500),
        StationPart(2, Tr("Countdown 1", "Countdown 1"), CAPSULE, listOf(ASTRONAUTS, TESTFLIGHT), 1_000),
        StationPart(3, Tr("Abheben!", "Lift-off!"), LIFT, listOf(ZORG, SABOTAGE), 1_500),
    )

    val partTypes: List<RoomType> get() = parts.map { it.type }

    fun partOf(type: RoomType): StationPart? = parts.firstOrNull { it.type.id == type.id }

    /** Where sightseers of the mission go: the deck, then the souvenir shop. */
    val DECK = RoomType(
        id = "deck", name = Tr("Aussichtsplattform", "Observation deck"), category = Category.SPECIAL, role = RoomRole.SPECIAL,
        width = 6, cost = 1_000_000, minStage = STAGE, art = "special_deck", maxCount = 2, minFloor = 90,
        description = Tr("Der beste Blick auf die Startrampe. Ferngläser 2 \$ pro Minute.", "The best view of the launch pad. Binoculars \$2 a minute."),
        effects = listOf(
            Tr("Schaulustige zahlen ${Spectators.ENTRANCE} \$ Eintritt", "spectators pay \$${Spectators.ENTRANCE} admission"),
            Tr("nur ab der 90. Etage", "only from floor 90 up"),
            Tr("wird schmutzig, der Reinigungsdienst putzt", "gets dirty, housekeeping cleans it"),
        ),
    )
    val SOUVENIRS = RoomType(
        id = "souvenirs", name = Tr("Souvenirladen", "Souvenir shop"), category = Category.SPECIAL, role = RoomRole.SPECIAL,
        width = 6, cost = 400_000, minStage = STAGE, art = "special_souvenirs", maxCount = 3,
        description = Tr("Echter Mondstaub, garantiert aus dem Keller.", "Genuine moon dust, guaranteed from the basement."),
        effects = listOf(Tr("Schaulustige kaufen für ${Spectators.SOUVENIR} \$ ein", "spectators spend \$${Spectators.SOUVENIR}")),
    )
}

// ---- state

val Simulation.missionStarted: Boolean get() = stage >= Mission.STAGE
val Simulation.liftedOff: Boolean get() = endgame.parts >= Mission.parts.size

/** The part the countdown is at, or null after lift-off or before stage 5. */
val Simulation.currentPart: StationPart? get() = if (!missionStarted) null else Mission.parts.getOrNull(endgame.parts)

fun Simulation.taskProgress(task: MissionTask): Long = endgame.progress[task.id] ?: 0L

fun Simulation.taskDone(task: MissionTask): Boolean = taskProgress(task) >= task.goal

/** Whether [task] belongs to the part being worked on and is still open. */
fun Simulation.taskOpen(task: MissionTask): Boolean = currentPart?.tasks?.contains(task) == true && !taskDone(task)

internal fun Simulation.addProgress(task: MissionTask, amount: Long = 1) {
    if (!taskOpen(task)) return
    val now = (taskProgress(task) + amount).coerceAtMost(task.goal)
    endgame.progress[task.id] = now
    if (now >= task.goal) taskCompleted(task)
}

/** For counts that go up and down (offices, astronauts): once reached, it stays done. */
internal fun Simulation.setProgress(task: MissionTask, value: Long) {
    if (!taskOpen(task)) return
    endgame.progress[task.id] = value.coerceIn(0, task.goal)
    if (value >= task.goal) taskCompleted(task)
}

private fun Simulation.taskCompleted(task: MissionTask) {
    news.post(tr("Mondmission: „${task.title}“ geschafft!", "Moon mission: \"${task.title}\" done!"), important = true)
    val part = currentPart ?: return
    if (part.tasks.all { taskDone(it) }) {
        news.post(tr("Alles bereit für die ${part.type.name}. Jetzt fehlen nur noch ${formatMoney(part.type.cost)} und ein Platz auf der obersten Etage.",
            "Everything is ready for the ${part.type.name}. All that's missing is ${formatMoney(part.type.cost)} and a spot on the top floor."), important = true)
    }
}

/** Progress of the current part's tasks, 0..1, for the HUD bar. */
val Simulation.missionProgress: Double get() {
    val part = currentPart ?: return 1.0
    return part.tasks.sumOf { (taskProgress(it).toDouble() / it.goal).coerceIn(0.0, 1.0) } / part.tasks.size
}

/** Why the station part [type] can't be built right now, or null. Asked by the build rules. */
fun Simulation.partBlocked(type: RoomType, floor: Int): String? {
    val part = Mission.partOf(type) ?: return null
    val current = currentPart
    return when {
        current == null || current.index != part.index -> tr("Erst ${current?.type?.name ?: Mission.parts.first().type.name}", "First the ${current?.type?.name ?: Mission.parts.first().type.name}")
        part.tasks.any { !taskDone(it) } -> tr("Erst die Aufgaben der Mondmission erledigen", "Finish the moon mission's tasks first")
        clock.totalMinutes < endgame.delayedUntil -> tr("Die Presse lacht noch über die Warteschlangen. Morgen wieder.", "The press is still laughing about the queues. Tomorrow.")
        floor != Mission.FLOOR -> tr("Nur auf der obersten Etage (${Mission.FLOOR})", "Top floor only (${Mission.FLOOR})")
        else -> null
    }
}

/** A station part was built: the countdown moves on. */
internal fun Simulation.partBuilt(room: Room) {
    val part = Mission.partOf(room.type) ?: return
    endgame.parts = maxOf(endgame.parts, part.index + 1)
    bumpLooks()
    if (liftedOff) {
        endgame.liftOffDay = clock.day
        news.post(tr("ABHEBEN! Der Mondlift fährt. Erster Halt: Mond. Bitte festhalten, die Fahrstuhlmusik spielt rückwärts.",
            "LIFT-OFF! The moon lift is running. First stop: the moon. Hold on tight, the lift music plays backwards."), room.center, important = true)
    } else {
        val next = Mission.parts[endgame.parts]
        news.post(tr("${part.type.name} steht! Nächster Schritt: ${next.step}, die ${next.type.name}.", "The ${part.type.name} is up! Next: ${next.step}, the ${next.type.name}."), room.center, important = true)
    }
}

// ---- hooks from the simulation

/** Mission tenants who move in while their task is open. */
internal fun Simulation.missionTenant(room: Room): TenantKind? {
    if (random.nextFloat() >= Mission.MISSION_TENANT_CHANCE) return null
    return when {
        room.type.role == RoomRole.HOTEL && taskOpen(Mission.SUITES) -> Tenants.byId("officials")
        room.type.role == RoomRole.OFFICE && !room.type.luxury && taskOpen(Mission.CONTROL) && hasWorkersFor(room.type.capacity) -> Tenants.byId("rocket")
        room.type.role == RoomRole.HOME && !room.type.luxury && taskOpen(Mission.ASTRONAUTS) -> Tenants.byId("astronauts")
        else -> null
    }
}

/** Hotel guests checked out of [room]: officials count for the suites if they liked it. */
internal fun Simulation.guestsLeft(room: Room, tenant: TenantKind) {
    if (tenant.id != "officials" || !taskOpen(Mission.SUITES)) return
    val mood = moodOf(room) ?: 70f
    if (mood >= 50f) {
        addProgress(Mission.SUITES)
    } else if (random.nextInt(3) == 0) {
        news.post(tr("Die Beamten reisen verstimmt ab und füllen ein Beschwerdeformular aus. In dreifacher Ausfertigung.",
            "The officials leave in a huff and fill in a complaint form. In triplicate."), room.center)
    }
}

/** Somebody ate in the tower: a cup for the rocket. */
internal fun Simulation.mealServed() {
    if (taskOpen(Mission.COFFEE)) addProgress(Mission.COFFEE)
}

/** A VIP went home; Zorg signs the landing permit if he liked it. */
internal fun Simulation.vipJudged(guestId: String, happy: Boolean, where: Loc) {
    if (guestId != "zorg" || !taskOpen(Mission.ZORG)) return
    if (happy) {
        news.post(tr("Botschafter Zorg unterschreibt die Landeerlaubnis. Mit allen Tentakeln, also siebenmal.", "Ambassador Zorg signs the landing permit. With every tentacle, so seven times."), where, important = true)
        addProgress(Mission.ZORG)
    } else {
        news.post(tr("Zorg verweigert die Landeerlaubnis. Er kommt wieder, sagt er. Vielleicht.", "Zorg refuses the landing permit. He'll be back, he says. Maybe."), where, important = true)
    }
}

/** Once an hour: copies, offices, astronauts and Dr. Hämisch. */
internal fun Simulation.missionHour() {
    if (!missionStarted || liftedOff) return
    if (taskOpen(Mission.PERMIT) && clock.weekday < 5 && clock.hour in 8 until 18 &&
        tower.allRooms.any { it.type == Catalog.COPYROOM } && jobs > 0
    ) addProgress(Mission.PERMIT, Mission.COPIES_PER_HOUR)
    if (taskOpen(Mission.CONTROL)) setProgress(Mission.CONTROL, tower.allRooms.count { it.tenant?.id == "rocket" }.toLong())
    if (taskOpen(Mission.ASTRONAUTS)) {
        val happy = tower.allRooms.filter { it.tenant?.id == "astronauts" && (moodOf(it) ?: 0f) >= Mission.ASTRONAUT_MOOD }.sumOf { it.type.capacity }
        setProgress(Mission.ASTRONAUTS, happy.toLong())
    }
    if (taskOpen(Mission.SABOTAGE) && activeEvents.none { it.id == "sabotage" } && random.nextFloat() < Mission.SABOTAGE_CHANCE) {
        val now = clock.totalMinutes
        startEvent(
            ActiveEvent("sabotage", now, now + Mission.SABOTAGE_MINUTES, floor = Mission.FLOOR),
            tr("Dr. Hämisch schleicht mit einem Schraubenschlüssel zur Startrampe! Schnell den Sicherheitsdienst schicken.",
                "Dr. Gloat is sneaking towards the launch pad with a spanner! Send security, quick."),
        )
    }
}

/** Why the test flight can't start now, or null. */
fun Simulation.testflightBlocked(): String? = when {
    !taskOpen(Mission.TESTFLIGHT) -> tr("Gerade kein Testflug nötig", "No test flight needed right now")
    activeEvents.any { it.id == "testflight" } -> tr("Es wird schon probegezündet", "The test firing is on already")
    else -> null
}

fun Simulation.startTestflight(): PlaceCheck {
    testflightBlocked()?.let { return PlaceCheck.no(it) }
    val now = clock.totalMinutes
    startEvent(
        ActiveEvent("testflight", now, now + Mission.TESTFLIGHT_HOURS * 60.0, floor = Mission.FLOOR),
        tr("Probezünden! Einen Tag lang wackelt der Turm. Die Kaffeetassen wandern über die Tische.", "Test firing! The tower shakes for a whole day. The coffee cups wander across the desks."),
    )
    return PlaceCheck.OK
}

// ---- spectators

object Spectators {
    const val ENTRANCE = 150L
    const val SOUVENIR = 80L

    /** The deck needs cleaning after this many spectators. */
    const val DIRTY_EVERY = 40
}

/** How many spectators come a day: more with every part, decorations and ads. */
val Simulation.spectatorsPerDay: Int get() {
    if (!missionStarted) return 0
    val base = if (endgame.parts == 0) Mission.FIRST_SPECTATORS else Mission.parts[endgame.parts - 1].spectators
    val decor = 1.0 + Decor.SPECTATOR_BONUS * endgame.decor.size
    val ads = if (campaignRunning) Promotion.CAMPAIGN_VISITORS else 1.0
    return (base * decor * ads).toInt()
}

/** A spectator got to the deck after [minutes]: admission, a verdict and a bit of rubbish. */
internal fun Simulation.spectatorAtDeck(deck: Room, minutes: Double) {
    endgame.spectatorsToday++
    if (minutes > Mission.SPECTATOR_PATIENCE || deck.dirty) endgame.spectatorsUnhappy++ else endgame.spectatorsHappy++
    val fee = if (deck.dirty) Spectators.ENTRANCE / 2 else Spectators.ENTRANCE
    book(fee, MoneyKind.SPECTATORS, tr("Eintritt Aussichtsplattform", "Observation deck admission"), deck.center)
    deckVisits[deck.id] = (deckVisits[deck.id] ?: 0) + 1
    if (!deck.dirty && deckVisits.getValue(deck.id) % Spectators.DIRTY_EVERY == 0) {
        deck.dirty = true
        bumpLooks()
    }
}

/** A spectator who never got there (no deck, no way up) goes home grumbling. */
internal fun Simulation.spectatorGaveUp() {
    endgame.spectatorsToday++
    endgame.spectatorsUnhappy++
}

/** Midnight: the press writes about the spectators. Too many grumbling ones hold up the countdown. */
internal fun Simulation.missionMidnight() {
    val total = endgame.spectatorsToday
    val unhappy = endgame.spectatorsUnhappy
    endgame.spectatorsToday = 0
    endgame.spectatorsHappy = 0
    endgame.spectatorsUnhappy = 0
    if (!missionStarted || liftedOff || total < 10) return
    if (unhappy > total * Mission.PRESS_SHARE) {
        endgame.delayedUntil = clock.totalMinutes + GameClock.MINUTES_PER_DAY
        news.post(tr("Die Presse: „Mondmission? Eher Wartemission.“ $unhappy von $total Schaulustigen gingen verärgert. Der Countdown wartet einen Tag.",
            "The press: \"Moon mission? More like queue mission.\" $unhappy of $total spectators left annoyed. The countdown waits a day."), important = true)
    }
}
