package com.example.airportManager.service.impl;

import com.example.airportManager.dto.flight.FlightCreateDTO;
import com.example.airportManager.dto.flight.FlightResponseDTO;
import com.example.airportManager.exception.ConflictException;
import com.example.airportManager.mapper.FlightMapper;
import com.example.airportManager.model.Flight;
import com.example.airportManager.model.FlightStatus;
import com.example.airportManager.model.Route;
import com.example.airportManager.repository.AircraftRepository;
import com.example.airportManager.repository.BookingRepository;
import com.example.airportManager.repository.FlightRepository;
import com.example.airportManager.repository.RouteRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

// Nota: metodele list()/listForPassenger() se bazeaza pe Specification<Flight> (JPA Criteria API),
// care are sens sa fie verificat printr-un test de integrare cu o baza de date reala (ex: @DataJpaTest),
// nu printr-un unit test cu mock-uri. Aici ne concentram pe metodele cu logica de business "pura".
@ExtendWith(MockitoExtension.class)
class FlightServiceImplTest {

    @Mock
    private FlightRepository flightRepository;
    @Mock
    private RouteRepository routeRepository;
    @Mock
    private AircraftRepository aircraftRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private FlightMapper flightMapper;

    @InjectMocks
    private FlightServiceImpl flightService;

    @Test
    void create_shouldSaveFlight_whenCodeIsUniqueAndRouteExists() {
        // Arrange
        FlightCreateDTO dto = new FlightCreateDTO(
                "RO123", 1L,
                LocalDateTime.of(2026, 8, 1, 10, 0),
                LocalDateTime.of(2026, 8, 1, 12, 0),
                FlightStatus.SCHEDULED, "A1", null
        );

        Route route = new Route();
        route.setId(1L);

        Flight entityToSave = new Flight();
        Flight savedFlight = new Flight();
        savedFlight.setId(5L);

        FlightResponseDTO expectedResponse = new FlightResponseDTO(
                5L, "RO123", 1L, "Bucuresti", "OTP", "Paris", "CDG",
                dto.departureTime(), dto.arrivalTime(), "A1", null, FlightStatus.SCHEDULED
        );

        when(flightRepository.findByCode("RO123")).thenReturn(Optional.empty());
        when(routeRepository.findById(1L)).thenReturn(Optional.of(route));
        when(flightMapper.toEntity(dto)).thenReturn(entityToSave);
        when(flightRepository.save(entityToSave)).thenReturn(savedFlight);
        when(flightMapper.toResponse(savedFlight)).thenReturn(expectedResponse);

        // Act
        FlightResponseDTO result = flightService.create(dto);

        // Assert
        assertThat(result).isEqualTo(expectedResponse);
        assertThat(entityToSave.getRoute()).isEqualTo(route);
        assertThat(entityToSave.getGate()).isEqualTo("A1");
        // dto.aircraftId() e null, deci aircraftRepository nu ar trebui interogat deloc.
        verify(aircraftRepository, never()).findById(any());
    }

    @Test
    void create_shouldThrowConflictException_whenFlightCodeAlreadyExists() {
        // Arrange
        FlightCreateDTO dto = new FlightCreateDTO(
                "RO123", 1L, LocalDateTime.now(), LocalDateTime.now().plusHours(2),
                FlightStatus.SCHEDULED, null, null
        );
        when(flightRepository.findByCode("RO123")).thenReturn(Optional.of(new Flight()));

        // Act + Assert
        assertThatThrownBy(() -> flightService.create(dto))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("RO123");

        verify(routeRepository, never()).findById(any());
    }

    @Test
    void getByIdForPassenger_shouldThrowAccessDeniedException_whenPassengerHasNoBookingOnFlight() {
        // Arrange
        Long flightId = 7L;
        UUID passengerId = UUID.randomUUID();
        Flight flight = new Flight();
        flight.setId(flightId);

        when(flightRepository.findById(flightId)).thenReturn(Optional.of(flight));
        when(bookingRepository.existsByFlightIdAndPassengerProfileUserId(flightId, passengerId)).thenReturn(false);

        // Act + Assert
        assertThatThrownBy(() -> flightService.getByIdForPassenger(flightId, passengerId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void delete_shouldThrowEntityNotFoundException_whenFlightDoesNotExist() {
        // Arrange
        when(flightRepository.existsById(1L)).thenReturn(false);

        // Act + Assert
        assertThatThrownBy(() -> flightService.delete(1L))
                .isInstanceOf(EntityNotFoundException.class);

        verify(flightRepository, never()).deleteById(any());
    }
}
