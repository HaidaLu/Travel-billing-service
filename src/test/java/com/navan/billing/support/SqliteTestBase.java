package com.navan.billing.support;

import java.nio.file.Path;

import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Each test class gets its own SQLite file and its own Spring context, so classes can't see each other's rows.
 *
 * <p>{@code @DirtiesContext} is required, not cosmetic: subclasses share identical context configuration, so
 * without it Spring's context cache would hand every class the FIRST class's context (and DB file, which JUnit
 * deletes once that class finishes).
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class SqliteTestBase {

    @TempDir
    static Path dbDir;

    @DynamicPropertySource
    static void sqliteFile(DynamicPropertyRegistry registry) {
        registry.add("BILLING_DB_PATH", () -> dbDir.resolve("test.db").toString());
    }
}
