package com.example.airportManager.validation;

import com.example.airportManager.dto.route.RouteCreateDTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// DifferentAirportsValidator implements Jakarta Bean Validation's ConstraintValidator<AnnotationType, TargetType>
// SPI: implementing isValid(value, context) lets us write custom @Valid-triggered validation logic beyond what
// simple per-field annotations like @NotNull can express. Spring invokes this automatically whenever a controller
// method binds a @Valid @RequestBody RouteCreateDTO annotated with the custom @DifferentAirports constraint - but
// here it is tested directly, standalone, with no Spring context and no HTTP layer, since the validator has no
// dependencies of its own.
//
// The business rule enforced here is: a route's origin and destination airport must not be the same airport (a
// flight cannot depart from and arrive at the same place). This rule spans two fields (originAirportId and
// destAirportId) on the same DTO, so it cannot be expressed with a single-field annotation - it needs a class-level
// validator that receives the whole RouteCreateDTO and compares both fields against each other.
class DifferentAirportsValidatorTest {

    private final DifferentAirportsValidator validator = new DifferentAirportsValidator();

    private RouteCreateDTO dtoWith(Long originAirportId, Long destAirportId) {
        return new RouteCreateDTO(originAirportId, destAirportId, 100, 60);
    }

    // isValid's second parameter, ConstraintValidatorContext, is normally used to build custom violation messages.
    // This validator never touches it, so every call below simply passes null in its place rather than mocking it.

    @Test
    void isValid_shouldReturnTrue_whenDtoIsNull() {
        // A null dto is treated as valid: it is not this validator's job to reject a missing/null request body -
        // that concern belongs to @NotNull (or the framework's deserialization step), so the validator defers by
        // returning true.
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    void isValid_shouldReturnFalse_whenOriginAndDestAirportIdsAreEqual() {
        // Invalid case: origin and destination resolve to the same airport id, which violates the "different
        // airports" rule.
        assertThat(validator.isValid(dtoWith(1L, 1L), null)).isFalse();
    }

    @Test
    void isValid_shouldReturnTrue_whenOriginAndDestAirportIdsDiffer() {
        // The straightforward valid case: distinct origin and destination airport ids satisfy the rule.
        assertThat(validator.isValid(dtoWith(1L, 2L), null)).isTrue();
    }

    @Test
    void isValid_shouldReturnTrue_whenOriginAirportIdIsNull() {
        // A null individual field is treated as valid, not invalid: the source explicitly short-circuits and
        // returns true when either originAirportId or destAirportId is null. It is not this validator's
        // responsibility to enforce that the fields are present - that is the job of a separate @NotNull
        // annotation on the field itself. This validator only owns the cross-field "must differ" relationship,
        // which is meaningless to check when one side is missing.
        assertThat(validator.isValid(dtoWith(null, 2L), null)).isTrue();
    }

    @Test
    void isValid_shouldReturnTrue_whenDestAirportIdIsNull() {
        // Same null-field-skip reasoning as above, but for the other field: destAirportId missing also defers to
        // @NotNull rather than being flagged by this cross-field validator.
        assertThat(validator.isValid(dtoWith(1L, null), null)).isTrue();
    }
}
