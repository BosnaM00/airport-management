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

// Plain unit test: CustomUserDetails is a simple wrapper around the User entity,
// so real model objects are built directly - no mocking needed.
class CustomUserDetailsTest {

    private Role role(RoleName name) {
        Role role = new Role();
        role.setId((long) name.ordinal() + 1);
        role.setName(name);
        return role;
    }

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

        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_PASSENGER");
    }

    @Test
    void getAuthorities_multipleRoles_returnsAllRolePrefixedAuthorities() {
        User user = user(UserStatus.ACTIVE, Set.of(role(RoleName.PASSENGER), role(RoleName.ADMIN)));
        CustomUserDetails details = new CustomUserDetails(user);

        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_PASSENGER", "ROLE_ADMIN");
    }

    @Test
    void getUsername_returnsUserEmail() {
        User user = user(UserStatus.ACTIVE, Set.of(role(RoleName.PASSENGER)));
        CustomUserDetails details = new CustomUserDetails(user);

        assertThat(details.getUsername()).isEqualTo("user@example.com");
    }

    @Test
    void getPassword_returnsUserPasswordHash() {
        User user = user(UserStatus.ACTIVE, Set.of(role(RoleName.PASSENGER)));
        CustomUserDetails details = new CustomUserDetails(user);

        assertThat(details.getPassword()).isEqualTo("hashed-pw");
    }

    @Test
    void isEnabled_returnsTrue_whenUserStatusActive() {
        User user = user(UserStatus.ACTIVE, Set.of(role(RoleName.PASSENGER)));
        CustomUserDetails details = new CustomUserDetails(user);

        assertThat(details.isEnabled()).isTrue();
    }

    @Test
    void isEnabled_returnsFalse_whenUserStatusNotActive() {
        User user = user(UserStatus.INACTIVE, Set.of(role(RoleName.PASSENGER)));
        CustomUserDetails details = new CustomUserDetails(user);

        assertThat(details.isEnabled()).isFalse();
    }

    @Test
    void accountStatusFlags_areAlwaysTrue() {
        User user = user(UserStatus.ACTIVE, Set.of(role(RoleName.PASSENGER)));
        CustomUserDetails details = new CustomUserDetails(user);

        assertThat(details.isAccountNonExpired()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
        assertThat(details.isCredentialsNonExpired()).isTrue();
    }

    @Test
    void getUserId_returnsUserId() {
        User user = user(UserStatus.ACTIVE, Set.of(role(RoleName.PASSENGER)));
        CustomUserDetails details = new CustomUserDetails(user);

        assertThat(details.getUserId()).isEqualTo(user.getId());
    }

    @Test
    void getUser_returnsSameWrappedUserInstance() {
        User user = user(UserStatus.ACTIVE, Set.of(role(RoleName.PASSENGER)));
        CustomUserDetails details = new CustomUserDetails(user);

        assertThat(details.getUser()).isSameAs(user);
    }
}
