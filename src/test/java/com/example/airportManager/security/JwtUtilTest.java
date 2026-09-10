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

// Unit tests for JwtUtil, the component responsible for signing, parsing and
// validating the JWTs used throughout this app's JWT-based auth flow: it mints
// a token for an authenticated user (generateToken), and later lets callers
// (chiefly JwtAuthenticationFilter) pull the username/expiration back out of an
// incoming token and confirm it's still valid (extractUsername/validateToken).
//
// Plain unit test (no Spring context): JwtUtil is a POJO whose only Spring
// dependency is the @Value-injected fields, which we set directly via
// ReflectionTestUtils since there's no constructor/setter for them. Not booting
// Spring keeps these tests fast and focused purely on JwtUtil's own logic
// (token encode/decode, expiration math, signature verification) rather than
// on wiring or property resolution.
class JwtUtilTest {

    // Test-only HMAC-SHA256 signing secret. jjwt requires HMAC-SHA256 keys to be
    // at least 32 bytes (256 bits); this string is intentionally long enough to
    // satisfy that requirement so Keys.hmacShaKeyFor(...) doesn't throw.
    private static final String SECRET = "test-secret-key-for-jwt-unit-tests-1234567890";
    private static final long EXPIRATION_MILLIS = 3600000L; // 1 hour

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        // JwtUtil's secret/expiration fields are @Value-injected by Spring at
        // runtime and have no setters, so there's no normal way to populate them
        // in a plain (non-Spring) unit test. ReflectionTestUtils.setField reaches
        // past that and assigns the private fields directly via reflection,
        // letting us configure the object without starting a Spring context or
        // supplying a property source - a common pattern for unit-testing
        // @Value-configured classes in isolation.
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expiration", EXPIRATION_MILLIS);
    }

    @Test
    void generateToken_thenExtractUsername_roundTripsToSameUsername() {
        // Spring Security's built-in User class is used here purely as a ready-made
        // UserDetails implementation - JwtUtil only ever calls getUsername() on it.
        User userDetails = new User("a@b.com", "pw", List.of());

        String token = jwtUtil.generateToken(userDetails);

        // Round-trips a generated token through extractUsername to prove the JWT
        // actually encodes the subject correctly, not just that generateToken
        // doesn't throw.
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("a@b.com");
    }

    @Test
    void generateToken_producesNonExpiredToken() {
        User userDetails = new User("a@b.com", "pw", List.of());

        String token = jwtUtil.generateToken(userDetails);

        // Confirms the expiration claim is actually set in the future (using the
        // injected EXPIRATION_MILLIS), i.e. that createToken's expiration math is
        // wired correctly rather than defaulting to some already-past value.
        assertThat(jwtUtil.extractExpiration(token)).isAfter(new Date());
    }

    @Test
    void validateToken_returnsTrue_forFreshTokenAndMatchingUserDetails() {
        User userDetails = new User("a@b.com", "pw", List.of());

        String token = jwtUtil.generateToken(userDetails);

        // The "happy path": a token just issued for this exact user, checked
        // against that same user, must validate successfully.
        assertThat(jwtUtil.validateToken(token, userDetails)).isTrue();
    }

    @Test
    void validateToken_returnsFalse_whenUsernameDoesNotMatch() {
        User tokenOwner = new User("a@b.com", "pw", List.of());
        User otherUser = new User("someone-else@b.com", "pw", List.of());

        String token = jwtUtil.generateToken(tokenOwner);

        // A token minted for one user must not validate against a different
        // user's UserDetails, even though the token itself is well-formed and
        // unexpired - guards against cross-user token misuse.
        assertThat(jwtUtil.validateToken(token, otherUser)).isFalse();
    }

    @Test
    void generateTokenWithExtraClaims_roundTripsUsernameCorrectly() {
        User userDetails = new User("a@b.com", "pw", List.of());
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", "ADMIN");

        String token = jwtUtil.generateToken(userDetails, extraClaims);

        // Verifies the overload that accepts extra (custom) claims still encodes
        // the subject correctly - adding extra claims shouldn't disturb the
        // username round-trip.
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("a@b.com");
    }
}
