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

// Unit test for AuthServiceImpl (registration + login business logic). This is a pure Mockito
// unit test, not a Spring integration test: @ExtendWith(MockitoExtension.class) boots
// Mockito's JUnit5 integration WITHOUT starting a Spring application context - no real security
// filter chain, no real database, no real password hashing - so the whole class runs in
// milliseconds. Each @Mock field below is a fake implementation of one of AuthServiceImpl's six
// collaborators, returning exactly what the test tells it to via when(...) stubbing.
// @InjectMocks builds a REAL AuthServiceImpl instance and wires all six mocks into it through
// the Lombok-generated (@RequiredArgsConstructor) constructor, so the actual business logic in
// register()/login() - role lookup, password hashing delegation, building the User/PassengerProfile
// entities, computing the roles set, building the AuthResponseDTO - executes unmodified. This
// isolates AuthServiceImpl's own orchestration logic from Spring Security, JWT signing, and
// persistence, so a failing test points precisely at a bug in this service, not in its
// dependencies.
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    // Fake repository: controls whether a given email is reported as already registered, and
    // what save() echoes back - no real DB is touched.
    @Mock
    private UserRepository userRepository;
    // Fake repository: supplies the PASSENGER Role entity that register() attaches to new users.
    @Mock
    private RoleRepository roleRepository;
    // Fake repository: records the PassengerProfile save() call made during registration; not
    // asserted on directly in these tests but must be mocked because it's a real constructor
    // dependency of AuthServiceImpl.
    @Mock
    private PassengerProfileRepository passengerProfileRepository;
    // Fake password encoder: stubbed to return a fixed "hashed-password" string instead of
    // running a real (slow) BCrypt hash.
    @Mock
    private PasswordEncoder passwordEncoder;
    // Fake JWT utility: stubbed to return a fixed token string instead of actually signing a JWT.
    @Mock
    private JwtUtil jwtUtil;
    // Fake Spring Security AuthenticationManager: stubbed to return a pre-built Authentication
    // result instead of validating real credentials against a UserDetailsService.
    @Mock
    private AuthenticationManager authenticationManager;

    // The real, unmocked AuthServiceImpl under test, with all six mocks above injected via its
    // constructor.
    @InjectMocks
    private AuthServiceImpl authService;

    // Happy-path registration: verifies that for a brand-new email, register() looks up the
    // PASSENGER role, hashes the password, saves the user, and returns an AuthResponseDTO whose
    // email/roles/token reflect what was built - i.e. the full "new passenger sign-up" flow.
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
        // any(User.class) / any(CustomUserDetails.class) are Mockito argument matchers meaning
        // "any instance of this type" - used here because we don't care about the exact User/
        // CustomUserDetails object built internally, only about faking what save()/
        // generateToken() return for it. thenAnswer(...) lets the stub compute its return value
        // from the actual invocation (here, simply echoing back argument 0).
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtUtil.generateToken(any(CustomUserDetails.class))).thenReturn("jwt-token-123");

        // Act
        AuthResponseDTO result = authService.register(request);

        // Assert
        assertThat(result.email()).isEqualTo("pasager@test.com");
        assertThat(result.roles()).containsExactly("PASSENGER");
        assertThat(result.token()).isEqualTo("jwt-token-123");
    }

    // Duplicate-email edge case: verifies registration is rejected up front when the email is
    // already taken, before any role lookup, hashing, or persistence happens - guards against
    // creating duplicate accounts for the same email address.
    @Test
    void register_shouldThrowConflictException_whenEmailAlreadyRegistered() {
        // Arrange
        RegisterRequestDTO request = new RegisterRequestDTO(
                "existent@test.com", "0722000000", "parola123",
                "PASSPORT", "AB1234", "RO", null, null
        );
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        // Act + Assert
        // assertThatThrownBy(...) is AssertJ's fluent exception-assertion API: it invokes the
        // lambda, catches whatever it throws, and lets us assert on the exception's type/message.
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("existent@test.com");
    }

    // Happy-path login: verifies login() delegates credential checking to Spring Security's
    // AuthenticationManager, then builds an AuthResponseDTO (email + JWT token) from the
    // authenticated principal - i.e. the service correctly unwraps CustomUserDetails and issues
    // a token rather than re-implementing authentication itself.
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

        // Building a real CustomUserDetails wrapping a User with a Role is needed here because
        // login() casts the Authentication's principal back to CustomUserDetails and reads the
        // wrapped User's roles/id/email - a bare mock wouldn't carry that data through faithfully.
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
