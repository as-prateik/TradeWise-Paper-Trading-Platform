package com.tradewise.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings. The secret has no default anywhere: the application fails fast at
 * startup when it is missing or too short (see {@link JwtService}).
 */
@ConfigurationProperties(prefix = "tradewise.security.jwt")
public record JwtProperties(String secret, long expirySeconds, String issuer) {
}
