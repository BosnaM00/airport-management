package com.example.airportManager.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// Plain unit test (no Spring context): JwtUtil is a POJO whose only Spring
// dependency is the @Value-injected fields, which we set directly via
// ReflectionTestUtils since there's no constructor/setter for them.
class JwtUtilTest {

    private static final String SECRET = "test-secret-key-for-jwt-unit-tests-1234567890";
    private static final long EXPIRATION_MILLIS = 3600000L; // 1 hour

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expiration", EXPIRATION_MILLIS);
    }

    @Test
    void generateToken_thenExtractUsername_roundTripsToSameUsername() {
        User userDetails = new User("a@b.com", "pw", List.of());

        String token = jwtUtil.generateToken(userDetails);

        assertThat(jwtUtil.extractUsername(token)).isEqualTo("a@b.com");
    }

    @Test
    void generateToken_producesNonExpiredToken() {
        User userDetails = new User("a@b.com", "pw", List.of());

        String token = jwtUtil.generateToken(userDetails);

        assertThat(jwtUtil.extractExpiration(token)).isAfter(new Date());
    }

    @Test
    void validateToken_returnsTrue_forFreshTokenAndMatchingUserDetails() {
        User userDetails = new User("a@b.com", "pw", List.of());

        String token = jwtUtil.generateToken(userDetails);

        assertThat(jwtUtil.validateToken(token, userDetails)).isTrue();
    }

    @Test
    void validateToken_returnsFalse_whenUsernameDoesNotMatch() {
        User tokenOwner = new User("a@b.com", "pw", List.of());
        User otherUser = new User("someone-else@b.com", "pw", List.of());

        String token = jwtUtil.generateToken(tokenOwner);

        assertThat(jwtUtil.validateToken(token, otherUser)).isFalse();
    }

    @Test
    void generateTokenWithExtraClaims_roundTripsUsernameCorrectly() {
        User userDetails = new User("a@b.com", "pw", List.of());
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", "ADMIN");

        String token = jwtUtil.generateToken(userDetails, extraClaims);

        assertThat(jwtUtil.extractUsername(token)).isEqualTo("a@b.com");
    }
}
