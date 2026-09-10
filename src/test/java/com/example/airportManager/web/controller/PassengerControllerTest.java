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

// PassengerController has no @PreAuthorize: every endpoint just requires *some*
// authenticated user (SecurityConfig's anyRequest().authenticated() fallback).
@WebMvcTest(PassengerController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class PassengerControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PassengerService passengerService;

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
        mvc.perform(post("/api/passengers")
                        .contentType("application/json")
                        .content("{\"firstName\":\"Jane\",\"lastName\":\"Doe\",\"email\":\"jane.doe@example.com\",\"docType\":\"PASSPORT\",\"docNumber\":\"X1234567\",\"nationality\":\"USA\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_returns201_forAuthenticatedCaller() throws Exception {
        PassengerResponseDTO response = new PassengerResponseDTO(1L, "Jane", "Doe", "jane.doe@example.com", "USA");
        when(passengerService.create(any())).thenReturn(response);

        mvc.perform(post("/api/passengers")
                        .with(user(passengerPrincipal()))
                        .contentType("application/json")
                        .content("{\"firstName\":\"Jane\",\"lastName\":\"Doe\",\"email\":\"jane.doe@example.com\",\"docType\":\"PASSPORT\",\"docNumber\":\"X1234567\",\"nationality\":\"USA\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void getById_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(get("/api/passengers/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getById_returns200_forAuthenticatedCaller() throws Exception {
        PassengerResponseDTO response = new PassengerResponseDTO(1L, "Jane", "Doe", "jane.doe@example.com", "USA");
        when(passengerService.getById(anyLong())).thenReturn(response);

        mvc.perform(get("/api/passengers/1").with(user(passengerPrincipal())))
                .andExpect(status().isOk());
    }
}
