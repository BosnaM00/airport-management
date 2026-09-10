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

@ExtendWith(MockitoExtension.class)
class PassengerServiceImplTest {

    @Mock
    private PassengerRepository passengerRepository;

    @Mock
    private PassengerMapper passengerMapper;

    @InjectMocks
    private PassengerServiceImpl passengerService;

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

    @Test
    void getById_shouldThrowEntityNotFoundException_whenPassengerDoesNotExist() {
        // Arrange
        when(passengerRepository.findById(99L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> passengerService.getById(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }
}
