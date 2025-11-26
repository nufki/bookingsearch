# BookingSearch API

The application exposes simple REST endpoints to search bookings using Apache Lucene.

The current setup uses the standard Lucene stack:
- `StandardAnalyzer`
- `TextField`
- `MultiFieldQueryParser`
- `IndexSearcher.search(...)`

Documents are broken down into terms (tokenization, lowercasing, stop words, etc.).  
An inverted index is built: for each term, there is a postings list with the document IDs in which that term occurs.

When a query (e.g. `migros`, `netflix`, …) is executed, Lucene checks which documents contain these terms and calculates a score for each document, by default **BM25** (previously TF-IDF).

This is **not** a classical vector search in the embedding sense:
- terms are discrete tokens
- relevance is based on term frequency, document length, inverse document frequency, etc.
- matching works like “document contains `netflix`”, and then results are sorted by score

No Euclidean distance, no embeddings – unless you explicitly add a vector index (`KnnVectorField`) and embeddings yourself.

---

## Endpoints

### `GET /bookings/search`

Performs a full-text search over:

- `bookingText` – original booking text
- `bookingTextNorm` – normalized version of `bookingText` (e.g. `Netflix.com` → `netflix com`)
- `moneyAccountId` – the account identifier (e.g. `ACC-3`)

If the `q` parameter is omitted or empty, a `MatchAllDocsQuery` is used and all documents are returned (up to `limit`).

In addition to full-text search, this endpoint supports numeric and date filters:

- **Amount range** (absolute value): `|amount|` between `minAmount` and `maxAmount`
- **Date filter**: `transactionDate` on or after `fromDate`
- **Credit/debit filters**:
    - credits = positive amounts (`amount > 0`)
    - debits = negative amounts (`amount < 0`)
    - both are included by default

**Query parameters**

- `q` (optional): free text query, Lucene query syntax supported
- `minAmount` (optional): minimum absolute amount (e.g. `50.00`)
- `maxAmount` (optional): maximum absolute amount (e.g. `200.00`)
- `fromDate` (optional): lower bound for the booking date, format `yyyy-MM-dd`
- `includeCredits` (optional, default `true`): whether to include positive amounts
- `includeDebits` (optional, default `true`): whether to include negative amounts
- `limit` (optional, default `20`): maximum number of results to return

If both `includeCredits` and `includeDebits` are set to `false`, the API will return an empty result.

---

### `GET /bookings/searchFuzzy`

Typo-tolerant search endpoint.

The controller transforms each term in `q` into a fuzzy Lucene term internally  
(e.g. `migors` → `migors~1`), so small spelling mistakes still return relevant results.

This endpoint supports the same filters as `/bookings/search`:

**Query parameters**

- `q` (optional): free text query with possible typos
- `minAmount` (optional): minimum absolute amount
- `maxAmount` (optional): maximum absolute amount
- `fromDate` (optional): lower bound for the booking date, format `yyyy-MM-dd`
- `includeCredits` (optional, default `true`): whether to include positive amounts
- `includeDebits` (optional, default `true`): whether to include negative amounts
- `limit` (optional, default `20`): maximum number of results to return

---

## Example Requests

### 1. Full-text search in booking text

Search all bookings containing the word `Beispiel` in the text (from the monkey data):

```bash
curl "http://localhost:8080/bookings/search?q=Beispiel"
```

Search for Migros bookings:

```bash
curl "http://localhost:8080/bookings/search?q=migros"
```

Search for Coop bookings:

```bash
curl "http://localhost:8080/bookings/search?q=coop"
```

Search for Netflix subscriptions  
(works even if the original text contains `Netflix.com` / `NETFLIX.COM` thanks to normalization):

```bash
curl "http://localhost:8080/bookings/search?q=netflix"
```

Search for Amazon orders:

```bash
curl "http://localhost:8080/bookings/search?q=amazon"
```

Search for SBB tickets:

```bash
curl "http://localhost:8080/bookings/search?q=sbb"
```

---

### 2. Filter by account (`moneyAccountId`)

Only bookings from account `ACC-3`:

```bash
curl "http://localhost:8080/bookings/search?q=ACC-3"
```

Netflix bookings on account `ACC-3`:

```bash
curl "http://localhost:8080/bookings/search?q=ACC-3 netflix"
```

---

### 3. Amount and date filters (standard search)

All bookings with an absolute amount between 50 and 200:

```bash
curl "http://localhost:8080/bookings/search?minAmount=50&maxAmount=200"
```

Netflix bookings with an absolute amount between 20 and 100:

```bash
curl "http://localhost:8080/bookings/search?q=netflix&minAmount=20&maxAmount=100"
```

All bookings from `2025-11-15` onwards:

```bash
curl "http://localhost:8080/bookings/search?fromDate=2025-11-15"
```

Only credits (positive amounts) between 10000 and 12000 from `2025-11-01`:

```bash
 curl "http://localhost:8080/bookings/search?minAmount=10000&maxAmount=12000&fromDate=2025-11-01&includeDebits=false"
```

Only debits (negative amounts) with absolute amount >= 9000:

```bash
curl "http://localhost:8080/bookings/search?minAmount=9000&includeCredits=false"
```

Netflix debits between 20 and 200 from `2025-11-15` onwards:

```bash
curl "http://localhost:8080/bookings/search?q=netflix&minAmount=20&maxAmount=200&fromDate=2025-11-15&includeCredits=false"
```

All Migros credits (e.g. refunds) with absolute amount <= 30:

```bash
curl "http://localhost:8080/bookings/search?q=migros&maxAmount=30&includeDebits=false"
```

---

### 4. Match-all queries

All bookings (match all) with a limit of 100 results:

```bash
curl "http://localhost:8080/bookings/search?limit=100"
```

All bookings with the default limit of 20:

```bash
curl "http://localhost:8080/bookings/search"
```

---

### 5. Typo-tolerant search (`/bookings/searchFuzzy`)

Misspelling in “Migros”:

```bash
curl "http://localhost:8080/bookings/searchFuzzy?q=migors"
```

Misspelling in “Netflix”:

```bash
curl "http://localhost:8080/bookings/searchFuzzy?q=netflxi"
```

Misspelling in “Amazon”:

```bash
curl "http://localhost:8080/bookings/searchFuzzy?q=amazn"
```

Typo-tolerant search with amount and date filters:

Netflix-like bookings with typos, only debits, from `2025-11-15` onwards, absolute amount between 20 and 200:

```bash
curl "http://localhost:8080/bookings/searchFuzzy?q=netflxi&minAmount=19&maxAmount=200&fromDate=2025-11-10&includeCredits=false"
```

Typo-tolerant search for Migros bookings (e.g. `migorz`), only credits with amount <= 30:

```bash
curl "http://localhost:8080/bookings/searchFuzzy?q=migorz&maxAmount=30&includeCredits=false"
```

These requests should still return the corresponding Migros, Netflix, or Amazon bookings as long as the fuzzy endpoint rewrites the query terms internally (by appending `~1` to each term).
