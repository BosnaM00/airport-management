package com.example.airportManager.web.controller;

import com.example.airportManager.dto.profile.EmployeeProfileResponseDTO;
import com.example.airportManager.model.Role;
import com.example.airportManager.model.RoleName;
import com.example.airportManager.model.User;
import com.example.airportManager.model.UserStatus;
import com.example.airportManager.security.CustomUserDetails;
import com.example.airportManager.security.CustomUserDetailsService;
import com.example.airportManager.security.JwtAuthenticationFilter;
import com.example.airportManager.security.JwtUtil;
import com.example.airportManager.security.SecurityConfig;
import com.example.airportManager.service.EmployeeProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Slice test for EmployeeProfileController. @WebMvcTest(EmployeeProfileController.class)
// boots ONLY the web layer: this controller, Spring MVC infrastructure, and the security
// filter chain - not the full application context.
//
// @WebMvcTest applies its own restrictive default security configuration, so the real
// SecurityConfig must be explicitly re-imported via @Import for the actual authorization
// rules to be exercised. JwtAuthenticationFilter is imported as a REAL bean (not
// @MockitoBean): a Mockito mock of a Servlet Filter never calls chain.doFilter(...),
// which would silently swallow every request - the single most important gotcha in this
// test suite.
//
// EmployeeProfileController is class-level @PreAuthorize("hasRole('EMPLOYEE')") -> every
// endpoint must reject anonymous callers and non-EMPLOYEE roles, and accept EMPLOYEE.
// SecurityConfig separately restricts "/api/employee/profile/**" to EMPLOYEE at the HTTP
// filter level (.requestMatchers("/api/employee/profile/**").hasRole("EMPLOYEE")) - both
// layers enforce the same rule independently (defense in depth).
// Endpoints also read the caller's identity via @AuthenticationPrincipal CustomUserDetails.
@WebMvcTest(EmployeeProfileController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class EmployeeProfileControllerTest {

    // Entry point for simulated HTTP requests against the web layer loaded by
    // @WebMvcTest; runs through the real filter chain (SecurityConfig +
    // JwtAuthenticationFilter included).
    @Autowired
    private MockMvc mvc;

    // @MockitoBean replaces the real EmployeeProfileService bean with a Mockito mock
    // (modern, non-deprecated replacement for @MockBean); only methods stubbed with
    // when(...) below return data.
    @MockitoBean
    private EmployeeProfileService employeeProfileService;

    // Mocked only to satisfy SecurityConfig's / JwtAuthenticationFilter's real-bean
    // dependencies; not stubbed or exercised directly, since these tests install the
    // Authentication principal directly (via SecurityMockMvcRequestPostProcessors.user
    // or @WithMockUser) instead of going through real JWT parsing / user lookup.
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    private static final String UPDATE_BODY =
            "{\"position\":\"Ground Crew\",\"department\":\"Operations\",\"grade\":\"Senior\",\"hireDate\":\"2020-01-15\"}";

    // Builds a real CustomUserDetails with an EMPLOYEE role so @AuthenticationPrincipal
    // resolves to a usable instance; SecurityMockMvcRequestPostProcessors.user(UserDetails)
    // installs it as the Authentication principal directly, bypassing the (mocked)
    // UserDetailsService.
    private CustomUserDetails employeePrincipal() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("employee@example.com");
        user.setPasswordHash("hash");
        user.setStatus(UserStatus.ACTIVE);
        Role role = new Role();
        role.setId(1L);
        role.setName(RoleName.EMPLOYEE);
        user.setRoles(Set.of(role));
        return new CustomUserDetails(user);
    }

    private EmployeeProfileResponseDTO sampleResponse(UUID userId) {
        return new EmployeeProfileResponseDTO(userId, "employee@example.com", "0722000000",
                "Ground Crew", "Operations", "Senior", LocalDate.of(2020, 1, 15));
    }

    // ---------- GET /api/employee/profile/me ----------

    // No Authorization/principal supplied: anonymous caller. Denied by both
    // SecurityConfig's filter-level "/api/employee/profile/**" -> hasRole("EMPLOYEE")
    // rule and the controller's class-level @PreAuthorize. No custom
    // AuthenticationEntryPoint is configured, so anonymous requests get 403 Forbidden
    // via the default Http403ForbiddenEntryPoint - never 401.
    @Test
    void getMyProfile_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(get("/api/employee/profile/me"))
                .andExpect(status().isForbidden());
    }

    // Authenticated but wrong role (PASSENGER): denied by both the SecurityConfig
    // filter-level rule and the @PreAuthorize("hasRole('EMPLOYEE')") check. Wrong-role
    // requests get the same 403 as anonymous ones (no dedicated 401 path exists here).
    @Test
    @WithMockUser(roles = "PASSENGER")
    void getMyProfile_returns403_forNonEmployeeRole() throws Exception {
        mvc.perform(get("/api/employee/profile/me"))
                .andExpect(status().isForbidden());
    }

    // Real EMPLOYEE CustomUserDetails installed as the principal (bypassing the mocked
    // CustomUserDetailsService) so @AuthenticationPrincipal resolves to a usable
    // instance; passes both security layers, and with EmployeeProfileService stubbed to
    // return a profile for that user's id, the controller responds 200 OK.
    @Test
    void getMyProfile_returns200_forEmployeePrincipal() throws Exception {
        CustomUserDetails principal = employeePrincipal();
        when(employeeProfileService.getMyProfile(any())).thenReturn(sampleResponse(principal.getUserId()));

        mvc.perform(get("/api/employee/profile/me").with(user(principal)))
                .andExpect(status().isOk());
    }

    // ---------- PUT /api/employee/profile/me ----------

    // Same anonymous-caller reasoning as the GET test above: denied by both security
    // layers, resulting in 403 Forbidden (default Http403ForbiddenEntryPoint), not 401.
    @Test
    void updateMyProfile_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(put("/api/employee/profile/me")
                        .contentType("application/json")
                        .content(UPDATE_BODY))
                .andExpect(status().isForbidden());
    }

    // Same wrong-role reasoning as the GET test above: a PASSENGER is denied by both
    // the filter-level rule and @PreAuthorize, yielding 403 (not 401).
    @Test
    @WithMockUser(roles = "PASSENGER")
    void updateMyProfile_returns403_forNonEmployeeRole() throws Exception {
        mvc.perform(put("/api/employee/profile/me")
                        .contentType("application/json")
                        .content(UPDATE_BODY))
                .andExpect(status().isForbidden());
    }

    // Real EMPLOYEE principal passes both security layers; EmployeeProfileService is
    // stubbed to return the updated profile, so the controller responds 200 OK.
    @Test
    void updateMyProfile_returns200_forEmployeePrincipal() throws Exception {
        CustomUserDetails principal = employeePrincipal();
        when(employeeProfileService.updateMyProfile(any(), any())).thenReturn(sampleResponse(principal.getUserId()));

        mvc.perform(put("/api/employee/profile/me")
                        .with(user(principal))
                        .contentType("application/json")
                        .content(UPDATE_BODY))
                .andExpect(status().isOk());
    }
}
