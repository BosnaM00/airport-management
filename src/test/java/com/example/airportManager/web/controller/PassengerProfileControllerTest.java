package com.example.airportManager.web.controller;

import com.example.airportManager.dto.profile.PassengerProfileResponseDTO;
import com.example.airportManager.model.Role;
import com.example.airportManager.model.RoleName;
import com.example.airportManager.model.User;
import com.example.airportManager.model.UserStatus;
import com.example.airportManager.security.CustomUserDetails;
import com.example.airportManager.security.CustomUserDetailsService;
import com.example.airportManager.security.JwtAuthenticationFilter;
import com.example.airportManager.security.JwtUtil;
import com.example.airportManager.security.SecurityConfig;
import com.example.airportManager.service.PassengerProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Slice test for PassengerProfileController.
// @WebMvcTest(PassengerProfileController.class) boots ONLY the web layer: this
// controller, Spring MVC infrastructure, and the security filter chain - not the full
// application context.
//
// @WebMvcTest applies its own restrictive default security configuration, so the real
// SecurityConfig must be explicitly re-imported via @Import for the actual authorization
// rules to be exercised. JwtAuthenticationFilter is imported as a REAL bean (not
// @MockitoBean): a Mockito mock of a Servlet Filter never calls chain.doFilter(...),
// which would silently swallow every request - the single most important gotcha in this
// test suite.
//
// PassengerProfileController is class-level @PreAuthorize("hasRole('PASSENGER')") -> every
// endpoint must reject anonymous callers and non-PASSENGER roles, and accept PASSENGER.
// SecurityConfig separately restricts "/api/passenger/profile/**" to PASSENGER at the
// HTTP filter level (.requestMatchers("/api/passenger/profile/**").hasRole("PASSENGER"))
// - both layers enforce the same rule independently (defense in depth).
// Endpoints also read the caller's identity via @AuthenticationPrincipal CustomUserDetails.
@WebMvcTest(PassengerProfileController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class PassengerProfileControllerTest {

    // Entry point for simulated HTTP requests against the web layer loaded by
    // @WebMvcTest; runs through the real filter chain (SecurityConfig +
    // JwtAuthenticationFilter included).
    @Autowired
    private MockMvc mvc;

    // @MockitoBean replaces the real PassengerProfileService bean with a Mockito mock
    // (modern, non-deprecated replacement for @MockBean); only methods stubbed with
    // when(...) below return data.
    @MockitoBean
    private PassengerProfileService passengerProfileService;

    // Mocked only to satisfy SecurityConfig's / JwtAuthenticationFilter's real-bean
    // dependencies; not stubbed or exercised directly, since these tests install the
    // Authentication principal directly (via SecurityMockMvcRequestPostProcessors.user
    // or @WithMockUser) instead of going through real JWT parsing / user lookup.
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    private static final String UPDATE_BODY =
            "{\"docType\":\"PASSPORT\",\"docNumber\":\"P1234567\",\"nationality\":\"RO\",\"loyaltyTier\":\"GOLD\",\"emergencyContact\":\"John Doe\"}";

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

    private PassengerProfileResponseDTO sampleResponse(UUID userId) {
        return new PassengerProfileResponseDTO(userId, "passenger@example.com", "0722000000",
                "PASSPORT", "P1234567", "RO", "GOLD", "John Doe");
    }

    // ---------- GET /api/passenger/profile/me ----------

    // No Authorization/principal supplied: anonymous caller. Denied by both
    // SecurityConfig's filter-level "/api/passenger/profile/**" -> hasRole("PASSENGER")
    // rule and the controller's class-level @PreAuthorize. No custom
    // AuthenticationEntryPoint is configured, so anonymous requests get 403 Forbidden
    // via the default Http403ForbiddenEntryPoint - never 401.
    @Test
    void getMyProfile_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(get("/api/passenger/profile/me"))
                .andExpect(status().isForbidden());
    }

    // Authenticated but wrong role (EMPLOYEE): denied by both the SecurityConfig
    // filter-level rule and the @PreAuthorize("hasRole('PASSENGER')") check. Wrong-role
    // requests get the same 403 as anonymous ones (no dedicated 401 path exists here).
    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getMyProfile_returns403_forNonPassengerRole() throws Exception {
        mvc.perform(get("/api/passenger/profile/me"))
                .andExpect(status().isForbidden());
    }

    // Real PASSENGER CustomUserDetails installed as the principal (bypassing the
    // mocked CustomUserDetailsService) so @AuthenticationPrincipal resolves to a usable
    // instance; passes both security layers, and with PassengerProfileService stubbed
    // to return a profile for that user's id, the controller responds 200 OK.
    @Test
    void getMyProfile_returns200_forPassengerPrincipal() throws Exception {
        CustomUserDetails principal = passengerPrincipal();
        when(passengerProfileService.getMyProfile(any())).thenReturn(sampleResponse(principal.getUserId()));

        mvc.perform(get("/api/passenger/profile/me").with(user(principal)))
                .andExpect(status().isOk());
    }

    // ---------- PUT /api/passenger/profile/me ----------

    // Same anonymous-caller reasoning as the GET test above: denied by both security
    // layers, resulting in 403 Forbidden (default Http403ForbiddenEntryPoint), not 401.
    @Test
    void updateMyProfile_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(put("/api/passenger/profile/me")
                        .contentType("application/json")
                        .content(UPDATE_BODY))
                .andExpect(status().isForbidden());
    }

    // Same wrong-role reasoning as the GET test above: an EMPLOYEE is denied by both
    // the filter-level rule and @PreAuthorize, yielding 403 (not 401).
    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void updateMyProfile_returns403_forNonPassengerRole() throws Exception {
        mvc.perform(put("/api/passenger/profile/me")
                        .contentType("application/json")
                        .content(UPDATE_BODY))
                .andExpect(status().isForbidden());
    }

    // Real PASSENGER principal passes both security layers; PassengerProfileService is
    // stubbed to return the updated profile, so the controller responds 200 OK.
    @Test
    void updateMyProfile_returns200_forPassengerPrincipal() throws Exception {
        CustomUserDetails principal = passengerPrincipal();
        when(passengerProfileService.updateMyProfile(any(), any())).thenReturn(sampleResponse(principal.getUserId()));

        mvc.perform(put("/api/passenger/profile/me")
                        .with(user(principal))
                        .contentType("application/json")
                        .content(UPDATE_BODY))
                .andExpect(status().isOk());
    }
}
