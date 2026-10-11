package com.navan.billing.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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
     * TODO (needs POST /invoices, step 9): create txn → invoice it → PATCH total. Expect 409; GET txn shows the old
     * total; GET invoice totals unchanged. Until then the 409 rule is covered by invoicedTransactionIsFrozen.
     */
    @Test
    @Disabled("TODO")
    void t07_patchInvoicedTransactionIs409() {
    }

    /** TODO (needs POST /invoices, step 9): create txn → invoice it → DELETE txn. Expect 409; GET txn is still 200. */
    @Test
    @Disabled("TODO")
    void t08_deleteInvoicedTransactionIs409() {
    }

    /**
     * Other tests in this class add rows too (order not guaranteed), so instead of fixed page sizes this walks every
     * page and compares the result with the ids in the DB: same order, nothing missing, nothing twice.
     */
    @Test
    void t10_listIsBoundedAndPaginated() {
        for (int i = 0; i < 55; i++) {
            assertThat(post(seed("trip_fee", "t10-" + i)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        JsonNode firstPage = list("");
        assertThat(firstPage.path("items").size()).as("default page size").isEqualTo(50);
        assertThat(firstPage.hasNonNull("next_cursor")).isTrue();
        assertThat(firstPage.at("/items/0/metadata").isObject()).as("list items are fully hydrated").isTrue();

        List<String> walked = new ArrayList<>();
        String cursor = null;
        do {
            JsonNode page = list("limit=20" + (cursor == null ? "" : "&cursor=" + cursor));
            assertThat(page.path("items").size()).isLessThanOrEqualTo(20);
            page.path("items").forEach(item -> walked.add(item.path("id").asText()));
            cursor = page.hasNonNull("next_cursor") ? page.path("next_cursor").asText() : null;
        } while (cursor != null);
        List<String> allIds = jdbc.sql("SELECT id FROM transactions ORDER BY id").query(Long.class).list()
                .stream().map(id -> "txn_" + id).toList();
        assertThat(walked).containsExactlyElementsOf(allIds);

        for (String bad : List.of("limit=1000", "limit=101", "limit=0", "cursor=not-a-cursor")) {
            assertThat(http.getForEntity("/transactions?" + bad, JsonNode.class).getStatusCode())
                    .as(bad).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(list("limit=100").path("items").size()).isEqualTo(Math.min(100, allIds.size()));
    }

    @Test
    void patchChangesOnlySentFieldsAndReplacesWholeLists() {
        JsonNode before = post(seed("hotel", "patch-hotel-1")).getBody();
        String id = before.path("id").asText();
        ObjectNode metadata = (ObjectNode) Fixtures.seed("hotel").get("metadata");
        metadata.put("name", "Hotel Mitte London (renovated)").putArray("extras");
        ObjectNode body = JsonNodeFactory.instance.objectNode().put("total", 345.70);
        body.putArray("tax_lines")
                .add(JsonNodeFactory.instance.objectNode().put("name", "VAT").put("rate", 0.07).put("amount", 21.70))
                .add(JsonNodeFactory.instance.objectNode().put("name", "City tax").put("amount", 14.00));
        body.set("metadata", metadata);

        ResponseEntity<JsonNode> patched = patch(id, body);

        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode after = get(id).getBody();
        assertThat(patched.getBody()).as("PATCH returns the stored record").isEqualTo(after);
        assertThat(after.path("total").decimalValue()).isEqualByComparingTo("345.70");
        assertThat(after.at("/tax_lines/1/name").asText()).isEqualTo("City tax");
        assertThat(after.at("/metadata/name").asText()).isEqualTo("Hotel Mitte London (renovated)");
        assertThat(after.at("/metadata/extras").size()).isZero();
        assertThat(after.path("fee_lines")).as("not sent → unchanged").isEqualTo(before.path("fee_lines"));
        assertThat(after.path("occurred_at")).as("not sent → unchanged").isEqualTo(before.path("occurred_at"));
        assertThat(after.path("updated_at").asText()).isGreaterThan(before.path("updated_at").asText());
        long rowId = rowId(id);
        assertThat(childRows("transaction_tax_lines", rowId)).isEqualTo(2);
        assertThat(childRows("hotel_extras", rowId)).isZero();
    }

    /** Every request here must fail AND leave the stored row byte-for-byte as it was (including updated_at). */
    @Test
    void invalidPatchIs400AndChangesNothing() {
        JsonNode before = post(seed("flight", "patch-flight-1")).getBody();
        String id = before.path("id").asText();
        ObjectNode partialMetadata = JsonNodeFactory.instance.objectNode();
        partialMetadata.putObject("metadata").put("origin", "TXL");
        ObjectNode hotelShapedMetadata = JsonNodeFactory.instance.objectNode();
        hotelShapedMetadata.set("metadata", Fixtures.seed("hotel").get("metadata"));
        ObjectNode badLineAfterGoodTotal = JsonNodeFactory.instance.objectNode().put("total", 300.00);
        badLineAfterGoodTotal.putArray("tax_lines").addObject().put("name", "VAT").put("amount", 1.005);

        List<ObjectNode> invalid = List.of(
                JsonNodeFactory.instance.objectNode().put("currency", "USD"),
                JsonNodeFactory.instance.objectNode().put("type", "hotel"),
                JsonNodeFactory.instance.objectNode().put("external_id", "renamed"),
                partialMetadata,
                hotelShapedMetadata,
                badLineAfterGoodTotal);
        for (ObjectNode body : invalid) {
            assertThat(patch(id, body).getStatusCode()).as(body.toString()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        assertThat(patch(id, partialMetadata).getBody().path("message").asText()).contains("metadata.destination");
        assertThat(get(id).getBody()).isEqualTo(before);
    }

    /** Interim: puts the row on an invoice through SQL until POST /invoices exists (then t07/t08 take over). */
    @Test
    void invoicedTransactionIsFrozen() {
        JsonNode before = post(seed("trip_fee", "invoiced-1")).getBody();
        String id = before.path("id").asText();
        putOnInvoice(rowId(id));

        ResponseEntity<JsonNode> patched = patch(id, JsonNodeFactory.instance.objectNode().put("total", 99.00));
        ResponseEntity<JsonNode> deleted = delete(id);

        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(patched.getBody().path("code").asText()).isEqualTo("transaction_invoiced");
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(get(id).getBody()).as("nothing persisted, nothing deleted").isEqualTo(before);
    }

    @Test
    void deleteRemovesTheRowAndAllItsChildren() {
        String id = post(seed("flight", "delete-flight-1")).getBody().path("id").asText();
        long rowId = rowId(id);

        assertThat(delete(id).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(get(id).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(delete(id).getStatusCode()).as("second delete").isEqualTo(HttpStatus.NOT_FOUND);
        for (String table : List.of("transaction_tax_lines", "transaction_fee_lines", "flight_details", "flight_coupons")) {
            assertThat(childRows(table, rowId)).as(table).isZero();
        }
    }

    @Test
    void patchAndDeleteUnknownIdAre404() {
        assertThat(patch("txn_999999", JsonNodeFactory.instance.objectNode().put("total", 1.00)).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(delete("txn_999999").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

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

    private ResponseEntity<JsonNode> patch(String id, ObjectNode body) {
        return http.exchange("/transactions/" + id, HttpMethod.PATCH, new HttpEntity<>(body), JsonNode.class);
    }

    private ResponseEntity<JsonNode> delete(String id) {
        return http.exchange("/transactions/" + id, HttpMethod.DELETE, null, JsonNode.class);
    }

    private static long rowId(String publicId) {
        return Long.parseLong(publicId.substring("txn_".length()));
    }

    private long childRows(String table, long rowId) {
        return jdbc.sql("SELECT COUNT(*) FROM " + table + " WHERE transaction_id = :id")
                .param("id", rowId).query(Long.class).single();
    }

    /** Until POST /invoices exists (step 9): the minimum rows that make a transaction count as invoiced. */
    private void putOnInvoice(long rowId) {
        long invoiceId = jdbc.sql("""
                        INSERT INTO invoices (currency, total_minor, created_at, updated_at)
                        VALUES ('EUR', 0, '2026-01-01T00:00:00.000Z', '2026-01-01T00:00:00.000Z') RETURNING id""")
                .query(Long.class).single();
        jdbc.sql("""
                        INSERT INTO invoice_lines
                            (invoice_id, position, transaction_id, type, external_id, total_minor, snapshot_json)
                        VALUES (:invoiceId, 0, :id, 'navan_fee', 'n/a', 0, '{}')""")
                .param("invoiceId", invoiceId).param("id", rowId).update();
    }

    private JsonNode list(String query) {
        ResponseEntity<JsonNode> response = http.getForEntity("/transactions?" + query, JsonNode.class);
        assertThat(response.getStatusCode()).as(query).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private long rows(String externalId) {
        return jdbc.sql("SELECT COUNT(*) FROM transactions WHERE external_id = :externalId")
                .param("externalId", externalId).query(Long.class).single();
    }
}
