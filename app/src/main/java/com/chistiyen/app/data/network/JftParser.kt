package com.chistiyen.app.data.network

import org.jsoup.Jsoup

data class JftData(
    val date: String = "",
    val title: String = "",
    val ref: String = "",
    val quote: String = "",
    val body: String = "",
    val jft: String = ""
)

object JftParser {
    fun parse(html: String): JftData? {
        return try {
            val doc = Jsoup.parse(html)
            // This selector depends on the actual HTML structure of na-russia.org
            // Will need adjustment based on real page structure
            JftData(
                date = doc.select(".jft-date").text(),
                title = doc.select(".jft-title").text(),
                ref = doc.select(".jft-ref").text(),
                quote = doc.select(".jft-quote").text(),
                body = doc.select(".jft-body").text(),
                jft = doc.select(".jft-jft").text()
            )
        } catch (e: Exception) {
            null
        }
    }
}

data class GroupData(
    val dayOfWeek: Int,
    val time: String,
    val name: String,
    val address: String,
    val types: List<String>
)

object GroupParser {
    fun parse(html: String): List<GroupData> {
        // Needs to be adapted to actual page structure
        return emptyList()
    }
}
