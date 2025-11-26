package ch.innuvation.bookingsearch

import jakarta.annotation.PostConstruct
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.*
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser
import org.apache.lucene.search.*
import org.apache.lucene.store.Directory
import org.apache.lucene.store.ByteBuffersDirectory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.random.Random
import java.math.BigDecimal
import java.time.LocalDate

@Service
class BookingSearchService {

    private val analyzer = StandardAnalyzer()
    private val directory: Directory = ByteBuffersDirectory()
    private val logger = LoggerFactory.getLogger(BookingSearchService::class.java)

    /**
     * Load demo data. Later this can be replaced by a DB query.
     */
    @PostConstruct
    fun initIndex() {
        val bookings = loadBookingsFromSomewhere()
        rebuildIndex(bookings)
    }

    private fun loadBookingsFromSomewhere(): List<Booking> {
        val today = LocalDate.now()

        val demoBookings = listOf(
            Booking(
                id = 1L,
                transactionDate = today.minusDays(1),
                amount = BigDecimal("-45.80"),
                moneyAccountId = "ACC-1",
                bookingText = "Migros Supermarkt Zürich Löwenstrasse"
            ),
            Booking(
                id = 2L,
                transactionDate = today.minusDays(2),
                amount = BigDecimal("-23.40"),
                moneyAccountId = "ACC-1",
                bookingText = "Einkauf bei MIGROS Online Shop"
            ),
            Booking(
                id = 3L,
                transactionDate = today.minusDays(3),
                amount = BigDecimal("-12.90"),
                moneyAccountId = "ACC-2",
                bookingText = "Coop Filiale Basel Bahnhof"
            ),
            Booking(
                id = 4L,
                transactionDate = today.minusDays(4),
                amount = BigDecimal("-8.50"),
                moneyAccountId = "ACC-2",
                bookingText = "COOP Pronto Tankstelle Zürich"
            ),
            Booking(
                id = 5L,
                transactionDate = today.minusDays(5),
                amount = BigDecimal("-19.90"),
                moneyAccountId = "ACC-3",
                bookingText = "Netflix.com Subscription"
            ),
            Booking(
                id = 6L,
                transactionDate = today.minusDays(6),
                amount = BigDecimal("-7.99"),
                moneyAccountId = "ACC-3",
                bookingText = "NETFLIX.COM Monthly Fee"
            ),
            Booking(
                id = 7L,
                transactionDate = today.minusDays(7),
                amount = BigDecimal("-89.00"),
                moneyAccountId = "ACC-4",
                bookingText = "Amazon Marketplace Order 123-4567890-1234567"
            ),
            Booking(
                id = 8L,
                transactionDate = today.minusDays(8),
                amount = BigDecimal("-15.75"),
                moneyAccountId = "ACC-4",
                bookingText = "AMAZON EU SARL Bestellung"
            ),
            Booking(
                id = 9L,
                transactionDate = today.minusDays(9),
                amount = BigDecimal("3200.00"),
                moneyAccountId = "ACC-5",
                bookingText = "Lohnzahlung Firma Innuvation GmbH"
            ),
            Booking(
                id = 10L,
                transactionDate = today.minusDays(10),
                amount = BigDecimal("-120.00"),
                moneyAccountId = "ACC-5",
                bookingText = "SBB Ticket Zürich - Bern"
            ),
            Booking(
                id = 10L,
                transactionDate = today.minusDays(10),
                amount = BigDecimal("10005"),
                moneyAccountId = "ACC-3",
                bookingText = "Lohnzahlung innuvation gmbh"
            )
        )

        // Monkey data with random amounts between -10000 and +10000 (inclusive)
        val random = Random(42)
        val monkeyBookings = (1L..5000L).map { i ->
            val randomAmount: Int = random.nextInt(from = -10_000, until = 10_001) // [-10000, 10000]

            Booking(
                id = 1000L + i,
                transactionDate = today.minusDays(i % 30),
                amount = BigDecimal.valueOf(randomAmount.toLong()),
                moneyAccountId = "ACC-${i % 5}",
                bookingText = "Example-Booking $i für Konto ACC-${i % 5} with amount of $randomAmount CHF"
            )
        }

        return demoBookings + monkeyBookings
    }

    fun rebuildIndex(bookings: List<Booking>) {
        val start = System.currentTimeMillis()

        val config = IndexWriterConfig(analyzer)
        IndexWriter(directory, config).use { writer ->
            writer.deleteAll()
            bookings.forEach { writer.addDocument(toDocument(it)) }
            writer.commit()
        }

        val duration = System.currentTimeMillis() - start
        logger.info("Lucene index initialized in {} ms ({} bookings)", duration, bookings.size)
    }

    private fun toDocument(booking: Booking): Document =
        Document().apply {
            // Technical ID
            add(StringField("id", booking.id.toString(), Field.Store.YES))

            // Date as string (for display)
            add(StringField("transactionDate", booking.transactionDate.toString(), Field.Store.YES))

            // Date as numeric (for range filters)
            val epochDay = booking.transactionDate.toEpochDay()
            add(LongPoint("transactionDateEpoch", epochDay))

            // Amount (signed, for credit/debit filters)
            val amountSigned = booking.amount.toDouble()
            add(DoublePoint("amount", amountSigned))

            // Amount absolute (for min/max amount filters)
            val amountAbs = booking.amount.abs().toDouble()
            add(DoublePoint("amountAbs", amountAbs))

            // Amount stored for display in the response
            add(StoredField("amount_store", amountSigned))

            // Account id (full-text searchable, e.g. "ACC-3")
            add(TextField("moneyAccountId", booking.moneyAccountId, Field.Store.YES))

            // Original booking text (full-text)
            add(TextField("bookingText", booking.bookingText, Field.Store.YES))

            // Normalized booking text (for robust search, not stored)
            val normalizedText = booking.bookingText
                .replace('.', ' ')
                .replace(',', ' ')
                .lowercase()

            add(TextField("bookingTextNorm", normalizedText, Field.Store.NO))
        }

