package com.navan.billing.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

/** Real HTTP against a real SQLite file (one file per test class, see {@link SqliteTestBase}). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class ApiTestBase extends SqliteTestBase {

    @Autowired
    protected TestRestTemplate http;
}
