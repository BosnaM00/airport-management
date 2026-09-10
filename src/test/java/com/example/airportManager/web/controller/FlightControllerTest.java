package com.example.airportManager.web.controller;

import com.example.airportManager.dto.flight.FlightDetailResponseDTO;
import com.example.airportManager.dto.flight.FlightResponseDTO;
import com.example.airportManager.model.FlightStatus;
import com.example.airportManager.model.Role;
import com.example.airportManager.model.RoleName;
import com.example.airportManager.model.User;
import com.example.airportManager.model.UserStatus;
import com.example.airportManager.security.CustomUserDetails;
import com.example.airportManager.security.CustomUserDetailsService;
import com.example.airportManager.security.JwtAuthenticationFilter;
import com.example.airportManager.security.JwtUtil;
import com.example.airportManager.security.SecurityConfig;
import com.example.airportManager.service.FlightService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// FlightController mixes method-level @PreAuthorize with role-branching business logic
// inside getById: the same @AuthenticationPrincipal endpoint calls a different
// FlightService method depending on whether the caller is staff (EMPLOYEE/ADMIN) or a
// PASSENGER, so both branches need to be verified explicitly with Mockito.verify(...).
@WebMvcTest(FlightController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class FlightControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private FlightService flightService;

    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    private static final String VALID_FLIGHT_CREATE_JSON = """
            {
                "code": "AB123",
                "routeId": 1,
                "departureTime": "2026-10-01T10:00:00",
                "arrivalTime": "2026-10-01T12:00:00",
                "status": "SCHEDULED",
                "gate": "A1",
                "aircraftId": 1
            }
            """;

    private CustomUserDetails principalWithRole(RoleName roleName) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(roleName.name().toLowerCase() + "@example.com");
        user.setPasswordHash("hash");
        user.setStatus(UserStatus.ACTIVE);
        Role role = new Role();
        role.setId(1L);
        role.setName(roleName);
        user.setRoles(Set.of(role));
        return new CustomUserDetails(user);
    }

    private CustomUserDetails passengerPrincipal() {
        return principalWithRole(RoleName.PASSENGER);
    }

    private CustomUserDetails employeePrincipal() {
        return principalWithRole(RoleName.EMPLOYEE);
    }

    private FlightResponseDTO sampleFlightResponse() {
        return new FlightResponseDTO(1L, "AB123", 1L, "Bucharest", "OTP", "Paris", "CDG",
                LocalDateTime.of(2026, 10, 1, 10, 0), LocalDateTime.of(2026, 10, 1, 12, 0),
                "A1", 1L, FlightStatus.SCHEDULED);
    }

    private FlightDetailResponseDTO sampleFlightDetailResponse() {
        return new FlightDetailResponseDTO(1L, "AB123", 1L, "Bucharest", "OTP", "Paris", "CDG",
                LocalDateTime.of(2026, 10, 1, 10, 0), LocalDateTime.of(2026, 10, 1, 12, 0),
                FlightStatus.SCHEDULED, "A1", 1L, "A320", List.of());
    }

    // ---- GET /api/flights (list) ----

    @Test
    void list_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(get("/api/flights"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PASSENGER")
    void list_returns200_forPassengerRole() throws Exception {
        when(flightService.list(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleFlightResponse())));

        mvc.perform(get("/api/flights"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void list_returns200_forEmployeeRole() throws Exception {
        when(flightService.list(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/flights"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_returns200_forAdminRole() throws Exception {
        when(flightService.list(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/flights"))
                .andExpect(status().isOk());
    }

    // ---- GET /api/flights/my (listMyFlights) ----

    @Test
    void listMyFlights_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(get("/api/flights/my"))
                .andExpect(status().isForbidden());
    }

    // SecurityConfig's HTTP-level rule for GET /api/flights/** allows PASSENGER, EMPLOYEE
    // and ADMIN alike (it doesn't distinguish the /my sub-path), so an EMPLOYEE request
    // passes the filter chain and reaches the controller. There, the method-level
    // @PreAuthorize("hasRole('PASSENGER')") on listMyFlights denies it and throws
    // AccessDeniedException *during* dispatch (i.e. inside the controller invocation),
    // which GlobalExceptionHandler's generic Exception.class handler catches and maps to
    // 500 rather than 403 (no dedicated AccessDeniedException handler is registered, and
    // this happens before Spring Security's filter-level 403 translation gets a chance).
    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void listMyFlights_returns500_forEmployeeRole() throws Exception {
        mvc.perform(get("/api/flights/my"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void listMyFlights_returns200_forPassengerPrincipal() throws Exception {
        when(flightService.listForPassenger(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleFlightResponse())));

        mvc.perform(get("/api/flights/my").with(user(passengerPrincipal())))
                .andExpect(status().isOk());
    }

    // ---- GET /api/flights/{id} (getById) - role-branching business logic ----

    @Test
    void getById_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(get("/api/flights/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getById_callsGetByIdWithIncludePassengersTrue_forEmployeePrincipal() throws Exception {
        when(flightService.getById(anyLong(), eq(true))).thenReturn(sampleFlightDetailResponse());

        mvc.perform(get("/api/flights/1").with(user(employeePrincipal())))
                .andExpect(status().isOk());

        verify(flightService).getById(anyLong(), eq(true));
    }

    @Test
    void getById_callsGetByIdForPassenger_forPassengerPrincipal() throws Exception {
        when(flightService.getByIdForPassenger(anyLong(), any())).thenReturn(sampleFlightDetailResponse());

        mvc.perform(get("/api/flights/1").with(user(passengerPrincipal())))
                .andExpect(status().isOk());

        verify(flightService).getByIdForPassenger(anyLong(), any());
    }

    // ---- POST /api/flights (create) ----

    @Test
    void create_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(post("/api/flights")
                        .contentType("application/json")
                        .content(VALID_FLIGHT_CREATE_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PASSENGER")
    void create_returns403_forPassengerRole() throws Exception {
        mvc.perform(post("/api/flights")
                        .contentType("application/json")
                        .content(VALID_FLIGHT_CREATE_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void create_returns201_forEmployeeRole() throws Exception {
        when(flightService.create(any())).thenReturn(sampleFlightResponse());

        mvc.perform(post("/api/flights")
                        .contentType("application/json")
                        .content(VALID_FLIGHT_CREATE_JSON))
                .andExpect(status().isCreated());
    }

    // ---- PUT /api/flights/{id} (update) ----

    @Test
    void update_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(put("/api/flights/1")
                        .contentType("application/json")
                        .content(VALID_FLIGHT_CREATE_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PASSENGER")
    void update_returns403_forPassengerRole() throws Exception {
        mvc.perform(put("/api/flights/1")
                        .contentType("application/json")
                        .content(VALID_FLIGHT_CREATE_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void update_returns200_forEmployeeRole() throws Exception {
        when(flightService.update(anyLong(), any())).thenReturn(sampleFlightResponse());

        mvc.perform(put("/api/flights/1")
                        .contentType("application/json")
                        .content(VALID_FLIGHT_CREATE_JSON))
                .andExpect(status().isOk());
    }

    // ---- DELETE /api/flights/{id} (delete) ----

    @Test
    void delete_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(delete("/api/flights/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PASSENGER")
    void delete_returns403_forPassengerRole() throws Exception {
        mvc.perform(delete("/api/flights/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void delete_returns204_forEmployeeRole() throws Exception {
        mvc.perform(delete("/api/flights/1"))
                .andExpect(status().isNoContent());

        verify(flightService).delete(1L);
    }
}
