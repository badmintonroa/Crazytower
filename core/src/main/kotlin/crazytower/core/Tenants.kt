package crazytower.core

/** When the people of a tenant are active. Used by the daily schedules. */
enum class Shift(val label: String, val startHour: Int, val endHour: Int) {
    DAY("Tagschicht", 9, 17),
    EVENING("Spätschicht", 14, 22),
    NIGHT("Nachtschicht", 22, 6),
}

/**
 * A tenant variant. The same room type can host very different tenants, e.g. an office
 * may be rented by tax accountants, clowns or a vampire insurance company.
 *
 * @param minStars the tower needs at least this many stars before the tenant shows up
 * @param look sprite set of its people
 */
data class TenantKind(
    val id: String,
    val name: String,
    val role: RoomRole,
    val art: String,
    val look: String,
    val minStars: Int = 1,
    val shift: Shift = Shift.DAY,
    val rentPerDay: Long = 0,
    val quirk: String = "",
    /** Only appears through events, never as a regular new tenant. */
    val eventOnly: Boolean = false,
    val minStage: Int = 1,
)

object Tenants {
    val all = listOf(
        // Offices
        TenantKind("accountant", "Steuerbüro Pfennig & Fuchs", RoomRole.OFFICE, "office_accountant", "suit",
            rentPerDay = 900, quirk = "Rechnet auch die Pausen ab."),
        TenantKind("startup", "Disruptiv GmbH", RoomRole.OFFICE, "office_startup", "startup",
            rentPerDay = 1_100, shift = Shift.EVENING, quirk = "Hat noch kein Produkt, aber ein Bällebad."),
        TenantKind("callcenter", "Rückruf-Zentrale Hallo?", RoomRole.OFFICE, "office_callcenter", "callcenter",
            rentPerDay = 800, shift = Shift.EVENING, quirk = "Ruft auch an, wenn keiner fragt."),
        TenantKind("excuses", "Ausreden-Agentur", RoomRole.OFFICE, "office_excuses", "suit2",
            minStars = 2, rentPerDay = 1_200, quirk = "Kommt zu spät. Hat eine sehr gute Begründung."),
        TenantKind("socks", "Zentralarchiv für einzelne Socken", RoomRole.OFFICE, "office_socks", "official",
            minStars = 2, rentPerDay = 1_000, quirk = "Sucht seit 1987 das Gegenstück."),
        TenantKind("ministry", "Ministerium für Zuständigkeit", RoomRole.OFFICE, "office_ministry", "official",
            minStars = 3, rentPerDay = 1_600, quirk = "Ist nicht zuständig."),
        TenantKind("vampire", "Vampir-Versicherung AG", RoomRole.OFFICE, "office_vampire", "vampire",
            minStars = 3, shift = Shift.NIGHT, rentPerDay = 2_000, quirk = "Öffnet erst nach Sonnenuntergang."),
        TenantKind("timetravel", "Zeitreisebüro Gestern & Co.", RoomRole.OFFICE, "office_timetravel", "timetraveler",
            minStars = 4, rentPerDay = 2_400, minStage = 3, quirk = "Hat den Mietvertrag nächste Woche unterschrieben."),
        TenantKind("supervillain", "Weltherrschaft Holding", RoomRole.OFFICE, "office_supervillain", "villain",
            minStars = 4, shift = Shift.NIGHT, rentPerDay = 3_000, minStage = 4, quirk = "Zahlt pünktlich. Verdächtig pünktlich."),
        TenantKind("alien", "Botschaft von Gliese 581", RoomRole.OFFICE, "office_alien", "alien",
            minStars = 5, shift = Shift.NIGHT, rentPerDay = 4_000, minStage = 5, quirk = "Fragt nach dem Chef des Planeten."),
        TenantKind("clown", "Clownsagentur Tröt", RoomRole.OFFICE, "office_clown", "clown",
            rentPerDay = 700, eventOnly = true, quirk = "War vorher ein seriöses Büro."),

        // Homes
        TenantKind("family", "Familie Krawuttke", RoomRole.HOME, "home_family", "casual",
            rentPerDay = 400, shift = Shift.DAY, quirk = "Grillt auf dem Balkon. Es gibt keinen Balkon."),
        TenantKind("students", "Studenten-WG", RoomRole.HOME, "home_students", "casual2",
            rentPerDay = 250, shift = Shift.NIGHT, quirk = "Der Abwasch ist ein Kunstprojekt."),
        TenantKind("retirees", "Ehepaar Ömmel", RoomRole.HOME, "home_retirees", "retiree",
            minStars = 2, rentPerDay = 450, quirk = "Beobachtet den Aufzug. Den ganzen Tag."),
        TenantKind("influencer", "Influencerin Lara Like", RoomRole.HOME, "home_influencer", "influencer",
            minStars = 3, rentPerDay = 800, shift = Shift.EVENING, quirk = "Filmt ihr Frühstück in 4K."),
        TenantKind("penguins", "Pinguin-Kolonie", RoomRole.HOME, "home_penguins", "penguin",
            minStars = 4, rentPerDay = 600, minStage = 3, quirk = "Dreht die Heizung auf minus zehn."),
        TenantKind("ghost", "Ein freundliches Gespenst", RoomRole.HOME, "home_ghost", "ghost",
            rentPerDay = 0, shift = Shift.NIGHT, eventOnly = true, quirk = "Zahlt keine Miete. Ist ja tot."),

        // Food
        TenantKind("imbiss", "Imbiss Zum Schiefen Turm", RoomRole.FOOD, "food_imbiss", "casual",
            rentPerDay = 700, shift = Shift.EVENING, quirk = "Die Currywurst ist laut Gutachten essbar."),
        TenantKind("sushi", "Sushi am Band (das Band ist kaputt)", RoomRole.FOOD, "food_sushi", "startup",
            minStars = 2, rentPerDay = 1_100, shift = Shift.EVENING, quirk = "Man muss zum Sushi laufen."),
        TenantKind("cafe", "Café Koffeinschock", RoomRole.FOOD, "food_cafe", "casual2",
            rentPerDay = 800, quirk = "Der Espresso hat eine eigene Steuernummer."),
    )

    fun byId(id: String): TenantKind = all.first { it.id == id }

    /** Tenants that may move into a room of [role] in a tower with [stars] stars at [stage]. */
    fun candidates(role: RoomRole, stars: Int, stage: Int): List<TenantKind> =
        all.filter { it.role == role && !it.eventOnly && it.minStars <= stars && it.minStage <= stage }
}
