package com.navan.billing.invoice.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.navan.billing.invoice.dto.TransactionSnapshot;
import com.navan.billing.support.Fixtures;
import com.navan.billing.transaction.controller.CreateTransactionRequest;
import com.navan.billing.transaction.dto.NewTransaction;
import com.navan.billing.transaction.dto.Transaction;

/** invoice_lines.snapshot_json must decode back to exactly what was encoded, for every type, with one "type" key. */
@JsonTest
class SnapshotCodecTest {

    private static final Instant AT = Instant.parse("2026-10-11T10:00:00Z");

    @Autowired
    ObjectMapper mapper;

    @ParameterizedTest
    @ValueSource(strings = {"flight", "hotel", "rail", "trip_fee", "agent_call_fee"})
    void roundTripsEveryTypeWithASingleTypeKey(String key) throws Exception {
        SnapshotCodec codec = new SnapshotCodec(mapper);
        TransactionSnapshot snapshot = TransactionSnapshot.of("txn_1", stored(key));

        String json = codec.encode(snapshot);

        assertThat(json.split("\"type\":", -1)).as("exactly one \"type\" key in " + json).hasSize(2);
        assertThat(codec.decode(json))
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(snapshot);
        JsonNode tree = mapper.readTree(json);
        assertThat(tree.path("total").decimalValue()).isEqualByComparingTo(snapshot.total());
        assertThat(tree.path("metadata").isObject()).isTrue();
    }

    private Transaction stored(String key) throws Exception {
        NewTransaction t = mapper.treeToValue(Fixtures.seed(key), CreateTransactionRequest.class).toNewTransaction();
        return new Transaction(1, t.type(), t.externalId(), t.currency(), t.occurredAt(), t.total(), t.taxLines(),
                t.feeLines(), t.metadata(), AT, AT);
    }
}
