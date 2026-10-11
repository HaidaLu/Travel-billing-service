package com.navan.billing.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.navan.billing.support.ApiTestBase;
import com.navan.billing.support.Fixtures;

/** Required tests from the brief; the number in each name matches the brief's numbered list. */
class TransactionApiTest extends ApiTestBase {

    @Autowired
    JdbcClient jdbc;

    /** All tests share one DB file, so each uses its own external_ids (t02 keeps the fixture's on purpose). */
    @Test
    void t01_createsEveryTypeAndRoundTripsMetadata() {
        Map<String, String> ids = new LinkedHashMap<>();
        for (String key : List.of("flight", "hotel", "rail", "trip_fee", "agent_call_fee")) {
            ResponseEntity<JsonNode> created = post(seed(key, "t01-" + key));

            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            String id = created.getBody().path("id").asText();
            assertThat(id).startsWith("txn_");
            assertThat(created.getBody()).as("POST returns the stored record").isEqualTo(get(id).getBody());
            ids.put(key, id);
        }

        JsonNode flight = get(ids.get("flight")).getBody();
        assertThat(flight.path("type").asText()).isEqualTo("flight");
        assertThat(flight.path("total").decimalValue()).isEqualByComparingTo("220.00");
        assertThat(flight.at("/tax_lines/0/name").asText()).isEqualTo("Airport tax");
        assertThat(flight.at("/fee_lines/0/name").asText()).isEqualTo("YQ");
        assertThat(flight.at("/metadata/coupons/0/flight_number").asText()).isEqualTo("BA982");
        assertThat(flight.path("metadata").has("type")).as("type appears once, at the top level").isFalse();

        JsonNode hotel = get(ids.get("hotel")).getBody();
        assertThat(hotel.at("/metadata/name").asText()).isEqualTo("Hotel Mitte London");
        assertThat(hotel.at("/metadata/address/city").asText()).isEqualTo("London");
        assertThat(hotel.at("/tax_lines/0/rate").decimalValue()).isEqualByComparingTo("0.07");

        JsonNode rail = get(ids.get("rail")).getBody();
        assertThat(rail.at("/metadata/legs").size()).isEqualTo(2);
        assertThat(rail.at("/metadata/legs/1/destination").asText()).isEqualTo("Paris Nord");
        assertThat(rail.at("/metadata/legs/0/class").asText()).isEqualTo("standard");

        assertThat(get(ids.get("trip_fee")).getBody().at("/metadata/fee_kind").asText()).isEqualTo("trip_fee");
        assertThat(get(ids.get("agent_call_fee")).getBody().at("/metadata/fee_kind").asText())
                .isEqualTo("agent_call_fee");
    }

    /** Uses the fixture's own external_id: flight, then duplicate_flight. Policy: 409 + existing_id. */
    @Test
    void t02_duplicateExternalIdDoesNotInsertSecondRow() {
        String firstId = post(Fixtures.seed("flight")).getBody().path("id").asText();

        ResponseEntity<JsonNode> duplicate = post(Fixtures.seed("duplicate_flight"));

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody().path("code").asText()).isEqualTo("duplicate_external_id");
        assertThat(duplicate.getBody().path("existing_id").asText()).isEqualTo(firstId);
        assertThat(rows("flight-TXL-LHR-2026-03-11")).isEqualTo(1);
        assertThat(get(firstId).getBody().at("/metadata/coupons").size())
                .as("the stored flight is untouched by the duplicate's empty coupons").isEqualTo(1);
    }

    @Test
    void unknownTypeOrFeeKindIs400AndStoresNothing() {
        ObjectNode cruise = seed("flight", "bad-type-1").put("type", "cruise");
        ObjectNode loungeFee = seed("trip_fee", "bad-kind-1");
        ((ObjectNode) loungeFee.get("metadata")).put("fee_kind", "lounge_fee");

        assertThat(post(cruise).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post(loungeFee).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rows("bad-type-1") + rows("bad-kind-1")).isZero();
    }

    /** The header is inserted before the bad tax line is reached, so this proves the whole create rolls back. */
    @Test
    void amountWithMoreThanTwoDecimalsIs400AndRollsBackTheHeader() {
        ObjectNode body = seed("trip_fee", "three-decimals-1");
        body.putArray("tax_lines").addObject().put("name", "VAT").put("amount", 1.005);

        ResponseEntity<JsonNode> response = post(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rows("three-decimals-1")).isZero();
    }

    @Test
    void getUnknownIdIs404() {
        assertThat(get("txn_999999").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("not-an-id").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * TODO: create txn → invoice it → PATCH total. Expect 409; GET txn shows the old total;
     * GET invoice totals unchanged.
     */
    @Test
    @Disabled("TODO")
    void t07_patchInvoicedTransactionIs409() {
    }

    /** TODO: create txn → invoice it → DELETE txn. Expect 409; GET txn is still 200. */
    @Test
    @Disabled("TODO")
    void t08_deleteInvoicedTransactionIs409() {
    }

    /**
     * TODO: create &gt; 50 txns. GET /transactions returns exactly 50 plus a next cursor;
     * limit=1000 → 400 (per PageRequest policy). Following the cursor yields the rest with no overlap.
     */
    @Test
    @Disabled("TODO")
    void t10_listIsBoundedAndPaginated() {
    }

    // Worth adding beyond the minimum: PATCH happy path, DELETE happy path (child rows gone).

    // ─── helpers ────────────────────────────────────────────────────────────────

    private static ObjectNode seed(String key, String externalId) {
        return Fixtures.seed(key).put("external_id", externalId);
    }

    private ResponseEntity<JsonNode> post(ObjectNode body) {
        return http.postForEntity("/transactions", body, JsonNode.class);
    }

    private ResponseEntity<JsonNode> get(String id) {
        return http.getForEntity("/transactions/" + id, JsonNode.class);
    }

    private long rows(String externalId) {
        return jdbc.sql("SELECT COUNT(*) FROM transactions WHERE external_id = :externalId")
                .param("externalId", externalId).query(Long.class).single();
    }
}
