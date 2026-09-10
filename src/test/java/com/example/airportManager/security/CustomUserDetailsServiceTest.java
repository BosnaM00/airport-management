package com.example.airportManager.security;

import com.example.airportManager.model.User;
import com.example.airportManager.model.UserStatus;
import com.example.airportManager.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

// Unit tests for CustomUserDetailsService, the Spring Security SPI
// implementation (UserDetailsService) that Spring calls to look up a user by
// username/email during authentication. Its single method,
// loadUserByUsername, is the exact method Spring Security's
// DaoAuthenticationProvider invokes when processing a login attempt, and
// UsernameNotFoundException is the exact exception type Spring Security
// expects when that lookup fails (it's caught and translated into a generic
// "bad credentials" response so login failures don't leak which usernames
// exist).
//
// Plain unit test (no Spring context): the repository dependency is mocked so
// no real database is involved, keeping the test fast and focused purely on
// this service's own lookup-then-wrap-or-throw logic.
@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    // Mocked collaborator standing in for the real Spring Data repository, so
    // the test can control exactly what findByEmail returns without touching a
    // database.
    @Mock
    private UserRepository userRepository;

    // The real CustomUserDetailsService under test, with the mocked
    // UserRepository injected by Mockito (via its @RequiredArgsConstructor).
    @InjectMocks
    private CustomUserDetailsService service;

    @Test
    void loadUserByUsername_whenUserExists_returnsCustomUserDetailsWrappingThatUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("found@example.com");
        user.setPasswordHash("hash");
        user.setStatus(UserStatus.ACTIVE);
        when(userRepository.findByEmail("found@example.com")).thenReturn(Optional.of(user));

        UserDetails result = service.loadUserByUsername("found@example.com");

        // When the repository finds a match, the service must return a
        // CustomUserDetails wrapping that exact User instance (not a copy), so
        // that Spring Security's principal ends up backed by the real entity.
        assertThat(result).isInstanceOf(CustomUserDetails.class);
        assertThat(((CustomUserDetails) result).getUser()).isSameAs(user);
        assertThat(result.getUsername()).isEqualTo("found@example.com");
    }

    @Test
    void loadUserByUsername_whenUserDoesNotExist_throwsUsernameNotFoundException() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        // When no user matches, the service must throw UsernameNotFoundException
        // specifically (not e.g. return null or throw some other exception type),
        // since that's the exact type Spring Security's DaoAuthenticationProvider
        // catches to signal a failed login.
        assertThatThrownBy(() -> service.loadUserByUsername("missing@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("missing@example.com");
    }
}
