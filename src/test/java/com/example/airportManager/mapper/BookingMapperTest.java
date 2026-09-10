package com.example.airportManager.mapper;

import com.example.airportManager.dto.booking.BookingCreateDTO;
import com.example.airportManager.dto.booking.BookingResponseDTO;
import com.example.airportManager.model.Booking;
import com.example.airportManager.model.BookingStatus;
import com.example.airportManager.model.Flight;
import com.example.airportManager.model.PassengerProfile;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BookingMapperTest {

    private final BookingMapper mapper = new BookingMapperImpl();

    @Test
    void toEntity_mapsAllFieldsExceptIgnored() {
        BookingCreateDTO dto = new BookingCreateDTO(42L);

        Booking entity = mapper.toEntity(dto);

        assertThat(entity.getId()).isNull();
        assertThat(entity.getPassengerProfile()).isNull();
        assertThat(entity.getFlight()).isNull();
        assertThat(entity.getPnr()).isNull();
        assertThat(entity.getStatus()).isNull();
    }

    @Test
    void toResponse_mapsAllFields_includingNestedPassengerAndFlightIds() {
        UUID userId = UUID.randomUUID();
        PassengerProfile passengerProfile = new PassengerProfile();
        passengerProfile.setUserId(userId);

        Flight flight = new Flight();
        flight.setId(7L);

        Booking entity = new Booking();
        entity.setId(1L);
        entity.setPassengerProfile(passengerProfile);
        entity.setFlight(flight);
        entity.setPnr("ABC123");
        entity.setStatus(BookingStatus.CONFIRMED);

        BookingResponseDTO response = mapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.pnr()).isEqualTo("ABC123");
        assertThat(response.passengerId()).isEqualTo(userId);
        assertThat(response.flightId()).isEqualTo(7L);
        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
    }
}
