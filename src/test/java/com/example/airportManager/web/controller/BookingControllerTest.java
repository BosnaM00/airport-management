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

// BookingController has no @PreAuthorize: every endpoint just requires *some*
// authenticated user (SecurityConfig's anyRequest().authenticated() fallback), and
// the controller reads the caller's identity via @AuthenticationPrincipal.
@WebMvcTest(BookingController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class BookingControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private BookingService bookingService;

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

    @Test
    void create_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(post("/api/bookings")
                        .contentType("application/json")
                        .content("{\"flightId\":1}"))
                .andExpect(status().isForbidden());
    }

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

    @Test
    void getById_returns200_forAuthenticatedCaller() throws Exception {
        BookingResponseDTO response = new BookingResponseDTO(1L, "PNR123", UUID.randomUUID(), 1L, BookingStatus.CONFIRMED);
        when(bookingService.getById(anyLong())).thenReturn(response);

        mvc.perform(get("/api/bookings/1").with(user(passengerPrincipal())))
                .andExpect(status().isOk());
    }
}
