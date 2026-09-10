package com.example.airportManager.web.controller;

import com.example.airportManager.dto.airport.AirportResponseDTO;
import com.example.airportManager.model.Role;
import com.example.airportManager.model.RoleName;
import com.example.airportManager.model.User;
import com.example.airportManager.model.UserStatus;
import com.example.airportManager.security.CustomUserDetails;
import com.example.airportManager.security.CustomUserDetailsService;
import com.example.airportManager.security.JwtAuthenticationFilter;
import com.example.airportManager.security.JwtUtil;
import com.example.airportManager.security.SecurityConfig;
import com.example.airportManager.service.AirportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Slice test for AirportController.
// @WebMvcTest(AirportController.class) boots ONLY the web layer: this controller,
// the Spring MVC infrastructure (argument resolvers, message converters, etc.) and
// the security filter chain - it does NOT start the full application context, so
// there is no real database or unrelated beans/services running.
// By default @WebMvcTest wires up its own restrictive (deny-most) security
// configuration instead of the app's real one, so @Import({SecurityConfig.class,
// JwtAuthenticationFilter.class}) pulls in the actual production config so the test
// exercises the real authorization rules. JwtAuthenticationFilter is imported as a
// REAL bean rather than @MockitoBean: a Mockito mock of a Servlet Filter never calls
// chain.doFilter(...), so it would silently swallow every request and short-circuit
// the whole filter pipeline before it ever reaches the controller - the single
// biggest gotcha in this test suite.
// AirportController has no @PreAuthorize: every endpoint just requires *some*
// authenticated user (SecurityConfig's anyRequest().authenticated() fallback, since
// /api/airports/** has no path-specific rule of its own).
@WebMvcTest(AirportController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AirportControllerTest {

    // MockMvc drives simulated HTTP requests through the DispatcherServlet/filter
    // chain without starting a real servlet container.
    @Autowired
    private MockMvc mvc;

    // @MockitoBean replaces the real AirportService bean in the test's
    // ApplicationContext with a Mockito mock (the non-deprecated Spring Boot 3.4+
    // replacement for @MockBean); the real service/DB layer is never touched, and
    // only the method calls stubbed below via when(...) return data - everything
    // else falls back to Mockito defaults (null/empty).
    @MockitoBean
    private AirportService airportService;

    // Mocked so JwtAuthenticationFilter (a real bean) can be constructed without a
    // real signing key/DB-backed lookup.
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    // Builds a real CustomUserDetails so @AuthenticationPrincipal resolves to a usable
    // instance; SecurityMockMvcRequestPostProcessors.user(UserDetails) installs it as
    // the Authentication principal directly, bypassing the (mocked) UserDetailsService.
    private CustomUserDetails passengerPrincipal() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("passenger@example.com");
        user.setPasswordHash("hash");
        user.setStatus(UserStatus.ACTIVE);
        Role role = new Role();
        role.setId(1L);
        role.setName(RoleName.PASSENGER);
        user.setRoles(Set.of(role));
        return new CustomUserDetails(user);
    }

    /**
     * GET /api/airports with no Authentication at all. Expect 403 isForbidden(), not
     * 401: SecurityConfig configures no custom AuthenticationEntryPoint, so Spring
     * Security falls back to the default Http403ForbiddenEntryPoint even for
     * unauthenticated callers.
     */
    @Test
    void list_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(get("/api/airports"))
                .andExpect(status().isForbidden());
    }

    /**
     * GET /api/airports for any authenticated user (no role restriction applies
     * here). Stubs airportService.list(...) to return a single-item Page so the
     * controller has something to serialize; expects 200 OK.
     */
    @Test
    void list_returns200_forAuthenticatedCaller() throws Exception {
        AirportResponseDTO response = new AirportResponseDTO(1L, "JFK", "KJFK", "John F. Kennedy", "New York", "USA", "America/New_York");
        Page<AirportResponseDTO> page = new PageImpl<>(List.of(response));
        when(airportService.list(any())).thenReturn(page);

        mvc.perform(get("/api/airports").with(user(passengerPrincipal())))
                .andExpect(status().isOk());
    }

    /**
     * POST /api/airports with no Authentication. Expect 403 isForbidden() (default
     * Http403ForbiddenEntryPoint, see note above) rather than 401.
     */
    @Test
    void create_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(post("/api/airports")
                        // .contentType(...) declares the request body's media type so
                        // Spring's HttpMessageConverter machinery picks the right
                        // converter (Jackson) to deserialize the JSON payload below.
                        .contentType("application/json")
                        .content("{\"iata\":\"JFK\",\"icao\":\"KJFK\",\"name\":\"John F. Kennedy\",\"city\":\"New York\",\"country\":\"USA\",\"timezone\":\"America/New_York\"}"))
                .andExpect(status().isForbidden());
    }

    /**
     * POST /api/airports as an authenticated user with a valid body. The controller
     * method is annotated @Valid @RequestBody (bean-validation runs against
     * AirportCreateDTO before the method body executes) and returns
     * ResponseEntity.status(HttpStatus.CREATED) on success, hence isCreated(). Stubs
     * airportService.create(...) to fake the persistence/creation step.
     */
    @Test
    void create_returns201_forAuthenticatedCaller() throws Exception {
        AirportResponseDTO response = new AirportResponseDTO(1L, "JFK", "KJFK", "John F. Kennedy", "New York", "USA", "America/New_York");
        when(airportService.create(any())).thenReturn(response);

        mvc.perform(post("/api/airports")
                        // .with(user(...)) is the SecurityMockMvcRequestPostProcessor
                        // that installs passengerPrincipal() as the Authentication
                        // principal for this single request.
                        .with(user(passengerPrincipal()))
                        .contentType("application/json")
                        .content("{\"iata\":\"JFK\",\"icao\":\"KJFK\",\"name\":\"John F. Kennedy\",\"city\":\"New York\",\"country\":\"USA\",\"timezone\":\"America/New_York\"}"))
                .andExpect(status().isCreated());
    }

    /**
     * PUT /api/airports/1 with no Authentication. Expect 403 isForbidden() (default
     * Http403ForbiddenEntryPoint), not 401.
     */
    @Test
    void update_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(put("/api/airports/1")
                        .contentType("application/json")
                        .content("{\"iata\":\"JFK\",\"icao\":\"KJFK\",\"name\":\"John F. Kennedy\",\"city\":\"New York\",\"country\":\"USA\",\"timezone\":\"America/New_York\"}"))
                .andExpect(status().isForbidden());
    }

    /**
     * PUT /api/airports/1 as an authenticated user with a valid body. Stubs
     * airportService.update(...) to fake the update; expects 200 OK, matching the
     * controller's ResponseEntity.ok(updated).
     */
    @Test
    void update_returns200_forAuthenticatedCaller() throws Exception {
        AirportResponseDTO response = new AirportResponseDTO(1L, "JFK", "KJFK", "John F. Kennedy Intl", "New York", "USA", "America/New_York");
        when(airportService.update(anyLong(), any())).thenReturn(response);

        mvc.perform(put("/api/airports/1")
                        .with(user(passengerPrincipal()))
                        .contentType("application/json")
                        .content("{\"iata\":\"JFK\",\"icao\":\"KJFK\",\"name\":\"John F. Kennedy Intl\",\"city\":\"New York\",\"country\":\"USA\",\"timezone\":\"America/New_York\"}"))
                .andExpect(status().isOk());
    }

    /**
     * DELETE /api/airports/1 with no Authentication. Expect 403 isForbidden() (default
     * Http403ForbiddenEntryPoint), not 401.
     */
    @Test
    void delete_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(delete("/api/airports/1"))
                .andExpect(status().isForbidden());
    }

    /**
     * DELETE /api/airports/1 as an authenticated user. airportService.delete(...) is
     * void, so no stubbing is needed (a Mockito mock's void methods no-op by
     * default); expects 204, matching the controller's ResponseEntity.noContent().
     */
    @Test
    void delete_returns204_forAuthenticatedCaller() throws Exception {
        mvc.perform(delete("/api/airports/1").with(user(passengerPrincipal())))
                .andExpect(status().isNoContent());
    }
}
