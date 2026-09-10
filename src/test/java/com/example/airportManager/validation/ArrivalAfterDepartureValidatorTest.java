package com.example.airportManager.validation;

import com.example.airportManager.dto.flight.FlightCreateDTO;
import com.example.airportManager.model.FlightStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ArrivalAfterDepartureValidatorTest {

    private final ArrivalAfterDepartureValidator validator = new ArrivalAfterDepartureValidator();

    private FlightCreateDTO dtoWith(LocalDateTime departureTime, LocalDateTime arrivalTime) {
        return new FlightCreateDTO("AA100", 1L, departureTime, arrivalTime, FlightStatus.SCHEDULED, "G1", 1L);
    }

    @Test
    void isValid_shouldReturnTrue_whenDtoIsNull() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    void isValid_shouldReturnTrue_whenArrivalIsAfterDeparture() {
        LocalDateTime departure = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime arrival = LocalDateTime.of(2026, 1, 1, 12, 0);

        assertThat(validator.isValid(dtoWith(departure, arrival), null)).isTrue();
    }

    @Test
    void isValid_shouldReturnFalse_whenArrivalIsBeforeDeparture() {
        LocalDateTime departure = LocalDateTime.of(2026, 1, 1, 12, 0);
        LocalDateTime arrival = LocalDateTime.of(2026, 1, 1, 10, 0);

        assertThat(validator.isValid(dtoWith(departure, arrival), null)).isFalse();
    }

    @Test
    void isValid_shouldReturnFalse_whenArrivalEqualsDeparture() {
        LocalDateTime same = LocalDateTime.of(2026, 1, 1, 10, 0);

        assertThat(validator.isValid(dtoWith(same, same), null)).isFalse();
    }

    @Test
    void isValid_shouldReturnTrue_whenDepartureTimeIsNull() {
        LocalDateTime arrival = LocalDateTime.of(2026, 1, 1, 10, 0);

        assertThat(validator.isValid(dtoWith(null, arrival), null)).isTrue();
    }

    @Test
    void isValid_shouldReturnTrue_whenArrivalTimeIsNull() {
        LocalDateTime departure = LocalDateTime.of(2026, 1, 1, 10, 0);

        assertThat(validator.isValid(dtoWith(departure, null), null)).isTrue();
    }
}
