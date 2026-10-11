package com.navan.billing.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.navan.billing.support.Fixtures;
import com.navan.billing.transaction.api.CreateTransactionRequest;
import com.navan.billing.transaction.domain.metadata.FlightMetadata;
import com.navan.billing.transaction.domain.metadata.HotelMetadata;
import com.navan.billing.transaction.domain.metadata.NavanFeeKind;
import com.navan.billing.transaction.domain.metadata.NavanFeeMetadata;
import com.navan.billing.transaction.domain.metadata.RailMetadata;

/** Locks the wire contract: every seed payload binds to the right typed metadata; unknowns are rejected. */
@JsonTest
class SeedPayloadBindingTest {

    @Autowired
    ObjectMapper mapper;

    @ParameterizedTest
    @ValueSource(strings = {"flight", "hotel", "rail", "trip_fee", "agent_call_fee", "duplicate_flight"})
    void seedBindsToTypedMetadata(String key) throws Exception {
        var req = mapper.treeToValue(Fixtures.seed(key), CreateTransactionRequest.class);
        assertThat(req.metadata()).isNotNull();
        assertThat(req.metadata().type()).isEqualTo(req.type());
    }

    @Test
    void typedFieldsSurvive() throws Exception {
        var flight = (FlightMetadata) bind("flight").metadata();
        assertThat(flight.coupons()).singleElement()
                .satisfies(c -> assertThat(c.flightNumber()).isEqualTo("BA982"));

        var hotel = (HotelMetadata) bind("hotel").metadata();
        assertThat(hotel.address().city()).isEqualTo("London");

        var rail = (RailMetadata) bind("rail").metadata();
        assertThat(rail.legs()).hasSize(2);
        assertThat(rail.legs().get(0).travelClass()).isEqualTo("standard");

        var fee = (NavanFeeMetadata) bind("agent_call_fee").metadata();
        assertThat(fee.feeKind()).isEqualTo(NavanFeeKind.AGENT_CALL_FEE);
    }

    @Test
    void unknownTypeIsRejected() {
        var body = Fixtures.seed("flight").put("type", "cruise");
        assertThatThrownBy(() -> mapper.treeToValue(body, CreateTransactionRequest.class))
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
    }

    @Test
    void unknownFeeKindIsRejected() {
        var body = Fixtures.seed("trip_fee");
        ((com.fasterxml.jackson.databind.node.ObjectNode) body.get("metadata")).put("fee_kind", "lounge_fee");
        assertThatThrownBy(() -> mapper.treeToValue(body, CreateTransactionRequest.class))
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
    }

    private CreateTransactionRequest bind(String key) throws Exception {
        return mapper.treeToValue(Fixtures.seed(key), CreateTransactionRequest.class);
    }
}
