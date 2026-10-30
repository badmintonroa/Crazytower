package crazytower.core

/**
 * When the people of a tenant are busy: employees are in the tower during these hours,
 * residents are out (at work, at school, at the fish market).
 *
 * @param days weekdays the block starts on, 0 = Monday
 * @param from start, in minutes of the day
 * @param to end, in minutes of the day; smaller than [from] for blocks over midnight
 * @param crews the block is split into this many crews who take turns (call centers, embassies)
 * @param lateUpTo every person turns up to this many minutes late, a new amount every day
 * @param wandering the block starts at a different hour every day (time travellers)
 */
data class WorkPlan(
    val label: Tr,
    val days: Set<Int>,
    val from: Int,
    val to: Int,
    val crews: Int = 1,
    val lateUpTo: Int = 0,
    val wandering: Boolean = false,
) {
    private val length: Int = ((to - from).mod(MINUTES)).let { if (it == 0) MINUTES else it }

    /** [days] as bits, asked for every person and tick, so without boxing. */
    private val dayMask: Int = days.fold(0) { mask, d -> mask or (1 shl d) }

    /** Start of the block that begins on [day], in game minutes, or null when it is a day off. */
    fun startOn(day: Int): Int? {
        if (dayMask and (1 shl day.mod(7)) == 0) return null
        val start = if (wandering) ((day * 7919L).mod(15L).toInt() + 3) * 60 else from
        return day * MINUTES + start
    }

    /** Whether [minute] (game minutes since the start) lies in a block, for one crew of the tenant. */
    fun isBusy(minute: Double, crew: Int = 0, lateness: Int = 0): Boolean {
        val day = (minute / MINUTES).toInt()
        val crewLength = length / crews
        for (d in day - 1..day) {
            val start = startOn(d) ?: continue
            val crewStart = start + crewLength * (crew % crews) + lateness
            val crewEnd = start + crewLength * (crew % crews + 1)
            if (minute >= crewStart && minute < crewEnd) return true
        }
        return false
    }

    /** End of the latest block that ended at or before [minute], or null. Rent is paid then. */
    fun lastEnd(minute: Double): Int? {
        val day = (minute / MINUTES).toInt()
        // asked for every room every game minute, so without lists
        for (d in day downTo day - 2) {
            val end = (startOn(d) ?: continue) + length
            if (end <= minute) return end
        }
        return null
    }

    /** End of the next block that ends after [minute]. */
    fun nextEnd(minute: Double): Int? {
        val day = (minute / MINUTES).toInt()
        for (d in day - 1..day + 7) {
            val end = (startOn(d) ?: continue) + length
            if (end > minute) return end
        }
        return null
    }

    /** The middle of the block running at [minute], used for lunch breaks. */
    fun middle(minute: Double): Int? {
        val day = (minute / MINUTES).toInt()
        return (day - 1..day).mapNotNull { d -> startOn(d) }.firstOrNull { minute >= it && minute < it + length }?.plus(length / 2)
    }

    companion object {
        private const val MINUTES = GameClock.MINUTES_PER_DAY
        val WEEKDAYS = setOf(0, 1, 2, 3, 4)
        val MON_TO_SAT = setOf(0, 1, 2, 3, 4, 5)
        val MON_TO_THU = setOf(0, 1, 2, 3)
        val DAILY = setOf(0, 1, 2, 3, 4, 5, 6)
        fun h(hour: Int, minute: Int = 0) = hour * 60 + minute
    }
}

/**
 * A tenant variant. The same room type can host very different tenants, e.g. an office
 * may be rented by tax accountants, clowns or a vampire insurance company.
 *
 * @param minStars the tower needs at least this many stars before the tenant shows up
 * @param look sprite set of its people; [crew] lists more sets when they don't all look alike
 * @param plan when its people are busy (see [WorkPlan])
 * @param rentPerDay offices and restaurants: paid at the end of every working day
 * @param salePrice homes: paid once when the tenant buys the flat
 */
