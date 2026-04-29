package com.example.airportManager.dto.route;

public record RouteResponseDTO(
        Long id,
        Long originAirportId,
        String originCity,
        String originIata,
        Long destAirportId,
        String destCity,
        String destIata,
        int distanceNm,
        int stdDurationMin
) {}
