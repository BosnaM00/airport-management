package com.example.airportManager.mapper;

import com.example.airportManager.dto.aircraft.AircraftCreateDTO;
import com.example.airportManager.dto.aircraft.AircraftResponseDTO;
import com.example.airportManager.model.Aircraft;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AircraftMapperTest {

    private final AircraftMapper mapper = new AircraftMapperImpl();

    @Test
    void toEntity_mapsAllFieldsExceptIgnored() {
        AircraftCreateDTO dto = new AircraftCreateDTO("YR-ABC", "Airbus A320", 180, "seatmap-a320", "ACTIVE");

        Aircraft entity = mapper.toEntity(dto);

        assertThat(entity.getId()).isNull();
        assertThat(entity.getFlights()).isNull();
        assertThat(entity.getTailNumber()).isEqualTo("YR-ABC");
        assertThat(entity.getModel()).isEqualTo("Airbus A320");
        assertThat(entity.getCapacity()).isEqualTo(180);
        assertThat(entity.getSeatMapRef()).isEqualTo("seatmap-a320");
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void toResponse_mapsAllFields() {
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
