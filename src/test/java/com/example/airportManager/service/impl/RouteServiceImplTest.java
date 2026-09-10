package com.example.airportManager.service.impl;

import com.example.airportManager.dto.route.RouteCreateDTO;
import com.example.airportManager.dto.route.RouteResponseDTO;
import com.example.airportManager.exception.ConflictException;
import com.example.airportManager.mapper.RouteMapper;
import com.example.airportManager.model.Airport;
import com.example.airportManager.model.Route;
import com.example.airportManager.repository.AirportRepository;
import com.example.airportManager.repository.RouteRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RouteServiceImplTest {

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private AirportRepository airportRepository;

    @Mock
    private RouteMapper routeMapper;

    @InjectMocks
    private RouteServiceImpl routeService;

    @Test
    void create_shouldSaveRoute_whenAirportsExistAndRouteIsNew() {
        // Arrange
        RouteCreateDTO dto = new RouteCreateDTO(1L, 2L, 500, 90);

        Airport origin = new Airport();
        origin.setId(1L);
        Airport dest = new Airport();
        dest.setId(2L);

        Route entityToSave = new Route();
        Route savedRoute = new Route();
        savedRoute.setId(10L);

        RouteResponseDTO expectedResponse =
                new RouteResponseDTO(10L, 1L, "Bucuresti", "OTP", 2L, "Paris", "CDG", 500, 90);

        when(routeRepository.existsByOriginAirportIdAndDestAirportId(1L, 2L)).thenReturn(false);
        when(airportRepository.findById(1L)).thenReturn(Optional.of(origin));
        when(airportRepository.findById(2L)).thenReturn(Optional.of(dest));
        when(routeMapper.toEntity(dto)).thenReturn(entityToSave);
        when(routeRepository.save(entityToSave)).thenReturn(savedRoute);
        when(routeMapper.toResponse(savedRoute)).thenReturn(expectedResponse);

        // Act
        RouteResponseDTO result = routeService.create(dto);

        // Assert
        assertThat(result).isEqualTo(expectedResponse);
        // Verificam ca originea si destinatia au fost setate corect pe entitatea trimisa spre salvare,
        // nu doar ca save() a fost apelat cu orice argument.
        assertThat(entityToSave.getOriginAirport()).isEqualTo(origin);
        assertThat(entityToSave.getDestAirport()).isEqualTo(dest);
    }

    @Test
    void create_shouldThrowConflictException_whenRouteAlreadyExistsForAirportPair() {
        // Arrange
        RouteCreateDTO dto = new RouteCreateDTO(1L, 2L, 500, 90);
        when(routeRepository.existsByOriginAirportIdAndDestAirportId(1L, 2L)).thenReturn(true);

        // Act + Assert
        assertThatThrownBy(() -> routeService.create(dto))
                .isInstanceOf(ConflictException.class);

        // Daca ruta exista deja, nu ar trebui cautate aeroporturile si nici salvat nimic.
        verify(airportRepository, never()).findById(any());
        verify(routeRepository, never()).save(any());
    }

    @Test
    void create_shouldThrowEntityNotFoundException_whenOriginAirportDoesNotExist() {
        // Arrange
        RouteCreateDTO dto = new RouteCreateDTO(1L, 2L, 500, 90);
        when(routeRepository.existsByOriginAirportIdAndDestAirportId(1L, 2L)).thenReturn(false);
        when(airportRepository.findById(1L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> routeService.create(dto))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Origin airport");
    }
}
