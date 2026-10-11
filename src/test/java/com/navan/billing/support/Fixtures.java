package com.navan.billing.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Reads the brief's fixtures straight from {@code fixtures/task-b} so tests and curls use the same payloads. */
public final class Fixtures {

    private static final Path DIR = Path.of("fixtures", "task-b");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Fixtures() {
    }

    /** One entry of seed-payloads.json, e.g. "flight", "trip_fee", "duplicate_flight". Fresh copy each call. */
    public static ObjectNode seed(String key) {
        JsonNode node = read("seed-payloads.json").get(key);
        if (node == null) {
            throw new IllegalArgumentException("no seed payload " + key);
        }
        ObjectNode copy = node.deepCopy();
        copy.remove("comment");
        return copy;
    }

    public static JsonNode expectedInvoice() {
        return read("expected-invoice.json");
    }

    private static JsonNode read(String file) {
        try {
            return MAPPER.readTree(DIR.resolve(file).toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