    /**
     * Full-text search with optional numeric filters:
     * - queryString: full-text query (Lucene syntax)
     * - minAmount / maxAmount: range on absolute amount (|amount| between min and max)
     * - fromDate: only bookings on or after this date
     * - includeCredits: include positive amounts
     * - includeDebits: include negative amounts
     */
    fun searchBookings(
        queryString: String?,
        minAmount: BigDecimal? = null,
        maxAmount: BigDecimal? = null,
        fromDate: LocalDate? = null,
        includeCredits: Boolean = true,
        includeDebits: Boolean = true,
        limit: Int = 20
    ): BookingSearchResponse {
        val start = System.currentTimeMillis()
        val queryText = queryString?.trim().orEmpty()

        // If both flags are false, nothing should be returned
        if (!includeCredits && !includeDebits) {
            return BookingSearchResponse(
                total = 0,
                limit = limit,
                results = emptyList()
            )
        }

        DirectoryReader.open(directory).use { reader ->
            val searcher = IndexSearcher(reader)

            // 1) Full-text query (MUST)
            val textQuery: Query = if (queryText.isEmpty()) {
                MatchAllDocsQuery()
            } else {
                val fields = arrayOf("bookingText", "bookingTextNorm", "moneyAccountId")
                val parser = MultiFieldQueryParser(fields, analyzer)
                parser.parse(queryText)
            }

            val booleanQuery = BooleanQuery.Builder()
                .add(textQuery, BooleanClause.Occur.MUST)

            // 2) Amount filter on absolute value: |amount| ∈ [minAmount, maxAmount]
            if (minAmount != null || maxAmount != null) {
                val lower = minAmount?.toDouble() ?: 0.0
                val upper = maxAmount?.toDouble() ?: Double.MAX_VALUE

                val amountRangeFilter = DoublePoint.newRangeQuery(
                    "amountAbs",
                    lower,
                    upper
                )
                booleanQuery.add(amountRangeFilter, BooleanClause.Occur.FILTER)
            }

            // 3) Date filter: transactionDate >= fromDate
            if (fromDate != null) {
                val fromEpoch = fromDate.toEpochDay()
                val dateFilter = LongPoint.newRangeQuery(
                    "transactionDateEpoch",
                    fromEpoch,
                    Long.MAX_VALUE
                )
                booleanQuery.add(dateFilter, BooleanClause.Occur.FILTER)
            }

            // 4) Credit / debit filter based on signed amount
            if (includeCredits && !includeDebits) {
                // Only credits: amount > 0
                val creditFilter = DoublePoint.newRangeQuery(
                    "amount",
                    0.0,
                    Double.MAX_VALUE
                )
                booleanQuery.add(creditFilter, BooleanClause.Occur.FILTER)
            } else if (!includeCredits && includeDebits) {
                // Only debits: amount < 0
                val debitFilter = DoublePoint.newRangeQuery(
                    "amount",
                    -Double.MAX_VALUE,
                    -0.0000001
                )
                booleanQuery.add(debitFilter, BooleanClause.Occur.FILTER)
            }
            // If both true: no additional signed-amount filter

            val finalQuery: Query = booleanQuery.build()

            val topDocs: TopDocs = searcher.search(finalQuery, limit)

            val results = topDocs.scoreDocs.map { scoreDoc ->
                val luceneDoc: Document = searcher.storedFields().document(scoreDoc.doc)

                val idStr: String = luceneDoc["id"] ?: error("Missing 'id' field")
                val id: Long = idStr.toLong()

                val dateStr: String = luceneDoc["transactionDate"] ?: error("Missing 'transactionDate'")
                val transactionDate: LocalDate = LocalDate.parse(dateStr)

                val amountField = luceneDoc.getField("amount_store") ?: error("Missing 'amount_store' field")
                val amountDouble: Double = amountField.numericValue().toDouble()

                val moneyAccountId: String = luceneDoc["moneyAccountId"] ?: error("Missing 'moneyAccountId'")
                val bookingText: String = luceneDoc["bookingText"] ?: error("Missing 'bookingText'")

                BookingSearchResult(
                    id = id,
                    transactionDate = transactionDate,
                    amount = BigDecimal.valueOf(amountDouble),
                    moneyAccountId = moneyAccountId,
                    bookingText = bookingText,
                    score = scoreDoc.score
                )
            }

            val duration = System.currentTimeMillis() - start
            logger.info(
                "Lucene search took {} ms (q='{}', minAmount={}, maxAmount={}, fromDate={}, includeCredits={}, includeDebits={}, limit={}, hits={})",
                duration,
                queryText,
                minAmount,
                maxAmount,
                fromDate,
                includeCredits,
                includeDebits,
                limit,
                results.size
            )
            return BookingSearchResponse(
                total = topDocs.totalHits.value,
                limit = limit,
                results = results
            )
        }
    }
}
