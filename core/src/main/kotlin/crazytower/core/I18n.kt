package crazytower.core

/** The languages the game speaks. */
enum class Language(val label: String) {
    DE("Deutsch"),
    EN("English"),
    ;

    companion object {
        /** German on a German system, English everywhere else. */
        fun system(): Language = if (java.util.Locale.getDefault().language == "de") DE else EN
    }
}

/**
 * The language of every text the game shows. The front end sets it from the options; texts
 * are picked when they are shown, so a change applies to everything drawn afterwards.
 * Messages already in the ticker keep the language they were written in.
 */
object I18n {
    @Volatile
    var language: Language = Language.DE

    val english: Boolean get() = language == Language.EN
}

/** The text in the current language: German first, English second. */
fun tr(de: String, en: String): String = if (I18n.english) en else de

/** A text in both languages for data defined once and shown later, such as tenant names. Prints in the current language. */
data class Tr(val de: String, val en: String) {
    override fun toString(): String = tr(de, en)
}

/** Groups the thousands: 48250 is "48.250" in German and "48,250" in English. */
fun formatNumber(value: Long): String {
    val digits = kotlin.math.abs(value).toString()
    val grouped = digits.reversed().chunked(3).joinToString(if (I18n.english) "," else ".").reversed()
    return if (value < 0) "-$grouped" else grouped
}

/** "1.800 $" in German, "$1,800" in English. */
fun formatMoney(value: Long): String =
    if (I18n.english) (if (value < 0) "-$" else "$") + formatNumber(kotlin.math.abs(value)) else "${formatNumber(value)} $"

/** "1.000–2.500 $" in German, "$1,000–2,500" in English; one amount when both are the same. */
fun formatMoneyRange(low: Long, high: Long): String = when {
    low == high -> formatMoney(low)
    I18n.english -> "${formatMoney(low)}–${formatNumber(high)}"
    else -> "${formatNumber(low)}–${formatMoney(high)}"
}
