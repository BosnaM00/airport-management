package com.example.airportManager.mapper;

import com.example.airportManager.dto.aircraft.AircraftCreateDTO;
import com.example.airportManager.dto.aircraft.AircraftResponseDTO;
import com.example.airportManager.model.Aircraft;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// MapStruct is a compile-time code generator, not a reflection-based/runtime mapping library: given the
// @Mapper(componentModel = "spring") AircraftMapper interface, the annotation processor (wired up via
// maven-compiler-plugin in pom.xml) generates a concrete AircraftMapperImpl class in this same package at build
// time (under target/generated-sources/annotations), containing real, plain field-by-field assignment code. So
// exercising AircraftMapperImpl in these tests is exercising actual compiled mapping logic, not a mock or a stub.
//
// componentModel = "spring" only adds a @Component annotation to the generated class so Spring's ApplicationContext
// can autowire it elsewhere in the app; it has zero effect on the mapping logic itself, which is why this test can
// just `new AircraftMapperImpl()` directly below with no Spring context needed at all.
//
// Because AircraftMapperImpl is generated fresh on every build, it will not be found as source code anywhere in
// this repo - it only exists after compilation.
class AircraftMapperTest {

    private final AircraftMapper mapper = new AircraftMapperImpl();

    @Test
    void toEntity_mapsAllFieldsExceptIgnored() {
        AircraftCreateDTO dto = new AircraftCreateDTO("YR-ABC", "Airbus A320", 180, "seatmap-a320", "ACTIVE");

        Aircraft entity = mapper.toEntity(dto);

        // id is DB-generated and must never come from client input, so the source declares
        // @Mapping(target = "id", ignore = true) and we assert it stays null after mapping a create DTO.
        assertThat(entity.getId()).isNull();
        // flights is a nested collection populated separately once the aircraft is persisted and flights are
        // scheduled against it, not at creation time, so the source declares
        // @Mapping(target = "flights", ignore = true) and we assert it stays null here too.
        assertThat(entity.getFlights()).isNull();
        assertThat(entity.getTailNumber()).isEqualTo("YR-ABC");
        assertThat(entity.getModel()).isEqualTo("Airbus A320");
        assertThat(entity.getCapacity()).isEqualTo(180);
        assertThat(entity.getSeatMapRef()).isEqualTo("seatmap-a320");
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void toResponse_mapsAllFields() {
        // toResponse has no @Mapping overrides in the source interface: it is a flat, 1:1 field mapping with no
        // ignored fields and no renames, so this test simply confirms every Aircraft field round-trips into the
        // response DTO unchanged.
        Aircraft entity = new Aircraft();
        entity.setId(1L);
        entity.setTailNumber("YR-ABC");
        entity.setModel("Airbus A320");
        entity.setCapacity(180);
        entity.setSeatMapRef("seatmap-a320");
        entity.setStatus("ACTIVE");

        AircraftResponseDTO response = mapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.tailNumber()).isEqualTo("YR-ABC");
        assertThat(response.model()).isEqualTo("Airbus A320");
        assertThat(response.capacity()).isEqualTo(180);
        assertThat(response.seatMapRef()).isEqualTo("seatmap-a320");
        assertThat(response.status()).isEqualTo("ACTIVE");
    }
}
