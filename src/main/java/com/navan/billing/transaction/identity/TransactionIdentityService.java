package com.navan.billing.transaction.identity;

import java.time.Instant;
import java.util.OptionalLong;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.navan.billing.transaction.domain.NewTransaction;
import com.navan.billing.transaction.persistence.TransactionRepository;

@Component
public class TransactionIdentityService {

    private final TransactionRepository repository;

    public TransactionIdentityService(TransactionRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public IdentityClaim claim(NewTransaction tx, Instant now) {
        OptionalLong inserted = repository.insertHeaderIfAbsent(tx, now);
        if (inserted.isPresent()) {
            return new IdentityClaim.Claimed(inserted.getAsLong());
        }
        long existingId = repository.findIdByExternalId(tx.externalId())
                .orElseThrow(() -> new IllegalStateException(
                        "insert of external_id '" + tx.externalId() + "' conflicted but no row was found"));
        return new IdentityClaim.AlreadyExists(existingId);
    }
}
