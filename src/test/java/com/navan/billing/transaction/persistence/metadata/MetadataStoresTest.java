package com.navan.billing.transaction.persistence.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.navan.billing.support.Fixtures;
import com.navan.billing.support.SqliteTestBase;
import com.navan.billing.transaction.api.CreateTransactionRequest;
import com.navan.billing.transaction.domain.NewTransaction;
import com.navan.billing.transaction.domain.Transaction;
import com.navan.billing.transaction.domain.metadata.FlightCoupon;
import com.navan.billing.transaction.domain.metadata.FlightMetadata;
import com.navan.billing.transaction.domain.metadata.HotelMetadata;
import com.navan.billing.transaction.domain.metadata.RailLeg;
import com.navan.billing.transaction.domain.metadata.RailMetadata;
import com.navan.billing.transaction.domain.metadata.TransactionMetadata;
import com.navan.billing.transaction.persistence.TransactionRepository;

/**
 * Step 2: each type's metadata goes into its own child tables and loads back equal to what was sent. Writes go
 * through {@link TransactionRepository} because the child tables reference the header row.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MetadataStoresTest extends SqliteTestBase {

    private static final Instant NOW = Instant.parse("2026-10-11T10:00:00Z");
    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final List<String> METADATA_TABLES = List.of("flight_details", "flight_coupons",
            "hotel_details", "hotel_extras", "rail_legs", "navan_fee_details");

    @Autowired
    TransactionRepository repository;
    @Autowired
    MetadataStoreRegistry stores;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    ObjectMapper mapper;

    @ParameterizedTest
    @ValueSource(strings = {"flight", "hotel", "rail", "trip_fee", "agent_call_fee", "duplicate_flight"})
    void seedMetadataRoundTripsFieldForField(String key) {
        NewTransaction input = seed(key, body -> { });

        Transaction stored = insertAndLoad(input);

        assertSameMetadata(stored.metadata(), input.metadata());
    }

    @Test
    void typeSpecificFieldsAreRealColumnsAndRows() {
        long flight = insertAndLoad(seed("flight", body -> { })).id();
        long hotel = insertAndLoad(seed("hotel", body -> { })).id();
        long rail = insertAndLoad(seed("rail", body -> { })).id();

        assertThat(rows("SELECT origin || '-' || destination FROM flight_details WHERE transaction_id = :id",
                flight, String.class)).containsExactly("TXL-LHR");
        assertThat(rows("SELECT fare_minor FROM flight_details WHERE transaction_id = :id", flight, Long.class))
                .containsExactly(18000L);
        assertThat(rows("SELECT flight_number FROM flight_coupons WHERE transaction_id = :id ORDER BY position",
                flight, String.class)).containsExactly("BA982");

        assertThat(rows("SELECT name || ' | ' || street || ', ' || city || ', ' || country FROM hotel_details"
                + " WHERE transaction_id = :id", hotel, String.class))
                .containsExactly("Hotel Mitte London | 12 Kingsway, London, GB");
        assertThat(rows("SELECT room_rate_minor FROM hotel_details WHERE transaction_id = :id", hotel, Long.class))
                .containsExactly(14000L);
        assertThat(rows("SELECT name FROM hotel_extras WHERE transaction_id = :id ORDER BY position",
                hotel, String.class)).containsExactly("Breakfast");

        assertThat(rows("SELECT origin || ' -> ' || destination FROM rail_legs WHERE transaction_id = :id"
                + " ORDER BY position", rail, String.class))
                .containsExactly("London St Pancras -> Lille Europe", "Lille Europe -> Paris Nord");
    }

    @Test
    void optionalFieldsRoundTripAsNullAndListsKeepOrder() {
        NewTransaction roundTrip = seed("flight", body -> ((ArrayNode) body.at("/metadata/coupons")).addObject()
                .put("flight_number", "BA987")
                .put("from", "LHR")
                .put("to", "TXL")
                .put("departure", "2026-03-14T18:00:00Z")
                .put("arrival", "2026-03-14T21:00:00Z"));
        List<FlightCoupon> coupons = ((FlightMetadata) insertAndLoad(roundTrip).metadata()).coupons();
        assertThat(coupons).extracting(FlightCoupon::flightNumber).containsExactly("BA982", "BA987");
        assertThat(coupons.get(1).carrier()).isNull();

        NewTransaction bareLeg = seed("rail", body ->
                ((ObjectNode) body.at("/metadata/legs/0")).remove(List.of("train_number", "class")));
        RailLeg leg = ((RailMetadata) insertAndLoad(bareLeg).metadata()).legs().get(0);
        assertThat(leg.trainNumber()).isNull();
        assertThat(leg.travelClass()).isNull();

        NewTransaction noExtras = seed("hotel", body -> ((ObjectNode) body.get("metadata")).remove("extras"));
        assertThat(((HotelMetadata) insertAndLoad(noExtras).metadata()).extras()).isEmpty();
    }

    /** PATCH replaces metadata as delete + insert, so both halves must work on their own. */
    @ParameterizedTest
    @ValueSource(strings = {"flight", "hotel", "rail", "trip_fee"})
    void deleteRemovesOnlyMetadataRowsAndInsertRestoresThem(String key) {
        NewTransaction input = seed(key, body -> { });
        long id = insertAndLoad(input).id();
        MetadataStore<TransactionMetadata> store = stores.forType(input.type());

        tx.executeWithoutResult(s -> store.delete(id));

        assertThat(metadataRows(id)).isZero();
        assertThat(rows("SELECT id FROM transactions WHERE id = :id", id, Long.class)).containsExactly(id);

        Transaction restored = tx.execute(s -> {
            store.insert(id, input.metadata());
            return repository.findById(id).orElseThrow();
        });
        assertSameMetadata(restored.metadata(), input.metadata());
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    /** A seed payload with a unique external_id and optional edits, bound through the real wire contract. */
    private NewTransaction seed(String key, Consumer<ObjectNode> edit) {
        ObjectNode body = Fixtures.seed(key);
        body.put("external_id", key + "-" + SEQ.incrementAndGet());
        edit.accept(body);
        try {
            return mapper.treeToValue(body, CreateTransactionRequest.class).toNewTransaction();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Transaction insertAndLoad(NewTransaction input) {
        return tx.execute(s -> {
            long id = repository.insertHeaderIfAbsent(input, NOW).orElseThrow();
            repository.insertChildren(id, input);
            return repository.findById(id).orElseThrow();
        });
    }

    private static void assertSameMetadata(TransactionMetadata actual, TransactionMetadata expected) {
        assertThat(actual)
                .isInstanceOf(expected.getClass())
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(expected);
    }

    private <T> List<T> rows(String sql, long transactionId, Class<T> type) {
        return jdbc.sql(sql).param("id", transactionId).query(type).list();
    }

    private long metadataRows(long transactionId) {
        return METADATA_TABLES.stream()
                .mapToLong(table -> jdbc.sql("SELECT COUNT(*) FROM " + table + " WHERE transaction_id = :id")
                        .param("id", transactionId).query(Long.class).single())
                .sum();
    }
}
