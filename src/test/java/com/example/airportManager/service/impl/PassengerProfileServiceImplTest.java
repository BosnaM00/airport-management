package com.example.airportManager.service.impl;

import com.example.airportManager.dto.profile.PassengerProfileResponseDTO;
import com.example.airportManager.dto.profile.PassengerProfileUpdateDTO;
import com.example.airportManager.model.PassengerProfile;
import com.example.airportManager.model.User;
import com.example.airportManager.repository.PassengerProfileRepository;
import com.example.airportManager.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link PassengerProfileServiceImpl}.
 *
 * <p>This is a pure Mockito unit test: {@code @ExtendWith(MockitoExtension.class)} boots
 * Mockito's JUnit5 integration WITHOUT starting a Spring context, so the test runs in
 * milliseconds. The {@code @Mock} fields below become fake implementations of
 * {@link PassengerProfileServiceImpl}'s collaborators (the two repositories) that return exactly
 * what each test stubs via {@code when(...)} - no real database is touched. The
 * {@code @InjectMocks} field is a REAL instance of {@link PassengerProfileServiceImpl}; Mockito
 * builds it and wires the mocks into it through its Lombok {@code @RequiredArgsConstructor}
 * constructor, so the service's own logic (lookups, field copying, DTO assembly, throwing
 * {@link EntityNotFoundException}) executes unmodified - only its dependencies are faked.
 *
 * <p>This isolates the service's own behavior from the database, so a failing assertion here
 * points precisely at a bug in {@link PassengerProfileServiceImpl}, not in JPA/repository wiring.
 */
@ExtendWith(MockitoExtension.class)
class PassengerProfileServiceImplTest {

    // Fake PassengerProfileRepository - we control exactly what findById/save return; no real DB is touched.
    @Mock
    private PassengerProfileRepository passengerProfileRepository;

    // Fake UserRepository - stands in for the "does this user exist" lookup the service performs first.
    @Mock
    private UserRepository userRepository;

    // Real, unmocked PassengerProfileServiceImpl under test, with the two mocks above injected via its constructor.
    @InjectMocks
    private PassengerProfileServiceImpl passengerProfileService;

    /**
     * Happy-path scenario: both the User and the PassengerProfile exist, so updateMyProfile
     * should copy every field from the DTO onto the in-memory profile entity, persist it, and
     * return a response DTO built manually in the service (there is no mapper here) that
     * reflects the new values. Guards against fields being dropped during the copy or the
     * response being built from stale/pre-update state.
     */
    @Test
    void updateMyProfile_shouldUpdateAndReturnProfile_whenUserAndProfileExist() {
        // Arrange
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("ion@test.com");
        user.setPhone("0722000000");

        PassengerProfile profile = new PassengerProfile();
        profile.setUserId(userId);

        PassengerProfileUpdateDTO dto =
                new PassengerProfileUpdateDTO("PASSPORT", "AB9999", "RO", "GOLD", "0733000000");

        // Optional.of(...) simulates a repository "hit" for both the user and the profile lookups.
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passengerProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        // save() intoarce exact obiectul primit, ca in majoritatea repository-urilor JPA reale.
        when(passengerProfileRepository.save(profile)).thenReturn(profile);

        // Act
        PassengerProfileResponseDTO result = passengerProfileService.updateMyProfile(userId, dto);

        // Assert: aici verificam continutul DTO-ului construit manual in service (nu exista mapper).
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.docType()).isEqualTo("PASSPORT");
        assertThat(result.docNumber()).isEqualTo("AB9999");
        assertThat(result.loyaltyTier()).isEqualTo("GOLD");
        // Verificam si ca profilul din memorie a fost efectiv modificat inainte de salvare.
        assertThat(profile.getDocNumber()).isEqualTo("AB9999");
    }

    /**
     * Edge case: the User itself does not exist (Optional.empty() simulates that repository
     * miss). Verifies the service fails fast with EntityNotFoundException on the very first
     * lookup, before ever touching the passengerProfileRepository - important so a bad/unknown
     * userId can't slip through to a NullPointerException further down.
     */
    @Test
    void getMyProfile_shouldThrowEntityNotFoundException_whenUserDoesNotExist() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act + Assert
        // assertThatThrownBy is AssertJ's fluent exception-assertion API: it invokes the lambda and lets us
        // chain assertions (here just isInstanceOf) on whatever it throws.
        assertThatThrownBy(() -> passengerProfileService.getMyProfile(userId))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
