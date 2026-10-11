package com.navan.billing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.navan.billing.support.ApiTestBase;

class HealthApiTest extends ApiTestBase {

    @Test
    void healthIsOk() {
        var response = http.getForEntity("/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("ok");
    }
}
