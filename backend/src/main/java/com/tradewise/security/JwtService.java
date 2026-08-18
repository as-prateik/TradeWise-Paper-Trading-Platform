package com.tradewise.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies HS256-signed JWTs. Subject is the user id; email travels as a claim.
 */
@Service
public class JwtService {

    private static final int MINIMUM_SECRET_BYTES = 32;

    private final SecretKey signingKey;
    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        String secret = properties.secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET is not configured. Refusing to start without a signing secret.");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least " + MINIMUM_SECRET_BYTES + " bytes for HS256.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.properties = properties;
    }

    public String issueToken(UUID userId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.expirySeconds())))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public long expirySeconds() {
        return properties.expirySeconds();
    }

    /**
     * Verifies signature, expiry and issuer. Returns the principal or empty when the
     * token is invalid for any reason — the caller never learns why, on purpose.
     */
    public Optional<AuthenticatedUser> verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            UUID userId = UUID.fromString(claims.getSubject());
            return Optional.of(new AuthenticatedUser(userId, claims.get("email", String.class)));
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
