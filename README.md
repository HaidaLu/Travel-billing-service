# Travel billing — transactions & snapshot invoices

## 1. Prerequisite
Java 17 · Spring Boot 3.5 · Spring JDBC · SQLite (file) · Flyway

## 2. Run

```bash
./mvnw spring-boot:run
```

Listens on `:8080` and creates `./billing.db`. Override with `PORT` / `BILLING_DB_PATH`.

No API keys or other secrets are needed: the service calls no external service.

If you edit `V1__init.sql` while developing, delete the local DB first (`rm -f billing.db billing.db-wal billing.db-shm`).
Otherwise Flyway refuses to start with a checksum mismatch.

## 3. Test

```bash
./mvnw test
```

Each test class runs against its own temporary SQLite file and never touches `./billing.db`.

## 4. Database & volume
- For Detailed Data model, please refer to [ARCHITECTURE.md](ARCHITECTURE.md)
- Schema: [`src/main/resources/db/migration/V1__init.sql`](src/main/resources/db/migration/V1__init.sql), applied by
Flyway at startup.

| Query | Served by |
|---|---|
| Duplicate `external_id` on POST | UNIQUE index `ux_transactions_external_id` |
| `GET /transactions/{id}`, `GET /invoices/{id}` | Primary keys. Child rows (tax/fee lines, metadata, invoice lines, rollups) are keyed by `(parent id, position)` |
| "Is this transaction on an invoice?" (PATCH/DELETE transaction, POST/PATCH invoice) | UNIQUE index `ux_invoice_lines_transaction_id` |
| `GET /transactions`, `GET /invoices` | Primary-key range scan: `WHERE id > ? ORDER BY id LIMIT ?` |

- **Duplicates are rejected by the database.** A create is `INSERT … ON CONFLICT (external_id) DO NOTHING` (see [`TransactionRepository.java`](src/main/java/com/navan/billing/transaction/persistence/TransactionRepository.java)), so the
  unique index, not an application check, decides which of two concurrent POSTs wins. The loser gets `409`.
- **Lists are keyset-paginated.** `limit` defaults to 50 and is at most 100; below 1 or above 100 returns `400`.
  Every list query has a SQL `LIMIT`, no query reads a whole table, and nothing is paginated in memory.
- **Invoiced transactions are protected in the schema too.** `invoice_lines.transaction_id` references
  `transactions(id)` without cascade, so the database refuses to delete a transaction that is on an invoice. Its
  unique index means a transaction is on at most one invoice, and at most once on it. The service checks this first
  so it can answer `409 transaction_already_invoiced` naming the invoice; the index guarantees it even if that check
  were bypassed.
- **Money is stored as integer minor units** (`total_minor`, `amount_minor`, scale 2), never as floating point.
- **Each write is one DB transaction.** SQLite runs in WAL mode with `transaction_mode=IMMEDIATE` and
  `busy_timeout=5000`, so concurrent writers queue instead of interleaving.


## 5. Example curls

The ids below (`txn_1`, `inv_1`, …) assume you start from an empty database and run the sections in order. Ids are
handed out in creation order, and a rejected request never uses one up.

1. If the app is already running, stop it (`Ctrl+C`).
2. Delete the database, so the next start creates an empty one:

   ```bash
   rm -f billing.db billing.db-wal billing.db-shm
   ```

3. Start the app and leave it running:

   ```bash
   ./mvnw spring-boot:run
   ```

4. In a second terminal, from the repo root (the `sqlite3` checks read `./billing.db`), check that it is up:

   ```bash
   curl -i http://localhost:8080/health
   ```

   Then run the sections below in order. Responses are compact JSON.

### 5.1. Create transactions

The bodies are the seed payloads from `fixtures/task-b/seed-payloads.json`. Each POST returns `201` with the stored
transaction.

Flight → `txn_1`:

