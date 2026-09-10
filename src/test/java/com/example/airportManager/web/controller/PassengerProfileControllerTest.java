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

// PassengerProfileController is class-level @PreAuthorize("hasRole('PASSENGER')") -> every
// endpoint must reject anonymous callers and non-PASSENGER roles, and accept PASSENGER.
// Endpoints also read the caller's identity via @AuthenticationPrincipal CustomUserDetails.
@WebMvcTest(PassengerProfileController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class PassengerProfileControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PassengerProfileService passengerProfileService;

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

    @Test
    void getMyProfile_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(get("/api/passenger/profile/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getMyProfile_returns403_forNonPassengerRole() throws Exception {
        mvc.perform(get("/api/passenger/profile/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMyProfile_returns200_forPassengerPrincipal() throws Exception {
        CustomUserDetails principal = passengerPrincipal();
        when(passengerProfileService.getMyProfile(any())).thenReturn(sampleResponse(principal.getUserId()));

        mvc.perform(get("/api/passenger/profile/me").with(user(principal)))
                .andExpect(status().isOk());
    }

    // ---------- PUT /api/passenger/profile/me ----------

    @Test
    void updateMyProfile_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(put("/api/passenger/profile/me")
                        .contentType("application/json")
                        .content(UPDATE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void updateMyProfile_returns403_forNonPassengerRole() throws Exception {
        mvc.perform(put("/api/passenger/profile/me")
                        .contentType("application/json")
                        .content(UPDATE_BODY))
                .andExpect(status().isForbidden());
    }

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
