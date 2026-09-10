package com.example.airportManager.service.impl;

import com.example.airportManager.dto.booking.BookingCreateDTO;
import com.example.airportManager.dto.booking.BookingResponseDTO;
import com.example.airportManager.mapper.BookingMapper;
import com.example.airportManager.model.Aircraft;
import com.example.airportManager.model.Booking;
import com.example.airportManager.model.BookingStatus;
import com.example.airportManager.model.Flight;
import com.example.airportManager.model.PassengerProfile;
import com.example.airportManager.repository.BookingRepository;
import com.example.airportManager.repository.FlightRepository;
import com.example.airportManager.repository.PassengerProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

// Unit test for BookingServiceImpl (create/cancel booking business logic, including seat-
// capacity enforcement and ownership checks). This is a pure Mockito unit test, not a Spring
// integration test: @ExtendWith(MockitoExtension.class) boots Mockito's JUnit5 integration
// WITHOUT starting a Spring application context, so no real database or transaction manager is
// involved and the test class runs in milliseconds. Each @Mock field fakes one of
// BookingServiceImpl's collaborators, returning exactly what the test stubs via when(...).
// @InjectMocks builds a REAL BookingServiceImpl and injects the four mocks below into it via
// the Lombok-generated (@RequiredArgsConstructor) constructor, so the service's own logic -
// capacity checks, PNR generation, status transitions, ownership validation - executes
// unmodified. This isolates BookingServiceImpl's own rules from the database and other
// services, so a failing test points precisely at a bug in this service's logic (e.g. an
// off-by-one in the capacity check, or a missing ownership guard).
@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    // Fake repository: controls what findById/countConfirmedByFlightId/save return - lets us
    // simulate "flight has N confirmed bookings" without touching a real DB.
    @Mock
    private BookingRepository bookingRepository;
    // Fake repository: supplies the PassengerProfile for a given user id (or empty, to simulate
    // a missing profile).
    @Mock
    private PassengerProfileRepository passengerProfileRepository;
    // Fake repository: supplies the Flight (with its Aircraft, whose capacity drives the
    // seat-availability check) for a given flight id.
    @Mock
    private FlightRepository flightRepository;
    // Fake mapper: converts the Booking entity to a BookingResponseDTO; stubbed so tests don't
    // depend on the real mapping implementation.
    @Mock
    private BookingMapper bookingMapper;

    // The real, unmocked BookingServiceImpl under test, with the four mocks above injected.
    @InjectMocks
    private BookingServiceImpl bookingService;

    // Happy-path booking creation: verifies that when confirmed bookings on the flight (50) are
    // below the aircraft's capacity (180), create() builds a CONFIRMED booking with a generated
    // PNR, saves it, and returns the mapped response. The fixture needs a full
    // PassengerProfile -> Flight -> Aircraft chain because the capacity check reads
    // flight.getAircraft().getCapacity().
    @Test
    void create_shouldCreateConfirmedBooking_whenSeatsAreAvailable() {
        // Arrange
        UUID userId = UUID.randomUUID();
        BookingCreateDTO dto = new BookingCreateDTO(1L);

        PassengerProfile passengerProfile = new PassengerProfile();
        passengerProfile.setUserId(userId);

        Aircraft aircraft = new Aircraft();
        aircraft.setCapacity(180);
        Flight flight = new Flight();
        flight.setId(1L);
        flight.setCode("RO123");
        flight.setAircraft(aircraft);

        BookingResponseDTO expectedResponse =
                new BookingResponseDTO(1L, "PNR12345", userId, 1L, BookingStatus.CONFIRMED);

        when(passengerProfileRepository.findById(userId)).thenReturn(Optional.of(passengerProfile));
        when(flightRepository.findById(1L)).thenReturn(Optional.of(flight));
        when(bookingRepository.countConfirmedByFlightId(1L)).thenReturn(50L); // sub capacitate (180)
        when(bookingRepository.save(org.mockito.ArgumentMatchers.any(Booking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(bookingMapper.toResponse(org.mockito.ArgumentMatchers.any(Booking.class))).thenReturn(expectedResponse);

        // Act
        BookingResponseDTO result = bookingService.create(dto, userId);

        // Assert
        assertThat(result).isEqualTo(expectedResponse);

        // Verificam ca booking-ul construit intern are statusul si PNR-ul generate corect.
        // ArgumentCaptor captures the actual object passed into a mocked method call (here,
        // bookingRepository.save(...)), so we can assert on its internal state afterwards - the
        // service builds the Booking entity itself, so this is the only way to inspect the
        // status/PNR it assigned before persisting, without changing the mock's return value.
        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        org.mockito.Mockito.verify(bookingRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(captor.getValue().getPnr()).startsWith("PNR");
    }

    // Capacity edge case: verifies that once confirmed bookings on a flight (2) reach the
    // aircraft's seat capacity (2), create() rejects the new booking with an
    // IllegalStateException instead of overbooking the flight - guards against the classic
    // off-by-one bug of allowing confirmed == capacity to still succeed.
    @Test
    void create_shouldThrowIllegalStateException_whenFlightIsFullyBooked() {
        // Arrange
        UUID userId = UUID.randomUUID();
        BookingCreateDTO dto = new BookingCreateDTO(1L);

        PassengerProfile passengerProfile = new PassengerProfile();
        passengerProfile.setUserId(userId);

        Aircraft aircraft = new Aircraft();
        aircraft.setCapacity(2);
        Flight flight = new Flight();
        flight.setId(1L);
        flight.setCode("RO123");
        flight.setAircraft(aircraft);

        when(passengerProfileRepository.findById(userId)).thenReturn(Optional.of(passengerProfile));
        when(flightRepository.findById(1L)).thenReturn(Optional.of(flight));
        when(bookingRepository.countConfirmedByFlightId(1L)).thenReturn(2L); // = capacitate -> plin

        // Act + Assert
        // assertThatThrownBy(...) is AssertJ's fluent exception-assertion API: it invokes the
        // lambda, catches whatever it throws, and lets us assert on the exception's type/message.
        assertThatThrownBy(() -> bookingService.create(dto, userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RO123");
    }

    // Ownership edge case: verifies cancel() rejects a cancellation attempt made by a user who
    // is not the owner of the booking, throwing AccessDeniedException instead of silently
    // cancelling someone else's reservation - guards against an authorization bypass (IDOR-style
    // bug) where any authenticated user could cancel any booking by id.
    @Test
    void cancel_shouldThrowAccessDeniedException_whenUserDoesNotOwnBooking() {
        // Arrange
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        PassengerProfile owner = new PassengerProfile();
        owner.setUserId(ownerId);

        Booking booking = new Booking();
        booking.setId(1L);
        booking.setPassengerProfile(owner);
        booking.setStatus(BookingStatus.CONFIRMED);

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        // Act + Assert: incercam sa anulam rezervarea altcuiva.
        assertThatThrownBy(() -> bookingService.cancel(1L, otherUserId))
                .isInstanceOf(AccessDeniedException.class);
    }
}
