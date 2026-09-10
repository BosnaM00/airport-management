package com.example.airportManager.mapper;

import com.example.airportManager.dto.passenger.PassengerCreateDTO;
import com.example.airportManager.dto.passenger.PassengerResponseDTO;
import com.example.airportManager.model.Passenger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// MapStruct is a compile-time code generator, not a reflection-based/runtime mapping library: given the
// @Mapper(componentModel = "spring") PassengerMapper interface, the annotation processor (wired up via
// maven-compiler-plugin in pom.xml) generates a concrete PassengerMapperImpl class in this same package at build
// time (under target/generated-sources/annotations), containing real, plain field-by-field assignment code. So
// exercising PassengerMapperImpl in these tests is exercising actual compiled mapping logic, not a mock or a stub.
//
// componentModel = "spring" only adds a @Component annotation to the generated class so Spring's ApplicationContext
// can autowire it elsewhere in the app; it has zero effect on the mapping logic itself, which is why this test can
// just `new PassengerMapperImpl()` directly below with no Spring context needed at all.
//
// Because PassengerMapperImpl is generated fresh on every build, it will not be found as source code anywhere in
// this repo - it only exists after compilation.
class PassengerMapperTest {

    private final PassengerMapper mapper = new PassengerMapperImpl();

    @Test
    void toEntity_mapsAllFieldsExceptIgnored() {
        PassengerCreateDTO dto = new PassengerCreateDTO("John", "Doe", "john.doe@example.com", "PASSPORT", "P1234567", "RO");

        Passenger entity = mapper.toEntity(dto);

        // id is DB-generated and must never come from client input, so the source declares
        // @Mapping(target = "id", ignore = true).
        assertThat(entity.getId()).isNull();
        // loyaltyTier and emergencyContact are not part of initial passenger registration - loyaltyTier is
        // assigned/updated by separate business logic and emergencyContact is filled in later by the passenger
        // themselves, so the source declares @Mapping(target = "loyaltyTier", ignore = true) and
        // @Mapping(target = "emergencyContact", ignore = true).
        assertThat(entity.getLoyaltyTier()).isNull();
        assertThat(entity.getEmergencyContact()).isNull();
        assertThat(entity.getFirstName()).isEqualTo("John");
        assertThat(entity.getLastName()).isEqualTo("Doe");
        assertThat(entity.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(entity.getDocType()).isEqualTo("PASSPORT");
        assertThat(entity.getDocNumber()).isEqualTo("P1234567");
        assertThat(entity.getNationality()).isEqualTo("RO");
    }

    @Test
    void toResponse_mapsAllFields() {
        // toResponse has no @Mapping overrides in the source interface: it is a flat, 1:1 field mapping, though
        // note the DTO intentionally omits docType/docNumber/loyaltyTier/emergencyContact fields (not asserted
        // below) since the response record simply does not declare those components.
        Passenger entity = new Passenger();
        entity.setId(1L);
        entity.setFirstName("John");
        entity.setLastName("Doe");
        entity.setEmail("john.doe@example.com");
        entity.setDocType("PASSPORT");
        entity.setDocNumber("P1234567");
        entity.setNationality("RO");
        entity.setLoyaltyTier("GOLD");
        entity.setEmergencyContact("Jane Doe");

        PassengerResponseDTO response = mapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.firstName()).isEqualTo("John");
        assertThat(response.lastName()).isEqualTo("Doe");
        assertThat(response.email()).isEqualTo("john.doe@example.com");
        assertThat(response.nationality()).isEqualTo("RO");
    }
}
