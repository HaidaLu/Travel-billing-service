package com.navan.billing.transaction.persistence.metadata;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.navan.billing.common.money.MinorUnits;
import com.navan.billing.transaction.dto.TransactionType;
import com.navan.billing.transaction.dto.metadata.HotelMetadata;
import com.navan.billing.transaction.dto.metadata.HotelMetadata.Address;
import com.navan.billing.transaction.dto.metadata.HotelMetadata.HotelExtra;

@Component
class HotelMetadataStore implements MetadataStore<HotelMetadata> {

    private static final RowMapper<HotelExtra> EXTRA = (rs, n) ->
            new HotelExtra(rs.getString("name"), MinorUnits.fromMinor(rs.getLong("amount_minor")));

    private final JdbcClient jdbc;

    HotelMetadataStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public TransactionType type() {
        return TransactionType.HOTEL;
    }

    @Override
    public Class<HotelMetadata> metadataClass() {
        return HotelMetadata.class;
    }

    @Override
    public void insert(long transactionId, HotelMetadata m) {
        jdbc.sql("""
                        INSERT INTO hotel_details (transaction_id, name, street, city, country,
                                                   check_in, check_out, nights, room_rate_minor)
                        VALUES (:id, :name, :street, :city, :country, :checkIn, :checkOut, :nights, :roomRate)""")
                .param("id", transactionId)
                .param("name", m.name())
                .param("street", m.address().street())
                .param("city", m.address().city())
                .param("country", m.address().country())
                .param("checkIn", m.checkIn().toString())
                .param("checkOut", m.checkOut().toString())
                .param("nights", m.nights())
                .param("roomRate", MinorUnits.toMinor(m.roomRatePerNight()))
                .update();
        List<HotelExtra> extras = m.extras();
        for (int position = 0; position < extras.size(); position++) {
            HotelExtra extra = extras.get(position);
            jdbc.sql("""
                            INSERT INTO hotel_extras (transaction_id, position, name, amount_minor)
                            VALUES (:id, :position, :name, :amount)""")
                    .param("id", transactionId)
                    .param("position", position)
                    .param("name", extra.name())
                    .param("amount", MinorUnits.toMinor(extra.amount()))
                    .update();
        }
    }

    @Override
    public HotelMetadata load(long transactionId) {
        List<HotelExtra> extras = jdbc.sql("""
                        SELECT name, amount_minor FROM hotel_extras
                        WHERE transaction_id = :id ORDER BY position""")
                .param("id", transactionId)
                .query(EXTRA)
                .list();
        return jdbc.sql("""
                        SELECT name, street, city, country, check_in, check_out, nights, room_rate_minor
                        FROM hotel_details WHERE transaction_id = :id""")
                .param("id", transactionId)
                .query((rs, n) -> new HotelMetadata(
                        rs.getString("name"),
                        new Address(rs.getString("street"), rs.getString("city"), rs.getString("country")),
                        LocalDate.parse(rs.getString("check_in")),
                        LocalDate.parse(rs.getString("check_out")),
                        rs.getInt("nights"),
                        MinorUnits.fromMinor(rs.getLong("room_rate_minor")),
                        extras))
                .single();
    }

    @Override
    public void delete(long transactionId) {
        jdbc.sql("DELETE FROM hotel_extras WHERE transaction_id = :id").param("id", transactionId).update();
        jdbc.sql("DELETE FROM hotel_details WHERE transaction_id = :id").param("id", transactionId).update();
    }
}
