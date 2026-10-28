package crazytower.core

/** A message for the tower ticker. [where] lets the player jump to the place it is about. */
data class NewsItem(val id: Int, val text: String, val where: Loc? = null, val important: Boolean = where != null)

/** The tower ticker: a short history of what happened in the tower. */
class NewsFeed(private val capacity: Int = 50) {
    private val items = ArrayDeque<NewsItem>()
    private var nextId = 1

    val latest: List<NewsItem> get() = items.toList()

    fun post(text: String, where: Loc? = null, important: Boolean = where != null): NewsItem {
        val item = NewsItem(nextId++, text, where, important)
        items.addFirst(item)
        while (items.size > capacity) items.removeLast()
        return item
    }
}
