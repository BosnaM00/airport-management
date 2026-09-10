package com.example.airportManager.web.controller;

import com.example.airportManager.dto.auth.AuthResponseDTO;
import com.example.airportManager.dto.auth.LoginRequestDTO;
import com.example.airportManager.dto.auth.RegisterRequestDTO;
import com.example.airportManager.security.CustomUserDetailsService;
import com.example.airportManager.security.JwtAuthenticationFilter;
import com.example.airportManager.security.JwtUtil;
import com.example.airportManager.security.SecurityConfig;
import com.example.airportManager.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @WebMvcTest loads only the web layer (this controller + Spring MVC infra + the
// security filter chain), not the whole application context. We @MockitoBean every
// collaborator that would otherwise need a real DB/JWT signing key to construct.
// SecurityConfig is imported so the slice runs under the REAL production filter
// chain/authorization rules, not @WebMvcTest's default (deny-all) security setup.
// JwtAuthenticationFilter must be a REAL bean (not @MockitoBean): a Mockito mock of a
// Filter does not call chain.doFilter(...), so it would silently swallow every
// request and short-circuit the whole pipeline. Its own dependencies (JwtUtil,
// CustomUserDetailsService) are mocked instead, since they touch no DB.
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @Test
    void register_returns201_forAnonymousCaller() throws Exception {
        AuthResponseDTO response = new AuthResponseDTO(UUID.randomUUID(), "a@b.com", Set.of("PASSENGER"), "token");
        when(authService.register(any())).thenReturn(response);

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"a@b.com","phone":"0722000000","password":"password123",
                                 "docType":"PASSPORT","docNumber":"X123","nationality":"RO"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void register_returns400_whenPasswordTooShort() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"a@b.com","phone":"0722000000","password":"short",
                                 "docType":"PASSPORT","docNumber":"X123","nationality":"RO"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_returns200_forAnonymousCaller() throws Exception {
        AuthResponseDTO response = new AuthResponseDTO(UUID.randomUUID(), "a@b.com", Set.of("PASSENGER"), "token");
        when(authService.login(any())).thenReturn(response);

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"a@b.com","password":"password123"}
                                """))
                .andExpect(status().isOk());
    }
}
