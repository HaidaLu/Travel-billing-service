package com.navan.billing.transaction.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import com.navan.billing.support.SqliteTestBase;
import com.navan.billing.transaction.domain.NewTransaction;
import com.navan.billing.transaction.domain.TransactionType;
import com.navan.billing.transaction.domain.metadata.NavanFeeKind;
import com.navan.billing.transaction.domain.metadata.NavanFeeMetadata;
import com.navan.billing.transaction.identity.IdentityClaim.AlreadyExists;
import com.navan.billing.transaction.identity.IdentityClaim.Claimed;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TransactionIdentityServiceTest extends SqliteTestBase {

    private static final Instant NOW = Instant.parse("2026-10-11T10:00:00Z");

    @Autowired
    TransactionIdentityService identity;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    JdbcClient jdbc;

    @Test
    void secondClaimGetsTheExistingIdAndOverwritesNothing() {
        IdentityClaim first = tx.execute(s -> identity.claim(fee("seq-1", "25.00"), NOW));
        IdentityClaim second = tx.execute(s -> identity.claim(fee("seq-1", "99.00"), NOW));

        assertThat(first).isInstanceOf(Claimed.class);
        long id = ((Claimed) first).id();
        assertThat(second).isEqualTo(new AlreadyExists(id));
        assertThat(rows("seq-1")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT total_minor FROM transactions WHERE id = :id").param("id", id)
                .query(Long.class).single()).isEqualTo(2500L);
    }

    @Test
    void transactionStaysUsableAfterAConflict() {
        tx.executeWithoutResult(s -> identity.claim(fee("usable-1", "25.00"), NOW));

        List<IdentityClaim> claims = tx.execute(s -> List.of(
                identity.claim(fee("usable-1", "25.00"), NOW),
                identity.claim(fee("usable-2", "25.00"), NOW)));

        assertThat(claims.get(0)).isInstanceOf(AlreadyExists.class);
        assertThat(claims.get(1)).isInstanceOf(Claimed.class);
        assertThat(rows("usable-2")).isEqualTo(1);
    }

    @Test
    void overlappingClaimsProduceExactlyOneWinner() throws Exception {
        int threads = 12;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<IdentityClaim>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return tx.execute(s -> identity.claim(fee("race-1", "25.00"), NOW));
                }));
            }
            start.countDown();
            List<IdentityClaim> claims = new ArrayList<>();
            for (Future<IdentityClaim> f : futures) {
                claims.add(f.get(30, TimeUnit.SECONDS));
            }

            List<Claimed> winners = claims.stream()
                    .filter(Claimed.class::isInstance).map(Claimed.class::cast).toList();
            assertThat(winners).hasSize(1);
            assertThat(claims).filteredOn(AlreadyExists.class::isInstance)
                    .hasSize(threads - 1)
                    .allMatch(c -> ((AlreadyExists) c).existingId() == winners.get(0).id());
            assertThat(rows("race-1")).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void claimRefusesToRunOutsideATransaction() {
        assertThatThrownBy(() -> identity.claim(fee("no-tx-1", "25.00"), NOW))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(rows("no-tx-1")).isZero();
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private static NewTransaction fee(String externalId, String total) {
        return new NewTransaction(TransactionType.NAVAN_FEE, externalId, "EUR", NOW, new BigDecimal(total),
                List.of(), List.of(), new NavanFeeMetadata(NavanFeeKind.TRIP_FEE, null, null));
    }

    private long rows(String externalId) {
        return jdbc.sql("SELECT COUNT(*) FROM transactions WHERE external_id = :externalId")
                .param("externalId", externalId).query(Long.class).single();
    }
}
