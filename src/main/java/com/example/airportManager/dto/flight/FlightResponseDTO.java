package com.example.airportManager.dto.flight;

import com.example.airportManager.model.FlightStatus;

import java.time.LocalDateTime;

public record FlightResponseDTO(
        Long id,
        String code,
        Long routeId,
        String originCity,
        String originIata,
        String destCity,
        String destIata,
        LocalDateTime departureTime,
        LocalDateTime arrivalTime,
        String gate,
        Long aircraftId,
        FlightStatus status
) {}
