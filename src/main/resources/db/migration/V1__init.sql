--
-- Conventions
--   money      : INTEGER minor units at fixed scale 2 (220.00 EUR -> 22000). See ARCHITECTURE.md.
--   rates      : TEXT decimal string ('0.07'), never REAL.
--   timestamps : fixed-width UTC TEXT 'YYYY-MM-DDTHH:MM:SS.sssZ' (DbTime) so string order = time order.
--                Dates (hotel check-in/out) : 'YYYY-MM-DD' TEXT.
--   ordering   : child rows carry `position` so GET round-trips list order.

-- ─── Transactions ────────────────────────────────────────────────────────────

CREATE TABLE transactions (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    external_id  TEXT    NOT NULL,
    type         TEXT    NOT NULL CHECK (type IN ('flight', 'hotel', 'rail', 'navan_fee')),
    currency     TEXT    NOT NULL CHECK (length(currency) = 3),
    occurred_at  TEXT    NOT NULL,
    total_minor  INTEGER NOT NULL,
    created_at   TEXT    NOT NULL,
    updated_at   TEXT    NOT NULL
);
-- Duplicate identity lives HERE, not in application code.
CREATE UNIQUE INDEX ux_transactions_external_id ON transactions (external_id);

CREATE TABLE transaction_tax_lines (
    transaction_id INTEGER NOT NULL REFERENCES transactions (id) ON DELETE CASCADE,
    position       INTEGER NOT NULL,
    name           TEXT    NOT NULL,
    rate           TEXT,
    amount_minor   INTEGER NOT NULL,
    PRIMARY KEY (transaction_id, position)
);

CREATE TABLE transaction_fee_lines (
    transaction_id INTEGER NOT NULL REFERENCES transactions (id) ON DELETE CASCADE,
    position       INTEGER NOT NULL,
    name           TEXT    NOT NULL,
    amount_minor   INTEGER NOT NULL,
    PRIMARY KEY (transaction_id, position)
);

-- ─── Type-specific details (one table family per type) ──────────────────────

CREATE TABLE flight_details (
    transaction_id INTEGER PRIMARY KEY REFERENCES transactions (id) ON DELETE CASCADE,
    origin         TEXT    NOT NULL,
    destination    TEXT    NOT NULL,
    fare_minor     INTEGER NOT NULL
);

CREATE TABLE flight_coupons (
    transaction_id INTEGER NOT NULL REFERENCES transactions (id) ON DELETE CASCADE,
    position       INTEGER NOT NULL,
    flight_number  TEXT    NOT NULL,
    carrier        TEXT,
    from_airport   TEXT    NOT NULL,
    to_airport     TEXT    NOT NULL,
    departure_at   TEXT    NOT NULL,
    arrival_at     TEXT    NOT NULL,
    PRIMARY KEY (transaction_id, position)
);
CREATE INDEX ix_flight_coupons_flight_number ON flight_coupons (flight_number);

CREATE TABLE hotel_details (
    transaction_id       INTEGER PRIMARY KEY REFERENCES transactions (id) ON DELETE CASCADE,
    name                 TEXT    NOT NULL,
    street               TEXT    NOT NULL,
    city                 TEXT    NOT NULL,
    country              TEXT    NOT NULL,
    check_in             TEXT    NOT NULL,
    check_out            TEXT    NOT NULL,
    nights               INTEGER NOT NULL,
    room_rate_minor      INTEGER NOT NULL
);

CREATE TABLE hotel_extras (
    transaction_id INTEGER NOT NULL REFERENCES transactions (id) ON DELETE CASCADE,
    position       INTEGER NOT NULL,
    name           TEXT    NOT NULL,
    amount_minor   INTEGER NOT NULL,
    PRIMARY KEY (transaction_id, position)
);

CREATE TABLE rail_legs (
    transaction_id INTEGER NOT NULL REFERENCES transactions (id) ON DELETE CASCADE,
    position       INTEGER NOT NULL,
    origin         TEXT    NOT NULL,
    destination    TEXT    NOT NULL,
    departure_at   TEXT    NOT NULL,
    arrival_at     TEXT    NOT NULL,
    train_number   TEXT,
    travel_class   TEXT,
    PRIMARY KEY (transaction_id, position)
);

CREATE TABLE navan_fee_details (
    transaction_id      INTEGER PRIMARY KEY REFERENCES transactions (id) ON DELETE CASCADE,
    fee_kind            TEXT NOT NULL CHECK (fee_kind IN ('trip_fee', 'agent_call_fee')),
    description         TEXT,
    related_external_id TEXT
);
CREATE INDEX ix_navan_fee_related ON navan_fee_details (related_external_id);

-- ─── Invoices (frozen snapshots) ─────────────────────────────────────────────

CREATE TABLE invoices (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    currency    TEXT    NOT NULL,
    total_minor INTEGER NOT NULL,
    created_at  TEXT    NOT NULL,
    updated_at  TEXT    NOT NULL
);

CREATE TABLE invoice_lines (
    invoice_id     INTEGER NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    position       INTEGER NOT NULL,
    -- Reference only (NOT a live join): used for "is this txn invoiced?" and as a DB-level
    -- backstop that refuses to delete an invoiced transaction. The money lives in snapshot_json.
    transaction_id INTEGER NOT NULL REFERENCES transactions (id),
    type           TEXT    NOT NULL,
    external_id    TEXT    NOT NULL,
    total_minor    INTEGER NOT NULL,
    snapshot_json  TEXT    NOT NULL,
    PRIMARY KEY (invoice_id, position)
);
-- A transaction is on at most one invoice (and so at most once per invoice). Also serves the
-- "is this txn invoiced?" lookup.
CREATE UNIQUE INDEX ux_invoice_lines_transaction_id ON invoice_lines (transaction_id);

CREATE TABLE invoice_tax_rollup (
    invoice_id   INTEGER NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    position     INTEGER NOT NULL,
    name         TEXT    NOT NULL,
    rate         TEXT,
    amount_minor INTEGER NOT NULL,
    PRIMARY KEY (invoice_id, position)
);

CREATE TABLE invoice_fee_rollup (
    invoice_id   INTEGER NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    position     INTEGER NOT NULL,
    name         TEXT    NOT NULL,
    amount_minor INTEGER NOT NULL,
    PRIMARY KEY (invoice_id, position)
);
