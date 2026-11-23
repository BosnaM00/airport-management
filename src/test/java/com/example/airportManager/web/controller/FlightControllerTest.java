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

// Slice test for FlightController - the richest test class in the suite, since
// FlightController combines several layers of security AND has role-branching business
// logic inside one of its own endpoints.
//
// @WebMvcTest(FlightController.class) boots ONLY the web layer: this controller, Spring
// MVC infrastructure, and the security filter chain - not the full application context.
// @WebMvcTest applies its own restrictive default security configuration, so the real
// SecurityConfig must be explicitly re-imported via @Import for the actual authorization
// rules to be exercised. JwtAuthenticationFilter is imported as a REAL bean (not
// @MockitoBean): a Mockito mock of a Servlet Filter never calls chain.doFilter(...),
// which would silently swallow every request - the single most important gotcha in this
// test suite.
//
// Two independent authorization layers apply to "/api/flights/**", and BOTH are
// exercised by this class (defense in depth):
//   1. SecurityConfig enforces HTTP-filter-level rules keyed on HTTP method: GET is open
//      to hasAnyRole("PASSENGER","EMPLOYEE","ADMIN"), while POST/PUT/DELETE are
//      restricted to hasAnyRole("EMPLOYEE","ADMIN"). This runs before the request ever
//      reaches the controller.
//   2. The controller itself carries per-method @PreAuthorize annotations (list/getById:
//      PASSENGER+EMPLOYEE+ADMIN, listMyFlights: PASSENGER only, create/update/delete:
//      EMPLOYEE+ADMIN only), evaluated via Spring's method security once dispatch
//      reaches the controller bean.
// These two layers don't always agree on granularity (see
// listMyFlights_returns500_forEmployeeRole below for the resulting quirk), which is
// exactly why both are tested independently here rather than assuming one implies
// the other.
//
// FlightController also mixes method-level @PreAuthorize with role-branching business
// logic inside getById: the same @AuthenticationPrincipal endpoint calls a different
// FlightService method depending on whether the caller is staff (EMPLOYEE/ADMIN) or a
// PASSENGER, so both branches need to be verified explicitly with Mockito.verify(...) -
// both branches can return 200 OK, so only verify(...) proves which service method (and
// therefore which code path) actually ran.
@WebMvcTest(FlightController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class FlightControllerTest {

    // Entry point for simulated HTTP requests against the web layer loaded by
    // @WebMvcTest; runs through the real filter chain (SecurityConfig +
    // JwtAuthenticationFilter included).
    @Autowired
    private MockMvc mvc;

    // @MockitoBean replaces the real FlightService bean with a Mockito mock (modern,
    // non-deprecated replacement for @MockBean); only methods stubbed with when(...)
    // below return data, and Mockito.verify(...) is used in several tests to confirm
    // exactly which FlightService method the controller actually invoked.
    @MockitoBean
    private FlightService flightService;

    // Mocked only to satisfy SecurityConfig's / JwtAuthenticationFilter's real-bean
    // dependencies; not stubbed or exercised directly, since these tests install the
    // Authentication principal directly (via SecurityMockMvcRequestPostProcessors.user
    // or @WithMockUser) instead of going through real JWT parsing / user lookup.
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

    // Builds a real CustomUserDetails wrapping a User with a single role of the given
    // RoleName, so @AuthenticationPrincipal CustomUserDetails resolves to a usable
    // instance. Used together with
    // SecurityMockMvcRequestPostProcessors.user(UserDetails), which installs the
    // returned instance directly as the Authentication principal, bypassing the
    // mocked CustomUserDetailsService entirely - necessary because getById's business
    // logic branches on userDetails.getUser().hasRole(...), which requires a real User
    // with real Role data, not just a @WithMockUser authority string.
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

    // Convenience wrapper around principalWithRole for the PASSENGER-branch tests.
    private CustomUserDetails passengerPrincipal() {
        return principalWithRole(RoleName.PASSENGER);
    }

    // Convenience wrapper around principalWithRole for the staff-branch (EMPLOYEE) tests.
    private CustomUserDetails employeePrincipal() {
        return principalWithRole(RoleName.EMPLOYEE);
    }

    // Sample DTO returned by list/create/update stubs - content is arbitrary, only used
    // to let the controller produce a 2xx response body.
    private FlightResponseDTO sampleFlightResponse() {
        return new FlightResponseDTO(1L, "AB123", 1L, "Bucharest", "OTP", "Paris", "CDG",
                LocalDateTime.of(2026, 10, 1, 10, 0), LocalDateTime.of(2026, 10, 1, 12, 0),
                "A1", 1L, FlightStatus.SCHEDULED);
    }

    // Sample DTO returned by getById's two branches (staff getById(id,true) and
    // passenger getByIdForPassenger(id,userId)) - content is arbitrary, only used to
    // let the controller produce a 200 OK response body.
    private FlightDetailResponseDTO sampleFlightDetailResponse() {
        return new FlightDetailResponseDTO(1L, "AB123", 1L, "Bucharest", "OTP", "Paris", "CDG",
                LocalDateTime.of(2026, 10, 1, 10, 0), LocalDateTime.of(2026, 10, 1, 12, 0),
                FlightStatus.SCHEDULED, "A1", 1L, "A320", List.of());
    }

    // ---- GET /api/flights (list) ----

    // No Authorization/principal supplied: anonymous caller. SecurityConfig's
    // filter-level GET rule (hasAnyRole("PASSENGER","EMPLOYEE","ADMIN")) rejects an
    // anonymous request before it reaches the controller's own @PreAuthorize. No
    // custom AuthenticationEntryPoint is configured, so anonymous requests get 403
    // Forbidden via the default Http403ForbiddenEntryPoint - never 401.
    @Test
    void list_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(get("/api/flights"))
                .andExpect(status().isForbidden());
    }

    // PASSENGER is allowed by both SecurityConfig's filter-level GET rule and the
    // controller's @PreAuthorize("hasAnyRole('PASSENGER','EMPLOYEE','ADMIN')") on
    // list(...); with FlightService stubbed, the controller responds 200 OK.
    @Test
    @WithMockUser(roles = "PASSENGER")
    void list_returns200_forPassengerRole() throws Exception {
        when(flightService.list(any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleFlightResponse())));

        mvc.perform(get("/api/flights"))
                .andExpect(status().isOk());
    }

    // EMPLOYEE is likewise allowed by both layers for GET /api/flights -> 200 OK.
    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void list_returns200_forEmployeeRole() throws Exception {
        when(flightService.list(any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/flights"))
                .andExpect(status().isOk());
    }

    // ADMIN is likewise allowed by both layers for GET /api/flights -> 200 OK.
    @Test
    @WithMockUser(roles = "ADMIN")
    void list_returns200_forAdminRole() throws Exception {
        when(flightService.list(any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/flights"))
                .andExpect(status().isOk());
    }

    // ---- GET /api/flights/my (listMyFlights) ----

    // No Authorization/principal supplied: anonymous caller is rejected by
    // SecurityConfig's filter-level GET rule for "/api/flights/**" before the request
    // ever reaches the controller (or its own PASSENGER-only @PreAuthorize) -> 403
    // Forbidden via the default Http403ForbiddenEntryPoint, not 401.
    @Test
    void listMyFlights_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(get("/api/flights/my"))
                .andExpect(status().isForbidden());
    }

    // Documents a real, intentionally-preserved quirk of the CURRENT application
    // behavior (not the "ideal" behavior): SecurityConfig's HTTP-level rule for
    // GET /api/flights/** allows PASSENGER, EMPLOYEE and ADMIN alike (it doesn't
    // distinguish the /my sub-path), so an EMPLOYEE request passes the filter chain and
    // reaches the controller. There, the method-level @PreAuthorize("hasRole('PASSENGER')")
    // on listMyFlights denies it and throws AccessDeniedException *during* dispatch
    // (i.e. inside the controller invocation, after the filter chain already let the
    // request through), which GlobalExceptionHandler's generic Exception.class handler
    // catches and maps to 500 rather than 403 (no dedicated AccessDeniedException
    // handler is registered, so it falls into the catch-all instead of Spring
    // Security's filter-level 403 translation, which never gets a chance to run here).
    // This test locks in that actual behavior as a regression guard, not as an
    // endorsement of it.
    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void listMyFlights_returns500_forEmployeeRole() throws Exception {
        mvc.perform(get("/api/flights/my"))
                .andExpect(status().isInternalServerError());
    }

    // Real PASSENGER principal (via SecurityMockMvcRequestPostProcessors.user(...))
    // passes both the filter-level GET rule and @PreAuthorize("hasRole('PASSENGER')");
    // FlightService.listForPassenger(...) is stubbed, so the controller responds 200 OK.
    @Test
    void listMyFlights_returns200_forPassengerPrincipal() throws Exception {
        when(flightService.listForPassenger(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleFlightResponse())));

        mvc.perform(get("/api/flights/my").with(user(passengerPrincipal())))
                .andExpect(status().isOk());
    }

    // ---- GET /api/flights/{id} (getById) - role-branching business logic ----
    //
    // getById is unusual in this codebase: the security layer only decides WHETHER the
    // request is allowed in (PASSENGER/EMPLOYEE/ADMIN via both SecurityConfig's
    // filter-level GET rule and the controller's @PreAuthorize), but WHICH service
    // method actually runs is decided by business logic inside the controller method
    // itself, branching on userDetails.getUser().hasRole(...): staff (EMPLOYEE/ADMIN)
    // get flightService.getById(id, true) (full detail incl. passengers), while
    // passengers get flightService.getByIdForPassenger(id, userId) (passenger-scoped
    // view). Both branches can return 200 OK with a FlightDetailResponseDTO body, so
    // asserting on the HTTP status alone would not prove the correct branch executed -
    // Mockito.verify(...) is used below specifically to prove which FlightService
    // method was actually invoked for each principal role.

    // No Authorization/principal supplied: anonymous caller is rejected by
    // SecurityConfig's filter-level GET rule for "/api/flights/**" -> 403 Forbidden
    // (default Http403ForbiddenEntryPoint), never 401.
    @Test
    void getById_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(get("/api/flights/1"))
                .andExpect(status().isForbidden());
    }

    // Real EMPLOYEE principal (built via principalWithRole/employeePrincipal) passes
    // both security layers, and userDetails.getUser().hasRole(EMPLOYEE) is true inside
    // the controller, so it must take the staff branch: flightService.getById(id, true).
    // verify(...) confirms that exact overload/argument combination was called - the
    // 200 OK status alone wouldn't distinguish this from the passenger branch below.
    @Test
    void getById_callsGetByIdWithIncludePassengersTrue_forEmployeePrincipal() throws Exception {
        when(flightService.getById(anyLong(), eq(true))).thenReturn(sampleFlightDetailResponse());

        mvc.perform(get("/api/flights/1").with(user(employeePrincipal())))
                .andExpect(status().isOk());

        verify(flightService).getById(anyLong(), eq(true));
    }

    // Real PASSENGER principal passes both security layers, and since the user has
    // neither EMPLOYEE nor ADMIN role, the controller takes the passenger branch:
    // flightService.getByIdForPassenger(id, userId). verify(...) confirms that method -
    // not getById(id, true) - was the one actually invoked.
    @Test
    void getById_callsGetByIdForPassenger_forPassengerPrincipal() throws Exception {
        when(flightService.getByIdForPassenger(anyLong(), any())).thenReturn(sampleFlightDetailResponse());

        mvc.perform(get("/api/flights/1").with(user(passengerPrincipal())))
                .andExpect(status().isOk());

        verify(flightService).getByIdForPassenger(anyLong(), any());
    }

    // ---- POST /api/flights (create) ----

    // No Authorization/principal supplied: SecurityConfig's filter-level POST rule
    // (hasAnyRole("EMPLOYEE","ADMIN")) rejects the anonymous request before it reaches
    // the controller -> 403 Forbidden (default Http403ForbiddenEntryPoint), not 401.
    @Test
    void create_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(post("/api/flights")
                        .contentType("application/json")
                        .content(VALID_FLIGHT_CREATE_JSON))
                .andExpect(status().isForbidden());
    }

    // PASSENGER is rejected by SecurityConfig's filter-level POST rule for
    // "/api/flights/**" (EMPLOYEE/ADMIN only) - unlike listMyFlights's /my quirk, here
    // both the filter-level rule and the controller's own
    // @PreAuthorize("hasAnyRole('EMPLOYEE','ADMIN')") agree, so the request is denied
    // cleanly at the filter level and never reaches the controller at all -> a plain
    // 403 Forbidden, not the 500 seen in the listMyFlights quirk.
    @Test
    @WithMockUser(roles = "PASSENGER")
    void create_returns403_forPassengerRole() throws Exception {
        mvc.perform(post("/api/flights")
                        .contentType("application/json")
                        .content(VALID_FLIGHT_CREATE_JSON))
                .andExpect(status().isForbidden());
    }

    // EMPLOYEE passes both the filter-level POST rule and the controller's
    // @PreAuthorize; with FlightService.create(...) stubbed, the controller responds
    // 201 Created.
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

    // No Authorization/principal supplied: SecurityConfig's filter-level PUT rule
    // (hasAnyRole("EMPLOYEE","ADMIN")) rejects the anonymous request before it reaches
    // the controller -> 403 Forbidden (default Http403ForbiddenEntryPoint), not 401.
    @Test
    void update_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(put("/api/flights/1")
                        .contentType("application/json")
                        .content(VALID_FLIGHT_CREATE_JSON))
                .andExpect(status().isForbidden());
    }

    // PASSENGER is rejected by SecurityConfig's filter-level PUT rule for
    // "/api/flights/**" (EMPLOYEE/ADMIN only), which agrees with the controller's
    // @PreAuthorize("hasAnyRole('EMPLOYEE','ADMIN')") - the request is denied cleanly
    // at the filter level, never reaching the controller -> 403 Forbidden.
    @Test
    @WithMockUser(roles = "PASSENGER")
    void update_returns403_forPassengerRole() throws Exception {
        mvc.perform(put("/api/flights/1")
                        .contentType("application/json")
                        .content(VALID_FLIGHT_CREATE_JSON))
                .andExpect(status().isForbidden());
    }

    // EMPLOYEE passes both the filter-level PUT rule and the controller's
    // @PreAuthorize; with FlightService.update(...) stubbed, the controller responds
    // 200 OK with the updated flight.
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

    // No Authorization/principal supplied: SecurityConfig's filter-level DELETE rule
    // (hasAnyRole("EMPLOYEE","ADMIN")) rejects the anonymous request before it reaches
    // the controller -> 403 Forbidden (default Http403ForbiddenEntryPoint), not 401.
    @Test
    void delete_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(delete("/api/flights/1"))
                .andExpect(status().isForbidden());
    }

    // PASSENGER is rejected by SecurityConfig's filter-level DELETE rule for
    // "/api/flights/**" (EMPLOYEE/ADMIN only), which agrees with the controller's
    // @PreAuthorize("hasAnyRole('EMPLOYEE','ADMIN')") - the request is denied cleanly
    // at the filter level, never reaching the controller -> 403 Forbidden.
    @Test
    @WithMockUser(roles = "PASSENGER")
    void delete_returns403_forPassengerRole() throws Exception {
        mvc.perform(delete("/api/flights/1"))
                .andExpect(status().isForbidden());
    }

    // EMPLOYEE passes both the filter-level DELETE rule and the controller's
    // @PreAuthorize -> 204 No Content. verify(...) confirms FlightService.delete(1L)
    // was actually invoked with the path-variable id, not just that the HTTP status
    // happened to be correct.
    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void delete_returns204_forEmployeeRole() throws Exception {
        mvc.perform(delete("/api/flights/1"))
                .andExpect(status().isNoContent());

        verify(flightService).delete(1L);
    }
}
