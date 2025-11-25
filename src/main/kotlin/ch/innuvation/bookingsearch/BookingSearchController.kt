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
    ): BookingSearchResponse =
        bookingSearchService.searchBookings(q, limit)



    @GetMapping("/bookings/searchFuzzy")
    fun searchFuzzy(
        @RequestParam(name = "q", required = false) q: String?,
        @RequestParam(required = false, defaultValue = "20") limit: Int
    ): BookingSearchResponse {

        val raw = q?.trim().orEmpty()

        val fuzzyQuery = raw
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { term -> "$term~1" } // oder deine Logik

        val effectiveQuery = fuzzyQuery.ifBlank { null }

        return bookingSearchService.searchBookings(effectiveQuery, limit)
    }
}