data class TenantKind(
    val id: String,
    val name: Tr,
    val role: RoomRole,
    val art: String,
    val look: String,
    val minStars: Int = 0,
    val plan: WorkPlan = OFFICE_HOURS,
    val rentPerDay: Long = 0,
    val salePrice: Long = 0,
    val quirk: Tr = Tr("", ""),
    /** Only appears through events, never as a regular new tenant. */
    val eventOnly: Boolean = false,
    val minStage: Int = 0,
    val crew: List<String> = emptyList(),
    /** Only moves into luxury rooms, and only luxury tenants do. */
    val luxury: Boolean = false,
) {
    /** The look of the [i]-th person of this tenant: the crew in turn, or always [look]. */
    fun lookFor(i: Int): String = if (crew.isEmpty()) look else crew[i % crew.size]
}

private val OFFICE_HOURS = WorkPlan(Tr("Mo–Fr 8–17 Uhr", "Mon–Fri 8 am–5 pm"), WorkPlan.WEEKDAYS, WorkPlan.h(8), WorkPlan.h(17))

/** Hotel guests see the town during the day and come back for the night. */
private val GUEST_PLAN = WorkPlan(Tr("tagsüber unterwegs, 9–18 Uhr", "out and about, 9 am–6 pm"), WorkPlan.DAILY, WorkPlan.h(9), WorkPlan.h(18))

object Tenants {
    private fun plan(label: Tr, days: Set<Int>, from: Int, to: Int, crews: Int = 1, lateUpTo: Int = 0, wandering: Boolean = false) =
        WorkPlan(label, days, WorkPlan.h(from), WorkPlan.h(to), crews, lateUpTo, wandering)

