package com.example.airportManager.mapper;

import com.example.airportManager.dto.airport.AirportCreateDTO;
import com.example.airportManager.dto.airport.AirportResponseDTO;
import com.example.airportManager.dto.airport.AirportUpdateDTO;
import com.example.airportManager.model.Airport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AirportMapperTest {

    private final AirportMapper mapper = new AirportMapperImpl();

    @Test
    void toEntity_mapsAllFieldsExceptIgnored() {
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
