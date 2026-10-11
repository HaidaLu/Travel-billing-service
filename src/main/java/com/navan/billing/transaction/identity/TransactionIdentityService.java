package com.navan.billing.transaction.identity;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.navan.billing.transaction.domain.NewTransaction;
import com.navan.billing.transaction.persistence.TransactionRepository;

/**
 * Duplicate-identity seam: owns the rule "one row per external_id, enforced by the DB, safe under races".
 * Knows nothing about tax lines, metadata or HTTP.
 */
@Component
public class TransactionIdentityService {

    private final TransactionRepository repository;

    public TransactionIdentityService(TransactionRepository repository) {
        this.repository = repository;
    }
    public IdentityClaim claim(NewTransaction tx, Instant now) {
        throw new UnsupportedOperationException("TODO");
    }
}
