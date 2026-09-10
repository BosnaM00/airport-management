package com.example.airportManager.mapper;

import com.example.airportManager.dto.route.RouteCreateDTO;
import com.example.airportManager.dto.route.RouteResponseDTO;
import com.example.airportManager.model.Airport;
import com.example.airportManager.model.Route;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// MapStruct is a compile-time code generator, not a reflection-based/runtime mapping library: given the
// @Mapper(componentModel = "spring") RouteMapper interface, the annotation processor (wired up via
// maven-compiler-plugin in pom.xml) generates a concrete RouteMapperImpl class in this same package at build
// time (under target/generated-sources/annotations), containing real, plain field-by-field assignment code. So
// exercising RouteMapperImpl in these tests is exercising actual compiled mapping logic, not a mock or a stub.
//
// componentModel = "spring" only adds a @Component annotation to the generated class so Spring's ApplicationContext
// can autowire it elsewhere in the app; it has zero effect on the mapping logic itself, which is why this test can
// just `new RouteMapperImpl()` directly below with no Spring context needed at all.
//
// Because RouteMapperImpl is generated fresh on every build, it will not be found as source code anywhere in
// this repo - it only exists after compilation.
class RouteMapperTest {

    private final RouteMapper mapper = new RouteMapperImpl();

    @Test
    void toEntity_mapsAllFieldsExceptIgnored() {
        RouteCreateDTO dto = new RouteCreateDTO(1L, 2L, 1500, 180);

        Route entity = mapper.toEntity(dto);

        // id is DB-generated and must never come from client input, so the source declares
        // @Mapping(target = "id", ignore = true).
        assertThat(entity.getId()).isNull();
        // originAirport and destAirport are relationships resolved separately by the service layer (looking up
        // the referenced airports by the ids carried on the DTO) rather than being mappable straight from a flat
        // create DTO, so the source declares @Mapping(target = "originAirport", ignore = true) and
        // @Mapping(target = "destAirport", ignore = true).
        assertThat(entity.getOriginAirport()).isNull();
        assertThat(entity.getDestAirport()).isNull();
        // flights is a nested collection populated separately as flights are scheduled on this route after it is
        // created, not at creation time, so the source declares @Mapping(target = "flights", ignore = true).
        assertThat(entity.getFlights()).isNull();
        assertThat(entity.getDistanceNm()).isEqualTo(1500);
        assertThat(entity.getStdDurationMin()).isEqualTo(180);
    }

    @Test
    void toResponse_mapsAllFields_includingNestedAirportData() {
        // toResponse flattens nested object graphs: the source declares
        // @Mapping(target = "originAirportId", source = "originAirport.id"),
        // @Mapping(target = "originCity", source = "originAirport.city"),
        // @Mapping(target = "originIata", source = "originAirport.iata"), and the mirrored dest* mappings. This
        // test builds real nested Airport entities on the Route to prove MapStruct correctly traverses the nested
        // object graph, not just flat 1:1 fields.
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
