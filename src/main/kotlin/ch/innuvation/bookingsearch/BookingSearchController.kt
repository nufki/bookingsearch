package ch.innuvation.bookingsearch

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class BookingSearchController(
    private val bookingSearchService: BookingSearchService
) {

    @GetMapping("/bookings/search")
    fun search(
        @RequestParam(name = "q", required = false) q: String?,
        @RequestParam(required = false, defaultValue = "20") limit: Int
    ): List<BookingSearchResult> =
        bookingSearchService.searchBookings(q, limit)


    @GetMapping("/bookings/searchFuzzy")
    fun searchFuzzy(
        @RequestParam(name = "q", required = false) q: String?,
        @RequestParam(required = false, defaultValue = "20") limit: Int
    ): List<BookingSearchResult> {

        val raw = q?.trim().orEmpty()

        // Fuzzyfy e.g. "migors coop" -> "migors~1 coop~1"
        val fuzzyQuery = raw
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { term -> "$term~1" }

        val effectiveQuery = fuzzyQuery.ifBlank { null }

        return bookingSearchService.searchBookings(effectiveQuery, limit)
    }

    @GetMapping("/bookings/searchWildcard")
    fun searchWildcard(
        @RequestParam(name = "q", required = false) q: String?,
        @RequestParam(required = false, defaultValue = "20") limit: Int
    ): List<BookingSearchResult> {

        val raw = q?.trim().orEmpty()

        // "netflix sbb" -> "netflix* sbb*"
        val wildcardQuery = raw
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { term ->
                // Only add if string doesn't already contain special characters / Wildcard
                if (term.any { it == '*' || it == '?' }) term else "$term*"
            }

        val effectiveQuery = wildcardQuery.ifBlank { null }

        return bookingSearchService.searchBookings(effectiveQuery, limit)
    }

}
