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

// Slice test for AuthController.
// @WebMvcTest loads only the web layer (this controller + Spring MVC infra + the
// security filter chain), not the whole application context. We @MockitoBean every
// collaborator that would otherwise need a real DB/JWT signing key to construct.
// SecurityConfig is imported so the slice runs under the REAL production filter
// chain/authorization rules, not @WebMvcTest's default (deny-all) security setup.
// JwtAuthenticationFilter must be a REAL bean (not @MockitoBean): a Mockito mock of a
// Filter does not call chain.doFilter(...), so it would silently swallow every
// request and short-circuit the whole pipeline. Its own dependencies (JwtUtil,
// CustomUserDetailsService) are mocked instead, since they touch no DB.
// Unlike the other controller slice tests, AuthController's endpoints are all under
// /api/auth/**, which SecurityConfig marks .permitAll() - so anonymous (unauthenticated)
// callers are expected to succeed here, not get 403.
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AuthControllerTest {

    // MockMvc drives simulated HTTP requests through the DispatcherServlet/filter
    // chain without a real servlet container.
    @Autowired
    private MockMvc mvc;

    // @MockitoBean replaces the real AuthService bean in the test's
    // ApplicationContext with a Mockito mock (the non-deprecated Spring Boot 3.4+
    // replacement for @MockBean); the real service/DB layer is never touched, and
    // only the calls stubbed below via when(...) return data - everything else falls
    // back to Mockito defaults (null/empty).
    @MockitoBean
    private AuthService authService;

    // Mocked so JwtAuthenticationFilter (a real bean) can be constructed without a
    // real signing key/DB-backed lookup.
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    /**
     * POST /api/auth/register with a valid body and no Authentication. /api/auth/**
     * is permitAll() in SecurityConfig, so an anonymous caller is allowed through to
     * the controller. The controller method is @Valid @RequestBody (bean validation
     * runs against RegisterRequestDTO before the method executes) and returns
     * ResponseEntity.status(HttpStatus.CREATED), hence isCreated(). Stubs
     * authService.register(...) to fake the registration/token issuance.
     */
    @Test
    void register_returns201_forAnonymousCaller() throws Exception {
        AuthResponseDTO response = new AuthResponseDTO(UUID.randomUUID(), "a@b.com", Set.of("PASSENGER"), "token");
        when(authService.register(any())).thenReturn(response);

        mvc.perform(post("/api/auth/register")
                        // .contentType(...) tells MockMvc/Spring which media type the
                        // body is in so the JSON is deserialized by Jackson into the
                        // @RequestBody DTO.
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"a@b.com","phone":"0722000000","password":"password123",
                                 "docType":"PASSPORT","docNumber":"X123","nationality":"RO"}
                                """))
                .andExpect(status().isCreated());
    }

    /**
     * POST /api/auth/register with a body that fails bean validation (password
     * shorter than the DTO's minimum length constraint). No authService stubbing is
     * needed because @Valid rejects the request before the controller method body
     * ever runs; Spring's validation failure handling returns 400 isBadRequest().
     */
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

    /**
     * POST /api/auth/login with a valid body and no Authentication. /api/auth/** is
     * permitAll(), so this anonymous request reaches the controller. Stubs
     * authService.login(...) to fake credential verification/token issuance; expects
     * 200 OK, matching the controller's ResponseEntity.ok(response).
     */
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
