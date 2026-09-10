package com.example.airportManager.mapper;

import com.example.airportManager.dto.booking.BookingCreateDTO;
import com.example.airportManager.dto.booking.BookingResponseDTO;
import com.example.airportManager.model.Booking;
import com.example.airportManager.model.BookingStatus;
import com.example.airportManager.model.Flight;
import com.example.airportManager.model.PassengerProfile;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// MapStruct is a compile-time code generator, not a reflection-based/runtime mapping library: given the
// @Mapper(componentModel = "spring") BookingMapper interface, the annotation processor (wired up via
// maven-compiler-plugin in pom.xml) generates a concrete BookingMapperImpl class in this same package at build
// time (under target/generated-sources/annotations), containing real, plain field-by-field assignment code. So
// exercising BookingMapperImpl in these tests is exercising actual compiled mapping logic, not a mock or a stub.
//
// componentModel = "spring" only adds a @Component annotation to the generated class so Spring's ApplicationContext
// can autowire it elsewhere in the app; it has zero effect on the mapping logic itself, which is why this test can
// just `new BookingMapperImpl()` directly below with no Spring context needed at all.
//
// Because BookingMapperImpl is generated fresh on every build, it will not be found as source code anywhere in
// this repo - it only exists after compilation.
class BookingMapperTest {

    private final BookingMapper mapper = new BookingMapperImpl();

    @Test
    void toEntity_mapsAllFieldsExceptIgnored() {
        BookingCreateDTO dto = new BookingCreateDTO(42L);

        Booking entity = mapper.toEntity(dto);

        // id is DB-generated and must never come from client input, so the source declares
        // @Mapping(target = "id", ignore = true).
        assertThat(entity.getId()).isNull();
        // passengerProfile and flight are relationships resolved separately by the service layer (looking up the
        // authenticated passenger and the requested flight by id) rather than being mappable straight from a flat
        // create DTO, so the source declares @Mapping(target = "passengerProfile", ignore = true) and
        // @Mapping(target = "flight", ignore = true).
        assertThat(entity.getPassengerProfile()).isNull();
        assertThat(entity.getFlight()).isNull();
        // pnr and status are server-computed (a generated booking reference and an initial status set by business
        // logic), not client-supplied, so the source declares @Mapping(target = "pnr", ignore = true) and
        // @Mapping(target = "status", ignore = true).
        assertThat(entity.getPnr()).isNull();
        assertThat(entity.getStatus()).isNull();
    }

    @Test
    void toResponse_mapsAllFields_includingNestedPassengerAndFlightIds() {
        // toResponse renames/flattens nested object graphs: the source declares
        // @Mapping(target = "passengerId", source = "passengerProfile.userId") and
        // @Mapping(target = "flightId", source = "flight.id"), so this test proves MapStruct correctly traverses
        // into the nested PassengerProfile and Flight associations to pull out their ids, rather than only handling
        // flat 1:1 fields.
        UUID userId = UUID.randomUUID();
        PassengerProfile passengerProfile = new PassengerProfile();
        passengerProfile.setUserId(userId);

        Flight flight = new Flight();
        flight.setId(7L);

        Booking entity = new Booking();
        entity.setId(1L);
        entity.setPassengerProfile(passengerProfile);
        entity.setFlight(flight);
        entity.setPnr("ABC123");
        entity.setStatus(BookingStatus.CONFIRMED);

        BookingResponseDTO response = mapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.pnr()).isEqualTo("ABC123");
        assertThat(response.passengerId()).isEqualTo(userId);
        assertThat(response.flightId()).isEqualTo(7L);
        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
    }
}
