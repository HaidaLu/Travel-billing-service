# Travel billing — transactions & snapshot invoices

Java 17 · Spring Boot 3.5 · Spring JDBC · SQLite (file) · Flyway

## Run

```bash
./mvnw spring-boot:run
```

Listens on `:8080` and creates `./billing.db`. Override with `PORT` / `BILLING_DB_PATH`.

If you edit `V1__init.sql` while developing, delete the local DB first (`rm -f billing.db billing.db-wal billing.db-shm`).
Otherwise Flyway refuses to start with a checksum mismatch.

## Test

```bash
./mvnw test
```

Each test class runs against its own temporary SQLite file and never touches `./billing.db`.

## API decisions

| Topic                                               | Decision                                                                                   |
|-----------------------------------------------------|--------------------------------------------------------------------------------------------|
| Ids                                                 | `txn_<n>` / `inv_<n>`; a malformed or wrong-prefix id is 404                               |
| Money                                               | JSON decimal numbers, stored as integer minor units at scale 2; more than 2 decimals → 400 |
| Duplicate `external_id`                             | 409 + `existing_id`                                                                        |
| Same txn id twice on one invoice                    | 409                                                                                        |
| Txn already on *another* invoice                    | 409 (`transaction_already_invoiced`, names the invoice)                                    |
| Rollup grouping                                     | taxes by (name, rate), rates compared numerically (0.07 = 0.070); fees by name; first-seen order |
| Pagination                                          | keyset: `?limit=&cursor=` → `{ "items": [...], "next_cursor": "..." }`, no cursor on the last page |
| `limit`                                             | default 50; `< 1` or `> 100` → 400                                                         |
| PATCH sends `type` / `external_id` / `currency`     | 400                                                                                        |
| List item shape                                     | fully hydrated (≤100 rows; note the N+1 cost)                                              |
| Total reconciliation (total vs fare + taxes + fees) | don't enforce                                                                              |

Error body: `{ "code": "...", "message": "...", "existing_id": "..." }`. `existing_id` is only present on a
duplicate `external_id` 409.

## Example curls

The create bodies come straight from `fixtures/task-b/seed-payloads.json`, the same payloads the tests use, so the
commands need `jq`. Run the sections in order in one shell, against a fresh DB: each step keeps the ids it gets
back in shell variables for the later steps.

```bash
BASE_URL=http://localhost:8080
SEEDS=fixtures/task-b/seed-payloads.json

# POST the JSON on stdin to /transactions and print the new id (e.g. txn_1).
create_txn() {
  curl -s -X POST "$BASE_URL/transactions" -H 'Content-Type: application/json' -d @- | jq -r .id
}
```

### 1. Create transactions

One of each type, including both Navan fee kinds. Each POST returns `201` with the stored transaction.

```bash
FLIGHT_ID=$(jq '.flight' "$SEEDS" | create_txn)
HOTEL_ID=$(jq '.hotel' "$SEEDS" | create_txn)
RAIL_ID=$(jq '.rail' "$SEEDS" | create_txn)
TRIP_FEE_ID=$(jq '.trip_fee' "$SEEDS" | create_txn)
AGENT_FEE_ID=$(jq '.agent_call_fee' "$SEEDS" | create_txn)
echo "$FLIGHT_ID $HOTEL_ID $RAIL_ID $TRIP_FEE_ID $AGENT_FEE_ID"
```

The later examples also need three transactions that are not part of the trip. They are seed variants with a
new `external_id`:

```bash
SOLO_ID=$(jq '.trip_fee | .external_id = "navan-trip-fee-solo"' "$SEEDS" | create_txn)            # single-id invoice
SPARE_ID=$(jq '.agent_call_fee | .external_id = "navan-agent-call-spare"' "$SEEDS" | create_txn)  # patch/delete, never invoiced
USD_ID=$(jq '.trip_fee | .external_id = "navan-trip-fee-usd" | .currency = "USD"' "$SEEDS" | create_txn)  # mixed currency
```

