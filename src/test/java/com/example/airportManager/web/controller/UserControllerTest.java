package com.example.airportManager.web.controller;

import com.example.airportManager.dto.user.UserResponseDTO;
import com.example.airportManager.model.UserStatus;
import com.example.airportManager.security.CustomUserDetailsService;
import com.example.airportManager.security.JwtAuthenticationFilter;
import com.example.airportManager.security.JwtUtil;
import com.example.airportManager.security.SecurityConfig;
import com.example.airportManager.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Slice test for UserController. @WebMvcTest(UserController.class) boots ONLY the web
// layer: this controller, Spring MVC infrastructure, and the security filter chain - not
// the full application context.
//
// @WebMvcTest applies its own restrictive default security configuration, so the real
// SecurityConfig must be explicitly re-imported via @Import for the actual authorization
// rules to be exercised. JwtAuthenticationFilter is imported as a REAL bean (not
// @MockitoBean): a Mockito mock of a Servlet Filter never calls chain.doFilter(...),
// which would silently swallow every request - the single most important gotcha in this
// test suite.
//
// UserController is class-level @PreAuthorize("hasRole('ADMIN')") -> every endpoint
// must reject anonymous callers and non-ADMIN roles, and accept ADMIN. SecurityConfig
// separately restricts "/api/users/**" to ADMIN at the HTTP filter level
// (.requestMatchers("/api/users/**").hasRole("ADMIN")) - both layers enforce the same
// rule independently (defense in depth): the filter chain rejects wrong roles before
// the request even reaches the controller, and @PreAuthorize would reject it again if
// it somehow got through.
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class UserControllerTest {

    // Entry point for simulated HTTP requests against the web layer loaded by
    // @WebMvcTest; runs through the real filter chain (SecurityConfig +
    // JwtAuthenticationFilter included).
    @Autowired
    private MockMvc mvc;

    // @MockitoBean replaces the real UserService bean with a Mockito mock (modern,
    // non-deprecated replacement for @MockBean); only listAll(...), stubbed below,
    // produces data - everything else returns Mockito defaults.
    @MockitoBean
    private UserService userService;

    // Mocked only to satisfy SecurityConfig's / JwtAuthenticationFilter's real-bean
    // dependencies; these tests authenticate via @WithMockUser rather than real JWTs,
    // so neither mock is stubbed or exercised directly.
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    // GET /api/users with no Authorization header: the caller is anonymous. No custom
    // AuthenticationEntryPoint is configured, so Spring Security falls back to
    // Http403ForbiddenEntryPoint: anonymous callers get 403, same as a denied role
    // (never 401 - there's no distinct "unauthenticated" vs "unauthorized" response
    // in this app).
    @Test
    void list_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(get("/api/users"))
                .andExpect(status().isForbidden());
    }

    // GET /api/users as an authenticated but non-ADMIN (PASSENGER) principal: denied by
    // both SecurityConfig's filter-level "/api/users/**" -> hasRole("ADMIN") rule and
    // the controller's class-level @PreAuthorize. Wrong-role requests get the same 403
    // Forbidden as anonymous ones (default Http403ForbiddenEntryPoint / AccessDenied
    // handling), never 401.
    @Test
    @WithMockUser(roles = "PASSENGER")
    void list_returns403_forNonAdminRole() throws Exception {
        mvc.perform(get("/api/users"))
                .andExpect(status().isForbidden());
    }

    // GET /api/users as an authenticated ADMIN: passes both the filter-level rule and
    // the class-level @PreAuthorize check. UserService.listAll(...) is stubbed to
    // return a page of users, so the controller responds 200 OK with that page.
    @Test
    @WithMockUser(roles = "ADMIN")
    void list_returns200_forAdminRole() throws Exception {
        UserResponseDTO user = new UserResponseDTO(UUID.randomUUID(), "a@b.com", "0722000000",
                UserStatus.ACTIVE, Set.of("PASSENGER"), LocalDateTime.now(), LocalDateTime.now(), null, null);
        when(userService.listAll(any())).thenReturn(new PageImpl<>(List.of(user)));

        mvc.perform(get("/api/users"))
                .andExpect(status().isOk());
    }
}
