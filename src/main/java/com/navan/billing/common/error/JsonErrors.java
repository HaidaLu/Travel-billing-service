package com.navan.billing.common.error;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

/**
 * Client-facing wording for request-binding errors: the JSON path, the offending value and what was expected.
 * Jackson's own messages name Java classes (com.navan...), which leaks internals and means nothing to a client.
 */
public final class JsonErrors {

    /** Only used to read enums' @JsonValue wire names; needs no app configuration. */
    private static final ObjectMapper WIRE = new ObjectMapper();

    private JsonErrors() {
    }

    public static String describe(JsonProcessingException e) {
        return describe(e, "");
    }

    /** @param pathPrefix JSON path of the node that was bound, e.g. "metadata" when only that subtree was bound */
    public static String describe(JsonProcessingException e, String pathPrefix) {
        if (e instanceof InvalidTypeIdException t) {
            return "unknown type '" + t.getTypeId() + "'" + oneOf(subtypeNames(t.getBaseType().getRawClass()));
        }
        if (e instanceof InvalidFormatException f) {
            Class<?> target = f.getTargetType();
            String value = "'" + f.getValue() + "'";
            if (target != null && target.isEnum()) {
                return path(f, pathPrefix) + ": " + value + " is not allowed" + oneOf(wireNames(target));
            }
            return path(f, pathPrefix) + ": invalid value " + value + expected(target);
        }
        if (e instanceof MismatchedInputException m) {
            return path(m, pathPrefix) + ": wrong JSON type" + expected(m.getTargetType());
        }
        if (e instanceof JsonParseException p) {
            JsonLocation at = p.getLocation();
            return "malformed JSON" + (at == null ? "" : " at line " + at.getLineNr() + ", column " + at.getColumnNr());
        }
        if (e instanceof JsonMappingException m) {
            return path(m, pathPrefix) + ": invalid value";
        }
        return "malformed JSON";
    }

    /** True if the error's JSON path starts inside an object of {@code type} (Jackson records the class or instance). */
    public static boolean startsInside(JsonMappingException e, Class<?> type) {
        if (e.getPath().isEmpty()) {
            return false;
        }
        Object from = e.getPath().get(0).getFrom();
        return from instanceof Class<?> c ? type.isAssignableFrom(c) : type.isInstance(from);
    }

    /** Bean Validation paths use Java names ({@code metadata.checkOut}); the API speaks snake_case. */
    public static String toJsonPath(String javaPath) {
        return javaPath.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    private static String path(JsonMappingException e, String prefix) {
        StringBuilder path = new StringBuilder(prefix);
        for (JsonMappingException.Reference ref : e.getPath()) {
            if (ref.getFieldName() != null) {
                path.append(path.isEmpty() ? "" : ".").append(ref.getFieldName());
            } else if (ref.getIndex() >= 0) {
                path.append('[').append(ref.getIndex()).append(']');
            }
        }
        return path.isEmpty() ? "body" : path.toString();
    }

    private static String expected(Class<?> target) {
        if (target == null) {
            return "";
        }
        if (target == Instant.class) {
            return ", expected an ISO-8601 timestamp with offset, e.g. 2026-03-11T06:40:00Z";
        }
        if (target == LocalDate.class) {
            return ", expected a date, e.g. 2026-03-11";
        }
        if (target == BigDecimal.class || Number.class.isAssignableFrom(target) || target.isPrimitive()) {
            return ", expected a number";
        }
        if (target == String.class) {
            return ", expected a string";
        }
        if (Collection.class.isAssignableFrom(target) || target.isArray()) {
            return ", expected an array";
        }
        return ", expected an object";
    }

    private static String oneOf(List<String> names) {
        return names.isEmpty() ? "" : ", expected one of " + names;
    }

    private static List<String> subtypeNames(Class<?> base) {
        JsonSubTypes subtypes = base.getAnnotation(JsonSubTypes.class);
        return subtypes == null ? List.of()
                : Arrays.stream(subtypes.value()).map(JsonSubTypes.Type::name).toList();
    }

    private static List<String> wireNames(Class<?> enumType) {
        return Arrays.stream(enumType.getEnumConstants()).map(c -> WIRE.convertValue(c, String.class)).toList();
    }
}
