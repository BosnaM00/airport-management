package com.example.airportManager.mapper;

import com.example.airportManager.dto.route.RouteCreateDTO;
import com.example.airportManager.dto.route.RouteResponseDTO;
import com.example.airportManager.model.Airport;
import com.example.airportManager.model.Route;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RouteMapperTest {

    private final RouteMapper mapper = new RouteMapperImpl();

    @Test
    void toEntity_mapsAllFieldsExceptIgnored() {
        RouteCreateDTO dto = new RouteCreateDTO(1L, 2L, 1500, 180);

        Route entity = mapper.toEntity(dto);

        assertThat(entity.getId()).isNull();
        assertThat(entity.getOriginAirport()).isNull();
        assertThat(entity.getDestAirport()).isNull();
        assertThat(entity.getFlights()).isNull();
        assertThat(entity.getDistanceNm()).isEqualTo(1500);
        assertThat(entity.getStdDurationMin()).isEqualTo(180);
    }

    @Test
    void toResponse_mapsAllFields_includingNestedAirportData() {
        Airport origin = new Airport();
        origin.setId(1L);
        origin.setCity("Bucharest");
        origin.setIata("OTP");

        Airport dest = new Airport();
        dest.setId(2L);
        dest.setCity("Paris");
        dest.setIata("CDG");

        Route entity = new Route();
        entity.setId(10L);
        entity.setDistanceNm(1500);
        entity.setStdDurationMin(180);
        entity.setOriginAirport(origin);
        entity.setDestAirport(dest);

        RouteResponseDTO response = mapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.originAirportId()).isEqualTo(1L);
        assertThat(response.originCity()).isEqualTo("Bucharest");
        assertThat(response.originIata()).isEqualTo("OTP");
        assertThat(response.destAirportId()).isEqualTo(2L);
        assertThat(response.destCity()).isEqualTo("Paris");
        assertThat(response.destIata()).isEqualTo("CDG");
        assertThat(response.distanceNm()).isEqualTo(1500);
        assertThat(response.stdDurationMin()).isEqualTo(180);
    }
}
