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

// AirportController has no @PreAuthorize: every endpoint just requires *some*
// authenticated user (SecurityConfig's anyRequest().authenticated() fallback).
@WebMvcTest(AirportController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AirportControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AirportService airportService;

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
    void list_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(get("/api/airports"))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_returns200_forAuthenticatedCaller() throws Exception {
        AirportResponseDTO response = new AirportResponseDTO(1L, "JFK", "KJFK", "John F. Kennedy", "New York", "USA", "America/New_York");
        Page<AirportResponseDTO> page = new PageImpl<>(List.of(response));
        when(airportService.list(any())).thenReturn(page);

        mvc.perform(get("/api/airports").with(user(passengerPrincipal())))
                .andExpect(status().isOk());
    }

    @Test
    void create_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(post("/api/airports")
                        .contentType("application/json")
                        .content("{\"iata\":\"JFK\",\"icao\":\"KJFK\",\"name\":\"John F. Kennedy\",\"city\":\"New York\",\"country\":\"USA\",\"timezone\":\"America/New_York\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_returns201_forAuthenticatedCaller() throws Exception {
        AirportResponseDTO response = new AirportResponseDTO(1L, "JFK", "KJFK", "John F. Kennedy", "New York", "USA", "America/New_York");
        when(airportService.create(any())).thenReturn(response);

        mvc.perform(post("/api/airports")
                        .with(user(passengerPrincipal()))
                        .contentType("application/json")
                        .content("{\"iata\":\"JFK\",\"icao\":\"KJFK\",\"name\":\"John F. Kennedy\",\"city\":\"New York\",\"country\":\"USA\",\"timezone\":\"America/New_York\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void update_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(put("/api/airports/1")
                        .contentType("application/json")
                        .content("{\"iata\":\"JFK\",\"icao\":\"KJFK\",\"name\":\"John F. Kennedy\",\"city\":\"New York\",\"country\":\"USA\",\"timezone\":\"America/New_York\"}"))
                .andExpect(status().isForbidden());
    }

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

    @Test
    void delete_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(delete("/api/airports/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_returns204_forAuthenticatedCaller() throws Exception {
        mvc.perform(delete("/api/airports/1").with(user(passengerPrincipal())))
                .andExpect(status().isNoContent());
    }
}
