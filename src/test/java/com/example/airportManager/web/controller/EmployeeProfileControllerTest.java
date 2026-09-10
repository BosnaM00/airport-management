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

// EmployeeProfileController is class-level @PreAuthorize("hasRole('EMPLOYEE')") -> every
// endpoint must reject anonymous callers and non-EMPLOYEE roles, and accept EMPLOYEE.
// Endpoints also read the caller's identity via @AuthenticationPrincipal CustomUserDetails.
@WebMvcTest(EmployeeProfileController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class EmployeeProfileControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private EmployeeProfileService employeeProfileService;

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

    @Test
    void getMyProfile_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(get("/api/employee/profile/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PASSENGER")
    void getMyProfile_returns403_forNonEmployeeRole() throws Exception {
        mvc.perform(get("/api/employee/profile/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMyProfile_returns200_forEmployeePrincipal() throws Exception {
        CustomUserDetails principal = employeePrincipal();
        when(employeeProfileService.getMyProfile(any())).thenReturn(sampleResponse(principal.getUserId()));

        mvc.perform(get("/api/employee/profile/me").with(user(principal)))
                .andExpect(status().isOk());
    }

    // ---------- PUT /api/employee/profile/me ----------

    @Test
    void updateMyProfile_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(put("/api/employee/profile/me")
                        .contentType("application/json")
                        .content(UPDATE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PASSENGER")
    void updateMyProfile_returns403_forNonEmployeeRole() throws Exception {
        mvc.perform(put("/api/employee/profile/me")
                        .contentType("application/json")
                        .content(UPDATE_BODY))
                .andExpect(status().isForbidden());
    }

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
