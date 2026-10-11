package com.navan.billing.transaction.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.navan.billing.common.time.DbTime;
import com.navan.billing.support.Fixtures;
import com.navan.billing.support.SqliteTestBase;
import com.navan.billing.transaction.controller.CreateTransactionRequest;
import com.navan.billing.transaction.domain.FeeLine;
import com.navan.billing.transaction.domain.NewTransaction;
import com.navan.billing.transaction.domain.TaxLine;
import com.navan.billing.transaction.domain.Transaction;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TransactionRepositoryTest extends SqliteTestBase {

    private static final Instant NOW = Instant.parse("2026-10-11T10:00:00.123456Z");

    @Autowired
    TransactionRepository repository;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    ObjectMapper mapper;

    @Test
    void tripFeeRoundTripsFieldForField() {
        NewTransaction input = tripFee("rt-1", body -> { });

        Transaction stored = tx.execute(s -> {
            long id = repository.insertHeaderIfAbsent(input, NOW).orElseThrow();
            repository.insertChildren(id, input);
            return repository.findById(id).orElseThrow();
        });

        assertThat(stored)
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFields("id", "createdAt", "updatedAt")
                .isEqualTo(input);
        assertThat(stored.total()).isEqualByComparingTo("25.00");
        assertThat(stored.createdAt()).isEqualTo(Instant.parse("2026-10-11T10:00:00.123Z"));
        assertThat(stored.updatedAt()).isEqualTo(stored.createdAt());
    }

    @Test
    void taxAndFeeLinesKeepOrderOptionalRateAndAreStoredSeparately() {
        NewTransaction input = tripFee("lines-1", body -> {
            body.set("tax_lines", mapper.valueToTree(List.of(
                    new TaxLine("VAT", new BigDecimal("0.190"), new BigDecimal("1.90")),
                    new TaxLine("City tax", null, new BigDecimal("2.00")))));
            body.set("fee_lines", mapper.valueToTree(List.of(
                    new FeeLine("Zeta fee", new BigDecimal("1.00")),
                    new FeeLine("Alpha fee", new BigDecimal("2.50")))));
        });

        Transaction stored = insertAndLoad(input);

        assertThat(stored.taxLines()).extracting(TaxLine::name).containsExactly("VAT", "City tax");
        assertThat(stored.taxLines().get(0).rate()).isEqualByComparingTo("0.19");
        assertThat(stored.taxLines().get(1).rate()).isNull();
        assertThat(stored.feeLines()).extracting(FeeLine::name).containsExactly("Zeta fee", "Alpha fee");
        assertThat(count("transaction_tax_lines", stored.id())).isEqualTo(2);
        assertThat(count("transaction_fee_lines", stored.id())).isEqualTo(2);
    }

    @Test
    void duplicateExternalIdInsertsNothingAndExposesExistingId() {
        NewTransaction first = tripFee("dup-1", body -> { });
        long id = insertAndLoad(first).id();

        NewTransaction again = tripFee("dup-1", body -> body.put("total", 99.0));
        var result = tx.execute(s -> repository.insertHeaderIfAbsent(again, NOW));

        assertThat(result).isEmpty();
        OptionalLong existing = tx.execute(s -> repository.findIdByExternalId("dup-1"));
        assertThat(existing).hasValue(id);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM transactions WHERE external_id = 'dup-1'")
                .query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void headerLinesAndMetadataAreAllOrNothing() {
        NewTransaction input = tripFee("atomic-1", body -> body.set("tax_lines", mapper.valueToTree(List.of(
                new TaxLine("VAT", null, BigDecimal.ONE)))));
        AtomicLong writtenId = new AtomicLong();

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            long id = repository.insertHeaderIfAbsent(input, NOW).orElseThrow();
            repository.insertChildren(id, input);
            writtenId.set(id);
            throw new IllegalStateException("boom after children were written");
        })).hasMessageContaining("boom");

        OptionalLong survivor = tx.execute(s -> repository.findIdByExternalId("atomic-1"));
        assertThat(survivor).isEmpty();
        assertThat(count("transaction_tax_lines", writtenId.get())).isZero();
        assertThat(count("navan_fee_details", writtenId.get())).isZero();
    }

    @Test
    void replaceLinesSwapsTheWholeList() {
        long id = insertAndLoad(tripFee("replace-1", body -> body.set("tax_lines", mapper.valueToTree(List.of(
                new TaxLine("Old A", null, BigDecimal.ONE), new TaxLine("Old B", null, BigDecimal.ONE)))))).id();

        Transaction after = tx.execute(s -> {
            repository.replaceTaxLines(id, List.of(new TaxLine("New", new BigDecimal("0.07"), new BigDecimal("0.70"))));
            repository.replaceFeeLines(id, List.of());
            return repository.findById(id).orElseThrow();
        });

        assertThat(after.taxLines()).extracting(TaxLine::name).containsExactly("New");
        assertThat(after.feeLines()).isEmpty();
    }

    @Test
    void updateHeaderOverwritesMutableColumnsAndBumpsUpdatedAt() {
        long id = insertAndLoad(tripFee("update-1", body -> { })).id();
        Instant later = NOW.plusSeconds(60);
        Instant newOccurredAt = Instant.parse("2026-03-12T00:00:00Z");

        Transaction after = tx.execute(s -> {
            assertThat(repository.updateHeader(id, newOccurredAt, new BigDecimal("30.10"), later)).isEqualTo(1);
            return repository.findById(id).orElseThrow();
        });

        assertThat(after.total()).isEqualByComparingTo("30.10");
        assertThat(after.occurredAt()).isEqualTo(newOccurredAt);
        assertThat(after.updatedAt()).isAfter(after.createdAt());
        Integer missingRows = tx.execute(s -> repository.updateHeader(999_999, newOccurredAt, BigDecimal.ONE, later));
        assertThat(missingRows).isZero();
    }

    @Test
    void deleteCascadesToLinesAndMetadata() {
        NewTransaction input = tripFee("delete-1", body -> body.set("tax_lines", mapper.valueToTree(List.of(
                new TaxLine("VAT", null, BigDecimal.ONE)))));
        long id = insertAndLoad(input).id();

        Integer deleted = tx.execute(s -> repository.deleteById(id));
        assertThat(deleted).isEqualTo(1);

        Optional<Transaction> gone = tx.execute(s -> repository.findById(id));
        assertThat(gone).isEmpty();
        assertThat(count("transaction_tax_lines", id)).isZero();
        assertThat(count("transaction_fee_lines", id)).isZero();
        assertThat(count("navan_fee_details", id)).isZero();
    }

    @Test
    void timestampsAreStoredFixedWidthSoTextOrderIsTimeOrder() {
        long id = insertAndLoad(tripFee("ts-1", body -> body.put("occurred_at", "2026-03-11T06:40:00Z"))).id();

        String raw = jdbc.sql("SELECT occurred_at FROM transactions WHERE id = :id")
                .param("id", id).query(String.class).single();

        assertThat(raw).isEqualTo("2026-03-11T06:40:00.000Z");
        assertThat(DbTime.format(Instant.parse("2026-03-11T06:40:00.5Z")))
                .isGreaterThan(DbTime.format(Instant.parse("2026-03-11T06:40:00Z")));
    }

    @Test
    void repositoryRefusesToRunOutsideATransaction() {
        assertThatThrownBy(() -> repository.findIdByExternalId("anything"))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    /** The trip_fee seed with a unique external_id and optional edits, bound through the real wire contract. */
    private NewTransaction tripFee(String externalId, Consumer<ObjectNode> edit) {
        ObjectNode body = Fixtures.seed("trip_fee");
        body.put("external_id", externalId);
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

    private long count(String table, long transactionId) {
        return jdbc.sql("SELECT COUNT(*) FROM " + table + " WHERE transaction_id = :id")
                .param("id", transactionId).query(Long.class).single();
    }
}
