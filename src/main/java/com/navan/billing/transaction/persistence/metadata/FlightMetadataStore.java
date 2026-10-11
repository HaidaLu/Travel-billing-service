package com.navan.billing.transaction.persistence.metadata;

import java.util.List;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.navan.billing.common.money.MinorUnits;
import com.navan.billing.common.time.DbTime;
import com.navan.billing.transaction.dto.TransactionType;
import com.navan.billing.transaction.dto.metadata.FlightCoupon;
import com.navan.billing.transaction.dto.metadata.FlightMetadata;

@Component
class FlightMetadataStore implements MetadataStore<FlightMetadata> {

    private static final RowMapper<FlightCoupon> COUPON = (rs, n) -> new FlightCoupon(
            rs.getString("flight_number"),
            rs.getString("carrier"),
            rs.getString("from_airport"),
            rs.getString("to_airport"),
            DbTime.parse(rs.getString("departure_at")),
            DbTime.parse(rs.getString("arrival_at")));

    private final JdbcClient jdbc;

    FlightMetadataStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public TransactionType type() {
        return TransactionType.FLIGHT;
    }

    @Override
    public Class<FlightMetadata> metadataClass() {
        return FlightMetadata.class;
    }

    @Override
    public void insert(long transactionId, FlightMetadata m) {
        jdbc.sql("""
                        INSERT INTO flight_details (transaction_id, origin, destination, fare_minor)
                        VALUES (:id, :origin, :destination, :fare)""")
                .param("id", transactionId)
                .param("origin", m.origin())
                .param("destination", m.destination())
                .param("fare", MinorUnits.toMinor(m.fare()))
                .update();
        List<FlightCoupon> coupons = m.coupons();
        for (int position = 0; position < coupons.size(); position++) {
            FlightCoupon c = coupons.get(position);
            jdbc.sql("""
                            INSERT INTO flight_coupons (transaction_id, position, flight_number, carrier,
                                                        from_airport, to_airport, departure_at, arrival_at)
                            VALUES (:id, :position, :flightNumber, :carrier, :from, :to, :departure, :arrival)""")
                    .param("id", transactionId)
                    .param("position", position)
                    .param("flightNumber", c.flightNumber())
                    .param("carrier", c.carrier())
                    .param("from", c.from())
                    .param("to", c.to())
                    .param("departure", DbTime.format(c.departure()))
                    .param("arrival", DbTime.format(c.arrival()))
                    .update();
        }
    }

    @Override
    public FlightMetadata load(long transactionId) {
        List<FlightCoupon> coupons = jdbc.sql("""
                        SELECT flight_number, carrier, from_airport, to_airport, departure_at, arrival_at
                        FROM flight_coupons WHERE transaction_id = :id ORDER BY position""")
                .param("id", transactionId)
                .query(COUPON)
                .list();
        return jdbc.sql("""
                        SELECT origin, destination, fare_minor
                        FROM flight_details WHERE transaction_id = :id""")
                .param("id", transactionId)
                .query((rs, n) -> new FlightMetadata(
                        rs.getString("origin"),
                        rs.getString("destination"),
                        MinorUnits.fromMinor(rs.getLong("fare_minor")),
                        coupons))
                .single();
    }

    @Override
    public void delete(long transactionId) {
        jdbc.sql("DELETE FROM flight_coupons WHERE transaction_id = :id").param("id", transactionId).update();
        jdbc.sql("DELETE FROM flight_details WHERE transaction_id = :id").param("id", transactionId).update();
    }
}
