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

// @ExtendWith(MockitoExtension.class) porneste Mockito pentru aceasta clasa de test:
// initializeaza automat campurile marcate cu @Mock si @InjectMocks, fara a porni
// contextul Spring. Acesta e motivul pentru care testul ruleaza in milisecunde.
@ExtendWith(MockitoExtension.class)
class AircraftServiceImplTest {

    // @Mock creeaza o implementare "falsa" a interfetei AircraftRepository.
    // Nu atinge baza de date - noi ii spunem exact ce sa returneze la fiecare apel.
    @Mock
    private AircraftRepository aircraftRepository;

    @Mock
    private AircraftMapper aircraftMapper;

    // @InjectMocks creeaza o instanta REALA de AircraftServiceImpl si ii injecteaza
    // cele doua mock-uri de mai sus (prin constructorul generat de @RequiredArgsConstructor).
    // Asadar logica din AircraftServiceImpl ruleaza normal; doar dependintele sunt false.
    @InjectMocks
    private AircraftServiceImpl aircraftService;

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

    @Test
    void create_shouldThrowConflictException_whenTailNumberAlreadyExists() {
        // Arrange: repository-ul "gaseste" deja un avion cu acelasi tail number.
        AircraftCreateDTO dto = new AircraftCreateDTO("YR-ABC", "Airbus A320", 180, null, "ACTIVE");
        when(aircraftRepository.findByTailNumber(dto.tailNumber()))
                .thenReturn(Optional.of(new Aircraft()));

        // Act + Assert: verificam ca exceptia asteptata este aruncata, cu mesajul corect.
        assertThatThrownBy(() -> aircraftService.create(dto))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("YR-ABC");

        // Important: daca s-a aruncat exceptia inainte de save(), inseamna ca save()
        // NU trebuie sa fi fost apelat niciodata. Verificam si asta.
        verify(aircraftRepository, never()).save(any());
    }

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
