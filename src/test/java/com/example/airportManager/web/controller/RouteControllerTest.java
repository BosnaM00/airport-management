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

// RouteController has no @PreAuthorize: every endpoint just requires *some*
// authenticated user (SecurityConfig's anyRequest().authenticated() fallback).
@WebMvcTest(RouteController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class RouteControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RouteService routeService;

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
        mvc.perform(get("/api/routes"))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_returns200_forAuthenticatedCaller() throws Exception {
        RouteResponseDTO response = new RouteResponseDTO(1L, 1L, "New York", "JFK", 2L, "Los Angeles", "LAX", 2475, 330);
        Page<RouteResponseDTO> page = new PageImpl<>(List.of(response));
        when(routeService.list(any(), any(), any())).thenReturn(page);

        mvc.perform(get("/api/routes").with(user(passengerPrincipal())))
                .andExpect(status().isOk());
    }

    @Test
    void create_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(post("/api/routes")
                        .contentType("application/json")
                        .content("{\"originAirportId\":1,\"destAirportId\":2,\"distanceNm\":2475,\"stdDurationMin\":330}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_returns201_forAuthenticatedCaller() throws Exception {
        RouteResponseDTO response = new RouteResponseDTO(1L, 1L, "New York", "JFK", 2L, "Los Angeles", "LAX", 2475, 330);
        when(routeService.create(any())).thenReturn(response);

        mvc.perform(post("/api/routes")
                        .with(user(passengerPrincipal()))
                        .contentType("application/json")
                        .content("{\"originAirportId\":1,\"destAirportId\":2,\"distanceNm\":2475,\"stdDurationMin\":330}"))
                .andExpect(status().isCreated());
    }

    @Test
    void delete_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(delete("/api/routes/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_returns204_forAuthenticatedCaller() throws Exception {
        mvc.perform(delete("/api/routes/1").with(user(passengerPrincipal())))
                .andExpect(status().isNoContent());
    }
}
