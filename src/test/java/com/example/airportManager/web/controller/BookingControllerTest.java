package com.example.airportManager.web.controller;

import com.example.airportManager.dto.booking.BookingResponseDTO;
import com.example.airportManager.model.BookingStatus;
import com.example.airportManager.model.Role;
import com.example.airportManager.model.RoleName;
import com.example.airportManager.model.User;
import com.example.airportManager.model.UserStatus;
import com.example.airportManager.security.CustomUserDetails;
import com.example.airportManager.security.CustomUserDetailsService;
import com.example.airportManager.security.JwtAuthenticationFilter;
import com.example.airportManager.security.JwtUtil;
import com.example.airportManager.security.SecurityConfig;
import com.example.airportManager.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Slice test for BookingController. @WebMvcTest(BookingController.class) boots ONLY the
// web layer: this controller, Spring MVC infrastructure, and the security filter chain -
// not the full application context (no real service/repository/persistence beans).
//
// @WebMvcTest applies its own restrictive default security configuration, so the real
// SecurityConfig must be explicitly re-imported via @Import for the actual authorization
// rules to be exercised. JwtAuthenticationFilter is imported as a REAL bean here (not
// @MockitoBean) for a critical reason: a Mockito mock of a Servlet Filter never calls
// chain.doFilter(...), which would silently swallow every single request before it
// reaches the rest of the filter chain / controller - this is the single most important
// gotcha in this test suite.
//
// BookingController has no @PreAuthorize: every endpoint just requires *some*
// authenticated user (SecurityConfig's anyRequest().authenticated() fallback), and
// the controller reads the caller's identity via @AuthenticationPrincipal.
@WebMvcTest(BookingController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class BookingControllerTest {

    // Entry point for performing simulated HTTP requests against the web layer loaded by
    // @WebMvcTest; requests run through the real filter chain (SecurityConfig +
    // JwtAuthenticationFilter included) without starting an actual servlet container.
    @Autowired
    private MockMvc mvc;

    // @MockitoBean replaces the real BookingService Spring bean with a Mockito mock (the
    // modern, non-deprecated replacement for @MockBean); only methods stubbed with
    // when(...) below return data, everything else yields Mockito defaults (null/empty).
    @MockitoBean
    private BookingService bookingService;

    // Mocked only so SecurityConfig / JwtAuthenticationFilter (both real beans here) have
    // the collaborators they need to be constructed; not stubbed or exercised directly,
    // since these tests install the Authentication principal directly rather than going
    // through real JWT parsing / user lookup.
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

    // POST /api/bookings with no Authorization/principal at all: the caller is
    // anonymous. BookingController has no @PreAuthorize, so SecurityConfig's
    // anyRequest().authenticated() catch-all applies, and since no custom
    // AuthenticationEntryPoint is configured, Spring Security's default
    // Http403ForbiddenEntryPoint denies the request with 403 Forbidden (not 401).
    @Test
    void create_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(post("/api/bookings")
                        .contentType("application/json")
                        .content("{\"flightId\":1}"))
                .andExpect(status().isForbidden());
    }

    // Any authenticated caller (here a PASSENGER principal installed via
    // SecurityMockMvcRequestPostProcessors.user(...)) may create a booking - the
    // endpoint has no role restriction beyond being authenticated. BookingService is
    // stubbed to return a response, so the controller responds 201 Created.
    @Test
    void create_returns201_forAuthenticatedCaller() throws Exception {
        BookingResponseDTO response = new BookingResponseDTO(1L, "PNR123", UUID.randomUUID(), 1L, BookingStatus.CONFIRMED);
        when(bookingService.create(any(), any())).thenReturn(response);

        mvc.perform(post("/api/bookings")
                        .with(user(passengerPrincipal()))
                        .contentType("application/json")
                        .content("{\"flightId\":1}"))
                .andExpect(status().isCreated());
    }

    // GET /api/bookings/{id} likewise only requires *some* authenticated principal
    // (no @PreAuthorize on this endpoint); with a valid principal and a stubbed
    // BookingService response, the controller returns 200 OK.
    @Test
    void getById_returns200_forAuthenticatedCaller() throws Exception {
        BookingResponseDTO response = new BookingResponseDTO(1L, "PNR123", UUID.randomUUID(), 1L, BookingStatus.CONFIRMED);
        when(bookingService.getById(anyLong())).thenReturn(response);

        mvc.perform(get("/api/bookings/1").with(user(passengerPrincipal())))
                .andExpect(status().isOk());
    }
}
