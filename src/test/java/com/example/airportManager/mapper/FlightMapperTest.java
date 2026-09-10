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

// MapStruct is a compile-time code generator, not a reflection-based/runtime mapping library: given the
// @Mapper(componentModel = "spring") FlightMapper interface, the annotation processor (wired up via
// maven-compiler-plugin in pom.xml) generates a concrete FlightMapperImpl class in this same package at build
// time (under target/generated-sources/annotations), containing real, plain field-by-field assignment code. So
// exercising FlightMapperImpl in these tests is exercising actual compiled mapping logic, not a mock or a stub.
//
// componentModel = "spring" only adds a @Component annotation to the generated class so Spring's ApplicationContext
// can autowire it elsewhere in the app; it has zero effect on the mapping logic itself, which is why this test can
// just `new FlightMapperImpl()` directly below with no Spring context needed at all.
//
// Because FlightMapperImpl is generated fresh on every build, it will not be found as source code anywhere in
// this repo - it only exists after compilation.
class FlightMapperTest {

    private final FlightMapper mapper = new FlightMapperImpl();

    @Test
    void toEntity_mapsAllFieldsExceptIgnored_andRenamesTimeFields() {
        LocalDateTime departure = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime arrival = LocalDateTime.of(2026, 1, 1, 12, 0);
        FlightCreateDTO dto = new FlightCreateDTO("AA100", 1L, departure, arrival, FlightStatus.SCHEDULED, "G1", 2L);

        Flight entity = mapper.toEntity(dto);

        // id is DB-generated and must never come from client input, so the source declares
        // @Mapping(target = "id", ignore = true).
        assertThat(entity.getId()).isNull();
        // route and aircraft are relationships resolved separately by the service layer (looking up the requested
        // route and aircraft by id) rather than being mappable straight from a flat create DTO, so the source
        // declares @Mapping(target = "route", ignore = true) and @Mapping(target = "aircraft", ignore = true).
        assertThat(entity.getRoute()).isNull();
        assertThat(entity.getAircraft()).isNull();
        // bookings is a nested collection populated separately as passengers book the flight after it is created,
        // not at creation time, so the source declares @Mapping(target = "bookings", ignore = true).
        assertThat(entity.getBookings()).isNull();
        assertThat(entity.getCode()).isEqualTo("AA100");
        // departureScheduled/arrivalScheduled on the entity are renamed from departureTime/arrivalTime on the DTO
        // (source declares @Mapping(target = "departureScheduled", source = "departureTime") and
        // @Mapping(target = "arrivalScheduled", source = "arrivalTime")). These assertions prove MapStruct handles
        // the DTO-vs-entity naming mismatch correctly, not just fields that happen to share the same name.
        assertThat(entity.getDepartureScheduled()).isEqualTo(departure);
        assertThat(entity.getArrivalScheduled()).isEqualTo(arrival);
        assertThat(entity.getGate()).isEqualTo("G1");
        assertThat(entity.getStatus()).isEqualTo(FlightStatus.SCHEDULED);
    }

    @Test
    void toResponse_mapsAllFields_includingNestedRouteAircraftAirportData() {
        // toResponse both flattens nested object graphs (route.originAirport.city -> originCity,
        // route.originAirport.iata -> originIata, route.destAirport.city -> destCity,
        // route.destAirport.iata -> destIata, route.id -> routeId, aircraft.id -> aircraftId) and renames fields
        // back the other direction (departureScheduled -> departureTime, arrivalScheduled -> arrivalTime). This
        // test builds a full Route/Airport/Aircraft object graph to prove MapStruct correctly traverses multiple
        // levels of nested associations, not just flat 1:1 fields.
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
