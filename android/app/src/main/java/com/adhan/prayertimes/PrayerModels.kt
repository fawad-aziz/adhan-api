package com.adhan.prayertimes

data class PrayerCardUi(
    val key: String,
    val label: String,
    val time: String,
)

data class PrayerPanelUi(
    val readableDate: String,
    val hijriDate: String,
    val cards: List<PrayerCardUi>,
    val nextKey: String?,
)

data class PrayerTimesUiState(
    val baseUrl: String = DEFAULT_FUNCTION_BASE_URL,
    val zip: String = "",
    val country: String = "us",
    val date: String = "",
    val method: String = "2",
    val loading: Boolean = false,
    val locationLoading: Boolean = false,
    val error: String? = null,
    val locationError: String? = null,
    val panel: PrayerPanelUi? = null,
)

/** Emulator: host machine localhost. Physical device: use your PC LAN IP. */
const val DEFAULT_FUNCTION_BASE_URL = "http://10.0.2.2:7071"

private val prayerOrder = listOf(
    "Fajr" to "Fajr",
    "Sunrise" to "Sunrise",
    "Dhuhr" to "Dhuhr",
    "Asr" to "Asr",
    "Maghrib" to "Maghrib",
    "Isha" to "Isha",
)

fun buildPrayerCards(timings: Map<String, String>): List<PrayerCardUi> =
    prayerOrder.map { (key, label) ->
        PrayerCardUi(key = key, label = label, time = timings[key] ?: "--:--")
    }

fun findNextPrayer(cards: List<PrayerCardUi>): PrayerCardUi? {
    val cal = java.util.Calendar.getInstance()
    val nowMinutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
    var best: Pair<Int, PrayerCardUi>? = null
    for (card in cards) {
        val mins = parseTimeToMinutes(card.time) ?: continue
        if (mins >= nowMinutes && (best == null || mins < best.first)) {
            best = mins to card
        }
    }
    return best?.second
}

private fun parseTimeToMinutes(raw: String): Int? {
    val head = raw.trim().substringBefore(" ").ifBlank { raw.trim() }
    val parts = head.split(":")
    if (parts.size < 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].filter { it.isDigit() }.take(2).toIntOrNull() ?: return null
    return h * 60 + m
}
