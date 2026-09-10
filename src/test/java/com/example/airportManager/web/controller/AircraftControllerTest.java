package com.example.airportManager.web.controller;

import com.example.airportManager.dto.aircraft.AircraftResponseDTO;
import com.example.airportManager.security.CustomUserDetailsService;
import com.example.airportManager.security.JwtAuthenticationFilter;
import com.example.airportManager.security.JwtUtil;
import com.example.airportManager.security.SecurityConfig;
import com.example.airportManager.service.AircraftService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Slice test for AircraftController.
// @WebMvcTest(AircraftController.class) boots ONLY the web layer: this controller,
// the Spring MVC infrastructure and the security filter chain - not the full
// application context, so no real database or unrelated beans/services are started.
// By default @WebMvcTest applies its own restrictive security config instead of the
// app's real one, so @Import({SecurityConfig.class, JwtAuthenticationFilter.class})
// pulls in the actual production SecurityConfig so the test exercises the real
// authorization rules. JwtAuthenticationFilter is imported as a REAL bean (not
// @MockitoBean) because a Mockito mock of a Servlet Filter never calls
// chain.doFilter(...), which would silently swallow every request and short-circuit
// the whole pipeline before it ever reaches the controller - the single most
// important gotcha in this whole test suite.
// AircraftController has method-level @PreAuthorize: list/getById/create/update allow
// EMPLOYEE or ADMIN, while delete is ADMIN-only. This is enforced twice, in fact:
// SecurityConfig's requestMatchers for /api/aircraft/** already restrict GET/POST/PUT
// to EMPLOYEE/ADMIN and DELETE to ADMIN at the filter-chain level, and the
// @PreAuthorize annotations on the controller methods enforce the same rules again
// at the method-security layer.
@WebMvcTest(AircraftController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AircraftControllerTest {

    // MockMvc drives simulated HTTP requests through the DispatcherServlet/filter
    // chain without a real servlet container.
    @Autowired
    private MockMvc mvc;

    // @MockitoBean replaces the real AircraftService bean in the test's
    // ApplicationContext with a Mockito mock (the non-deprecated Spring Boot 3.4+
    // replacement for @MockBean); the real service/DB layer is never touched, and
    // only the calls stubbed below via when(...) return data - everything else falls
    // back to Mockito defaults (null/empty).
    @MockitoBean
    private AircraftService aircraftService;

    // Mocked so JwtAuthenticationFilter (a real bean) can be constructed without a
    // real signing key/DB-backed lookup.
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    // Shared valid JSON body for create/update requests, reused across the tests
    // below to avoid repeating the same literal.
    private static final String CREATE_BODY =
            "{\"tailNumber\":\"YR-ABC\",\"model\":\"Airbus A320\",\"capacity\":180,\"seatMapRef\":\"seatmap-a320\",\"status\":\"ACTIVE\"}";

    // Builds the AircraftResponseDTO that stubbed service calls return; this
    // controller only needs role-based checks (no @AuthenticationPrincipal), so
    // unlike the other controllers' passengerPrincipal() helper, tests here use the
    // simpler @WithMockUser(roles = ...) annotation instead of a real
    // CustomUserDetails principal.
    private AircraftResponseDTO sampleResponse() {
        return new AircraftResponseDTO(1L, "YR-ABC", "Airbus A320", 180, "seatmap-a320", "ACTIVE");
    }

    // ---------- GET /api/aircraft (list) ----------

    /**
     * GET /api/aircraft with no Authentication. Expect 403 isForbidden(), not 401:
     * SecurityConfig configures no custom AuthenticationEntryPoint, so Spring
     * Security falls back to the default Http403ForbiddenEntryPoint even for
     * unauthenticated callers.
     */
    @Test
    void list_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(get("/api/aircraft"))
                .andExpect(status().isForbidden());
    }

    /**
     * GET /api/aircraft as a PASSENGER. Both SecurityConfig's requestMatchers for
     * GET /api/aircraft/** and the controller's
     * @PreAuthorize("hasAnyRole('EMPLOYEE', 'ADMIN')") require EMPLOYEE or ADMIN, so
     * a PASSENGER is rejected with 403.
     * @WithMockUser(roles = "PASSENGER") is Spring Security's lightweight test
     * annotation: it installs a stub Authentication with the given role, without
     * needing a real UserDetails/CustomUserDetails instance.
     */
    @Test
    @WithMockUser(roles = "PASSENGER")
    void list_returns403_forWrongRole() throws Exception {
        mvc.perform(get("/api/aircraft"))
                .andExpect(status().isForbidden());
    }

    /**
     * GET /api/aircraft as an EMPLOYEE, an allowed role. Stubs
     * aircraftService.list(...) to return a single-item Page; expects 200 OK.
     */
    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void list_returns200_forEmployeeRole() throws Exception {
        Page<AircraftResponseDTO> page = new PageImpl<>(List.of(sampleResponse()));
        when(aircraftService.list(any())).thenReturn(page);

        mvc.perform(get("/api/aircraft"))
                .andExpect(status().isOk());
    }

    /**
     * GET /api/aircraft as an ADMIN, an allowed role. Stubs
     * aircraftService.list(...) to return a single-item Page; expects 200 OK.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void list_returns200_forAdminRole() throws Exception {
        Page<AircraftResponseDTO> page = new PageImpl<>(List.of(sampleResponse()));
        when(aircraftService.list(any())).thenReturn(page);

        mvc.perform(get("/api/aircraft"))
                .andExpect(status().isOk());
    }

    // ---------- GET /api/aircraft/{id} ----------

    /**
     * GET /api/aircraft/1 with no Authentication. Expect 403 isForbidden() (default
     * Http403ForbiddenEntryPoint), not 401.
     */
    @Test
    void getById_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(get("/api/aircraft/1"))
                .andExpect(status().isForbidden());
    }

    /**
     * GET /api/aircraft/1 as a PASSENGER: rejected because getById requires
     * EMPLOYEE or ADMIN (both via SecurityConfig and @PreAuthorize).
     */
    @Test
    @WithMockUser(roles = "PASSENGER")
    void getById_returns403_forWrongRole() throws Exception {
        mvc.perform(get("/api/aircraft/1"))
                .andExpect(status().isForbidden());
    }

    /**
     * GET /api/aircraft/1 as an EMPLOYEE. Stubs aircraftService.getById(...) to
     * fake the lookup; expects 200 OK.
     */
    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getById_returns200_forEmployeeRole() throws Exception {
        when(aircraftService.getById(anyLong())).thenReturn(sampleResponse());

        mvc.perform(get("/api/aircraft/1"))
                .andExpect(status().isOk());
    }

    /**
     * GET /api/aircraft/1 as an ADMIN. Stubs aircraftService.getById(...) to fake
     * the lookup; expects 200 OK.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void getById_returns200_forAdminRole() throws Exception {
        when(aircraftService.getById(anyLong())).thenReturn(sampleResponse());

        mvc.perform(get("/api/aircraft/1"))
                .andExpect(status().isOk());
    }

    // ---------- POST /api/aircraft (create) ----------

    /**
     * POST /api/aircraft with no Authentication. Expect 403 isForbidden() (default
     * Http403ForbiddenEntryPoint), not 401.
     */
    @Test
    void create_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(post("/api/aircraft")
                        // .contentType(...) tells MockMvc/Spring which media type the
                        // body is in so the JSON is deserialized by Jackson into the
                        // @Valid @RequestBody DTO.
                        .contentType("application/json")
                        .content(CREATE_BODY))
                .andExpect(status().isForbidden());
    }

    /**
     * POST /api/aircraft as a PASSENGER: rejected because create requires EMPLOYEE
     * or ADMIN.
     */
    @Test
    @WithMockUser(roles = "PASSENGER")
    void create_returns403_forWrongRole() throws Exception {
        mvc.perform(post("/api/aircraft")
                        .contentType("application/json")
                        .content(CREATE_BODY))
                .andExpect(status().isForbidden());
    }

    /**
     * POST /api/aircraft as an EMPLOYEE, an allowed role. The controller method is
     * @Valid @RequestBody (bean validation runs against AircraftCreateDTO before the
     * method executes) and returns ResponseEntity.status(HttpStatus.CREATED), hence
     * isCreated(). Stubs aircraftService.create(...) to fake the creation.
     */
    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void create_returns201_forEmployeeRole() throws Exception {
        when(aircraftService.create(any())).thenReturn(sampleResponse());

        mvc.perform(post("/api/aircraft")
                        .contentType("application/json")
                        .content(CREATE_BODY))
                .andExpect(status().isCreated());
    }

    /**
     * POST /api/aircraft as an ADMIN, an allowed role. Stubs
     * aircraftService.create(...) to fake the creation; expects 201.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void create_returns201_forAdminRole() throws Exception {
        when(aircraftService.create(any())).thenReturn(sampleResponse());

        mvc.perform(post("/api/aircraft")
                        .contentType("application/json")
                        .content(CREATE_BODY))
                .andExpect(status().isCreated());
    }

    // ---------- PUT /api/aircraft/{id} (update) ----------

    /**
     * PUT /api/aircraft/1 with no Authentication. Expect 403 isForbidden() (default
     * Http403ForbiddenEntryPoint), not 401.
     */
    @Test
    void update_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(put("/api/aircraft/1")
                        .contentType("application/json")
                        .content(CREATE_BODY))
                .andExpect(status().isForbidden());
    }

    /**
     * PUT /api/aircraft/1 as a PASSENGER: rejected because update requires EMPLOYEE
     * or ADMIN.
     */
    @Test
    @WithMockUser(roles = "PASSENGER")
    void update_returns403_forWrongRole() throws Exception {
        mvc.perform(put("/api/aircraft/1")
                        .contentType("application/json")
                        .content(CREATE_BODY))
                .andExpect(status().isForbidden());
    }

    /**
     * PUT /api/aircraft/1 as an EMPLOYEE, an allowed role. Stubs
     * aircraftService.update(...) to fake the update; expects 200 OK.
     */
    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void update_returns200_forEmployeeRole() throws Exception {
        when(aircraftService.update(anyLong(), any())).thenReturn(sampleResponse());

        mvc.perform(put("/api/aircraft/1")
                        .contentType("application/json")
                        .content(CREATE_BODY))
                .andExpect(status().isOk());
    }

    /**
     * PUT /api/aircraft/1 as an ADMIN, an allowed role. Stubs
     * aircraftService.update(...) to fake the update; expects 200 OK.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void update_returns200_forAdminRole() throws Exception {
        when(aircraftService.update(anyLong(), any())).thenReturn(sampleResponse());

        mvc.perform(put("/api/aircraft/1")
                        .contentType("application/json")
                        .content(CREATE_BODY))
                .andExpect(status().isOk());
    }

    // ---------- DELETE /api/aircraft/{id} (ADMIN only) ----------

    /**
     * DELETE /api/aircraft/1 with no Authentication. Expect 403 isForbidden()
     * (default Http403ForbiddenEntryPoint), not 401.
     */
    @Test
    void delete_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(delete("/api/aircraft/1"))
                .andExpect(status().isForbidden());
    }

    /**
     * DELETE /api/aircraft/1 as a PASSENGER: rejected, delete is ADMIN-only.
     */
    @Test
    @WithMockUser(roles = "PASSENGER")
    void delete_returns403_forPassengerRole() throws Exception {
        mvc.perform(delete("/api/aircraft/1"))
                .andExpect(status().isForbidden());
    }

    // Unlike the other 4 endpoints, delete is ADMIN-only: EMPLOYEE must also be rejected.
    /**
     * DELETE /api/aircraft/1 as an EMPLOYEE: also rejected, since unlike
     * list/getById/create/update (EMPLOYEE or ADMIN), delete requires ADMIN alone
     * per both SecurityConfig and @PreAuthorize("hasRole('ADMIN')").
     */
    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void delete_returns403_forEmployeeRole() throws Exception {
        mvc.perform(delete("/api/aircraft/1"))
                .andExpect(status().isForbidden());
    }

    /**
     * DELETE /api/aircraft/1 as an ADMIN, the only allowed role for this endpoint.
     * aircraftService.delete(...) is void, so no stubbing is needed; expects 204.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_returns204_forAdminRole() throws Exception {
        mvc.perform(delete("/api/aircraft/1"))
                .andExpect(status().isNoContent());
    }
}