### 2. Duplicate create

POST the flight's `external_id` a second time:

```bash
jq '.duplicate_flight | del(.comment)' "$SEEDS" | curl -i -X POST "$BASE_URL/transactions" \
  -H 'Content-Type: application/json' -d @-
```

Expected: `409` pointing at the stored flight, e.g.
`{"code":"duplicate_external_id","message":"...","existing_id":"txn_1"}`.

Only one row exists for that `external_id`:

```bash
sqlite3 billing.db "SELECT COUNT(*) FROM transactions WHERE external_id = 'flight-TXL-LHR-2026-03-11';"
```

The same check through the API (fewer than 100 rows on a fresh DB):

```bash
curl -s "$BASE_URL/transactions?limit=100" \
  | jq '[.items[] | select(.external_id == "flight-TXL-LHR-2026-03-11")] | length'
```

Both print `1`.

### 3. Get and list transactions

```bash
curl -s "$BASE_URL/transactions/$FLIGHT_ID" | jq      # metadata.coupons
curl -s "$BASE_URL/transactions/$HOTEL_ID" | jq       # metadata.name, metadata.address
curl -s "$BASE_URL/transactions/$RAIL_ID" | jq        # metadata.legs (2)
curl -s "$BASE_URL/transactions/$AGENT_FEE_ID" | jq   # metadata.fee_kind
curl -i "$BASE_URL/transactions/txn_999999"           # 404
```

List with the default page size (50), then page through with a small limit:

```bash
curl -s "$BASE_URL/transactions" | jq '{count: (.items | length), next_cursor}'

PAGE1=$(curl -s "$BASE_URL/transactions?limit=3")
echo "$PAGE1" | jq '[.items[].id], .next_cursor'
curl -s "$BASE_URL/transactions?limit=3&cursor=$(echo "$PAGE1" | jq -r .next_cursor)" \
  | jq '[.items[].id], .next_cursor'
```

`limit` above 100 is rejected rather than returning an unbounded page:

```bash
curl -i "$BASE_URL/transactions?limit=1000"   # 400
```

### 4. Create invoices

EUR + USD on one invoice returns `409`, and nothing is stored:

```bash
curl -i -X POST "$BASE_URL/invoices" -H 'Content-Type: application/json' \
  -d "{\"transaction_ids\": [\"$SOLO_ID\", \"$USD_ID\"]}"
```

The trip invoice (flight + hotel + rail + both Navan fees):

```bash
INVOICE_ID=$(curl -s -X POST "$BASE_URL/invoices" -H 'Content-Type: application/json' \
  -d "{\"transaction_ids\": [\"$FLIGHT_ID\", \"$HOTEL_ID\", \"$RAIL_ID\", \"$TRIP_FEE_ID\", \"$AGENT_FEE_ID\"]}" \
  | jq -r .id)
curl -s "$BASE_URL/invoices/$INVOICE_ID" | jq '{id, currency, total, tax_rollup, fee_rollup}'
```

Expected, matching `expected-invoice.json`: total `685.70` EUR; tax rollup `Airport tax 15.00`,
`VAT (0.07) 21.70`; fee rollup `YQ 25.00`, `Booking fee 4.00`.

A single-id invoice:

```bash
curl -i -X POST "$BASE_URL/invoices" -H 'Content-Type: application/json' \
  -d "{\"transaction_id\": \"$SOLO_ID\"}"
```

Rejected requests:

```bash
# Same id twice on one invoice → 409
curl -i -X POST "$BASE_URL/invoices" -H 'Content-Type: application/json' \
  -d "{\"transaction_ids\": [\"$SPARE_ID\", \"$SPARE_ID\"]}"

# Already on another invoice → 409
curl -i -X POST "$BASE_URL/invoices" -H 'Content-Type: application/json' \
  -d "{\"transaction_id\": \"$FLIGHT_ID\"}"

# Unknown id → 404
curl -i -X POST "$BASE_URL/invoices" -H 'Content-Type: application/json' \
  -d '{"transaction_id": "txn_999999"}'
```

