package com.example.airportManager.service.impl;

import com.example.airportManager.dto.airport.AirportUpdateDTO;
import com.example.airportManager.dto.airport.AirportResponseDTO;
import com.example.airportManager.mapper.AirportMapper;
import com.example.airportManager.model.Airport;
import com.example.airportManager.repository.AirportRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AirportServiceImplTest {

    @Mock
    private AirportRepository airportRepository;

    @Mock
    private AirportMapper airportMapper;

    @InjectMocks
    private AirportServiceImpl airportService;

    @Test
    void update_shouldSaveUpdatedAirport_whenAirportExists() {
        // Arrange
        Long id = 1L;
        Airport existing = new Airport();
        existing.setId(id);
        existing.setIata("OTP");

        AirportUpdateDTO dto = new AirportUpdateDTO("OTP", "LROP", "Henri Coanda", "Bucuresti", "Romania", "EET");
        AirportResponseDTO expectedResponse =
                new AirportResponseDTO(id, "OTP", "LROP", "Henri Coanda", "Bucuresti", "Romania", "EET");

        when(airportRepository.findById(id)).thenReturn(Optional.of(existing));
        // airportMapper.update(...) e o metoda void care modifica "existing" pe loc (MapStruct @MappingTarget).
        // Nu trebuie sa facem when(...) pentru metode void - Mockito nu face nimic implicit, ceea ce e ok aici
        // pentru ca noi verificam doar ca save() a fost apelat cu entitatea corecta mai jos.
        when(airportRepository.save(existing)).thenReturn(existing);
        when(airportMapper.toResponse(existing)).thenReturn(expectedResponse);

        // Act
        AirportResponseDTO result = airportService.update(id, dto);

        // Assert
        assertThat(result).isEqualTo(expectedResponse);
        verify(airportMapper).update(existing, dto);
        verify(airportRepository).save(existing);
    }

    @Test
    void update_shouldThrowEntityNotFoundException_whenAirportDoesNotExist() {
        // Arrange
        Long id = 42L;
        AirportUpdateDTO dto = new AirportUpdateDTO("OTP", "LROP", "Henri Coanda", "Bucuresti", "Romania", "EET");
        when(airportRepository.findById(id)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> airportService.update(id, dto))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("42");

        // Daca aeroportul nu exista, nu ar trebui sa se incerce niciun save.
        verify(airportRepository, never()).save(any());
    }

    @Test
    void delete_shouldRemoveAirport_whenItExists() {
        // Arrange
        Long id = 1L;
        when(airportRepository.existsById(id)).thenReturn(true);

        // Act
        airportService.delete(id);

        // Assert: pentru metode void, verificam ca actiunea a avut loc, nu o valoare de return.
        verify(airportRepository).deleteById(id);
    }
}
