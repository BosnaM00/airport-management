package com.example.airportManager.service.impl;

import com.example.airportManager.dto.aircraft.AircraftCreateDTO;
import com.example.airportManager.dto.aircraft.AircraftResponseDTO;
import com.example.airportManager.exception.ConflictException;
import com.example.airportManager.mapper.AircraftMapper;
import com.example.airportManager.model.Aircraft;
import com.example.airportManager.repository.AircraftRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Unit test for AircraftServiceImpl (the business-logic layer sitting between the
// AircraftController and the persistence layer). This is a pure Mockito unit test, not a
// Spring integration test: @ExtendWith(MockitoExtension.class) boots Mockito's JUnit5
// integration WITHOUT starting a Spring application context, so the test class initializes
// its @Mock/@InjectMocks fields and runs in milliseconds instead of seconds. Each @Mock field
// becomes a fake implementation of one of AircraftServiceImpl's collaborators (repository /
// mapper) that returns exactly what we tell it to via when(...) stubbing - no real database is
// ever touched. @InjectMocks, on the other hand, creates a REAL instance of AircraftServiceImpl
// and wires the mocks into it through the constructor Lombok generates via
// @RequiredArgsConstructor, so the actual validation/branching logic inside the service class
// executes unmodified for every test - only its dependencies are faked. This isolation means
// that when a test fails, the bug is necessarily in AircraftServiceImpl's own logic, not in the
// database, a mapper implementation, or some other service.
//
// @ExtendWith(MockitoExtension.class) porneste Mockito pentru aceasta clasa de test:
// initializeaza automat campurile marcate cu @Mock si @InjectMocks, fara a porni
// contextul Spring. Acesta e motivul pentru care testul ruleaza in milisecunde.
@ExtendWith(MockitoExtension.class)
class AircraftServiceImplTest {

    // Fake repository collaborator: we control exactly what findById/findByTailNumber/save
    // return, no real DB is touched.
    // @Mock creeaza o implementare "falsa" a interfetei AircraftRepository.
    // Nu atinge baza de date - noi ii spunem exact ce sa returneze la fiecare apel.
    @Mock
    private AircraftRepository aircraftRepository;

    // Fake mapper collaborator: converts between DTOs and the Aircraft entity; we stub its
    // output so the test doesn't depend on the real MapStruct/manual mapping implementation.
    @Mock
    private AircraftMapper aircraftMapper;

    // The real, unmocked AircraftServiceImpl under test - Mockito injects the two mocks above
    // into it, so every branch of the service's own logic (uniqueness checks, not-found
    // handling, etc.) executes for real during these tests.
    // @InjectMocks creeaza o instanta REALA de AircraftServiceImpl si ii injecteaza
    // cele doua mock-uri de mai sus (prin constructorul generat de @RequiredArgsConstructor).
    // Asadar logica din AircraftServiceImpl ruleaza normal; doar dependintele sunt false.
    @InjectMocks
    private AircraftServiceImpl aircraftService;

    // Happy-path test: verifies that when no aircraft with the same tail number exists yet,
    // create() proceeds to map the DTO, save the entity, and return the mapped response -
    // guarding against regressions that would accidentally short-circuit the save.
    @Test
    void create_shouldSaveAircraft_whenTailNumberIsUnique() {
        // ---------- Arrange ----------
        // Pregatim datele de intrare si spunem mock-urilor ce sa raspunda.
        AircraftCreateDTO dto = new AircraftCreateDTO("YR-ABC", "Airbus A320", 180, "seatmap-a320", "ACTIVE");

        Aircraft entityToSave = new Aircraft();
        entityToSave.setTailNumber(dto.tailNumber());
        entityToSave.setModel(dto.model());
        entityToSave.setCapacity(dto.capacity());

        Aircraft savedEntity = new Aircraft();
        savedEntity.setId(1L);
        savedEntity.setTailNumber(dto.tailNumber());
        savedEntity.setModel(dto.model());
        savedEntity.setCapacity(dto.capacity());

        AircraftResponseDTO expectedResponse =
                new AircraftResponseDTO(1L, dto.tailNumber(), dto.model(), dto.capacity(), dto.seatMapRef(), dto.status());

        // Nu exista deja un avion cu acest tail number -> create() trebuie sa continue normal.
        when(aircraftRepository.findByTailNumber(dto.tailNumber())).thenReturn(Optional.empty());
        when(aircraftMapper.toEntity(dto)).thenReturn(entityToSave);
        when(aircraftRepository.save(entityToSave)).thenReturn(savedEntity);
        when(aircraftMapper.toResponse(savedEntity)).thenReturn(expectedResponse);

        // ---------- Act ----------
        // Apelam metoda reala pe care o testam.
        AircraftResponseDTO result = aircraftService.create(dto);

        // ---------- Assert ----------
        // Verificam rezultatul returnat...
        assertThat(result).isEqualTo(expectedResponse);
        // ...si, optional, ca repository-ul a fost intr-adevar apelat cu save().
        // Acest lucru confirma ca metoda nu s-a oprit mai devreme dintr-un alt motiv.
        verify(aircraftRepository).save(entityToSave);
    }

    // Duplicate-tail-number edge case: verifies the uniqueness guard rejects creating a second
    // aircraft with a tail number that already exists, and - just as importantly - that it does
    // so BEFORE any write happens (no wasted/partial save). This guards against data corruption
    // from duplicate physical-aircraft records.
    @Test
    void create_shouldThrowConflictException_whenTailNumberAlreadyExists() {
        // Arrange: repository-ul "gaseste" deja un avion cu acelasi tail number.
        AircraftCreateDTO dto = new AircraftCreateDTO("YR-ABC", "Airbus A320", 180, null, "ACTIVE");
        when(aircraftRepository.findByTailNumber(dto.tailNumber()))
                .thenReturn(Optional.of(new Aircraft()));

        // Act + Assert: verificam ca exceptia asteptata este aruncata, cu mesajul corect.
        // assertThatThrownBy(...) is AssertJ's fluent API for asserting that a lambda call
        // throws: it invokes the supplied code, catches whatever it throws, and lets us chain
        // assertions on the exception's type and message.
        assertThatThrownBy(() -> aircraftService.create(dto))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("YR-ABC");

        // Important: daca s-a aruncat exceptia inainte de save(), inseamna ca save()
        // NU trebuie sa fi fost apelat niciodata. Verificam si asta.
        // any() is a Mockito argument matcher meaning "any Aircraft instance" - used here
        // because we don't care about the exact argument, only that save() was never invoked
        // (verify(..., never())).
        verify(aircraftRepository, never()).save(any());
    }

    // Not-found edge case: verifies getById() translates a missing repository row into a
    // domain-level EntityNotFoundException carrying the requested id in its message, rather
    // than letting a null/Optional leak up to the controller layer.
    @Test
    void getById_shouldThrowEntityNotFoundException_whenAircraftDoesNotExist() {
        // Arrange
        when(aircraftRepository.findById(99L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> aircraftService.getById(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }
}
