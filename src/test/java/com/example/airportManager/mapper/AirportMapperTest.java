package com.example.airportManager.mapper;

import com.example.airportManager.dto.airport.AirportCreateDTO;
import com.example.airportManager.dto.airport.AirportResponseDTO;
import com.example.airportManager.dto.airport.AirportUpdateDTO;
import com.example.airportManager.model.Airport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// MapStruct is a compile-time code generator, not a reflection-based/runtime mapping library: given the
// @Mapper(componentModel = "spring") AirportMapper interface, the annotation processor (wired up via
// maven-compiler-plugin in pom.xml) generates a concrete AirportMapperImpl class in this same package at build
// time (under target/generated-sources/annotations), containing real, plain field-by-field assignment code. So
// exercising AirportMapperImpl in these tests is exercising actual compiled mapping logic, not a mock or a stub.
//
// componentModel = "spring" only adds a @Component annotation to the generated class so Spring's ApplicationContext
// can autowire it elsewhere in the app; it has zero effect on the mapping logic itself, which is why this test can
// just `new AirportMapperImpl()` directly below with no Spring context needed at all.
//
// Because AirportMapperImpl is generated fresh on every build, it will not be found as source code anywhere in
// this repo - it only exists after compilation.
class AirportMapperTest {

    private final AirportMapper mapper = new AirportMapperImpl();

    @Test
    void toEntity_mapsAllFieldsExceptIgnored() {
        // toEntity has no @Mapping overrides in the source interface (id has no explicit ignore here, but a
        // create DTO naturally carries no id field to map from, so MapStruct leaves the generated Airport's id at
        // its default null).
        AirportCreateDTO dto = new AirportCreateDTO("OTP", "LROP", "Henri Coanda", "Bucharest", "Romania", "Europe/Bucharest");

        Airport entity = mapper.toEntity(dto);

        assertThat(entity.getId()).isNull();
        assertThat(entity.getIata()).isEqualTo("OTP");
        assertThat(entity.getIcao()).isEqualTo("LROP");
        assertThat(entity.getName()).isEqualTo("Henri Coanda");
        assertThat(entity.getCity()).isEqualTo("Bucharest");
        assertThat(entity.getCountry()).isEqualTo("Romania");
        assertThat(entity.getTimezone()).isEqualTo("Europe/Bucharest");
    }

    @Test
    void update_shouldMutateEntityInPlace() {
        // update(@MappingTarget Airport entity, AirportUpdateDTO dto) uses MapStruct's @MappingTarget pattern: the
        // annotated parameter is mutated in place field-by-field and the method returns void, rather than
        // constructing and returning a brand-new Airport. This is the standard MapStruct idiom for partial-update
        // endpoints, where the caller loads an existing managed entity (e.g. from a repository) and applies DTO
        // values onto it so persistence-context/JPA identity is preserved.
        Airport entity = new Airport();
        entity.setId(5L);
        entity.setIata("OTP");
        entity.setIcao("LROP");
        entity.setName("Henri Coanda");
        entity.setCity("Bucharest");
        entity.setCountry("Romania");
        entity.setTimezone("Europe/Bucharest");

        AirportUpdateDTO dto = new AirportUpdateDTO("CLJ", "LRCL", "Cluj Avram Iancu", "Cluj-Napoca", "Romania", "Europe/Bucharest");

        mapper.update(entity, dto);

        // AirportUpdateDTO carries no id field, so id is never touched by the mapping; asserting it stays 5L
        // confirms the @MappingTarget entity is mutated in place rather than replaced, and that fields absent from
        // the DTO are left untouched on the entity.
        assertThat(entity.getId()).isEqualTo(5L);
        assertThat(entity.getIata()).isEqualTo("CLJ");
        assertThat(entity.getIcao()).isEqualTo("LRCL");
        assertThat(entity.getName()).isEqualTo("Cluj Avram Iancu");
        assertThat(entity.getCity()).isEqualTo("Cluj-Napoca");
        assertThat(entity.getCountry()).isEqualTo("Romania");
        assertThat(entity.getTimezone()).isEqualTo("Europe/Bucharest");
    }

    @Test
    void toResponse_mapsAllFields() {
        // toResponse has no @Mapping overrides in the source interface: it is a flat, 1:1 field mapping, so this
        // test simply confirms every Airport field round-trips into the response DTO unchanged.
        Airport entity = new Airport();
        entity.setId(5L);
        entity.setIata("OTP");
        entity.setIcao("LROP");
        entity.setName("Henri Coanda");
        entity.setCity("Bucharest");
        entity.setCountry("Romania");
        entity.setTimezone("Europe/Bucharest");

        AirportResponseDTO response = mapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.iata()).isEqualTo("OTP");
        assertThat(response.icao()).isEqualTo("LROP");
        assertThat(response.name()).isEqualTo("Henri Coanda");
        assertThat(response.city()).isEqualTo("Bucharest");
        assertThat(response.country()).isEqualTo("Romania");
        assertThat(response.timezone()).isEqualTo("Europe/Bucharest");
    }
}
