package crazytower.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A save game. Only the lasting state is stored: rooms, tenants, money, time and news.
 * People are recreated from their rooms on load; visitors simply come again.
 */
@Serializable
data class SaveGame(
    val version: Int = VERSION,
    val minutes: Double,
    val money: Long,
    val stage: Int,
    val stars: Int,
    val bestStars: Int = 0,
    val vipSurvived: Boolean,
    val earnedToday: Long,
    val rooms: List<SavedRoom>,
    val news: List<SavedNews>,
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        const val VERSION = 1
        private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

        fun fromJson(text: String): SaveGame = json.decodeFromString(serializer(), text)
    }
}

@Serializable
data class SavedRoom(
    val id: Int,
    val type: String,
    val floor: Int,
    val x: Int,
    val since: Double,
    val tenant: String? = null,
    val mood: Float? = null,
    /** Only for rooms the player stretched (the lobby). */
    val width: Int? = null,
    val paidUntil: Double? = null,
    val salePaid: Long = 0,
    val cabins: Int = 1,
    val sold: Boolean = false,
    val dirty: Boolean = false,
    val paid: Long = 0,
)

@Serializable
data class SavedNews(val text: String, val floor: Int? = null, val x: Float? = null, val important: Boolean = false)
