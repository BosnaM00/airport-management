package com.example.airportManager.security;

import com.example.airportManager.model.Role;
import com.example.airportManager.model.RoleName;
import com.example.airportManager.model.User;
import com.example.airportManager.model.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Unit tests for CustomUserDetails, the adapter that bridges this app's own
// User domain entity to Spring Security's UserDetails contract. Spring
// Security's authentication/authorization machinery (e.g. DaoAuthenticationProvider,
// method security checks) only ever talks to UserDetails - it has no idea this
// app's User entity exists - so this class is what makes an app User usable as
// a Spring Security principal.
//
// Plain unit test: CustomUserDetails is a simple wrapper around the User entity,
// so real model objects are built directly - no mocking needed since there are
// no collaborators to fake. The tests exercise the UserDetails contract methods
// Spring Security itself calls during authentication (e.g. getUsername,
// getPassword) and authorization (getAuthorities, isEnabled, and the other
// account-status flags), confirming each one correctly delegates to the
// wrapped User.
class CustomUserDetailsTest {

    // Builds a real Role domain object with the given RoleName, used to compose
    // the User's role set for each test scenario.
    private Role role(RoleName name) {
        Role role = new Role();
        role.setId((long) name.ordinal() + 1);
        role.setName(name);
        return role;
    }

    // Builds a real User domain object with a fixed email/password-hash and the
    // given status/roles, letting each test vary only what it cares about.
    private User user(UserStatus status, Set<Role> roles) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setPasswordHash("hashed-pw");
        user.setStatus(status);
        user.setRoles(roles);
        return user;
    }

    @Test
    void getAuthorities_singleRole_returnsSingleRolePrefixedAuthority() {
        User user = user(UserStatus.ACTIVE, Set.of(role(RoleName.PASSENGER)));
        CustomUserDetails details = new CustomUserDetails(user);

        // A single Role must map to a single GrantedAuthority whose name is
        // prefixed with "ROLE_", matching Spring Security's convention for
        // role-based authorities (e.g. used by hasRole("PASSENGER") checks).
        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_PASSENGER");
    }

    @Test
    void getAuthorities_multipleRoles_returnsAllRolePrefixedAuthorities() {
        User user = user(UserStatus.ACTIVE, Set.of(role(RoleName.PASSENGER), role(RoleName.ADMIN)));
        CustomUserDetails details = new CustomUserDetails(user);

        // Confirms multiple roles all get converted, not just the first one -
        // order doesn't matter since getAuthorities returns a Set.
        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_PASSENGER", "ROLE_ADMIN");
    }

    @Test
    void getUsername_returnsUserEmail() {
        User user = user(UserStatus.ACTIVE, Set.of(role(RoleName.PASSENGER)));
        CustomUserDetails details = new CustomUserDetails(user);

        // This app uses email as the login identifier, so UserDetails.getUsername()
        // must delegate to User.getEmail() (not some other field) since Spring
        // Security uses this value to identify/match the authenticated principal.
        assertThat(details.getUsername()).isEqualTo("user@example.com");
    }

    @Test
    void getPassword_returnsUserPasswordHash() {
        User user = user(UserStatus.ACTIVE, Set.of(role(RoleName.PASSENGER)));
        CustomUserDetails details = new CustomUserDetails(user);

        // getPassword() must expose the already-hashed credential (never a raw
        // password) since this is what Spring Security's password encoder
        // compares against during login.
        assertThat(details.getPassword()).isEqualTo("hashed-pw");
    }

    @Test
    void isEnabled_returnsTrue_whenUserStatusActive() {
        User user = user(UserStatus.ACTIVE, Set.of(role(RoleName.PASSENGER)));
        CustomUserDetails details = new CustomUserDetails(user);

        // ACTIVE users must be allowed to authenticate.
        assertThat(details.isEnabled()).isTrue();
    }

    @Test
    void isEnabled_returnsFalse_whenUserStatusNotActive() {
        User user = user(UserStatus.INACTIVE, Set.of(role(RoleName.PASSENGER)));
        CustomUserDetails details = new CustomUserDetails(user);

        // Any non-ACTIVE status must disable the account, which Spring Security's
        // DaoAuthenticationProvider checks to reject login for disabled accounts.
        assertThat(details.isEnabled()).isFalse();
    }

    @Test
    void accountStatusFlags_areAlwaysTrue() {
        User user = user(UserStatus.ACTIVE, Set.of(role(RoleName.PASSENGER)));
        CustomUserDetails details = new CustomUserDetails(user);

        // This app doesn't model account expiry/locking/credential expiry
        // separately from UserStatus, so these three flags are hardcoded to true
        // in CustomUserDetails - this test pins down that intentional behavior.
        assertThat(details.isAccountNonExpired()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
        assertThat(details.isCredentialsNonExpired()).isTrue();
    }

    @Test
    void getUserId_returnsUserId() {
        User user = user(UserStatus.ACTIVE, Set.of(role(RoleName.PASSENGER)));
        CustomUserDetails details = new CustomUserDetails(user);

        // getUserId() is an app-specific extra (not part of UserDetails) that lets
        // callers get at the entity's UUID primary key without unwrapping the User.
        assertThat(details.getUserId()).isEqualTo(user.getId());
    }

    @Test
    void getUser_returnsSameWrappedUserInstance() {
        User user = user(UserStatus.ACTIVE, Set.of(role(RoleName.PASSENGER)));
        CustomUserDetails details = new CustomUserDetails(user);

        // getUser() should expose the exact wrapped instance (isSameAs, not just
        // equals) confirming CustomUserDetails is a thin, non-copying wrapper.
        assertThat(details.getUser()).isSameAs(user);
    }
}
