package com.example.airportManager.service.impl;

import com.example.airportManager.dto.profile.EmployeeProfileResponseDTO;
import com.example.airportManager.dto.profile.EmployeeProfileUpdateDTO;
import com.example.airportManager.model.EmployeeProfile;
import com.example.airportManager.model.User;
import com.example.airportManager.repository.EmployeeProfileRepository;
import com.example.airportManager.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link EmployeeProfileServiceImpl}.
 *
 * <p>This is a pure Mockito unit test: {@code @ExtendWith(MockitoExtension.class)} boots
 * Mockito's JUnit5 integration WITHOUT starting a Spring context, so the test class runs in
 * milliseconds instead of the seconds a full {@code @SpringBootTest} would take. The {@code @Mock}
 * fields below become fake implementations of {@link EmployeeProfileServiceImpl}'s collaborators
 * (the two repositories) that return exactly what each test tells them to via {@code when(...)}
 * stubbing - no real database is ever touched. The {@code @InjectMocks} field is a REAL instance
 * of {@link EmployeeProfileServiceImpl}; Mockito constructs it and wires the mocks above into it
 * through its Lombok-generated {@code @RequiredArgsConstructor} constructor, so the service's
 * actual business logic (lookups, field copying, throwing {@link EntityNotFoundException}) runs
 * unmodified - only its dependencies are faked.
 *
 * <p>Isolating the service this way means a failing assertion points precisely at a bug in
 * {@link EmployeeProfileServiceImpl} itself, rather than in the database, another service, or
 * Spring wiring.
 */
@ExtendWith(MockitoExtension.class)
class EmployeeProfileServiceImplTest {

    // Fake EmployeeProfileRepository - we control exactly what findById/save return; no real DB is touched.
    @Mock
    private EmployeeProfileRepository employeeProfileRepository;

    // Fake UserRepository - stands in for the "does this user exist" lookup the service performs first.
    @Mock
    private UserRepository userRepository;

    // Real, unmocked EmployeeProfileServiceImpl under test, with the two mocks above injected via its constructor.
    @InjectMocks
    private EmployeeProfileServiceImpl employeeProfileService;

    /**
     * Happy-path scenario: both the User and the EmployeeProfile exist for the given id, so
     * updateMyProfile should copy every field from the DTO onto the profile, persist it, and
     * return a response DTO reflecting the new values. Guards against the update silently
     * dropping fields or returning stale data instead of the freshly-saved profile.
     */
    @Test
    void updateMyProfile_shouldUpdateAndReturnProfile_whenUserAndProfileExist() {
        // Arrange
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("angajat@test.com");
        user.setPhone("0722000000");

        EmployeeProfile profile = new EmployeeProfile();
        profile.setUserId(userId);

        EmployeeProfileUpdateDTO dto =
                new EmployeeProfileUpdateDTO("Dispecer", "Operatiuni", "G2", LocalDate.of(2022, 3, 1));

        // Optional.of(...) simulates a repository "hit" - the row is found - for both the user and the profile lookups.
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(employeeProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(employeeProfileRepository.save(profile)).thenReturn(profile);

        // Act
        EmployeeProfileResponseDTO result = employeeProfileService.updateMyProfile(userId, dto);

        // Assert
        assertThat(result.position()).isEqualTo("Dispecer");
        assertThat(result.department()).isEqualTo("Operatiuni");
        assertThat(result.hireDate()).isEqualTo(LocalDate.of(2022, 3, 1));
    }

    /**
     * Edge case: the User exists but has no associated EmployeeProfile row (Optional.empty()
     * simulates that repository miss). Verifies the service throws EntityNotFoundException with
     * a message mentioning "Employee profile" rather than returning null or throwing an
     * unrelated/unchecked exception - important so callers (e.g. a REST controller) can map this
     * to a proper 404 response.
     */
    @Test
    void getMyProfile_shouldThrowEntityNotFoundException_whenProfileDoesNotExist() {
        // Arrange
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(employeeProfileRepository.findById(userId)).thenReturn(Optional.empty());

        // Act + Assert
        // assertThatThrownBy is AssertJ's fluent API for asserting that a lambda throws: it executes the
        // supplied call and lets us chain isInstanceOf/hasMessageContaining checks on the thrown exception.
        assertThatThrownBy(() -> employeeProfileService.getMyProfile(userId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Employee profile");
    }
}
