package com.example.airportManager.web.controller;

import com.example.airportManager.dto.route.RouteResponseDTO;
import com.example.airportManager.model.Role;
import com.example.airportManager.model.RoleName;
import com.example.airportManager.model.User;
import com.example.airportManager.model.UserStatus;
import com.example.airportManager.security.CustomUserDetails;
import com.example.airportManager.security.CustomUserDetailsService;
import com.example.airportManager.security.JwtAuthenticationFilter;
import com.example.airportManager.security.JwtUtil;
import com.example.airportManager.security.SecurityConfig;
import com.example.airportManager.service.RouteService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Slice test for RouteController.
// @WebMvcTest(RouteController.class) boots ONLY the web layer: this controller, the
// Spring MVC infrastructure and the security filter chain - not the full application
// context, so no real database or unrelated beans/services are started.
// By default @WebMvcTest applies its own restrictive security config instead of the
// app's real one, so @Import({SecurityConfig.class, JwtAuthenticationFilter.class})
// pulls in the actual production SecurityConfig so the test exercises the real
// authorization rules. JwtAuthenticationFilter is imported as a REAL bean (not
// @MockitoBean) because a Mockito mock of a Servlet Filter never calls
// chain.doFilter(...), which would silently swallow every request and short-circuit
// the whole pipeline before it ever reaches the controller - the single most
// important gotcha in this whole test suite.
// RouteController has no @PreAuthorize: every endpoint just requires *some*
// authenticated user (SecurityConfig's anyRequest().authenticated() fallback, since
// /api/routes/** has no path-specific rule of its own).
@WebMvcTest(RouteController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class RouteControllerTest {

    // MockMvc drives simulated HTTP requests through the DispatcherServlet/filter
    // chain without a real servlet container.
    @Autowired
    private MockMvc mvc;

    // @MockitoBean replaces the real RouteService bean in the test's
    // ApplicationContext with a Mockito mock (the non-deprecated Spring Boot 3.4+
    // replacement for @MockBean); the real service/DB layer is never touched, and
    // only the calls stubbed below via when(...) return data - everything else falls
    // back to Mockito defaults (null/empty).
    @MockitoBean
    private RouteService routeService;

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
     * GET /api/routes with no Authentication. Expect 403 isForbidden(), not 401:
     * SecurityConfig configures no custom AuthenticationEntryPoint, so Spring
     * Security falls back to the default Http403ForbiddenEntryPoint even for
     * unauthenticated callers.
     */
    @Test
    void list_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(get("/api/routes"))
                .andExpect(status().isForbidden());
    }

    /**
     * GET /api/routes for any authenticated user (no role restriction applies).
     * Stubs routeService.list(...) (pageable + optional origin/dest filters) to
     * return a single-item Page; expects 200 OK.
     */
    @Test
    void list_returns200_forAuthenticatedCaller() throws Exception {
        RouteResponseDTO response = new RouteResponseDTO(1L, 1L, "New York", "JFK", 2L, "Los Angeles", "LAX", 2475, 330);
        Page<RouteResponseDTO> page = new PageImpl<>(List.of(response));
        when(routeService.list(any(), any(), any())).thenReturn(page);

        mvc.perform(get("/api/routes").with(user(passengerPrincipal())))
                .andExpect(status().isOk());
    }

    /**
     * POST /api/routes with no Authentication. Expect 403 isForbidden() (default
     * Http403ForbiddenEntryPoint), not 401.
     */
    @Test
    void create_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(post("/api/routes")
                        // .contentType(...) tells MockMvc/Spring which media type the
                        // body is in so the JSON gets deserialized by Jackson into
                        // the @RequestBody DTO.
                        .contentType("application/json")
                        .content("{\"originAirportId\":1,\"destAirportId\":2,\"distanceNm\":2475,\"stdDurationMin\":330}"))
                .andExpect(status().isForbidden());
    }

    /**
     * POST /api/routes as an authenticated user with a valid body. The controller
     * method is @Valid @RequestBody (bean validation runs against RouteCreateDTO
     * before the method executes) and returns
     * ResponseEntity.status(HttpStatus.CREATED), hence isCreated(). Stubs
     * routeService.create(...) to fake the creation.
     */
    @Test
    void create_returns201_forAuthenticatedCaller() throws Exception {
        RouteResponseDTO response = new RouteResponseDTO(1L, 1L, "New York", "JFK", 2L, "Los Angeles", "LAX", 2475, 330);
        when(routeService.create(any())).thenReturn(response);

        mvc.perform(post("/api/routes")
                        // .with(user(...)) installs passengerPrincipal() as the
                        // Authentication principal for this single request.
                        .with(user(passengerPrincipal()))
                        .contentType("application/json")
                        .content("{\"originAirportId\":1,\"destAirportId\":2,\"distanceNm\":2475,\"stdDurationMin\":330}"))
                .andExpect(status().isCreated());
    }

    /**
     * DELETE /api/routes/1 with no Authentication. Expect 403 isForbidden() (default
     * Http403ForbiddenEntryPoint), not 401.
     */
    @Test
    void delete_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(delete("/api/routes/1"))
                .andExpect(status().isForbidden());
    }

    /**
     * DELETE /api/routes/1 as an authenticated user. routeService.delete(...) is
     * void, so no stubbing is needed; expects 204, matching the controller's
     * ResponseEntity.noContent().
     */
    @Test
    void delete_returns204_forAuthenticatedCaller() throws Exception {
        mvc.perform(delete("/api/routes/1").with(user(passengerPrincipal())))
                .andExpect(status().isNoContent());
    }
}
