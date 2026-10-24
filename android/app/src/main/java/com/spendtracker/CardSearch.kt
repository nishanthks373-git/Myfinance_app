package com.spendtracker

/**
 * Searches the "Card list" tab by category. A card matches when a category column (Movies, Fuel,
 * Lounge...) or the text of a benefit (BookMyShow BOGO, 5% on Zomato...) contains the search words,
 * or a related word (movie -> movies, bookmyshow, bms, pvr, district, cinema...).
 */
object CardSearch {
    private val groups = listOf(
        listOf("movie", "movies", "cinema", "film", "bookmyshow", "bms", "pvr", "inox", "district", "bogo", "entertainment", "ott"),
        listOf("food", "dining", "restaurant", "zomato", "swiggy", "delivery", "eazydiner"),
        listOf("grocery", "groceries", "supermarket", "bigbasket", "blinkit", "zepto", "instamart", "star bazaar", "instant delivery"),
        listOf("shopping", "amazon", "flipkart", "myntra", "ajio", "nykaa", "online", "smartbuy", "vouchers"),
        listOf("travel", "flight", "flights", "hotel", "hotels", "mmt", "makemytrip", "etihad", "miles", "airport"),
        listOf("lounge", "airport", "priority pass"),
        listOf("fuel", "petrol", "diesel"),
        listOf("insurance", "premium"),
        listOf("school", "college", "education", "educational", "tuition", "fees"),
        listOf("upi", "gpay", "phonepe", "paytm"),
        listOf("bill", "bills", "utility", "utilities", "electricity", "water", "gas", "internet", "broadband", "recharge"),
        listOf("tax", "taxes"),
    )

    /** The words to look for: what was typed, plus related words when it names a known category. */
    fun terms(query: String): List<String> {
        val q = query.trim().lowercase()
        if (q.length < 2) return if (q.isEmpty()) emptyList() else listOf(q)
        val out = linkedSetOf(q)
        for (g in groups) {
            if (g.any { it == q || (q.length >= 3 && (it.startsWith(q) || q.startsWith(it))) }) out += g
        }
        return out.toList()
    }

    private fun meaningful(v: String): Boolean {
        val t = v.trim().lowercase()
        return t.isNotEmpty() && t != "0" && t != "0%" && t != "-" && t != "—" && t != "na" && t != "n/a"
    }

    /** (category, text) pairs of this card that match. The card name and annual fee columns are not searched here. */
    fun matches(header: List<String>, row: List<String>, terms: List<String>): List<Pair<String, String>> {
        if (terms.isEmpty()) return emptyList()
        val hits = mutableListOf<Pair<String, String>>()
        for (i in 2 until header.size) {
            val h = header[i]
            val v = row.getOrNull(i).orEmpty()
            if (!meaningful(v)) continue
            val hl = h.lowercase()
            val vl = v.lowercase()
            if (terms.any { hl.contains(it) || vl.contains(it) }) hits += h to v.trim()
        }
        return hits
    }
}
