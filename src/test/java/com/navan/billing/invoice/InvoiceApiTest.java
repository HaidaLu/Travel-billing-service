package com.navan.billing.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.navan.billing.support.ApiTestBase;
import com.navan.billing.support.Fixtures;

/** Required invoice tests from the brief (names match its numbered list) plus the policy edges. */
class InvoiceApiTest extends ApiTestBase {

    private static final List<String> TRIP = List.of("flight", "hotel", "rail", "trip_fee", "agent_call_fee");

    @Autowired
    JdbcClient jdbc;

    @Test
    void t03_tripInvoiceMatchesExpectedTotals() {
        JsonNode expected = Fixtures.expectedInvoice();
        Map<String, String> ids = new LinkedHashMap<>();
        TRIP.forEach(key -> ids.put(key, createTxn(key, "t03-" + key)));

        ResponseEntity<JsonNode> created = postInvoice(List.copyOf(ids.values()));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode invoice = created.getBody();
        assertThat(invoice.path("id").asText()).startsWith("inv_");
        assertThat(invoice).as("POST returns the stored invoice")
                .isEqualTo(getInvoice(invoice.path("id").asText()).getBody());
        assertThat(invoice.path("currency").asText()).isEqualTo("EUR");
        assertThat(invoice.path("total").decimalValue())
                .isEqualByComparingTo(expected.at("/totals/grand_total").decimalValue());
        assertThat(invoice.path("lines").size()).isEqualTo(5);
        ids.forEach((key, id) -> assertThat(line(invoice, id).path("total").decimalValue()).as(key)
                .isEqualByComparingTo(expected.at("/totals/" + key).decimalValue()));
        assertRollup(invoice.path("tax_rollup"), expected.path("tax_rollup_example"));
        assertRollup(invoice.path("fee_rollup"), expected.path("fee_rollup_example"));

        assertThat(line(invoice, ids.get("flight")).at("/metadata/coupons/0/flight_number").asText()).isEqualTo("BA982");
        assertThat(line(invoice, ids.get("hotel")).at("/metadata/address/city").asText()).isEqualTo("London");
        assertThat(line(invoice, ids.get("rail")).at("/metadata/legs").size()).isEqualTo(2);
        assertThat(line(invoice, ids.get("trip_fee")).at("/metadata/fee_kind").asText()).isEqualTo("trip_fee");
        assertThat(line(invoice, ids.get("agent_call_fee")).at("/metadata/fee_kind").asText())
                .isEqualTo("agent_call_fee");
    }

    /** The API refuses to PATCH an invoiced transaction (t07), so the stored row is changed underneath it in SQL. */
    @Test
    void t04_invoiceIsASnapshot() {
        String flight = createTxn("flight", "t04-flight");
        String invoiceId = postInvoice(List.of(flight)).getBody().path("id").asText();
        JsonNode before = getInvoice(invoiceId).getBody();

        jdbc.sql("UPDATE transactions SET total_minor = 99900 WHERE id = :id").param("id", rowId(flight)).update();
        jdbc.sql("UPDATE flight_coupons SET flight_number = 'XX000' WHERE transaction_id = :id")
                .param("id", rowId(flight)).update();

        assertThat(http.getForEntity("/transactions/" + flight, JsonNode.class).getBody().path("total").decimalValue())
                .as("the live row did change").isEqualByComparingTo("999.00");
        JsonNode after = getInvoice(invoiceId).getBody();
        assertThat(line(after, flight).path("total").decimalValue()).isEqualByComparingTo("220.00");
        assertThat(line(after, flight).at("/metadata/coupons/0/flight_number").asText()).isEqualTo("BA982");
        assertThat(after).isEqualTo(before);
    }

