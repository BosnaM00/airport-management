package com.example.airportManager.mapper;

import com.example.airportManager.dto.flight.FlightCreateDTO;
import com.example.airportManager.dto.flight.FlightResponseDTO;
import com.example.airportManager.model.Aircraft;
import com.example.airportManager.model.Airport;
import com.example.airportManager.model.Flight;
import com.example.airportManager.model.FlightStatus;
import com.example.airportManager.model.Route;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class FlightMapperTest {

    private final FlightMapper mapper = new FlightMapperImpl();

    @Test
    void toEntity_mapsAllFieldsExceptIgnored_andRenamesTimeFields() {
        LocalDateTime departure = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime arrival = LocalDateTime.of(2026, 1, 1, 12, 0);
        FlightCreateDTO dto = new FlightCreateDTO("AA100", 1L, departure, arrival, FlightStatus.SCHEDULED, "G1", 2L);

        Flight entity = mapper.toEntity(dto);

        assertThat(entity.getId()).isNull();
        assertThat(entity.getRoute()).isNull();
        assertThat(entity.getAircraft()).isNull();
        assertThat(entity.getBookings()).isNull();
        assertThat(entity.getCode()).isEqualTo("AA100");
        assertThat(entity.getDepartureScheduled()).isEqualTo(departure);
        assertThat(entity.getArrivalScheduled()).isEqualTo(arrival);
        assertThat(entity.getGate()).isEqualTo("G1");
        assertThat(entity.getStatus()).isEqualTo(FlightStatus.SCHEDULED);
    }

    @Test
    void toResponse_mapsAllFields_includingNestedRouteAircraftAirportData() {
        Airport origin = new Airport();
        origin.setId(1L);
        origin.setCity("Bucharest");
        origin.setIata("OTP");

        Airport dest = new Airport();
        dest.setId(2L);
        dest.setCity("Paris");
        dest.setIata("CDG");

        Route route = new Route();
        route.setId(10L);
        route.setOriginAirport(origin);
        route.setDestAirport(dest);

        Aircraft aircraft = new Aircraft();
        aircraft.setId(3L);

        LocalDateTime departure = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime arrival = LocalDateTime.of(2026, 1, 1, 12, 0);

        Flight entity = new Flight();
        entity.setId(100L);
        entity.setCode("AA100");
        entity.setDepartureScheduled(departure);
        entity.setArrivalScheduled(arrival);
        entity.setGate("G1");
        entity.setStatus(FlightStatus.SCHEDULED);
        entity.setRoute(route);
        entity.setAircraft(aircraft);

        FlightResponseDTO response = mapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.code()).isEqualTo("AA100");
        assertThat(response.routeId()).isEqualTo(10L);
        assertThat(response.originCity()).isEqualTo("Bucharest");
        assertThat(response.originIata()).isEqualTo("OTP");
        assertThat(response.destCity()).isEqualTo("Paris");
        assertThat(response.destIata()).isEqualTo("CDG");
        assertThat(response.departureTime()).isEqualTo(departure);
        assertThat(response.arrivalTime()).isEqualTo(arrival);
        assertThat(response.gate()).isEqualTo("G1");
        assertThat(response.aircraftId()).isEqualTo(3L);
        assertThat(response.status()).isEqualTo(FlightStatus.SCHEDULED);
    }
}
