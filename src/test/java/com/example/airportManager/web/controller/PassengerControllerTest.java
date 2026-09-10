package com.example.airportManager.web.controller;

import com.example.airportManager.dto.passenger.PassengerResponseDTO;
import com.example.airportManager.model.Role;
import com.example.airportManager.model.RoleName;
import com.example.airportManager.model.User;
import com.example.airportManager.model.UserStatus;
import com.example.airportManager.security.CustomUserDetails;
import com.example.airportManager.security.CustomUserDetailsService;
import com.example.airportManager.security.JwtAuthenticationFilter;
import com.example.airportManager.security.JwtUtil;
import com.example.airportManager.security.SecurityConfig;
import com.example.airportManager.service.PassengerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Slice test for PassengerController.
// @WebMvcTest(PassengerController.class) boots ONLY the web layer: this controller,
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
// PassengerController has no @PreAuthorize: every endpoint just requires *some*
// authenticated user (SecurityConfig's anyRequest().authenticated() fallback, since
// /api/passengers/** has no path-specific rule of its own).
@WebMvcTest(PassengerController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class PassengerControllerTest {

    // MockMvc drives simulated HTTP requests through the DispatcherServlet/filter
    // chain without a real servlet container.
    @Autowired
    private MockMvc mvc;

    // @MockitoBean replaces the real PassengerService bean in the test's
    // ApplicationContext with a Mockito mock (the non-deprecated Spring Boot 3.4+
    // replacement for @MockBean); the real service/DB layer is never touched, and
    // only the calls stubbed below via when(...) return data - everything else falls
    // back to Mockito defaults (null/empty).
    @MockitoBean
    private PassengerService passengerService;

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
     * POST /api/passengers with no Authentication. Expect 403 isForbidden(), not 401:
     * SecurityConfig configures no custom AuthenticationEntryPoint, so Spring
     * Security falls back to the default Http403ForbiddenEntryPoint even for
     * unauthenticated callers.
     */
    @Test
    void create_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(post("/api/passengers")
                        // .contentType(...) tells MockMvc/Spring which media type the
                        // body is in so the JSON gets deserialized by Jackson into
                        // the @RequestBody DTO.
                        .contentType("application/json")
                        .content("{\"firstName\":\"Jane\",\"lastName\":\"Doe\",\"email\":\"jane.doe@example.com\",\"docType\":\"PASSPORT\",\"docNumber\":\"X1234567\",\"nationality\":\"USA\"}"))
                .andExpect(status().isForbidden());
    }

    /**
     * POST /api/passengers as an authenticated user with a valid body. The
     * controller method is @Valid @RequestBody (bean validation runs against
     * PassengerCreateDTO before the method executes) and returns
     * ResponseEntity.status(HttpStatus.CREATED), hence isCreated(). Stubs
     * passengerService.create(...) to fake the creation.
     */
    @Test
    void create_returns201_forAuthenticatedCaller() throws Exception {
        PassengerResponseDTO response = new PassengerResponseDTO(1L, "Jane", "Doe", "jane.doe@example.com", "USA");
        when(passengerService.create(any())).thenReturn(response);

        mvc.perform(post("/api/passengers")
                        // .with(user(...)) installs passengerPrincipal() as the
                        // Authentication principal for this single request.
                        .with(user(passengerPrincipal()))
                        .contentType("application/json")
                        .content("{\"firstName\":\"Jane\",\"lastName\":\"Doe\",\"email\":\"jane.doe@example.com\",\"docType\":\"PASSPORT\",\"docNumber\":\"X1234567\",\"nationality\":\"USA\"}"))
                .andExpect(status().isCreated());
    }

    /**
     * GET /api/passengers/1 with no Authentication. Expect 403 isForbidden() (default
     * Http403ForbiddenEntryPoint), not 401.
     */
    @Test
    void getById_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(get("/api/passengers/1"))
                .andExpect(status().isForbidden());
    }

    /**
     * GET /api/passengers/1 for any authenticated user (no role restriction
     * applies). Stubs passengerService.getById(...) to fake the lookup; expects
     * 200 OK, matching the controller's ResponseEntity.ok(passenger).
     */
    @Test
    void getById_returns200_forAuthenticatedCaller() throws Exception {
        PassengerResponseDTO response = new PassengerResponseDTO(1L, "Jane", "Doe", "jane.doe@example.com", "USA");
        when(passengerService.getById(anyLong())).thenReturn(response);

        mvc.perform(get("/api/passengers/1").with(user(passengerPrincipal())))
                .andExpect(status().isOk());
    }
}
