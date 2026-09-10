package com.example.airportManager.service.impl;

import com.example.airportManager.dto.profile.EmployeeProfileResponseDTO;
import com.example.airportManager.dto.profile.EmployeeProfileUpdateDTO;
import com.example.airportManager.model.EmployeeProfile;
import com.example.airportManager.model.User;
import com.example.airportManager.repository.EmployeeProfileRepository;
import com.example.airportManager.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeProfileServiceImplTest {

    @Mock
    private EmployeeProfileRepository employeeProfileRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private EmployeeProfileServiceImpl employeeProfileService;

    @Test
    void updateMyProfile_shouldUpdateAndReturnProfile_whenUserAndProfileExist() {
        // Arrange
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("angajat@test.com");
        user.setPhone("0722000000");

        EmployeeProfile profile = new EmployeeProfile();
        profile.setUserId(userId);

        EmployeeProfileUpdateDTO dto =
                new EmployeeProfileUpdateDTO("Dispecer", "Operatiuni", "G2", LocalDate.of(2022, 3, 1));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(employeeProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(employeeProfileRepository.save(profile)).thenReturn(profile);

        // Act
        EmployeeProfileResponseDTO result = employeeProfileService.updateMyProfile(userId, dto);

        // Assert
        assertThat(result.position()).isEqualTo("Dispecer");
        assertThat(result.department()).isEqualTo("Operatiuni");
        assertThat(result.hireDate()).isEqualTo(LocalDate.of(2022, 3, 1));
    }

    @Test
    void getMyProfile_shouldThrowEntityNotFoundException_whenProfileDoesNotExist() {
        // Arrange
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(employeeProfileRepository.findById(userId)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> employeeProfileService.getMyProfile(userId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Employee profile");
    }
}
