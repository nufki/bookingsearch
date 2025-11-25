# BookingSearch API

The application exposes simple REST endpoints to search bookings using Apache Lucene.
The current setup uses the standard lucene stack which is:
StandardAnalyzer, TextField, MultiFieldQueryParser, IndexSearcher.search(...)
Documents are broken down into terms (tokenization, lowercasing, stop words, etc.).
An inverted index is built: for each term, there is a postings list with the document IDs in which that term occurs.
When a query (e.g. migros, netflix, …) is made, Lucene checks: which documents contain these terms?
Then it calculates a score for each document, by default BM25 (previously TF-IDF).
This is not a classical vector search in the embedding sense, but terms are discrete tokens.
Relevance is based on term frequency, document length, inverse document frequency, etc.
Matching works, for example, like “document contains netflix”, and then results are sorted by score.
No Euclidean distance, no embeddings – unless you build something custom yourself with NumericFields or similar. 

## Endpoints

### `GET /bookings/search`

Performs a full-text search over:

- `bookingTextNorm` – normalized version of `bookingText` (e.g. `Netflix.com` → `netflix com`)
- `moneyAccountId` – the account identifier (e.g. `ACC-3`)

If the `q` parameter is omitted or empty, a `MatchAllDocsQuery` is used and all documents are returned (up to `limit`).

**Query parameters**

- `q` (optional): free text query, Lucene query syntax supported
- `limit` (optional, default `20`): maximum number of results to return

---

### `GET /bookings/searchFuzzy`

Typo-tolerant search endpoint.

The controller transforms each term in `q` into a fuzzy / wildcard Lucene term internally (e.g. `migors` → `migors~1` or `migors*`), so small spelling mistakes still return relevant results.

**Query parameters**

- `q` (optional): free text query with possible typos
- `limit` (optional, default `20`): maximum number of results to return

---

## Example Requests

### 1. Full-text search in `bookingText`

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

### 3. Match-all queries

All bookings (match all) with a limit of 100 results:

```bash
curl "http://localhost:8080/bookings/search?limit=100"
```

All bookings with the default limit of 20:

```bash
curl "http://localhost:8080/bookings/search"
```

---

### 4. Typo-tolerant search (`/bookings/searchFuzzy`)

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

These requests should still return the corresponding Migros, Netflix, or Amazon bookings as long as the fuzzy endpoint rewrites the query terms internally (e.g. by appending `~1` or `*`).
