package com.example.airportManager.service.impl;

import com.example.airportManager.dto.auth.AuthResponseDTO;
import com.example.airportManager.dto.auth.LoginRequestDTO;
import com.example.airportManager.dto.auth.RegisterRequestDTO;
import com.example.airportManager.exception.ConflictException;
import com.example.airportManager.model.Role;
import com.example.airportManager.model.RoleName;
import com.example.airportManager.model.User;
import com.example.airportManager.model.UserStatus;
import com.example.airportManager.repository.PassengerProfileRepository;
import com.example.airportManager.repository.RoleRepository;
import com.example.airportManager.repository.UserRepository;
import com.example.airportManager.security.CustomUserDetails;
import com.example.airportManager.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PassengerProfileRepository passengerProfileRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void register_shouldCreateUserWithPassengerRoleAndReturnToken() {
        // Arrange
        RegisterRequestDTO request = new RegisterRequestDTO(
                "pasager@test.com", "0722000000", "parola123",
                "PASSPORT", "AB1234", "RO", null, null
        );

        Role passengerRole = new Role();
        passengerRole.setName(RoleName.PASSENGER);

        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(roleRepository.findByName(RoleName.PASSENGER)).thenReturn(Optional.of(passengerRole));
        when(passwordEncoder.encode(request.password())).thenReturn("hashed-password");
        // save() intoarce acelasi obiect primit ca argument - suficient pentru acest test,
        // pentru ca ne intereseaza continutul final al DTO-ului, nu identitatea instantei.
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtUtil.generateToken(any(CustomUserDetails.class))).thenReturn("jwt-token-123");

        // Act
        AuthResponseDTO result = authService.register(request);

        // Assert
        assertThat(result.email()).isEqualTo("pasager@test.com");
        assertThat(result.roles()).containsExactly("PASSENGER");
        assertThat(result.token()).isEqualTo("jwt-token-123");
    }

    @Test
    void register_shouldThrowConflictException_whenEmailAlreadyRegistered() {
        // Arrange
        RegisterRequestDTO request = new RegisterRequestDTO(
                "existent@test.com", "0722000000", "parola123",
                "PASSPORT", "AB1234", "RO", null, null
        );
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        // Act + Assert
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("existent@test.com");
    }

    @Test
    void login_shouldReturnToken_whenCredentialsAreValid() {
        // Arrange
        LoginRequestDTO request = new LoginRequestDTO("pasager@test.com", "parola123");

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(request.email());
        Role passengerRole = new Role();
        passengerRole.setName(RoleName.PASSENGER);
        user.setRoles(Set.of(passengerRole));

        CustomUserDetails userDetails = new CustomUserDetails(user);

        // authenticationManager.authenticate(...) e apelul catre Spring Security; il simulam
        // sa returneze o Authentication al carei principal e utilizatorul nostru.
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtUtil.generateToken(userDetails)).thenReturn("jwt-token-456");

        // Act
        AuthResponseDTO result = authService.login(request);

        // Assert
        assertThat(result.email()).isEqualTo("pasager@test.com");
        assertThat(result.token()).isEqualTo("jwt-token-456");
    }
}
