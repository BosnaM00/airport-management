package com.example.airportManager.web.controller;

import com.example.airportManager.dto.aircraft.AircraftResponseDTO;
import com.example.airportManager.security.CustomUserDetailsService;
import com.example.airportManager.security.JwtAuthenticationFilter;
import com.example.airportManager.security.JwtUtil;
import com.example.airportManager.security.SecurityConfig;
import com.example.airportManager.service.AircraftService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// AircraftController has method-level @PreAuthorize: list/getById/create/update allow
// EMPLOYEE or ADMIN, while delete is ADMIN-only.
@WebMvcTest(AircraftController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AircraftControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AircraftService aircraftService;

    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    private static final String CREATE_BODY =
            "{\"tailNumber\":\"YR-ABC\",\"model\":\"Airbus A320\",\"capacity\":180,\"seatMapRef\":\"seatmap-a320\",\"status\":\"ACTIVE\"}";

    private AircraftResponseDTO sampleResponse() {
        return new AircraftResponseDTO(1L, "YR-ABC", "Airbus A320", 180, "seatmap-a320", "ACTIVE");
    }

    // ---------- GET /api/aircraft (list) ----------

    @Test
    void list_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(get("/api/aircraft"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PASSENGER")
    void list_returns403_forWrongRole() throws Exception {
        mvc.perform(get("/api/aircraft"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void list_returns200_forEmployeeRole() throws Exception {
        Page<AircraftResponseDTO> page = new PageImpl<>(List.of(sampleResponse()));
        when(aircraftService.list(any())).thenReturn(page);

        mvc.perform(get("/api/aircraft"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_returns200_forAdminRole() throws Exception {
        Page<AircraftResponseDTO> page = new PageImpl<>(List.of(sampleResponse()));
        when(aircraftService.list(any())).thenReturn(page);

        mvc.perform(get("/api/aircraft"))
                .andExpect(status().isOk());
    }

    // ---------- GET /api/aircraft/{id} ----------

    @Test
    void getById_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(get("/api/aircraft/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PASSENGER")
    void getById_returns403_forWrongRole() throws Exception {
        mvc.perform(get("/api/aircraft/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getById_returns200_forEmployeeRole() throws Exception {
        when(aircraftService.getById(anyLong())).thenReturn(sampleResponse());

        mvc.perform(get("/api/aircraft/1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getById_returns200_forAdminRole() throws Exception {
        when(aircraftService.getById(anyLong())).thenReturn(sampleResponse());

        mvc.perform(get("/api/aircraft/1"))
                .andExpect(status().isOk());
    }

    // ---------- POST /api/aircraft (create) ----------

    @Test
    void create_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(post("/api/aircraft")
                        .contentType("application/json")
                        .content(CREATE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PASSENGER")
    void create_returns403_forWrongRole() throws Exception {
        mvc.perform(post("/api/aircraft")
                        .contentType("application/json")
                        .content(CREATE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void create_returns201_forEmployeeRole() throws Exception {
        when(aircraftService.create(any())).thenReturn(sampleResponse());

        mvc.perform(post("/api/aircraft")
                        .contentType("application/json")
                        .content(CREATE_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_returns201_forAdminRole() throws Exception {
        when(aircraftService.create(any())).thenReturn(sampleResponse());

        mvc.perform(post("/api/aircraft")
                        .contentType("application/json")
                        .content(CREATE_BODY))
                .andExpect(status().isCreated());
    }

    // ---------- PUT /api/aircraft/{id} (update) ----------

    @Test
    void update_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(put("/api/aircraft/1")
                        .contentType("application/json")
                        .content(CREATE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PASSENGER")
    void update_returns403_forWrongRole() throws Exception {
        mvc.perform(put("/api/aircraft/1")
                        .contentType("application/json")
                        .content(CREATE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void update_returns200_forEmployeeRole() throws Exception {
        when(aircraftService.update(anyLong(), any())).thenReturn(sampleResponse());

        mvc.perform(put("/api/aircraft/1")
                        .contentType("application/json")
                        .content(CREATE_BODY))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_returns200_forAdminRole() throws Exception {
        when(aircraftService.update(anyLong(), any())).thenReturn(sampleResponse());

        mvc.perform(put("/api/aircraft/1")
                        .contentType("application/json")
                        .content(CREATE_BODY))
                .andExpect(status().isOk());
    }

    // ---------- DELETE /api/aircraft/{id} (ADMIN only) ----------

    @Test
    void delete_returns403_forAnonymousCaller() throws Exception {
        mvc.perform(delete("/api/aircraft/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PASSENGER")
    void delete_returns403_forPassengerRole() throws Exception {
        mvc.perform(delete("/api/aircraft/1"))
                .andExpect(status().isForbidden());
    }

    // Unlike the other 4 endpoints, delete is ADMIN-only: EMPLOYEE must also be rejected.
    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void delete_returns403_forEmployeeRole() throws Exception {
        mvc.perform(delete("/api/aircraft/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_returns204_forAdminRole() throws Exception {
        mvc.perform(delete("/api/aircraft/1"))
                .andExpect(status().isNoContent());
    }
}