### 5. Get and list invoices

```bash
curl -s "$BASE_URL/invoices/$INVOICE_ID" | jq
curl -i "$BASE_URL/invoices/inv_999999"   # 404
```

Same paging rules as `/transactions`:

```bash
PAGE1=$(curl -s "$BASE_URL/invoices?limit=1")
echo "$PAGE1" | jq '[.items[].id], .next_cursor'
curl -s "$BASE_URL/invoices?limit=1&cursor=$(echo "$PAGE1" | jq -r .next_cursor)" \
  | jq '[.items[].id], .next_cursor'
```

### 6. Patch and delete a transaction

PATCH accepts `occurred_at`, `total`, `tax_lines`, `fee_lines` and `metadata`. An omitted field stays unchanged,
and a list that is sent replaces the whole stored list. `metadata`, if sent, replaces the stored metadata and must be
complete for the transaction's stored type. Sending `type`, `external_id` or `currency` is a `400`.

A transaction that is not on an invoice can be changed and deleted:

```bash
curl -i -X PATCH "$BASE_URL/transactions/$SPARE_ID" -H 'Content-Type: application/json' \
  -d '{"total": 30.00}'                                  # 200
curl -i -X DELETE "$BASE_URL/transactions/$SPARE_ID"   # 204
curl -i "$BASE_URL/transactions/$SPARE_ID"             # 404
```

A transaction on an invoice is frozen. The PATCH returns `409` and persists nothing:

```bash
curl -i -X PATCH "$BASE_URL/transactions/$FLIGHT_ID" -H 'Content-Type: application/json' \
  -d '{"total": 999.00}'
curl -s "$BASE_URL/invoices/$INVOICE_ID" | jq '.lines[] | select(.type == "flight") | .total'   # still 220.00
```

DELETE also returns `409`, and the row stays:

```bash
curl -i -X DELETE "$BASE_URL/transactions/$FLIGHT_ID"                        # 409
curl -s -o /dev/null -w '%{http_code}\n' "$BASE_URL/transactions/$FLIGHT_ID"   # 200
```

### 7. Patch an invoice

PATCH replaces the set of transactions and rebuilds the snapshot from the current stored transactions in one write:

```bash
curl -s -X PATCH "$BASE_URL/invoices/$INVOICE_ID" -H 'Content-Type: application/json' \
  -d "{\"transaction_ids\": [\"$FLIGHT_ID\", \"$HOTEL_ID\"]}" \
  | jq '{total, lines: [.lines[].transaction_id]}'   # total 551.70
```

A failed PATCH leaves the invoice unchanged:

```bash
# Unknown id → 404
curl -i -X PATCH "$BASE_URL/invoices/$INVOICE_ID" -H 'Content-Type: application/json' \
  -d "{\"transaction_ids\": [\"$FLIGHT_ID\", \"txn_999999\"]}"

# Mixed currency → 409
curl -i -X PATCH "$BASE_URL/invoices/$INVOICE_ID" -H 'Content-Type: application/json' \
  -d "{\"transaction_ids\": [\"$FLIGHT_ID\", \"$USD_ID\"]}"

curl -s "$BASE_URL/invoices/$INVOICE_ID" | jq .total   # still 551.70
```

### 8. Delete an invoice

Deleting an invoice removes its snapshot and rollup rows. The underlying transactions are kept:

```bash
curl -i -X DELETE "$BASE_URL/invoices/$INVOICE_ID"                            # 204
curl -s -o /dev/null -w '%{http_code}\n' "$BASE_URL/invoices/$INVOICE_ID"       # 404
curl -s -o /dev/null -w '%{http_code}\n' "$BASE_URL/transactions/$FLIGHT_ID"    # 200
```
