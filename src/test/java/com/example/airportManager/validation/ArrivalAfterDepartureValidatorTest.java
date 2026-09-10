package com.example.airportManager.validation;

import com.example.airportManager.dto.flight.FlightCreateDTO;
import com.example.airportManager.model.FlightStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// ArrivalAfterDepartureValidator implements Jakarta Bean Validation's ConstraintValidator<AnnotationType, TargetType>
// SPI: implementing isValid(value, context) lets us plug in a custom validation rule that goes beyond what a simple
// field-level annotation like @NotNull can express. Spring invokes this automatically whenever a controller method
// binds a @Valid @RequestBody FlightCreateDTO annotated with the custom @ArrivalAfterDeparture constraint - but in
// this test class we call isValid(...) directly, standalone, with no Spring context and no HTTP layer, because the
// validator itself is a plain POJO with no dependencies to wire up.
//
// The business rule enforced here is: a flight's arrival time must be strictly after its departure time. This rule
// spans two fields (departureTime and arrivalTime) on the same DTO, so it cannot be expressed with a single-field
// annotation - it needs a class-level validator that receives the whole FlightCreateDTO and compares both fields
// against each other.
class ArrivalAfterDepartureValidatorTest {

    private final ArrivalAfterDepartureValidator validator = new ArrivalAfterDepartureValidator();

    private FlightCreateDTO dtoWith(LocalDateTime departureTime, LocalDateTime arrivalTime) {
        return new FlightCreateDTO("AA100", 1L, departureTime, arrivalTime, FlightStatus.SCHEDULED, "G1", 1L);
    }

    // isValid's second parameter, ConstraintValidatorContext, is normally used to build custom violation messages
    // (e.g. disabling the default message and adding a field-specific one). This validator never touches it, so
    // every call below simply passes null in its place rather than mocking it.

    @Test
    void isValid_shouldReturnTrue_whenDtoIsNull() {
        // A null dto is treated as valid: it is not this validator's job to reject a missing/null request body -
        // that concern belongs to @NotNull (or the framework's deserialization step), so the validator defers by
        // returning true and lets other layers own "required-ness".
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    void isValid_shouldReturnTrue_whenArrivalIsAfterDeparture() {
        // The straightforward valid case: arrival strictly after departure satisfies the rule.
        LocalDateTime departure = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime arrival = LocalDateTime.of(2026, 1, 1, 12, 0);

        assertThat(validator.isValid(dtoWith(departure, arrival), null)).isTrue();
    }

    @Test
    void isValid_shouldReturnFalse_whenArrivalIsBeforeDeparture() {
        // Invalid ordering: arrival scheduled before departure can never happen for a real flight.
        LocalDateTime departure = LocalDateTime.of(2026, 1, 1, 12, 0);
        LocalDateTime arrival = LocalDateTime.of(2026, 1, 1, 10, 0);

        assertThat(validator.isValid(dtoWith(departure, arrival), null)).isFalse();
    }

    @Test
    void isValid_shouldReturnFalse_whenArrivalEqualsDeparture() {
        // Boundary case: the source calls arrivalTime().isAfter(departureTime()), which is a strict comparison
        // (not isAfter-or-equal), so equal timestamps are rejected - a flight cannot arrive at the exact instant
        // it departs.
        LocalDateTime same = LocalDateTime.of(2026, 1, 1, 10, 0);

        assertThat(validator.isValid(dtoWith(same, same), null)).isFalse();
    }

    @Test
    void isValid_shouldReturnTrue_whenDepartureTimeIsNull() {
        // A null individual field is treated as valid, not invalid: the source explicitly short-circuits and
        // returns true when either departureTime or arrivalTime is null. It is not this validator's responsibility
        // to enforce that the fields are present - that is the job of a separate @NotNull annotation on the field
        // itself. This validator only owns the cross-field "arrival after departure" relationship, and that
        // relationship is meaningless to check when one side is missing.
        LocalDateTime arrival = LocalDateTime.of(2026, 1, 1, 10, 0);

        assertThat(validator.isValid(dtoWith(null, arrival), null)).isTrue();
    }

    @Test
    void isValid_shouldReturnTrue_whenArrivalTimeIsNull() {
        // Same null-field-skip reasoning as above, but for the other field: arrivalTime missing also defers to
        // @NotNull rather than being flagged by this cross-field validator.
        LocalDateTime departure = LocalDateTime.of(2026, 1, 1, 10, 0);

        assertThat(validator.isValid(dtoWith(departure, null), null)).isTrue();
    }
}
