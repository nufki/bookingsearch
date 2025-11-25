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
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import org.apache.lucene.store.ByteBuffersDirectory


@Service
class BookingSearchService {

    private val analyzer = StandardAnalyzer()
    private val directory: Directory = ByteBuffersDirectory()

    /**
     * Load demo data. Later connect to db result query
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
            )
        )
        // 2) Add more data
        val monkeyBookings = (1L..5000L).map { i ->
            Booking(
                id = 1000L + i,
                transactionDate = today.minusDays(i % 30),
                amount = BigDecimal.valueOf((i % 200) - 100.0), // -100..+100
                moneyAccountId = "ACC-${i % 5}",
                bookingText = "Beispiel-Buchung $i für Konto ACC-${i % 5} mit Betrag ${(i % 200) - 100} CHF"
            )
        }
        return demoBookings + monkeyBookings
    }

    fun rebuildIndex(bookings: List<Booking>) {
        val config = IndexWriterConfig(analyzer)
        IndexWriter(directory, config).use { writer ->
            writer.deleteAll()
            bookings.forEach { writer.addDocument(toDocument(it)) }
            writer.commit()
        }
    }

    private fun toDocument(booking: Booking): Document =
        Document().apply {
            // ID
            add(StringField("id", booking.id.toString(), Field.Store.YES))

            // Date (yyyy-MM-dd)
            add(StringField("transactionDate", booking.transactionDate.toString(), Field.Store.YES))

            // Amount
            add(DoublePoint("amount", booking.amount.toDouble()))
            add(StoredField("amount_store", booking.amount.toDouble()))

            // Account id (exact match)
            add(TextField("moneyAccountId", booking.moneyAccountId, Field.Store.YES))

            // Fulltext
            add(TextField("bookingText", booking.bookingText, Field.Store.YES))

            val normalizedText = booking.bookingText
                .replace('.', ' ')
                .replace(',', ' ')
                .lowercase()

            add(TextField("bookingTextNorm", normalizedText, Field.Store.NO))
        }

    fun searchBookings(queryString: String?, limit: Int = 20): BookingSearchResponse {
        val queryText = queryString?.trim().orEmpty()

        DirectoryReader.open(directory).use { reader ->
            val searcher = IndexSearcher(reader)

            val query: Query = if (queryText.isEmpty()) {
                MatchAllDocsQuery()
            } else {
                val fields = arrayOf("bookingText", "bookingTextNorm", "moneyAccountId")
                val parser = MultiFieldQueryParser(fields, analyzer)
                parser.parse(queryText)
            }

            val topDocs: TopDocs = searcher.search(query, limit)

            var results = topDocs.scoreDocs.map { scoreDoc ->
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

            return BookingSearchResponse(
                total = topDocs.totalHits.value,
                limit = limit,
                results = results
            )
        }
    }
}
