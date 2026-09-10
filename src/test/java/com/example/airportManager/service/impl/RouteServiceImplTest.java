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

/**
 * Unit test for {@link RouteServiceImpl}.
 *
 * <p>This is a pure Mockito unit test: {@code @ExtendWith(MockitoExtension.class)} boots
 * Mockito's JUnit5 integration WITHOUT starting a Spring context, so it runs in milliseconds.
 * The {@code @Mock} fields below become fake implementations of {@link RouteServiceImpl}'s
 * collaborators - the route repository, the airport repository, and the mapper - that return
 * exactly what each test stubs via {@code when(...)}; no real database and no real mapping logic
 * runs. The {@code @InjectMocks} field is a REAL instance of {@link RouteServiceImpl}; Mockito
 * builds it and wires the mocks into it via its Lombok {@code @RequiredArgsConstructor}
 * constructor, so the service's own branching logic (duplicate-route check, origin/destination
 * lookups, throwing {@link ConflictException}/{@link EntityNotFoundException}) executes
 * unmodified.
 *
 * <p>Isolating the service this way means a failing assertion points precisely at a bug in
 * {@link RouteServiceImpl}'s own validation/branching, not in the database or the mapper.
 */
@ExtendWith(MockitoExtension.class)
class RouteServiceImplTest {

    // Fake RouteRepository - we control exactly what existsByOriginAirportIdAndDestAirportId/save return; no real DB is touched.
    @Mock
    private RouteRepository routeRepository;

    // Fake AirportRepository - stands in for the origin/destination airport existence checks the service performs.
    @Mock
    private AirportRepository airportRepository;

    // Fake RouteMapper - we control exactly what toEntity/toResponse return, so real mapping logic is never exercised here.
    @Mock
    private RouteMapper routeMapper;

    // Real, unmocked RouteServiceImpl under test, with the mocks above injected via its constructor.
    @InjectMocks
    private RouteServiceImpl routeService;

    /**
     * Happy-path scenario: no duplicate route exists and both the origin and destination
     * airports are found, so create() should map the DTO to an entity, attach the resolved
     * Airport entities to it, save it, and return the mapped response. The extra assertions on
     * entityToSave verify the service actually sets originAirport/destAirport on the entity
     * BEFORE saving it, not just that save() was called with some argument.
     */
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

    /**
     * Edge case: a route already exists for this origin/destination pair. Verifies create()
     * short-circuits with ConflictException immediately after the existence check, and - crucially -
     * never proceeds to look up the airports or save anything. Guards against wasted lookups and
     * against accidentally creating a duplicate route despite the early check.
     */
    @Test
    void create_shouldThrowConflictException_whenRouteAlreadyExistsForAirportPair() {
        // Arrange
        RouteCreateDTO dto = new RouteCreateDTO(1L, 2L, 500, 90);
        when(routeRepository.existsByOriginAirportIdAndDestAirportId(1L, 2L)).thenReturn(true);

        // Act + Assert
        // assertThatThrownBy is AssertJ's fluent exception-assertion API: it invokes the lambda and lets us
        // chain isInstanceOf checks on whatever it throws.
        assertThatThrownBy(() -> routeService.create(dto))
                .isInstanceOf(ConflictException.class);

        // Daca ruta exista deja, nu ar trebui cautate aeroporturile si nici salvat nimic.
        // verify(mock, never())... asserts a method was NEVER invoked on the mock; any() is a Mockito
        // argument matcher that accepts any value of that type - used here because we don't care which id.
        verify(airportRepository, never()).findById(any());
        verify(routeRepository, never()).save(any());
    }

    /**
     * Edge case: the route is not a duplicate, but the origin airport id doesn't resolve to any
     * Airport row (Optional.empty() simulates that repository miss). Verifies create() fails
     * fast with EntityNotFoundException mentioning "Origin airport" specifically - distinguishing
     * this failure from the "Destination airport not found" case - so callers get an accurate
     * error message instead of a generic/ambiguous one.
     */
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
