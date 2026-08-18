package com.tradewise.security;

import java.util.UUID;

/**
 * The authenticated principal. Identity is always read from here (i.e. from the verified
 * token), never from a request body or path parameter.
 */
public record AuthenticatedUser(UUID userId, String email) {
}
