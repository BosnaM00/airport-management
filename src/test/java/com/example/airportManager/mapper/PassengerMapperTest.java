package com.example.airportManager.mapper;

import com.example.airportManager.dto.passenger.PassengerCreateDTO;
import com.example.airportManager.dto.passenger.PassengerResponseDTO;
import com.example.airportManager.model.Passenger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PassengerMapperTest {

    private final PassengerMapper mapper = new PassengerMapperImpl();

    @Test
    void toEntity_mapsAllFieldsExceptIgnored() {
        PassengerCreateDTO dto = new PassengerCreateDTO("John", "Doe", "john.doe@example.com", "PASSPORT", "P1234567", "RO");

        Passenger entity = mapper.toEntity(dto);

        assertThat(entity.getId()).isNull();
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
