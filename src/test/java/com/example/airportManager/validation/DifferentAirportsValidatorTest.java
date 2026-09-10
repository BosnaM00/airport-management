package com.example.airportManager.validation;

import com.example.airportManager.dto.route.RouteCreateDTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DifferentAirportsValidatorTest {

    private final DifferentAirportsValidator validator = new DifferentAirportsValidator();

    private RouteCreateDTO dtoWith(Long originAirportId, Long destAirportId) {
        return new RouteCreateDTO(originAirportId, destAirportId, 100, 60);
    }

    @Test
    void isValid_shouldReturnTrue_whenDtoIsNull() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    void isValid_shouldReturnFalse_whenOriginAndDestAirportIdsAreEqual() {
        assertThat(validator.isValid(dtoWith(1L, 1L), null)).isFalse();
    }

    @Test
    void isValid_shouldReturnTrue_whenOriginAndDestAirportIdsDiffer() {
        assertThat(validator.isValid(dtoWith(1L, 2L), null)).isTrue();
    }

    @Test
    void isValid_shouldReturnTrue_whenOriginAirportIdIsNull() {
        assertThat(validator.isValid(dtoWith(null, 2L), null)).isTrue();
    }

    @Test
    void isValid_shouldReturnTrue_whenDestAirportIdIsNull() {
        assertThat(validator.isValid(dtoWith(1L, null), null)).isTrue();
    }
}