    val all = listOf(
        // Offices
        TenantKind("accountant", Tr("Steuerbüro Pfennig & Fuchs", "Penny & Pincher Tax Advisers"), RoomRole.OFFICE, "office_accountant", "npc_accountant",
            crew = listOf("npc_accountant", "npc_office_blazer", "npc_office_sleeves", "npc_office_boss"),
            rentPerDay = 2_200, quirk = Tr("Rechnet auch die Pausen ab.", "Bills the breaks as well.")),
        TenantKind("startup", Tr("Disruptiv GmbH", "Disruptive Ltd."), RoomRole.OFFICE, "office_startup", "npc_startup",
            crew = listOf("npc_startup", "npc_office_it", "npc_office_intern"),
            rentPerDay = 2_400, plan = plan(Tr("Mo–Sa 11–23 Uhr", "Mon–Sat 11 am–11 pm"), WorkPlan.MON_TO_SAT, 11, 23), quirk = Tr("Hat noch kein Produkt, aber ein Bällebad.", "No product yet, but a ball pit.")),
        TenantKind("callcenter", Tr("Rückruf-Zentrale Hallo?", "Callback Centre Hello?"), RoomRole.OFFICE, "office_callcenter", "npc_callcenter",
            crew = listOf("npc_callcenter", "npc_callcenter", "npc_office_blazer"),
            rentPerDay = 2_100, plan = plan(Tr("täglich 7–22, 2 Schichten", "daily 7 am–10 pm, 2 shifts"), WorkPlan.DAILY, 7, 22, crews = 2), quirk = Tr("Ruft auch an, wenn keiner fragt.", "Calls even when nobody asked.")),
        TenantKind("excuses", Tr("Ausreden-Agentur", "Excuses Agency"), RoomRole.OFFICE, "office_excuses", "npc_excuses",
            crew = listOf("npc_excuses", "npc_office_sleeves"),
            minStars = 2, rentPerDay = 2_600, plan = plan(Tr("Mo–Fr 9–17 Uhr (ungefähr)", "Mon–Fri 9–5 (roughly)"), WorkPlan.WEEKDAYS, 9, 17, lateUpTo = 180),
            quirk = Tr("Kommt zu spät. Hat eine sehr gute Begründung.", "Always late. Has a very good reason.")),
        TenantKind("socks", Tr("Zentralarchiv für einzelne Socken", "Central Archive of Odd Socks"), RoomRole.OFFICE, "office_socks", "npc_detective",
            minStars = 2, rentPerDay = 2_400, plan = plan(Tr("Mo–Fr 14–23 Uhr", "Mon–Fri 2 pm–11 pm"), WorkPlan.WEEKDAYS, 14, 23), quirk = Tr("Sucht seit 1987 das Gegenstück.", "Has been looking for the other one since 1987.")),
        TenantKind("ministry", Tr("Ministerium für Zuständigkeit", "Ministry of Responsibility"), RoomRole.OFFICE, "office_ministry", "npc_ministry",
            crew = listOf("npc_ministry", "npc_ministry", "npc_office_boss"),
            minStars = 3, rentPerDay = 3_500, plan = plan(Tr("Mo–Do 10–12 Uhr", "Mon–Thu 10 am–12 pm"), WorkPlan.MON_TO_THU, 10, 12), quirk = Tr("Ist nicht zuständig.", "Is not responsible.")),
        TenantKind("vampire", Tr("Vampir-Versicherung AG", "Vampire Insurance Inc."), RoomRole.OFFICE, "office_vampire", "npc_vampire_count",
            crew = listOf("npc_vampire_count", "npc_vampire_secretary", "npc_vampire_intern", "npc_vampire_old", "npc_vampire_collector"),
            minStars = 3, rentPerDay = 4_000, plan = plan(Tr("täglich 20–4 Uhr", "daily 8 pm–4 am"), WorkPlan.DAILY, 20, 4), quirk = Tr("Öffnet erst nach Sonnenuntergang.", "Only opens after sunset.")),
        TenantKind("timetravel", Tr("Zeitreisebüro Gestern & Co.", "Yesterday & Co. Time Travel"), RoomRole.OFFICE, "office_timetravel", "npc_timetraveller",
            minStars = 4, rentPerDay = 4_800, minStage = 3, plan = plan(Tr("jeden Tag andere 8 Std.", "a different 8 hours every day"), WorkPlan.DAILY, 9, 17, wandering = true),
            quirk = Tr("Hat den Mietvertrag nächste Woche unterschrieben.", "Signed the lease next week.")),
        TenantKind("supervillain", Tr("Weltherrschaft Holding", "World Domination Holdings"), RoomRole.OFFICE, "office_supervillain", "npc_supervillain",
            crew = listOf("npc_supervillain", "npc_office_it"),
            minStars = 4, rentPerDay = 6_000, minStage = 4, plan = plan(Tr("täglich 22–6 Uhr", "daily 10 pm–6 am"), WorkPlan.DAILY, 22, 6),
            quirk = Tr("Zahlt pünktlich. Verdächtig pünktlich.", "Pays on time. Suspiciously on time.")),
        TenantKind("alien", Tr("Botschaft von Gliese 581", "Embassy of Gliese 581"), RoomRole.OFFICE, "office_alien", "npc_alien",
            minStars = 5, rentPerDay = 7_200, minStage = 5, plan = plan(Tr("rund um die Uhr, 3 Schichten", "around the clock, 3 shifts"), WorkPlan.DAILY, 0, 24, crews = 3),
            quirk = Tr("Fragt nach dem Chef des Planeten.", "Asks to see the planet's manager.")),
        TenantKind("clown", Tr("Clownsagentur Tröt", "Honk Clown Agency"), RoomRole.OFFICE, "office_clown", "npc_clown_classic",
            crew = listOf("npc_clown_classic", "npc_clown_boss", "npc_clown_mime", "npc_clown_sad", "npc_clown_juggler"),
            rentPerDay = 1_700, eventOnly = true, plan = plan(Tr("Mo–Fr 9–18 Uhr", "Mon–Fri 9 am–6 pm"), WorkPlan.WEEKDAYS, 9, 18), quirk = Tr("War vorher ein seriöses Büro.", "Used to be a serious office.")),

        // Luxury offices
        TenantKind("hedgefund", Tr("Hedgefonds Geld & Mehr Geld", "Money & More Money Hedge Fund"), RoomRole.OFFICE, "office_empty", "npc_office_boss",
            crew = listOf("npc_office_boss", "npc_office_blazer", "npc_office_sleeves", "npc_accountant"),
            minStars = 4, minStage = 4, luxury = true, rentPerDay = 15_000, plan = plan(Tr("Mo–Fr 7–20 Uhr", "Mon–Fri 7 am–8 pm"), WorkPlan.WEEKDAYS, 7, 20),
            quirk = Tr("Wettet auf das Wetter von gestern. Und gewinnt.", "Bets on yesterday's weather. And wins.")),
        TenantKind("dragonbank", Tr("Drachenbank Goldhort", "Goldhoard Dragon Bank"), RoomRole.OFFICE, "office_empty", "npc_supervillain",
            crew = listOf("npc_supervillain", "npc_office_boss", "npc_vampire_secretary"),
            minStars = 4, minStage = 4, luxury = true, rentPerDay = 17_000, plan = plan(Tr("Mo–Sa 10–18 Uhr", "Mon–Sat 10 am–6 pm"), WorkPlan.MON_TO_SAT, 10, 18),
            quirk = Tr("Der Chef sitzt auf dem Tresor. Wörtlich.", "The boss sits on the vault. Literally.")),
        TenantKind("atlantis", Tr("Konsulat von Atlantis", "Consulate of Atlantis"), RoomRole.OFFICE, "office_empty", "npc_penguin",
            crew = listOf("npc_penguin", "npc_ministry", "npc_office_blazer"),
            minStars = 5, minStage = 5, luxury = true, rentPerDay = 22_000, plan = plan(Tr("Mo–Fr 9–15 Uhr (bei Ebbe)", "Mon–Fri 9–3 (at low tide)"), WorkPlan.WEEKDAYS, 9, 15),
            quirk = Tr("Bringt immer nasse Füße mit.", "Always brings wet feet.")),

        // Homes: the plan says when the residents are out
        TenantKind("family", Tr("Familie Krawuttke", "The Krawuttke Family"), RoomRole.HOME, "home_family", "npc_family_dad",
            crew = listOf("npc_family_dad", "npc_family_mom", "npc_kid"),
            salePrice = 15_000, plan = plan(Tr("Mo–Fr 8–17 Uhr (Arbeit)", "Mon–Fri 8–5 (work)"), WorkPlan.WEEKDAYS, 8, 17), quirk = Tr("Grillt auf dem Balkon. Es gibt keinen Balkon.", "Barbecues on the balcony. There is no balcony.")),
        TenantKind("students", Tr("Studenten-WG", "Student Flatshare"), RoomRole.HOME, "home_students", "npc_student_hoodie",
            crew = listOf("npc_student_hoodie", "npc_student_bookworm", "npc_student_party", "npc_student_gamer", "npc_student_artist"),
            salePrice = 15_000, plan = plan(Tr("täglich 13–20 Uhr (Uni)", "daily 1–8 pm (uni)"), WorkPlan.DAILY, 13, 20), quirk = Tr("Der Abwasch ist ein Kunstprojekt.", "The washing-up is an art project.")),
        TenantKind("retirees", Tr("Ehepaar Ömmel", "Mr and Mrs Ömmel"), RoomRole.HOME, "home_retirees", "npc_retiree",
            minStars = 2, salePrice = 15_000, plan = plan(Tr("Mo–Sa 10–12 Uhr (Einkauf)", "Mon–Sat 10–12 (shopping)"), WorkPlan.MON_TO_SAT, 10, 12), quirk = Tr("Beobachtet den Aufzug. Den ganzen Tag.", "Watches the elevator. All day.")),
        TenantKind("influencer", Tr("Influencerin Lara Like", "Influencer Lara Like"), RoomRole.HOME, "home_influencer", "npc_student_gamer",
            minStars = 3, salePrice = 15_000, plan = plan(Tr("täglich 14–22 Uhr (Shooting)", "daily 2–10 pm (photo shoot)"), WorkPlan.DAILY, 14, 22), quirk = Tr("Filmt ihr Frühstück in 4K.", "Films her breakfast in 4K.")),
        TenantKind("penguins", Tr("Pinguin-Kolonie", "Penguin Colony"), RoomRole.HOME, "home_penguins", "npc_penguin",
            minStars = 4, salePrice = 15_000, minStage = 3, plan = plan(Tr("täglich 6–9 Uhr (Fischmarkt)", "daily 6–9 am (fish market)"), WorkPlan.DAILY, 6, 9), quirk = Tr("Dreht die Heizung auf minus zehn.", "Turns the heating down to minus ten.")),
        TenantKind("ghost", Tr("Ein freundliches Gespenst", "A Friendly Ghost"), RoomRole.HOME, "home_ghost", "npc_ghost",
            salePrice = 0, eventOnly = true, plan = plan(Tr("täglich 23–4 Uhr (Spuken)", "daily 11 pm–4 am (haunting)"), WorkPlan.DAILY, 23, 4), quirk = Tr("Zahlt keine Miete. Ist ja tot.", "Pays no rent. Being dead and all.")),

        // Hotel guests: out during the day, they pay [rentPerDay] per night when they check out
        TenantKind("business", Tr("Herr Eilig auf Geschäftsreise", "Mr Hurry on a Business Trip"), RoomRole.HOTEL, "home_empty", "npc_office_blazer",
            crew = listOf("npc_office_blazer", "npc_office_sleeves"),
            rentPerDay = 3_000, plan = GUEST_PLAN, quirk = Tr("Hat drei Meetings gleichzeitig. Im Schlaf.", "Has three meetings at once. In his sleep.")),
        TenantKind("selfie", Tr("Reisegruppe Selfie", "Selfie Tour Group"), RoomRole.HOTEL, "home_empty", "npc_tourist",
            rentPerDay = 3_200, plan = GUEST_PLAN, quirk = Tr("Fotografiert den Feuerlöscher. Von allen Seiten.", "Photographs the fire extinguisher. From every angle.")),
        TenantKind("wedding", Tr("Hochzeitsgäste (sehr laut)", "Wedding Guests (very loud)"), RoomRole.HOTEL, "home_empty", "npc_family_mom",
            crew = listOf("npc_family_mom", "npc_family_dad"),
            rentPerDay = 3_500, plan = GUEST_PLAN, quirk = Tr("Haben die Torte im Aufzug vergessen.", "Left the cake in the elevator.")),
        TenantKind("count", Tr("Ein Graf auf Durchreise", "A Count Passing Through"), RoomRole.HOTEL, "home_empty", "npc_vampire_count",
            minStars = 4, rentPerDay = 4_500, plan = plan(Tr("tagsüber im Sarg, 6–20 Uhr", "in the coffin by day, 6 am–8 pm"), WorkPlan.DAILY, 6, 20),
            quirk = Tr("Bittet um ein Zimmer ohne Fenster und ohne Knoblauch.", "Asks for a room without windows or garlic.")),
        TenantKind("band", Tr("Rockband Die Lärmschutzwände", "Rock Band The Noise Barriers"), RoomRole.HOTEL, "home_empty", "npc_student_party",
            crew = listOf("npc_student_party", "npc_student_hoodie"),
            minStars = 4, rentPerDay = 5_000, plan = GUEST_PLAN, quirk = Tr("Werfen den Fernseher aus dem Fenster. Es ist ein Bild von einem Fernseher.", "Throw the TV out of the window. It is a picture of a TV.")),
        TenantKind("spacetourists", Tr("Weltraumtouristen", "Space Tourists"), RoomRole.HOTEL, "home_empty", "npc_alien",
            minStars = 5, rentPerDay = 6_000, plan = GUEST_PLAN, quirk = Tr("Geben fünf Sterne. Von zwölf möglichen.", "Give five stars. Out of twelve.")),

        // Cleaning companies: the hours they work
        TenantKind("staubfrei", Tr("Putzkolonne Staubfrei & Söhne", "Dustfree & Sons Cleaners"), RoomRole.HOUSEKEEPING, "home_empty", "npc_janitor",
            plan = plan(Tr("täglich 9–19 Uhr", "daily 9 am–7 pm"), WorkPlan.DAILY, 9, 19), quirk = Tr("Wischt auch schlechte Laune weg. Meistens.", "Mops up bad moods too. Mostly.")),
        TenantKind("robots", Tr("Saugroboter-Schwarm", "Robot Vacuum Swarm"), RoomRole.HOUSEKEEPING, "home_empty", "npc_office_it",
            plan = plan(Tr("täglich 8–20 Uhr", "daily 8 am–8 pm"), WorkPlan.DAILY, 8, 20), quirk = Tr("Hat einen Hamster adoptiert. Der Hamster ist nicht einverstanden.", "Adopted a hamster. The hamster disagrees.")),
        TenantKind("housegeist", Tr("Hausgeist Blitzeblank", "Sparkle the House Spirit"), RoomRole.HOUSEKEEPING, "home_empty", "npc_ghost",
            minStars = 4, plan = plan(Tr("nachts 22–6 Uhr", "nights 10 pm–6 am"), WorkPlan.DAILY, 22, 6), quirk = Tr("Putzt nur, wenn keiner hinsieht.", "Only cleans when nobody is looking.")),

        // Food: opening hours
        TenantKind("imbiss", Tr("Imbiss Zum Schiefen Turm", "The Leaning Tower Snack Bar"), RoomRole.FOOD, "food_imbiss", "npc_family_dad",
            rentPerDay = 900, plan = plan(Tr("täglich 11–23 Uhr", "daily 11 am–11 pm"), WorkPlan.DAILY, 11, 23), quirk = Tr("Die Currywurst ist laut Gutachten essbar.", "An expert report says the currywurst is edible.")),
        TenantKind("sushi", Tr("Sushi am Band (das Band ist kaputt)", "Conveyor Sushi (the belt is broken)"), RoomRole.FOOD, "food_sushi", "npc_startup",
            minStars = 2, rentPerDay = 1_400, plan = plan(Tr("täglich 12–22 Uhr", "daily 12–10 pm"), WorkPlan.DAILY, 12, 22), quirk = Tr("Man muss zum Sushi laufen.", "You have to walk to the sushi.")),
        TenantKind("cafe", Tr("Café Koffeinschock", "Café Caffeine Shock"), RoomRole.FOOD, "food_cafe", "npc_office_intern",
            rentPerDay = 1_000, plan = plan(Tr("täglich 7–18 Uhr", "daily 7 am–6 pm"), WorkPlan.DAILY, 7, 18), quirk = Tr("Der Espresso hat eine eigene Steuernummer.", "The espresso has its own tax number.")),
    )

    fun byId(id: String): TenantKind = all.first { it.id == id }

    /** Tenants that may move into a room of [type] in a tower with [stars] stars at [stage]. */
    fun candidates(type: RoomType, stars: Int, stage: Int): List<TenantKind> =
        all.filter { it.role == type.role && it.luxury == type.luxury && !it.eventOnly && it.minStars <= stars && it.minStage <= stage }
}
