package com.example.airportManager.service.impl;

import com.example.airportManager.dto.passenger.PassengerCreateDTO;
import com.example.airportManager.dto.passenger.PassengerResponseDTO;
import com.example.airportManager.mapper.PassengerMapper;
import com.example.airportManager.model.Passenger;
import com.example.airportManager.repository.PassengerRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link PassengerServiceImpl}.
 *
 * <p>This is a pure Mockito unit test: {@code @ExtendWith(MockitoExtension.class)} boots
 * Mockito's JUnit5 integration WITHOUT starting a Spring context, so it runs in milliseconds.
 * The {@code @Mock} fields become fake implementations of {@link PassengerServiceImpl}'s
 * collaborators - the repository and the mapper - that return exactly what each test stubs via
 * {@code when(...)}; no real database and no real MapStruct/manual mapping logic runs. The
 * {@code @InjectMocks} field is a REAL instance of {@link PassengerServiceImpl}; Mockito builds
 * it and wires the mocks above into it via its Lombok {@code @RequiredArgsConstructor}
 * constructor, so the service's own orchestration logic (call mapper, call repository, call
 * mapper again, or throw {@link EntityNotFoundException}) executes unmodified.
 *
 * <p>Faking both the repository and the mapper keeps these tests focused purely on whether
 * {@link PassengerServiceImpl} calls its collaborators in the right order with the right
 * arguments - a failure here means a bug in the service's orchestration, not in the mapper's
 * field-mapping logic or the database.
 */
@ExtendWith(MockitoExtension.class)
class PassengerServiceImplTest {

    // Fake PassengerRepository - we control exactly what save/findById return; no real DB is touched.
    @Mock
    private PassengerRepository passengerRepository;

    // Fake PassengerMapper - we control exactly what toEntity/toResponse return, so this test never
    // exercises the mapper's real field-mapping logic (that belongs in a mapper-specific test).
    @Mock
    private PassengerMapper passengerMapper;

    // Real, unmocked PassengerServiceImpl under test, with the mocks above injected via its constructor.
    @InjectMocks
    private PassengerServiceImpl passengerService;

    /**
     * Happy-path scenario: verifies create() wires its collaborators together correctly - it
     * must pass the DTO to the mapper to get an entity, pass that exact entity to the
     * repository to get a saved entity, then pass that exact saved entity back to the mapper to
     * build the response. Because every mock stub is keyed on a specific object identity
     * (mappedEntity, savedEntity), this also implicitly checks the service isn't swapping in
     * the wrong instance at any step.
     */
    @Test
    void create_shouldMapAndSavePassenger() {
        // Arrange
        PassengerCreateDTO dto = new PassengerCreateDTO("Ion", "Popescu", "ion@test.com", "PASSPORT", "AB1234", "RO");
        Passenger mappedEntity = new Passenger();
        Passenger savedEntity = new Passenger();
        savedEntity.setId(1L);
        PassengerResponseDTO expectedResponse = new PassengerResponseDTO(1L, "Ion", "Popescu", "ion@test.com", "RO");

        when(passengerMapper.toEntity(dto)).thenReturn(mappedEntity);
        when(passengerRepository.save(mappedEntity)).thenReturn(savedEntity);
        when(passengerMapper.toResponse(savedEntity)).thenReturn(expectedResponse);

        // Act
        PassengerResponseDTO result = passengerService.create(dto);

        // Assert
        assertThat(result).isEqualTo(expectedResponse);
    }

    /**
     * Edge case: no Passenger row exists for the requested id (Optional.empty() simulates that
     * repository miss). Verifies getById() throws EntityNotFoundException with the missing id
     * in the message, rather than returning null or letting an unchecked exception propagate -
     * important for producing a clean 404 at the API layer.
     */
    @Test
    void getById_shouldThrowEntityNotFoundException_whenPassengerDoesNotExist() {
        // Arrange
        when(passengerRepository.findById(99L)).thenReturn(Optional.empty());

        // Act + Assert
        // assertThatThrownBy is AssertJ's fluent exception-assertion API: it runs the lambda and lets us
        // chain isInstanceOf/hasMessageContaining checks on whatever it throws.
        assertThatThrownBy(() -> passengerService.getById(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }
}
