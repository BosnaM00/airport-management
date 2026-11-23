package com.example.airportManager.dto.flight;

import com.example.airportManager.model.FlightStatus;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record FlightFilterDTO(
        String code,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
        Long routeId,
        FlightStatus status,
        String origin,
        String destination,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
) {}
