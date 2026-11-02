package crazytower.core

/**
 * People looks that the tenants and VIPs already ask for, but whose sprite sheet may not exist yet.
 * Until `tools/art/import_people.py` puts the sheet into `people/`, the game shows the stand-in.
 * Once every sheet exists, the entries can stay: they are only used for missing sheets.
 */
object Looks {
    val standIns: Map<String, String> = mapOf(
        // homes and penthouses
        "npc_influencer" to "npc_student_gamer",
        "npc_retiree_2" to "npc_retiree",
        "npc_popstar" to "npc_student_party",
        "npc_heir" to "npc_vip",
        "npc_astronaut" to "npc_timetraveller",
        // luxury offices
        "npc_banker" to "npc_office_boss",
        "npc_dragon" to "npc_supervillain",
        "npc_atlantean" to "npc_penguin",
        // hotel guests
        "npc_bride" to "npc_family_mom",
        "npc_groom" to "npc_family_dad",
        "npc_rocker" to "npc_student_party",
        "npc_alien_tourist" to "npc_alien",
        "npc_queen" to "npc_vip",
        "npc_diva" to "npc_student_artist",
        "npc_billionaire_bathrobe" to "npc_office_boss",
        // housekeeping, food and shops
        "npc_robot_vacuum" to "npc_office_it",
        "npc_cook" to "npc_family_dad",
        "npc_sushi_chef" to "npc_startup",
        "npc_barista" to "npc_office_intern",
        "npc_spaeti" to "npc_family_dad",
        "npc_robo_auntie" to "npc_office_it",
        // VIPs
        "npc_baron_goldzahn" to "npc_vip",
        "npc_vlogger" to "npc_tourist",
        "npc_alien_ambassador" to "npc_alien",
    )

    /** The look to draw for [look] when only the sheets in [available] exist, or null if none fits. */
    fun resolve(look: String, available: Set<String>): String? =
        if (look in available) look else standIns[look]?.takeIf { it in available }
}
