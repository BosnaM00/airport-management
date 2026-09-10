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

// Unit test for FlightServiceImpl (flight creation/update/delete business logic and
// passenger-facing access control). This is a pure Mockito unit test, not a Spring integration
// test: @ExtendWith(MockitoExtension.class) boots Mockito's JUnit5 integration WITHOUT starting
// a Spring application context, so no real database, transaction manager, or JPA Criteria API
// is involved - the test class runs in milliseconds. Each @Mock field fakes one of
// FlightServiceImpl's five collaborators, returning exactly what the test stubs via when(...).
// @InjectMocks builds a REAL FlightServiceImpl and injects all five mocks into it via the
// Lombok-generated (@RequiredArgsConstructor) constructor, so the service's own logic - code
// uniqueness checks, route/aircraft resolution, ownership-based access control - executes
// unmodified. This isolates FlightServiceImpl's own rules from the database and other services,
// so a failing test points precisely at a bug in this service.
//
// Nota: metodele list()/listForPassenger() se bazeaza pe Specification<Flight> (JPA Criteria API),
// care are sens sa fie verificat printr-un test de integrare cu o baza de date reala (ex: @DataJpaTest),
// nu printr-un unit test cu mock-uri. Aici ne concentram pe metodele cu logica de business "pura".
@ExtendWith(MockitoExtension.class)
class FlightServiceImplTest {

    // Fake repository: controls what findByCode/findById/existsById/save return - lets us
    // simulate "flight code already taken" or "flight not found" without a real DB.
    @Mock
    private FlightRepository flightRepository;
    // Fake repository: supplies the Route a new/updated flight is attached to.
    @Mock
    private RouteRepository routeRepository;
    // Fake repository: supplies the optional Aircraft assigned to a flight.
    @Mock
    private AircraftRepository aircraftRepository;
    // Fake repository: used for the passenger-ownership check (does this passenger have a
    // booking on this flight?) in getByIdForPassenger().
    @Mock
    private BookingRepository bookingRepository;
    // Fake mapper: converts between DTOs and the Flight entity; stubbed so tests don't depend on
    // the real mapping implementation.
    @Mock
    private FlightMapper flightMapper;

    // The real, unmocked FlightServiceImpl under test, with the five mocks above injected.
    @InjectMocks
    private FlightServiceImpl flightService;

    // Happy-path creation: verifies that when the flight code is unique and the referenced
    // route exists, create() resolves the route, sets it (and the gate) on the mapped entity,
    // saves it, and returns the mapped response - while explicitly confirming that
    // aircraftRepository is never queried when no aircraftId is supplied (dto.aircraftId() is
    // null), guarding against an unnecessary/incorrect lookup.
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
        // any() is a Mockito argument matcher meaning "any Long id whatsoever" - used here
        // because we're asserting findById() was never called at all (never()), so the exact
        // argument it would have received doesn't matter.
        verify(aircraftRepository, never()).findById(any());
    }

    // Duplicate-code edge case: verifies create() rejects a flight whose code already exists
    // BEFORE doing any route lookup - guards against wasted work and against accidentally
    // creating two flights with the same code.
    @Test
    void create_shouldThrowConflictException_whenFlightCodeAlreadyExists() {
        // Arrange
        FlightCreateDTO dto = new FlightCreateDTO(
                "RO123", 1L, LocalDateTime.now(), LocalDateTime.now().plusHours(2),
                FlightStatus.SCHEDULED, null, null
        );
        when(flightRepository.findByCode("RO123")).thenReturn(Optional.of(new Flight()));

        // Act + Assert
        // assertThatThrownBy(...) is AssertJ's fluent exception-assertion API: it invokes the
        // lambda, catches whatever it throws, and lets us assert on the exception's type/message.
        assertThatThrownBy(() -> flightService.create(dto))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("RO123");

        verify(routeRepository, never()).findById(any());
    }

    // Authorization edge case: verifies getByIdForPassenger() denies access when the requesting
    // passenger has no booking on the flight, throwing AccessDeniedException instead of leaking
    // flight details to passengers who aren't ticketed on it - guards against an authorization
    // bypass (IDOR-style bug) where any authenticated passenger could view any flight's detail
    // by id.
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

    // Not-found edge case: verifies delete() checks existence first and throws
    // EntityNotFoundException without ever calling deleteById() when the flight doesn't exist -
    // guards against a silent no-op delete or an unchecked-exception leak from the repository.
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