    @Test
    void t05_mixedCurrencyIs409() {
        String eur = createTxn("trip_fee", "t05-eur");
        String usd = createTxn(Fixtures.seed("trip_fee").put("external_id", "t05-usd").put("currency", "USD"));
        long invoicesBefore = count("SELECT COUNT(*) FROM invoices");

        ResponseEntity<JsonNode> response = postInvoice(List.of(eur, usd));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().path("code").asText()).isEqualTo("mixed_currency");
        assertThat(count("SELECT COUNT(*) FROM invoices")).as("no invoice created").isEqualTo(invoicesBefore);
    }

    @Test
    void t09_deleteInvoiceKeepsTransactions() {
        String a = createTxn("trip_fee", "t09-a");
        String b = createTxn("rail", "t09-b");
        String invoiceId = postInvoice(List.of(a, b)).getBody().path("id").asText();

        assertThat(deleteInvoice(invoiceId).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(getInvoice(invoiceId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(deleteInvoice(invoiceId).getStatusCode()).as("second delete").isEqualTo(HttpStatus.NOT_FOUND);
        for (String table : List.of("invoice_lines", "invoice_tax_rollup", "invoice_fee_rollup")) {
            assertThat(count("SELECT COUNT(*) FROM " + table + " WHERE invoice_id = " + rowId(invoiceId)))
                    .as(table).isZero();
        }
        for (String txn : List.of(a, b)) {
            assertThat(http.getForEntity("/transactions/" + txn, JsonNode.class).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
            assertThat(patchTxnTotal(txn, "1.00").getStatusCode()).as("no longer frozen").isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    void requestPoliciesRejectWithoutCreatingAnything() {
        String a = createTxn("trip_fee", "policy-a");
        String onOtherInvoice = createTxn("agent_call_fee", "policy-b");
        String otherInvoice = postInvoice(List.of(onOtherInvoice)).getBody().path("id").asText();
        long invoicesBefore = count("SELECT COUNT(*) FROM invoices");

        ResponseEntity<JsonNode> twice = postInvoice(List.of(a, a));
        ResponseEntity<JsonNode> missing = postInvoice(List.of(a, "txn_999999"));
        ResponseEntity<JsonNode> elsewhere = postInvoice(List.of(a, onOtherInvoice));

        assertThat(twice.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(twice.getBody().path("code").asText()).isEqualTo("duplicate_invoice_line");
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(elsewhere.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(elsewhere.getBody().path("code").asText()).isEqualTo("transaction_already_invoiced");
        assertThat(elsewhere.getBody().path("message").asText()).contains(otherInvoice);
        assertThat(count("SELECT COUNT(*) FROM invoices")).isEqualTo(invoicesBefore);
        assertThat(patchTxnTotal(a, "26.00").getStatusCode()).as("a was never invoiced").isEqualTo(HttpStatus.OK);
    }

    /**
     * One invoice per transaction is also a schema rule, not only the service's 409: copying a's line onto a second
     * invoice in SQL, bypassing the service, is refused by the database.
     */
    @Test
    void schemaRefusesATransactionOnTwoInvoices() {
        String a = createTxn("trip_fee", "schema-a");
        String b = createTxn("agent_call_fee", "schema-b");
        String first = postInvoice(List.of(a)).getBody().path("id").asText();
        String second = postInvoice(List.of(b)).getBody().path("id").asText();

        assertThatThrownBy(() -> jdbc.sql("""
                        INSERT INTO invoice_lines
                            (invoice_id, position, transaction_id, type, external_id, total_minor, snapshot_json)
                        SELECT :second, 1, transaction_id, type, external_id, total_minor, snapshot_json
                        FROM invoice_lines WHERE invoice_id = :first""")
                .param("second", rowId(second))
                .param("first", rowId(first))
                .update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("UNIQUE constraint failed: invoice_lines.transaction_id");
        assertThat(count("SELECT COUNT(*) FROM invoice_lines WHERE transaction_id = " + rowId(a))).isEqualTo(1);
    }

    @Test
    void singleTransactionIdBodyWorks() {
        String a = createTxn("trip_fee", "single-a");

        ResponseEntity<JsonNode> created =
                http.postForEntity("/invoices", Map.of("transaction_id", a), JsonNode.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().path("total").decimalValue()).isEqualByComparingTo("25.00");
    }

    /** PATCH rebuilds from CURRENT stored data: c is edited while still un-invoiced, then added. */
    @Test
    void patchInvoiceRebuildsFromCurrentDataAndFreesRemovedTransactions() {
        String a = createTxn("trip_fee", "rebuild-a");
        String b = createTxn("agent_call_fee", "rebuild-b");
        String c = createTxn("rail", "rebuild-c");
        JsonNode created = postInvoice(List.of(a, b)).getBody();
        String invoiceId = created.path("id").asText();
        assertThat(created.path("total").decimalValue()).isEqualByComparingTo("40.00");
        assertThat(patchTxnTotal(c, "100.00").getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> patched = patchInvoice(invoiceId, List.of(b, c));

        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode invoice = patched.getBody();
        assertThat(invoice.path("id").asText()).isEqualTo(invoiceId);
        assertThat(invoice.path("total").decimalValue()).isEqualByComparingTo("115.00");
        assertThat(lineIds(invoice)).containsExactly(b, c);
        assertThat(invoice.at("/fee_rollup/0/name").asText()).isEqualTo("Booking fee");
        assertThat(invoice.path("updated_at").asText()).isGreaterThan(created.path("updated_at").asText());
        assertThat(patchTxnTotal(a, "1.00").getStatusCode()).as("a left the invoice").isEqualTo(HttpStatus.OK);
        assertThat(patchTxnTotal(c, "1.00").getStatusCode()).as("c is now frozen").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void failedPatchLeavesTheInvoiceUnchanged() {
        String a = createTxn("trip_fee", "unchanged-a");
        String usd = createTxn(Fixtures.seed("trip_fee").put("external_id", "unchanged-usd").put("currency", "USD"));
        String invoiceId = postInvoice(List.of(a)).getBody().path("id").asText();
        JsonNode before = getInvoice(invoiceId).getBody();

        assertThat(patchInvoice(invoiceId, List.of(a, usd)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(patchInvoice(invoiceId, List.of(a, "txn_999999")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(patchInvoice(invoiceId, List.of(a, a)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(patchInvoice("inv_999999", List.of(a)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(getInvoice(invoiceId).getBody()).isEqualTo(before);
    }

    @Test
    void listIsBoundedAndPaginated() {
        for (int i = 0; i < 3; i++) {
            postInvoice(List.of(createTxn("trip_fee", "list-" + i)));
        }

        List<String> walked = new ArrayList<>();
        String cursor = null;
        do {
            ResponseEntity<JsonNode> page = http.getForEntity(
                    "/invoices?limit=2" + (cursor == null ? "" : "&cursor=" + cursor), JsonNode.class);
            assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(page.getBody().path("items").size()).isLessThanOrEqualTo(2);
            page.getBody().path("items").forEach(item -> walked.add(item.path("id").asText()));
            cursor = page.getBody().hasNonNull("next_cursor") ? page.getBody().path("next_cursor").asText() : null;
        } while (cursor != null);

        List<String> allIds = jdbc.sql("SELECT id FROM invoices ORDER BY id").query(Long.class).list()
                .stream().map(id -> "inv_" + id).toList();
        assertThat(walked).hasSizeGreaterThanOrEqualTo(3).containsExactlyElementsOf(allIds);
        assertThat(http.getForEntity("/invoices?limit=1000", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private String createTxn(String key, String externalId) {
        return createTxn(Fixtures.seed(key).put("external_id", externalId));
    }

    private String createTxn(ObjectNode body) {
        ResponseEntity<JsonNode> response = http.postForEntity("/transactions", body, JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().path("id").asText();
    }

    private ResponseEntity<JsonNode> postInvoice(List<String> transactionIds) {
        return http.postForEntity("/invoices", Map.of("transaction_ids", transactionIds), JsonNode.class);
    }

    private ResponseEntity<JsonNode> getInvoice(String id) {
        return http.getForEntity("/invoices/" + id, JsonNode.class);
    }

    private ResponseEntity<JsonNode> patchInvoice(String id, List<String> transactionIds) {
        return http.exchange("/invoices/" + id, HttpMethod.PATCH,
                new HttpEntity<>(Map.of("transaction_ids", transactionIds)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> deleteInvoice(String id) {
        return http.exchange("/invoices/" + id, HttpMethod.DELETE, null, JsonNode.class);
    }

    private ResponseEntity<JsonNode> patchTxnTotal(String id, String total) {
        return http.exchange("/transactions/" + id, HttpMethod.PATCH,
                new HttpEntity<>(Map.of("total", new BigDecimal(total))), JsonNode.class);
    }

    private static JsonNode line(JsonNode invoice, String transactionId) {
        for (JsonNode line : invoice.path("lines")) {
            if (line.path("transaction_id").asText().equals(transactionId)) {
                return line;
            }
        }
        return fail("no line for " + transactionId + " in " + invoice);
    }

    private static List<String> lineIds(JsonNode invoice) {
        List<String> ids = new ArrayList<>();
        invoice.path("lines").forEach(line -> ids.add(line.path("transaction_id").asText()));
        return ids;
    }

    /** Same order, names and amounts; a rate only where the expectation has one. */
    private static void assertRollup(JsonNode actual, JsonNode expected) {
        assertThat(actual.size()).isEqualTo(expected.size());
        for (int i = 0; i < expected.size(); i++) {
            JsonNode a = actual.get(i);
            JsonNode e = expected.get(i);
            assertThat(a.path("name").asText()).isEqualTo(e.path("name").asText());
            assertThat(a.path("amount").decimalValue()).as(e.toString())
                    .isEqualByComparingTo(e.path("amount").decimalValue());
            if (e.has("rate")) {
                assertThat(a.path("rate").decimalValue()).isEqualByComparingTo(e.path("rate").decimalValue());
            } else {
                assertThat(a.has("rate")).as(a.toString()).isFalse();
            }
        }
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    private static long rowId(String publicId) {
        return Long.parseLong(publicId.substring(publicId.indexOf('_') + 1));
    }
}
