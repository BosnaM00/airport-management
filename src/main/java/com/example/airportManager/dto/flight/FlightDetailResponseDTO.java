package com.example.airportManager.dto.flight;

import com.example.airportManager.model.FlightStatus;

import java.time.LocalDateTime;
import java.util.List;

public record FlightDetailResponseDTO(
        Long id,
        String code,
        Long routeId,
        String originCity,
        String originIata,
        String destCity,
        String destIata,
        LocalDateTime departureTime,
        LocalDateTime arrivalTime,
        FlightStatus status,
        String gate,
        Long aircraftId,
        String aircraftModel,
        List<PassengerInfoDTO> passengers
) {}
