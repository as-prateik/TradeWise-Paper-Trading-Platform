package com.tradewise.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-0123456789abcdef0123456789abcdef";

    private final JwtService jwtService = new JwtService(new JwtProperties(SECRET, 3600, "tradewise"));

    @Test
    @DisplayName("issued token verifies back to the same principal")
    void issuedTokenRoundTrips() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.issueToken(userId, "user@example.com");
        Optional<AuthenticatedUser> principal = jwtService.verify(token);

        assertThat(principal).hasValue(new AuthenticatedUser(userId, "user@example.com"));
    }

    @Test
    @DisplayName("a tampered token is rejected")
    void tamperedTokenIsRejected() {
        String token = jwtService.issueToken(UUID.randomUUID(), "user@example.com");
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThat(jwtService.verify(tampered)).isEmpty();
    }

    @Test
    @DisplayName("a token signed with a different key is rejected")
    void foreignTokenIsRejected() {
        JwtService otherService = new JwtService(
                new JwtProperties("another-secret-0123456789abcdef0123456789abcdef", 3600, "tradewise"));
        String foreignToken = otherService.issueToken(UUID.randomUUID(), "user@example.com");

        assertThat(jwtService.verify(foreignToken)).isEmpty();
    }

    @Test
    @DisplayName("an expired token is rejected")
    void expiredTokenIsRejected() {
        JwtService shortLived = new JwtService(new JwtProperties(SECRET, -60, "tradewise"));
        String expired = shortLived.issueToken(UUID.randomUUID(), "user@example.com");

        assertThat(jwtService.verify(expired)).isEmpty();
    }

    @Test
    @DisplayName("a token with the wrong issuer is rejected")
    void wrongIssuerIsRejected() {
        JwtService otherIssuer = new JwtService(new JwtProperties(SECRET, 3600, "someone-else"));
        String token = otherIssuer.issueToken(UUID.randomUUID(), "user@example.com");

        assertThat(jwtService.verify(token)).isEmpty();
    }

    @Test
    @DisplayName("startup fails fast when the secret is missing or too short")
    void missingOrShortSecretFailsFast() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("", 3600, "tradewise")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");

        assertThatThrownBy(() -> new JwtService(new JwtProperties("too-short", 3600, "tradewise")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
