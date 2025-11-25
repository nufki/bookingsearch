package ch.innuvation.bookingsearch

import java.math.BigDecimal
import java.time.LocalDate

data class Booking(
    val id: Long,
    val transactionDate: LocalDate,
    val amount: BigDecimal,
    val moneyAccountId: String,
    val bookingText: String
)

data class BookingSearchResult(
    val id: Long,
    val transactionDate: LocalDate,
    val amount: BigDecimal,
    val moneyAccountId: String,
    val bookingText: String,
    val score: Float
)
