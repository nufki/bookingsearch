package ch.innuvation.bookingsearch

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate

@RestController
class BookingSearchController(
    private val bookingSearchService: BookingSearchService
) {

    @GetMapping("/bookings/search")
    fun search(
        @RequestParam(name = "q", required = false) q: String?,
        @RequestParam(name = "minAmount", required = false) minAmount: BigDecimal?,
        @RequestParam(name = "maxAmount", required = false) maxAmount: BigDecimal?,
        @RequestParam(name = "fromDate", required = false) fromDate: String?, // yyyy-MM-dd
        @RequestParam(name = "includeCredits", required = false, defaultValue = "true") includeCredits: Boolean,
        @RequestParam(name = "includeDebits", required = false, defaultValue = "true") includeDebits: Boolean,
        @RequestParam(required = false, defaultValue = "20") limit: Int
    ): BookingSearchResponse {

        val parsedFromDate = fromDate?.let { LocalDate.parse(it) }

        return bookingSearchService.searchBookings(
            queryString = q,
            minAmount = minAmount,
            maxAmount = maxAmount,
            fromDate = parsedFromDate,
            includeCredits = includeCredits,
            includeDebits = includeDebits,
            limit = limit
        )
    }


    @GetMapping("/bookings/searchFuzzy")
    fun searchFuzzy(
        @RequestParam(name = "q", required = false) q: String?,
        @RequestParam(name = "minAmount", required = false) minAmount: BigDecimal?,
        @RequestParam(name = "maxAmount", required = false) maxAmount: BigDecimal?,
        @RequestParam(name = "fromDate", required = false) fromDate: String?, // ISO yyyy-MM-dd
        @RequestParam(name = "includeCredits", required = false, defaultValue = "true") includeCredits: Boolean,
        @RequestParam(name = "includeDebits", required = false, defaultValue = "true") includeDebits: Boolean,
        @RequestParam(required = false, defaultValue = "20") limit: Int
    ): BookingSearchResponse {
        val raw = q?.trim().orEmpty()

        // Turn each term into a fuzzy term: "migors coop" -> "migors~2 coop~2"
        val fuzzyQuery = raw
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { term -> "$term~2" } // use levenshtein distance = 2

        val effectiveQuery = fuzzyQuery.ifBlank { null }
        val parsedFromDate = fromDate?.let { LocalDate.parse(it) }

        return bookingSearchService.searchBookings(
            queryString = effectiveQuery,
            minAmount = minAmount,
            maxAmount = maxAmount,
            fromDate = parsedFromDate,
            includeCredits = includeCredits,
            includeDebits = includeDebits,
            limit = limit
        )
    }
}
