package com.navan.billing.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.navan.billing.support.ApiTestBase;
import com.navan.billing.support.Fixtures;

/**
 * Brief §4: "Two overlapping PATCH/DELETE on the same id must not tear a row." Last-write-wins is fine; a mix of two
 * writes is not. Own class (and DB file), like t06.
 */
class ConcurrentPatchDeleteTest extends ApiTestBase {

    private static final int WRITERS = 12;
    private static final int ROUNDS = 5;

    @Autowired
    JdbcClient jdbc;

    /**
     * Writer i sends total i+1 and lines all tagged "w{i}"; the stored row must be one writer's, whole. A race may
     * not fire in a given run, so it is repeated: with PATCH made non-atomic, single rounds caught it 2 times in 3.
     */
    @Test
    void overlappingPatchesNeverMixTwoWrites() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            String id = create("patch-race-" + round);

            List<ResponseEntity<JsonNode>> responses = runTogether(i -> patch(id, writer(i)));

            assertThat(responses).extracting(r -> r.getStatusCode().value()).as("round " + round).containsOnly(200);
            JsonNode stored = http.getForEntity("/transactions/" + id, JsonNode.class).getBody();
            int winner = stored.path("total").decimalValue().intValueExact() - 1;
            assertThat(names(stored.path("tax_lines"))).as("round %d: tax lines all from the writer whose total won", round)
                    .containsExactly(tag(winner, "a"), tag(winner, "b"), tag(winner, "c"));
            assertThat(names(stored.path("fee_lines"))).as("round " + round).containsExactly(tag(winner, "fee"));
            assertThat(childRows("transaction_tax_lines", id)).as("round %d: no leftovers", round).isEqualTo(3);
            assertThat(childRows("transaction_fee_lines", id)).as("round " + round).isEqualTo(1);
        }
    }

    /**
     * One DELETE races the PATCHes: each PATCH lands wholly before it (200) or finds nothing (404). Which of those
     * happens is up to the scheduler (all 404 is a valid run), so only the set of allowed outcomes is asserted.
     */
    @Test
    void patchesRacingADeleteLeaveNoOrphans() throws Exception {
        String id = create("delete-race");
        int deleter = WRITERS / 2;

        List<ResponseEntity<JsonNode>> responses = runTogether(i -> i == deleter
                ? http.exchange("/transactions/" + id, HttpMethod.DELETE, null, JsonNode.class)
                : patch(id, writer(i)));

        assertThat(responses.remove(deleter).getStatusCode().value()).isEqualTo(204);
        assertThat(responses).extracting(r -> r.getStatusCode().value()).isSubsetOf(200, 404);
        assertThat(http.getForEntity("/transactions/" + id, JsonNode.class).getStatusCode().value()).isEqualTo(404);
        for (String table : List.of("transaction_tax_lines", "transaction_fee_lines", "navan_fee_details")) {
            assertThat(childRows(table, id)).as(table).isZero();
        }
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    /** Same release mechanism as t06: every thread parks on one latch, then all requests go at once. */
    private List<ResponseEntity<JsonNode>> runTogether(IntFunction<ResponseEntity<JsonNode>> request)
            throws Exception {
        CountDownLatch ready = new CountDownLatch(WRITERS);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(WRITERS);
        try {
            List<Future<ResponseEntity<JsonNode>>> futures = new ArrayList<>();
            for (int i = 0; i < WRITERS; i++) {
                int writer = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return request.apply(writer);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<ResponseEntity<JsonNode>> responses = new ArrayList<>();
            for (Future<ResponseEntity<JsonNode>> f : futures) {
                responses.add(f.get(30, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            pool.shutdownNow();
        }
    }

    private String create(String externalId) {
        return http.postForEntity("/transactions", Fixtures.seed("trip_fee").put("external_id", externalId),
                JsonNode.class).getBody().path("id").asText();
    }

    private ResponseEntity<JsonNode> patch(String id, Map<String, Object> body) {
        return http.exchange("/transactions/" + id, HttpMethod.PATCH, new HttpEntity<>(body), JsonNode.class);
    }

    private static Map<String, Object> writer(int i) {
        return Map.of(
                "total", new BigDecimal(i + 1),
                "tax_lines", List.of(line(tag(i, "a")), line(tag(i, "b")), line(tag(i, "c"))),
                "fee_lines", List.of(line(tag(i, "fee"))));
    }

    private static Map<String, Object> line(String name) {
        return Map.of("name", name, "amount", new BigDecimal("0.50"));
    }

    private static String tag(int writer, String suffix) {
        return "w" + writer + "-" + suffix;
    }

    private static List<String> names(JsonNode lines) {
        List<String> names = new ArrayList<>();
        lines.forEach(line -> names.add(line.path("name").asText()));
        return names;
    }

    private long childRows(String table, String publicId) {
        return jdbc.sql("SELECT COUNT(*) FROM " + table + " WHERE transaction_id = :id")
                .param("id", Long.parseLong(publicId.substring("txn_".length())))
                .query(Long.class).single();
    }
}
