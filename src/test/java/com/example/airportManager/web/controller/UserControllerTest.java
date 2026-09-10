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

// UserController is class-level @PreAuthorize("hasRole('ADMIN')") -> every endpoint
// must reject anonymous callers and non-ADMIN roles, and accept ADMIN.
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class UserControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    // No custom AuthenticationEntryPoint is configured, so Spring Security falls back
    // to Http403ForbiddenEntryPoint: anonymous callers get 403, same as a denied role.
    @Test
    void list_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(get("/api/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PASSENGER")
    void list_returns403_forNonAdminRole() throws Exception {
        mvc.perform(get("/api/users"))
                .andExpect(status().isForbidden());
    }

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
