# Travel billing — transactions & snapshot invoices

Java 17 · Spring Boot 3.5 · Spring JDBC · SQLite (file) · Flyway

## Run

```bash
./mvnw spring-boot:run
```

Listens on `:8080` and creates `./billing.db`. Override with `PORT` / `BILLING_DB_PATH`.

## Test

```bash
./mvnw test
```

## API decisions

| Topic                                               | Decision                                      | 
|-----------------------------------------------------|-----------------------------------------------|
| Duplicate `external_id`                             | 409 + `existing_id`                           |
| Same txn id twice on one invoice                    | 409                                           |  
| Txn already on *another* invoice                    | 409                                           | 
| `limit > 100`                                       | 400                                           | 
| PATCH sends `type` / `external_id` / `currency`     | 400                                           |  
| List item shape                                     | fully hydrated (≤100 rows; note the N+1 cost) |  
| Total reconciliation (total vs fare + taxes + fees) | don't enforce                                 |

## Example curls

Set the base URL:

```bash
BASE_URL=http://localhost:8080
```

### 1. Create transactions

**Flight**

```bash
curl -i -X POST "$BASE_URL/transactions" \
  -H 'Content-Type: application/json' \
  -d '{
    "type": "flight",
    "external_id": "flight-TXL-LHR-2026-03-11",
    "currency": "EUR",
    "occurred_at": "2026-03-11T10:00:00Z",
    "total": 220.00,
    "tax_lines": [],
    "fee_lines": [],
    "metadata": {}
  }'
```

**Hotel**

```bash
curl -i -X POST "$BASE_URL/transactions" \
  -H 'Content-Type: application/json' \
  -d '{
    "type": "hotel",
    "external_id": "hotel-BER-2026-03-11",
    "currency": "EUR",
    "occurred_at": "2026-03-11T12:00:00Z",
    "total": 331.70,
    "tax_lines": [
      {"name": "VAT", "rate": "0.07", "amount": 21.70}
    ],
    "fee_lines": [],
    "metadata": {}
  }'
```

**Rail**

```bash
curl -i -X POST "$BASE_URL/transactions" \
  -H 'Content-Type: application/json' \
  -d '{
    "type": "rail",
    "external_id": "rail-BER-HAM-2026-03-12",
    "currency": "EUR",
    "occurred_at": "2026-03-12T09:00:00Z",
    "total": 94.00,
    "tax_lines": [],
    "fee_lines": [],
    "metadata": {}
  }'
```

**Navan trip fee**

```bash
curl -i -X POST "$BASE_URL/transactions" \
  -H 'Content-Type: application/json' \
  -d '{
    "type": "navan_fee",
    "external_id": "fee-trip-2026-03-11",
    "currency": "EUR",
    "occurred_at": "2026-03-11T13:00:00Z",
    "total": 25.00,
    "tax_lines": [],
    "fee_lines": [],
    "metadata": {"fee_kind": "trip_fee"}
  }'
```

**Navan agent call fee**

```bash
curl -i -X POST "$BASE_URL/transactions" \
  -H 'Content-Type: application/json' \
  -d '{
    "type": "navan_fee",
    "external_id": "fee-agent-call-2026-03-11",
    "currency": "EUR",
    "occurred_at": "2026-03-11T14:00:00Z",
    "total": 15.00,
    "tax_lines": [],
    "fee_lines": [],
    "metadata": {"fee_kind": "agent_call_fee"}
  }'
```

For each successful request, note the returned transaction `id`. The examples below use placeholder IDs; replace them with IDs returned by your API.

### 2. Duplicate transaction creation

Submitting the same `external_id` again must not create a second transaction.

```bash
curl -i -X POST "$BASE_URL/transactions" \
  -H 'Content-Type: application/json' \
  -d '{
    "type": "flight",
    "external_id": "flight-TXL-LHR-2026-03-11",
    "currency": "EUR",
    "occurred_at": "2026-03-11T10:00:00Z",
    "total": 220.00,
    "tax_lines": [],
    "fee_lines": [],
    "metadata": {}
  }'
```

Expected: `409 Conflict` under this API's duplicate policy.

### 3. Get and list transactions

Get one transaction:

```bash
curl -i "$BASE_URL/transactions/1"
```

List transactions with pagination:

```bash
curl -i "$BASE_URL/transactions?limit=50"
```

Request a later page using the cursor returned by the previous response:

```bash
curl -i "$BASE_URL/transactions?limit=50&cursor=REPLACE_WITH_NEXT_CURSOR"
```

### 4. Patch and delete a transaction

Patch a transaction that has not been added to an invoice. Adjust the request body to match the fields supported by your PATCH endpoint.

```bash
curl -i -X PATCH "$BASE_URL/transactions/6" \
  -H 'Content-Type: application/json' \
  -d '{"total": 30.00}'
```

Delete an un-invoiced transaction:

```bash
curl -i -X DELETE "$BASE_URL/transactions/6"
```

A transaction that belongs to an invoice cannot be modified or deleted:

```bash
curl -i -X PATCH "$BASE_URL/transactions/1" \
  -H 'Content-Type: application/json' \
  -d '{"total": 225.00}'
```

Expected: `409 Conflict`, with no changes persisted.

```bash
curl -i -X DELETE "$BASE_URL/transactions/1"
```

Expected: `409 Conflict`.

### 5. Create an invoice

Replace the example IDs with existing transaction IDs. All transactions in one invoice must use the same currency.

```bash
curl -i -X POST "$BASE_URL/invoices" \
  -H 'Content-Type: application/json' \
  -d '{
    "transaction_ids": ["1", "2", "3", "4", "5"]
  }'
```

Record the returned invoice `id` for the next requests.

To create an invoice with a single transaction, use:

```bash
curl -i -X POST "$BASE_URL/invoices" \
  -H 'Content-Type: application/json' \
  -d '{"transaction_id": "1"}'
```

### 6. Get and list invoices

Get one invoice:

```bash
curl -i "$BASE_URL/invoices/1"
```

List invoices:

```bash
curl -i "$BASE_URL/invoices?limit=50"
```

Request the next page using the returned cursor:

```bash
curl -i "$BASE_URL/invoices?limit=50&cursor=REPLACE_WITH_NEXT_CURSOR"
```

### 7. Patch an invoice

Replace the invoice's transaction list. The invoice snapshot and rollups should be rebuilt atomically from the current transaction data.

```bash
curl -i -X PATCH "$BASE_URL/invoices/1" \
  -H 'Content-Type: application/json' \
  -d '{
    "transaction_ids": ["1", "2"]
  }'
```

If any transaction ID does not exist, expect `404 Not Found`. If the transactions have different currencies, expect `409 Conflict`; the existing invoice must remain unchanged.

### 8. Delete an invoice

```bash
curl -i -X DELETE "$BASE_URL/invoices/1"
```

Deleting an invoice removes its snapshot and rollup rows but does not delete the underlying transactions.

### 9. Duplicate transaction IDs within one invoice

The same transaction ID must not appear twice in one invoice. This API uses `409 Conflict` for duplicate IDs.

```bash
curl -i -X POST "$BASE_URL/invoices" \
  -H 'Content-Type: application/json' \
  -d '{
    "transaction_ids": ["1", "1"]
  }'
```

Expected: `409 Conflict`.