```bash
curl -i -X POST http://localhost:8080/transactions -H 'Content-Type: application/json' -d '{
  "type": "flight",
  "external_id": "flight-TXL-LHR-2026-03-11",
  "currency": "EUR",
  "occurred_at": "2026-03-11T06:40:00Z",
  "total": 220.0,
  "tax_lines": [{ "name": "Airport tax", "amount": 15.0 }],
  "fee_lines": [{ "name": "YQ", "amount": 25.0 }],
  "metadata": {
    "origin": "TXL",
    "destination": "LHR",
    "fare": 180.0,
    "coupons": [{
      "carrier": "BA", "flight_number": "BA982", "from": "TXL", "to": "LHR",
      "departure": "2026-03-11T07:00:00Z", "arrival": "2026-03-11T08:20:00Z"
    }]
  }
}'
```

Hotel → `txn_2`:

```bash
curl -i -X POST http://localhost:8080/transactions -H 'Content-Type: application/json' -d '{
  "type": "hotel",
  "external_id": "hotel-LON-2026-03-11",
  "currency": "EUR",
  "occurred_at": "2026-03-11T15:00:00Z",
  "total": 331.7,
  "tax_lines": [{ "name": "VAT", "rate": 0.07, "amount": 21.7 }],
  "fee_lines": [],
  "metadata": {
    "name": "Hotel Mitte London",
    "address": { "street": "12 Kingsway", "city": "London", "country": "GB" },
    "check_in": "2026-03-11",
    "check_out": "2026-03-13",
    "nights": 2,
    "room_rate_per_night": 140.0,
    "extras": [{ "name": "Breakfast", "amount": 30.0 }]
  }
}'
```

Rail → `txn_3`:

```bash
curl -i -X POST http://localhost:8080/transactions -H 'Content-Type: application/json' -d '{
  "type": "rail",
  "external_id": "rail-STP-PAR-2026-03-13",
  "currency": "EUR",
  "occurred_at": "2026-03-13T09:00:00Z",
  "total": 94.0,
  "tax_lines": [],
  "fee_lines": [{ "name": "Booking fee", "amount": 4.0 }],
  "metadata": {
    "legs": [
      { "origin": "London St Pancras", "destination": "Lille Europe", "train_number": "EST9108", "class": "standard",
        "departure": "2026-03-13T09:00:00Z", "arrival": "2026-03-13T10:30:00Z" },
      { "origin": "Lille Europe", "destination": "Paris Nord", "train_number": "EST9122", "class": "standard",
        "departure": "2026-03-13T11:00:00Z", "arrival": "2026-03-13T12:05:00Z" }
    ]
  }
}'
```

Navan trip fee → `txn_4`:

```bash
curl -i -X POST http://localhost:8080/transactions -H 'Content-Type: application/json' -d '{
  "type": "navan_fee",
  "external_id": "navan-trip-fee-2026-03-11",
  "currency": "EUR",
  "occurred_at": "2026-03-11T06:40:00Z",
  "total": 25.0,
  "tax_lines": [],
  "fee_lines": [],
  "metadata": {
    "fee_kind": "trip_fee",
    "description": "Navan trip fee",
    "related_external_id": "flight-TXL-LHR-2026-03-11"
  }
}'
```

Navan agent call fee → `txn_5`:

```bash
curl -i -X POST http://localhost:8080/transactions -H 'Content-Type: application/json' -d '{
  "type": "navan_fee",
  "external_id": "navan-agent-call-2026-03-11",
  "currency": "EUR",
  "occurred_at": "2026-03-11T18:00:00Z",
  "total": 15.0,
  "tax_lines": [],
  "fee_lines": [],
  "metadata": {
    "fee_kind": "agent_call_fee",
    "description": "Agent fee for support call",
    "related_external_id": "hotel-LON-2026-03-11"
  }
}'
```

The later examples also need three transactions that are not part of the trip:

