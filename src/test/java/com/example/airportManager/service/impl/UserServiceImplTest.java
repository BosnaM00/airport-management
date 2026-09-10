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

/**
 * Unit test for {@link UserServiceImpl}.
 *
 * <p>This is a pure Mockito unit test: {@code @ExtendWith(MockitoExtension.class)} boots
 * Mockito's JUnit5 integration WITHOUT starting a Spring context, so it runs in milliseconds.
 * The {@code @Mock} fields below become fake implementations of {@link UserServiceImpl}'s five
 * collaborators (four repositories plus the password encoder) that return exactly what each
 * test stubs via {@code when(...)}; no real database, no real password hashing, and no Spring
 * wiring is involved. The {@code @InjectMocks} field is a REAL instance of
 * {@link UserServiceImpl}; Mockito builds it and wires the mocks above into it via its Lombok
 * {@code @RequiredArgsConstructor} constructor, so the service's own business logic (email
 * uniqueness check, role resolution, conditional employee/passenger profile creation, response
 * DTO assembly) executes unmodified - only its dependencies are faked.
 *
 * <p>This isolation means a failing assertion here points precisely at a bug in
 * {@link UserServiceImpl}'s own logic (e.g. an employee profile being created when it shouldn't
 * be), rather than in the database, password encoding, or Spring configuration.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    // Fake UserRepository - we control exactly what existsByEmail/save/findById return; no real DB is touched.
    @Mock
    private UserRepository userRepository;
    // Fake RoleRepository - stands in for looking up Role entities by RoleName (e.g. EMPLOYEE, PASSENGER).
    @Mock
    private RoleRepository roleRepository;
    // Fake EmployeeProfileRepository - lets us verify whether/what the service saves when an EMPLOYEE role is present.
    @Mock
    private EmployeeProfileRepository employeeProfileRepository;
    // Fake PassengerProfileRepository - lets us verify whether/what the service saves when a PASSENGER role is present.
    @Mock
    private PassengerProfileRepository passengerProfileRepository;
    // Fake PasswordEncoder - avoids running real (slow, non-deterministic-looking) hashing during the test.
    @Mock
    private PasswordEncoder passwordEncoder;

    // Real, unmocked UserServiceImpl under test, with the five mocks above injected via its constructor.
    @InjectMocks
    private UserServiceImpl userService;

    /**
     * Happy-path scenario for create(): an EMPLOYEE-role signup with employee-specific fields
     * (position/department/hireDate) populated. Verifies the service (a) persists the User with
     * the encoded password and resolved role, and (b) also persists a matching EmployeeProfile
     * with the correct fields - while correctly NOT creating a PassengerProfile, since no
     * passenger role was requested. This guards against the two "if role contains X" branches in
     * UserServiceImpl.create() bleeding into each other.
     *
     * <p>The fixture below is nontrivial (Role, User, and a UUID pre-set on the saved user)
     * because the service re-fetches the user by id via {@code userRepository.findById(...)}
     * after saving it to build the response DTO, so the mock must be wired to return the same
     * "saved" user for both the save() and the subsequent findById() calls.
     */
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

        // Optional.of(...) simulates finding the EMPLOYEE role; existsByEmail(false) simulates "email not taken yet".
        when(userRepository.existsByEmail(dto.email())).thenReturn(false);
        when(roleRepository.findByName(RoleName.EMPLOYEE)).thenReturn(Optional.of(employeeRole));
        when(passwordEncoder.encode(dto.password())).thenReturn("hashed-password");
        // any(User.class) is a Mockito argument matcher accepting any User instance - used because the exact
        // User object the service constructs internally isn't available to match against by reference.
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userRepository.findById(userId)).thenReturn(Optional.of(savedUser));

        // Act
        UserResponseDTO result = userService.create(dto);

        // Assert
        assertThat(result.email()).isEqualTo("angajat@test.com");
        assertThat(result.roles()).containsExactly("EMPLOYEE");

        // Verificam ca s-a creat si profilul de angajat, capturand entitatea trimisa spre salvare
        // ca sa ii inspectam campurile - util cand vrei sa verifici mai mult decat "a fost apelat".
        // ArgumentCaptor lets us grab the actual object passed into a mocked method call (here,
        // employeeProfileRepository.save(...)) so we can assert on its individual fields, rather than
        // just verifying that save() was called with "any" argument.
        ArgumentCaptor<EmployeeProfile> profileCaptor = ArgumentCaptor.forClass(EmployeeProfile.class);
        verify(employeeProfileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getPosition()).isEqualTo("Dispecer");
        assertThat(profileCaptor.getValue().getDepartment()).isEqualTo("Operatiuni");

        // Nu era rol de pasager, deci acel profil nu trebuie creat.
        verify(passengerProfileRepository, org.mockito.Mockito.never()).save(any());
    }

    /**
     * Edge case: the email in the DTO is already registered (existsByEmail returns true).
     * Verifies create() fails fast with ConflictException before doing any further work - in
     * particular, userRepository.save() must never be invoked - so a duplicate signup can never
     * accidentally create a second User row for the same email.
     */
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
