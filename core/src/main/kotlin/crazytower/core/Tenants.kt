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
    val label: String,
    val days: Set<Int>,
    val from: Int,
    val to: Int,
    val crews: Int = 1,
    val lateUpTo: Int = 0,
    val wandering: Boolean = false,
) {
    private val length: Int get() = ((to - from).mod(MINUTES)).let { if (it == 0) MINUTES else it }

    /** Start of the block that begins on [day], in game minutes, or null when it is a day off. */
    fun startOn(day: Int): Int? {
        if (day.mod(7) !in days) return null
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
        return (day - 2..day).mapNotNull { d -> startOn(d)?.plus(length) }.filter { it <= minute }.maxOrNull()
    }

    /** End of the next block that ends after [minute]. */
    fun nextEnd(minute: Double): Int? {
        val day = (minute / MINUTES).toInt()
        return (day - 1..day + 7).mapNotNull { d -> startOn(d)?.plus(length) }.filter { it > minute }.minOrNull()
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
    val name: String,
    val role: RoomRole,
    val art: String,
    val look: String,
    val minStars: Int = 0,
    val plan: WorkPlan = OFFICE_HOURS,
    val rentPerDay: Long = 0,
    val salePrice: Long = 0,
    val quirk: String = "",
    /** Only appears through events, never as a regular new tenant. */
    val eventOnly: Boolean = false,
    val minStage: Int = 0,
    val crew: List<String> = emptyList(),
) {
    /** The look of the [i]-th person of this tenant: the crew in turn, or always [look]. */
    fun lookFor(i: Int): String = if (crew.isEmpty()) look else crew[i % crew.size]
}

private val OFFICE_HOURS = WorkPlan("Mo–Fr 8–17 Uhr", WorkPlan.WEEKDAYS, WorkPlan.h(8), WorkPlan.h(17))

object Tenants {
    private fun plan(label: String, days: Set<Int>, from: Int, to: Int, crews: Int = 1, lateUpTo: Int = 0, wandering: Boolean = false) =
        WorkPlan(label, days, WorkPlan.h(from), WorkPlan.h(to), crews, lateUpTo, wandering)

    val all = listOf(
        // Offices
        TenantKind("accountant", "Steuerbüro Pfennig & Fuchs", RoomRole.OFFICE, "office_accountant", "npc_accountant",
            crew = listOf("npc_accountant", "npc_office_blazer", "npc_office_sleeves", "npc_office_boss"),
            rentPerDay = 2_200, quirk = "Rechnet auch die Pausen ab."),
        TenantKind("startup", "Disruptiv GmbH", RoomRole.OFFICE, "office_startup", "npc_startup",
            crew = listOf("npc_startup", "npc_office_it", "npc_office_intern"),
            rentPerDay = 2_400, plan = plan("Mo–Sa 11–23 Uhr", WorkPlan.MON_TO_SAT, 11, 23), quirk = "Hat noch kein Produkt, aber ein Bällebad."),
        TenantKind("callcenter", "Rückruf-Zentrale Hallo?", RoomRole.OFFICE, "office_callcenter", "npc_callcenter",
            crew = listOf("npc_callcenter", "npc_callcenter", "npc_office_blazer"),
            rentPerDay = 1_900, plan = plan("täglich 7–22, 2 Schichten", WorkPlan.DAILY, 7, 22, crews = 2), quirk = "Ruft auch an, wenn keiner fragt."),
        TenantKind("excuses", "Ausreden-Agentur", RoomRole.OFFICE, "office_excuses", "npc_excuses",
            crew = listOf("npc_excuses", "npc_office_sleeves"),
            minStars = 2, rentPerDay = 2_600, plan = plan("Mo–Fr 9–17 Uhr (ungefähr)", WorkPlan.WEEKDAYS, 9, 17, lateUpTo = 180),
            quirk = "Kommt zu spät. Hat eine sehr gute Begründung."),
        TenantKind("socks", "Zentralarchiv für einzelne Socken", RoomRole.OFFICE, "office_socks", "npc_detective",
            minStars = 2, rentPerDay = 2_400, plan = plan("Mo–Fr 14–23 Uhr", WorkPlan.WEEKDAYS, 14, 23), quirk = "Sucht seit 1987 das Gegenstück."),
        TenantKind("ministry", "Ministerium für Zuständigkeit", RoomRole.OFFICE, "office_ministry", "npc_ministry",
            crew = listOf("npc_ministry", "npc_ministry", "npc_office_boss"),
            minStars = 3, rentPerDay = 3_500, plan = plan("Mo–Do 10–12 Uhr", WorkPlan.MON_TO_THU, 10, 12), quirk = "Ist nicht zuständig."),
        TenantKind("vampire", "Vampir-Versicherung AG", RoomRole.OFFICE, "office_vampire", "npc_vampire_count",
            crew = listOf("npc_vampire_count", "npc_vampire_secretary", "npc_vampire_intern", "npc_vampire_old", "npc_vampire_collector"),
            minStars = 3, rentPerDay = 4_000, plan = plan("täglich 20–4 Uhr", WorkPlan.DAILY, 20, 4), quirk = "Öffnet erst nach Sonnenuntergang."),
        TenantKind("timetravel", "Zeitreisebüro Gestern & Co.", RoomRole.OFFICE, "office_timetravel", "npc_timetraveller",
            minStars = 4, rentPerDay = 4_800, minStage = 3, plan = plan("jeden Tag andere 8 Std.", WorkPlan.DAILY, 9, 17, wandering = true),
            quirk = "Hat den Mietvertrag nächste Woche unterschrieben."),
        TenantKind("supervillain", "Weltherrschaft Holding", RoomRole.OFFICE, "office_supervillain", "npc_supervillain",
            crew = listOf("npc_supervillain", "npc_office_it"),
            minStars = 4, rentPerDay = 6_000, minStage = 4, plan = plan("täglich 22–6 Uhr", WorkPlan.DAILY, 22, 6),
            quirk = "Zahlt pünktlich. Verdächtig pünktlich."),
        TenantKind("alien", "Botschaft von Gliese 581", RoomRole.OFFICE, "office_alien", "npc_alien",
            minStars = 5, rentPerDay = 7_200, minStage = 5, plan = plan("rund um die Uhr, 3 Schichten", WorkPlan.DAILY, 0, 24, crews = 3),
            quirk = "Fragt nach dem Chef des Planeten."),
        TenantKind("clown", "Clownsagentur Tröt", RoomRole.OFFICE, "office_clown", "npc_clown_classic",
            crew = listOf("npc_clown_classic", "npc_clown_boss", "npc_clown_mime", "npc_clown_sad", "npc_clown_juggler"),
            rentPerDay = 1_700, eventOnly = true, plan = plan("Mo–Fr 9–18 Uhr", WorkPlan.WEEKDAYS, 9, 18), quirk = "War vorher ein seriöses Büro."),

        // Homes: the plan says when the residents are out
        TenantKind("family", "Familie Krawuttke", RoomRole.HOME, "home_family", "npc_family_dad",
            crew = listOf("npc_family_dad", "npc_family_mom", "npc_kid"),
            salePrice = 24_000, plan = plan("Mo–Fr 8–17 Uhr (Arbeit)", WorkPlan.WEEKDAYS, 8, 17), quirk = "Grillt auf dem Balkon. Es gibt keinen Balkon."),
        TenantKind("students", "Studenten-WG", RoomRole.HOME, "home_students", "npc_student_hoodie",
            crew = listOf("npc_student_hoodie", "npc_student_bookworm", "npc_student_party", "npc_student_gamer", "npc_student_artist"),
            salePrice = 20_000, plan = plan("täglich 13–20 Uhr (Uni)", WorkPlan.DAILY, 13, 20), quirk = "Der Abwasch ist ein Kunstprojekt."),
        TenantKind("retirees", "Ehepaar Ömmel", RoomRole.HOME, "home_retirees", "npc_retiree",
            minStars = 2, salePrice = 28_000, plan = plan("Mo–Sa 10–12 Uhr (Einkauf)", WorkPlan.MON_TO_SAT, 10, 12), quirk = "Beobachtet den Aufzug. Den ganzen Tag."),
        TenantKind("influencer", "Influencerin Lara Like", RoomRole.HOME, "home_influencer", "npc_student_gamer",
            minStars = 3, salePrice = 40_000, plan = plan("täglich 14–22 Uhr (Shooting)", WorkPlan.DAILY, 14, 22), quirk = "Filmt ihr Frühstück in 4K."),
        TenantKind("penguins", "Pinguin-Kolonie", RoomRole.HOME, "home_penguins", "npc_penguin",
            minStars = 4, salePrice = 36_000, minStage = 3, plan = plan("täglich 6–9 Uhr (Fischmarkt)", WorkPlan.DAILY, 6, 9), quirk = "Dreht die Heizung auf minus zehn."),
        TenantKind("ghost", "Ein freundliches Gespenst", RoomRole.HOME, "home_ghost", "npc_ghost",
            salePrice = 0, eventOnly = true, plan = plan("täglich 23–4 Uhr (Spuken)", WorkPlan.DAILY, 23, 4), quirk = "Zahlt keine Miete. Ist ja tot."),

        // Food: opening hours
        TenantKind("imbiss", "Imbiss Zum Schiefen Turm", RoomRole.FOOD, "food_imbiss", "npc_family_dad",
            rentPerDay = 900, plan = plan("täglich 11–23 Uhr", WorkPlan.DAILY, 11, 23), quirk = "Die Currywurst ist laut Gutachten essbar."),
        TenantKind("sushi", "Sushi am Band (das Band ist kaputt)", RoomRole.FOOD, "food_sushi", "npc_startup",
            minStars = 2, rentPerDay = 1_400, plan = plan("täglich 12–22 Uhr", WorkPlan.DAILY, 12, 22), quirk = "Man muss zum Sushi laufen."),
        TenantKind("cafe", "Café Koffeinschock", RoomRole.FOOD, "food_cafe", "npc_office_intern",
            rentPerDay = 1_000, plan = plan("täglich 7–18 Uhr", WorkPlan.DAILY, 7, 18), quirk = "Der Espresso hat eine eigene Steuernummer."),
    )

    fun byId(id: String): TenantKind = all.first { it.id == id }

    /** Tenants that may move into a room of [role] in a tower with [stars] stars at [stage]. */
    fun candidates(role: RoomRole, stars: Int, stage: Int): List<TenantKind> =
        all.filter { it.role == role && !it.eventOnly && it.minStars <= stars && it.minStage <= stage }
}