```bash
# txn_6: EUR, for the single-id invoice
curl -i -X POST http://localhost:8080/transactions -H 'Content-Type: application/json' \
  -d '{"type": "navan_fee", "external_id": "navan-trip-fee-solo", "currency": "EUR",
       "occurred_at": "2026-03-11T06:40:00Z", "total": 25.0, "tax_lines": [], "fee_lines": [],
       "metadata": {"fee_kind": "trip_fee", "description": "Navan trip fee"}}'

# txn_7: EUR, never invoiced, for patch and delete
curl -i -X POST http://localhost:8080/transactions -H 'Content-Type: application/json' \
  -d '{"type": "navan_fee", "external_id": "navan-agent-call-spare", "currency": "EUR",
       "occurred_at": "2026-03-11T18:00:00Z", "total": 15.0, "tax_lines": [], "fee_lines": [],
       "metadata": {"fee_kind": "agent_call_fee", "description": "Agent fee for support call"}}'

# txn_8: USD, for the mixed-currency invoice
curl -i -X POST http://localhost:8080/transactions -H 'Content-Type: application/json' \
  -d '{"type": "navan_fee", "external_id": "navan-trip-fee-usd", "currency": "USD",
       "occurred_at": "2026-03-11T06:40:00Z", "total": 25.0, "tax_lines": [], "fee_lines": [],
       "metadata": {"fee_kind": "trip_fee", "description": "Navan trip fee"}}'
```

### 5.2. Duplicate create

POST the fixture's `duplicate_flight`, which reuses the flight's `external_id`:

```bash
curl -i -X POST http://localhost:8080/transactions -H 'Content-Type: application/json' -d '{
  "type": "flight",
  "external_id": "flight-TXL-LHR-2026-03-11",
  "currency": "EUR",
  "occurred_at": "2026-03-11T06:40:00Z",
  "total": 220.0,
  "tax_lines": [{ "name": "Airport tax", "amount": 15.0 }],
  "fee_lines": [{ "name": "YQ", "amount": 25.0 }],
  "metadata": { "origin": "TXL", "destination": "LHR", "fare": 180.0, "coupons": [] }
}'
```

Expected: `409` pointing at the stored flight:
`{"code":"duplicate_external_id","message":"...","existing_id":"txn_1"}`. Its empty `coupons` would be a `400` for a
new flight, but the duplicate check runs first, so the client learns that the booking already exists.

Only one row exists for that `external_id`. Check it in the database:

```bash
sqlite3 billing.db "SELECT COUNT(*) FROM transactions WHERE external_id = 'flight-TXL-LHR-2026-03-11';"
```

Or through the API:

```bash
curl -s 'http://localhost:8080/transactions?limit=100' | grep -o '"external_id":"flight-TXL-LHR-2026-03-11"' | wc -l
```

Both print `1`.

Ten overlapping POSTs with the same new `external_id` also leave one row: one `201` (`txn_9`) and nine `409`s, in any
order:

```bash
for i in 1 2 3 4 5 6 7 8 9 10; do
  curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8080/transactions \
    -H 'Content-Type: application/json' \
    -d '{"type": "navan_fee", "external_id": "navan-trip-fee-race", "currency": "EUR",
         "occurred_at": "2026-03-11T06:40:00Z", "total": 25.0, "tax_lines": [], "fee_lines": [],
         "metadata": {"fee_kind": "trip_fee", "description": "Navan trip fee"}}' &
done; wait
```

### 5.3. Get and list transactions

Each GET returns the type-specific metadata that was stored:

```bash
curl -i http://localhost:8080/transactions/txn_1     # flight: metadata.coupons
curl -i http://localhost:8080/transactions/txn_2     # hotel: metadata.name, metadata.address
curl -i http://localhost:8080/transactions/txn_3     # rail: metadata.legs (2)
curl -i http://localhost:8080/transactions/txn_4     # navan_fee: metadata.fee_kind = trip_fee
curl -i http://localhost:8080/transactions/txn_5     # navan_fee: metadata.fee_kind = agent_call_fee
curl -i http://localhost:8080/transactions/txn_999   # 404
```

The list returns at most 50 items by default:

```bash
curl -s http://localhost:8080/transactions   # all 9 transactions; no next_cursor, so this is the last page
```

To page through, pass each response's `next_cursor` to the next request:

