package com.adhan.prayertimes

import org.json.JSONObject

object AladhanJsonParser {

    fun parsePanel(json: String): PrayerPanelUi? {
        val root = JSONObject(json)
        val data = root.optJSONObject("data") ?: return null
        val timingsObj = data.optJSONObject("timings") ?: return null
        val timings = mutableMapOf<String, String>()
        val keys = timingsObj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            timings[k] = timingsObj.optString(k, "--:--")
        }
        val cards = buildPrayerCards(timings)
        val next = findNextPrayer(cards)

        val dateObj = data.optJSONObject("date")
        val readable = dateObj?.optString("readable", "") ?: ""
        val hijri = dateObj?.optJSONObject("hijri")
        val hijriDate = hijri?.optString("date", "")?.takeIf { it.isNotBlank() }
            ?: run {
                val month = hijri?.optJSONObject("month")?.optString("en", "")
                val year = hijri?.optString("year", "")
                if (!month.isNullOrBlank() && !year.isNullOrBlank()) "$month $year" else ""
            }

        return PrayerPanelUi(
            readableDate = readable,
            hijriDate = hijriDate,
            cards = cards,
            nextKey = next?.key,
        )
    }

    fun parseErrorMessage(json: String): String? = try {
        val o = JSONObject(json)
        if (o.has("error")) o.getString("error") else null
    } catch (_: Exception) {
        null
    }
}
