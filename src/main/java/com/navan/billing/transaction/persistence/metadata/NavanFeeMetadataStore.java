package com.navan.billing.transaction.persistence.metadata;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.navan.billing.transaction.domain.TransactionType;
import com.navan.billing.transaction.domain.metadata.NavanFeeKind;
import com.navan.billing.transaction.domain.metadata.NavanFeeMetadata;

@Component
class NavanFeeMetadataStore implements MetadataStore<NavanFeeMetadata> {

    private final JdbcClient jdbc;

    NavanFeeMetadataStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public TransactionType type() {
        return TransactionType.NAVAN_FEE;
    }

    @Override
    public Class<NavanFeeMetadata> metadataClass() {
        return NavanFeeMetadata.class;
    }

    @Override
    public void insert(long transactionId, NavanFeeMetadata m) {
        jdbc.sql("""
                        INSERT INTO navan_fee_details (transaction_id, fee_kind, description, related_external_id)
                        VALUES (:id, :kind, :description, :related)""")
                .param("id", transactionId)
                .param("kind", m.feeKind().wire())
                .param("description", m.description())
                .param("related", m.relatedExternalId())
                .update();
    }

    @Override
    public NavanFeeMetadata load(long transactionId) {
        return jdbc.sql("""
                        SELECT fee_kind, description, related_external_id
                        FROM navan_fee_details WHERE transaction_id = :id""")
                .param("id", transactionId)
                .query((rs, n) -> new NavanFeeMetadata(
                        NavanFeeKind.fromWire(rs.getString("fee_kind")),
                        rs.getString("description"),
                        rs.getString("related_external_id")))
                .single();
    }

    @Override
    public void delete(long transactionId) {
        jdbc.sql("DELETE FROM navan_fee_details WHERE transaction_id = :id").param("id", transactionId).update();
    }
}