```bash
curl -s 'http://localhost:8080/transactions?limit=3'             # txn_1..txn_3, "next_cursor":"Mw"
curl -s 'http://localhost:8080/transactions?limit=3&cursor=Mw'   # txn_4..txn_6, "next_cursor":"Ng"
curl -s 'http://localhost:8080/transactions?limit=3&cursor=Ng'   # txn_7..txn_9, no next_cursor (last page)
```

`limit` above 100 is rejected rather than returning an unbounded page:

```bash
curl -i 'http://localhost:8080/transactions?limit=1000'   # 400
```

### 5.4. Create invoices

EUR + USD on one invoice returns `409 mixed_currency`, and nothing is stored:

```bash
curl -i -X POST http://localhost:8080/invoices -H 'Content-Type: application/json' \
  -d '{"transaction_ids": ["txn_6", "txn_8"]}'
```

The trip invoice (flight + hotel + rail + both Navan fees) → `inv_1`:

```bash
curl -i -X POST http://localhost:8080/invoices -H 'Content-Type: application/json' \
  -d '{"transaction_ids": ["txn_1", "txn_2", "txn_3", "txn_4", "txn_5"]}'
```

Expected, matching `expected-invoice.json`: `"total":685.70` in EUR; `tax_rollup` is `Airport tax 15.00` and
`VAT (rate 0.07) 21.70`; `fee_rollup` is `YQ 25.00` and `Booking fee 4.00`; `lines` holds a snapshot of each of the
five transactions.

A single-id invoice → `inv_2`:

```bash
curl -i -X POST http://localhost:8080/invoices -H 'Content-Type: application/json' \
  -d '{"transaction_id": "txn_6"}'
```

Rejected requests:

```bash
# Same id twice on one invoice → 409 duplicate_invoice_line
curl -i -X POST http://localhost:8080/invoices -H 'Content-Type: application/json' \
  -d '{"transaction_ids": ["txn_7", "txn_7"]}'

# Already on another invoice → 409 transaction_already_invoiced
curl -i -X POST http://localhost:8080/invoices -H 'Content-Type: application/json' \
  -d '{"transaction_id": "txn_1"}'

# Unknown id → 404
curl -i -X POST http://localhost:8080/invoices -H 'Content-Type: application/json' \
  -d '{"transaction_ids": ["txn_7", "txn_999"]}'
```

### 5.5. Get and list invoices

```bash
curl -i http://localhost:8080/invoices/inv_1     # snapshots, tax_rollup, fee_rollup, total
curl -i http://localhost:8080/invoices/inv_999   # 404
```

Same paging rules as `/transactions`:

```bash
curl -s 'http://localhost:8080/invoices?limit=1'             # inv_1, "next_cursor":"MQ"
curl -s 'http://localhost:8080/invoices?limit=1&cursor=MQ'   # inv_2, no next_cursor (last page)
curl -i 'http://localhost:8080/invoices?limit=1000'          # 400
```

### 5.6. Patch and delete a transaction

PATCH accepts `occurred_at`, `total`, `tax_lines`, `fee_lines` and `metadata`. An omitted field stays unchanged,
and a list that is sent replaces the whole stored list. `metadata`, if sent, replaces the stored metadata and must be
complete for the transaction's stored type. Sending `type`, `external_id` or `currency` is a `400`.

A transaction that is not on an invoice can be changed:

```bash
curl -i -X PATCH http://localhost:8080/transactions/txn_7 -H 'Content-Type: application/json' -d '{
  "occurred_at": "2026-03-12T09:00:00Z",
  "total": 30.00,
  "tax_lines": [{ "name": "VAT", "rate": 0.19, "amount": 4.79 }],
  "metadata": {
    "fee_kind": "agent_call_fee",
    "description": "Agent fee for a second support call",
    "related_external_id": "hotel-LON-2026-03-11"
  }
}'
```

Expected: `200` with the new values. `fee_lines` was not sent, so it is unchanged.

Rejected PATCHes:

