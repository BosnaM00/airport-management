package com.example.airportManager.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Unit tests for JwtAuthenticationFilter, which runs once per incoming HTTP
// request as part of the security filter chain: it reads the "Authorization"
// header, and if it finds a valid "Bearer <jwt>" token, resolves the user and
// populates SecurityContextHolder so downstream authorization checks (e.g.
// @PreAuthorize, method security) see an authenticated principal for the rest
// of the request.
//
// JwtAuthenticationFilter extends OncePerRequestFilter, a Spring base class
// that guarantees doFilterInternal runs exactly once per request even if the
// request is internally forwarded/included elsewhere in the servlet container
// (plain javax/jakarta Filters can otherwise be invoked multiple times per
// request in those cases).
//
// Plain unit test (no Spring context, no real servlet container): the test
// class deliberately lives in the same `security` package as the filter so it
// can call the `protected` doFilterInternal(...) method directly with mocked
// request/response/chain objects, rather than needing a running server. This
// keeps the test fast and isolated to just this filter's branching logic.
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    // Mocked collaborator: stands in for the real JwtUtil so we can control
    // exactly what username a token resolves to (or force it to throw) without
    // dealing with real JWT signing/parsing.
    @Mock
    private JwtUtil jwtUtil;

    // Mocked collaborator: stands in for the real user lookup so we can hand
    // back a canned UserDetails without touching the database/repository layer.
    @Mock
    private CustomUserDetailsService userDetailsService;

    // Mocked inbound HTTP request; only its getHeader("Authorization") is
    // stubbed per test to drive the filter's branches.
    @Mock
    private HttpServletRequest request;

    // Mocked HTTP response; the filter under test never writes to it directly,
    // it's only required by the doFilterInternal method signature.
    @Mock
    private HttpServletResponse response;

    // Mocked FilterChain representing "the rest of the filter chain / the
    // servlet". Tests verify it's always invoked so a request is never silently
    // dropped by this filter, valid auth or not.
    @Mock
    private FilterChain filterChain;

    // The real JwtAuthenticationFilter under test, with the @Mock fields above
    // injected into its constructor by Mockito (it uses @RequiredArgsConstructor
    // for jwtUtil/userDetailsService).
    @InjectMocks
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void clearSecurityContextBefore() {
        // SecurityContextHolder is a static, ThreadLocal-backed holder for the
        // "current" request's Authentication. Because it's static/thread-scoped
        // rather than instance state, an Authentication set by one test can leak
        // into the next test if they happen to run on the same thread, causing
        // flaky, order-dependent failures. Clearing it before each test ensures
        // every test starts from a known "nobody is authenticated" state.
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearSecurityContextAfter() {
        // Also clear after each test so this test's authentication (if any was
        // set) can't bleed into whatever runs next on this thread, for the same
        // ThreadLocal-leak reason as above.
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_noAuthorizationHeader_passesThroughWithoutSettingAuthentication() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        // No Authorization header -> filter must still call chain.doFilter so the
        // request isn't dropped; it just proceeds unauthenticated (later security
        // checks will reject it if the endpoint requires auth).
        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_headerDoesNotStartWithBearer_passesThroughWithoutSettingAuthentication() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic somecredentials");

        filter.doFilterInternal(request, response, filterChain);

        // A non-Bearer auth scheme (e.g. Basic) is not this filter's concern -
        // it should be ignored rather than treated as a malformed JWT, and the
        // request should still proceed down the chain unauthenticated.
        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_validBearerTokenAndValidUser_setsAuthentication() throws Exception {
        String token = "valid.jwt.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.extractUsername(token)).thenReturn("user@example.com");

        UserDetails userDetails = org.mockito.Mockito.mock(UserDetails.class);
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(userDetails);
        when(jwtUtil.validateToken(token, userDetails)).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        // The "happy path": a well-formed Bearer token for a resolvable, valid
        // user must result in a populated SecurityContext (via a
        // UsernamePasswordAuthenticationToken - Spring Security's standard
        // Authentication implementation for principal/credentials/authorities)
        // so the rest of the request is treated as authenticated as that user.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(userDetails);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_validBearerTokenButInvalidToken_doesNotSetAuthentication() throws Exception {
        String token = "invalid.jwt.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.extractUsername(token)).thenReturn("user@example.com");

        UserDetails userDetails = org.mockito.Mockito.mock(UserDetails.class);
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(userDetails);
        when(jwtUtil.validateToken(token, userDetails)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        // Token parses and the user resolves, but validateToken (e.g. an expired
        // or mismatched token) says no - the filter must not authenticate the
        // request, yet must still let it continue down the chain rather than
        // short-circuiting it.
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_extractUsernameThrows_swallowsExceptionAndStillProceeds() throws Exception {
        String token = "malformed.jwt.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.extractUsername(token)).thenThrow(new RuntimeException("malformed token"));

        filter.doFilterInternal(request, response, filterChain);

        // extractUsername throws -> proves a malformed/tampered token can't crash
        // the filter chain: the exception is swallowed (and logged) and the
        // request proceeds unauthenticated rather than the whole app erroring out
        // with a 500.
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
