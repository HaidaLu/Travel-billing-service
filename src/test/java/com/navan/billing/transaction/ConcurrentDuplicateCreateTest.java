package com.navan.billing.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.navan.billing.support.ApiTestBase;
import com.navan.billing.support.Fixtures;

/** Brief #6. A separate class (and DB file) so other tests' rows can't affect the count. */
class ConcurrentDuplicateCreateTest extends ApiTestBase {

    private static final int REQUESTS = 16;

    @Autowired
    JdbcClient jdbc;

    /**
     * {@value #REQUESTS} threads POST the same flight body. Each thread signals {@code ready} and parks on
     * {@code go}, so all requests are released together and really overlap instead of running one by one.
     */
    @Test
    void t06_overlappingPostsLeaveExactlyOneRow() throws Exception {
        ObjectNode body = Fixtures.seed("flight");
        String externalId = body.path("external_id").asText();
        CountDownLatch ready = new CountDownLatch(REQUESTS);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(REQUESTS);
        List<ResponseEntity<JsonNode>> responses = new ArrayList<>();
        try {
            List<Future<ResponseEntity<JsonNode>>> futures = new ArrayList<>();
            for (int i = 0; i < REQUESTS; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return http.postForEntity("/transactions", body, JsonNode.class);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            for (Future<ResponseEntity<JsonNode>> f : futures) {
                responses.add(f.get(30, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(responses).extracting(r -> r.getStatusCode().value())
                .as("every request ends in 201 or 409, never 500").containsOnly(201, 409);
        List<ResponseEntity<JsonNode>> created = responses.stream()
                .filter(r -> r.getStatusCode().value() == 201).toList();
        assertThat(created).hasSize(1);
        String winnerId = created.get(0).getBody().path("id").asText();
        assertThat(responses).filteredOn(r -> r.getStatusCode().value() == 409)
                .hasSize(REQUESTS - 1)
                .allSatisfy(r -> assertThat(r.getBody().path("existing_id").asText()).isEqualTo(winnerId));

        assertThat(jdbc.sql("SELECT COUNT(*) FROM transactions WHERE external_id = :externalId")
                .param("externalId", externalId).query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM flight_coupons").query(Long.class).single())
                .as("losers left no child rows behind").isEqualTo(1);
    }
}