```bash
# Identity fields can't change → 400
curl -i -X PATCH http://localhost:8080/transactions/txn_7 -H 'Content-Type: application/json' \
  -d '{"currency": "USD"}'

# Unknown id → 404
curl -i -X PATCH http://localhost:8080/transactions/txn_999 -H 'Content-Type: application/json' \
  -d '{"total": 1.00}'
```

It can also be deleted:

```bash
curl -i -X DELETE http://localhost:8080/transactions/txn_7   # 204
curl -i http://localhost:8080/transactions/txn_7             # 404
curl -i -X DELETE http://localhost:8080/transactions/txn_7   # 404
```

A transaction on an invoice is frozen. PATCH and DELETE both return `409 transaction_invoiced` and change nothing:

```bash
curl -i -X PATCH http://localhost:8080/transactions/txn_1 -H 'Content-Type: application/json' \
  -d '{"total": 999.00}'                                     # 409
curl -i -X DELETE http://localhost:8080/transactions/txn_1   # 409
curl -i http://localhost:8080/transactions/txn_1             # 200, "total":220.00
curl -i http://localhost:8080/invoices/inv_1                 # unchanged, "total":685.70
```

The invoice is a stored copy, not a live view of the transactions. Since the API won't change an invoiced
transaction, change the flight directly in the database to see this:

```bash
sqlite3 billing.db "UPDATE transactions SET total_minor = 99900 WHERE id = 1;"
curl -s http://localhost:8080/transactions/txn_1   # "total":999.00
curl -s http://localhost:8080/invoices/inv_1       # still "total":685.70, and the flight line still "total":220.00
sqlite3 billing.db "UPDATE transactions SET total_minor = 22000 WHERE id = 1;"   # put it back
```

### 5.7. Patch an invoice

PATCH replaces the set of transactions and rebuilds the snapshot from the current stored transactions in one write:

```bash
curl -i -X PATCH http://localhost:8080/invoices/inv_1 -H 'Content-Type: application/json' \
  -d '{"transaction_ids": ["txn_1", "txn_2"]}'   # 200, "total":551.70
```

The rail ticket (`txn_3`) is no longer on any invoice, so it can be changed again:

```bash
curl -i -X PATCH http://localhost:8080/transactions/txn_3 -H 'Content-Type: application/json' \
  -d '{"total": 100.00}'   # 200
```

Adding it back uses its current stored total, `100.00` rather than the original `94.00`:

```bash
curl -i -X PATCH http://localhost:8080/invoices/inv_1 -H 'Content-Type: application/json' \
  -d '{"transaction_ids": ["txn_1", "txn_2", "txn_3"]}'   # 200, "total":651.70
```

A failed PATCH leaves the invoice unchanged:

```bash
# Unknown transaction → 404
curl -i -X PATCH http://localhost:8080/invoices/inv_1 -H 'Content-Type: application/json' \
  -d '{"transaction_ids": ["txn_1", "txn_999"]}'

# Mixed currency → 409
curl -i -X PATCH http://localhost:8080/invoices/inv_1 -H 'Content-Type: application/json' \
  -d '{"transaction_ids": ["txn_1", "txn_8"]}'

# Unknown invoice → 404
curl -i -X PATCH http://localhost:8080/invoices/inv_999 -H 'Content-Type: application/json' \
  -d '{"transaction_ids": ["txn_1"]}'

curl -i http://localhost:8080/invoices/inv_1   # still "total":651.70
```

### 5.8. Delete an invoice

Deleting an invoice removes its snapshot and rollup rows. The transactions are kept, and they are no longer frozen:

```bash
curl -i -X DELETE http://localhost:8080/invoices/inv_1   # 204
curl -i http://localhost:8080/invoices/inv_1             # 404
curl -i -X DELETE http://localhost:8080/invoices/inv_1   # 404
curl -i http://localhost:8080/transactions/txn_1         # 200
curl -i -X PATCH http://localhost:8080/transactions/txn_1 -H 'Content-Type: application/json' \
  -d '{"total": 230.00}'                                 # 200
```
