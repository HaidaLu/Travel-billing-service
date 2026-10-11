# Architecture

## Seams

```
HTTP          transaction.api / invoice.api            parse, validate shape, map status codes
Use cases     transaction.service / invoice.service    @Transactional boundary, business rules
Identity      transaction.identity                     one row per external_id (DB-enforced)
Persistence   transaction.persistence (+ metadata/*)   SQL for header, tax/fee lines, per-type tables
              invoice.persistence                      SQL for invoice header, snapshot lines, rollups
Snapshot      invoice.snapshot                         pure: transactions → invoice draft + rollups
```

Dependency direction: invoice → transaction. Transaction asks "is this invoiced?" through the
`InvoiceReferenceChecker` interface it owns.

## `external_id` uniqueness & concurrent POSTs
TODO

## Invoice snapshot
TODO

## Tables & models
TODO: taxes vs fees, flight coupons/OD/flight number, hotel name+address, rail legs, navan_fee kinds.
Why child tables per type rather than a JSON column.

## Money
TODO

## High volume
TODO: what breaks first under concurrent PATCH + duplicate POST + large lists, and what was done.
