package com.navan.billing;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class BillingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BillingApplication.class, args);
    }

    /** Injected everywhere "now" is needed so tests can pin time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
