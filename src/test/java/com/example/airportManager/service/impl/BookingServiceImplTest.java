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

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private PassengerProfileRepository passengerProfileRepository;
    @Mock
    private FlightRepository flightRepository;
    @Mock
    private BookingMapper bookingMapper;

    @InjectMocks
    private BookingServiceImpl bookingService;

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
        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        org.mockito.Mockito.verify(bookingRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(captor.getValue().getPnr()).startsWith("PNR");
    }

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
        assertThatThrownBy(() -> bookingService.create(dto, userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RO123");
    }

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
