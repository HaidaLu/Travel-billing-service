package com.navan.billing.transaction.persistence.metadata;

import java.util.List;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.navan.billing.common.time.DbTime;
import com.navan.billing.transaction.dto.TransactionType;
import com.navan.billing.transaction.dto.metadata.RailLeg;
import com.navan.billing.transaction.dto.metadata.RailMetadata;

@Component
class RailMetadataStore implements MetadataStore<RailMetadata> {

    private static final RowMapper<RailLeg> LEG = (rs, n) -> new RailLeg(
            rs.getString("origin"),
            rs.getString("destination"),
            DbTime.parse(rs.getString("departure_at")),
            DbTime.parse(rs.getString("arrival_at")),
            rs.getString("train_number"),
            rs.getString("travel_class"));

    private final JdbcClient jdbc;

    RailMetadataStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public TransactionType type() {
        return TransactionType.RAIL;
    }

    @Override
    public Class<RailMetadata> metadataClass() {
        return RailMetadata.class;
    }

    @Override
    public void insert(long transactionId, RailMetadata m) {
        List<RailLeg> legs = m.legs();
        for (int position = 0; position < legs.size(); position++) {
            RailLeg leg = legs.get(position);
            jdbc.sql("""
                            INSERT INTO rail_legs (transaction_id, position, origin, destination,
                                                   departure_at, arrival_at, train_number, travel_class)
                            VALUES (:id, :position, :origin, :destination, :departure, :arrival, :train, :class)""")
                    .param("id", transactionId)
                    .param("position", position)
                    .param("origin", leg.origin())
                    .param("destination", leg.destination())
                    .param("departure", DbTime.format(leg.departure()))
                    .param("arrival", DbTime.format(leg.arrival()))
                    .param("train", leg.trainNumber())
                    .param("class", leg.travelClass())
                    .update();
        }
    }

    @Override
    public RailMetadata load(long transactionId) {
        return new RailMetadata(jdbc.sql("""
                        SELECT origin, destination, departure_at, arrival_at, train_number, travel_class
                        FROM rail_legs WHERE transaction_id = :id ORDER BY position""")
                .param("id", transactionId)
                .query(LEG)
                .list());
    }

    @Override
    public void delete(long transactionId) {
        jdbc.sql("DELETE FROM rail_legs WHERE transaction_id = :id").param("id", transactionId).update();
    }
}
