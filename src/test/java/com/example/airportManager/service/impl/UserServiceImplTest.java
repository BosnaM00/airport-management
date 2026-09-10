package com.example.airportManager.service.impl;

import com.example.airportManager.dto.user.UserCreateDTO;
import com.example.airportManager.dto.user.UserResponseDTO;
import com.example.airportManager.exception.ConflictException;
import com.example.airportManager.model.EmployeeProfile;
import com.example.airportManager.model.Role;
import com.example.airportManager.model.RoleName;
import com.example.airportManager.model.User;
import com.example.airportManager.model.UserStatus;
import com.example.airportManager.repository.EmployeeProfileRepository;
import com.example.airportManager.repository.PassengerProfileRepository;
import com.example.airportManager.repository.RoleRepository;
import com.example.airportManager.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private EmployeeProfileRepository employeeProfileRepository;
    @Mock
    private PassengerProfileRepository passengerProfileRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void create_shouldCreateUserAndEmployeeProfile_whenEmployeeRoleProvided() {
        // Arrange
        UserCreateDTO dto = new UserCreateDTO(
                "angajat@test.com", "0722000000", "parola123",
                Set.of(RoleName.EMPLOYEE),
                "Dispecer", "Operatiuni", "G2", LocalDate.of(2022, 3, 1),
                null, null, null, null, null
        );

        Role employeeRole = new Role();
        employeeRole.setName(RoleName.EMPLOYEE);

        UUID userId = UUID.randomUUID();
        User savedUser = new User();
        savedUser.setId(userId);
        savedUser.setEmail(dto.email());
        savedUser.setPhone(dto.phone());
        savedUser.setStatus(UserStatus.ACTIVE);
        savedUser.setRoles(Set.of(employeeRole));

        when(userRepository.existsByEmail(dto.email())).thenReturn(false);
        when(roleRepository.findByName(RoleName.EMPLOYEE)).thenReturn(Optional.of(employeeRole));
        when(passwordEncoder.encode(dto.password())).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userRepository.findById(userId)).thenReturn(Optional.of(savedUser));

        // Act
        UserResponseDTO result = userService.create(dto);

        // Assert
        assertThat(result.email()).isEqualTo("angajat@test.com");
        assertThat(result.roles()).containsExactly("EMPLOYEE");

        // Verificam ca s-a creat si profilul de angajat, capturand entitatea trimisa spre salvare
        // ca sa ii inspectam campurile - util cand vrei sa verifici mai mult decat "a fost apelat".
        ArgumentCaptor<EmployeeProfile> profileCaptor = ArgumentCaptor.forClass(EmployeeProfile.class);
        verify(employeeProfileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getPosition()).isEqualTo("Dispecer");
        assertThat(profileCaptor.getValue().getDepartment()).isEqualTo("Operatiuni");

        // Nu era rol de pasager, deci acel profil nu trebuie creat.
        verify(passengerProfileRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void create_shouldThrowConflictException_whenEmailAlreadyRegistered() {
        // Arrange
        UserCreateDTO dto = new UserCreateDTO(
                "existent@test.com", "0722000000", "parola123",
                Set.of(RoleName.PASSENGER),
                null, null, null, null, null, null, null, null, null
        );
        when(userRepository.existsByEmail(dto.email())).thenReturn(true);

        // Act + Assert
        assertThatThrownBy(() -> userService.create(dto))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("existent@test.com");

        verify(userRepository, org.mockito.Mockito.never()).save(any());
    }
}
