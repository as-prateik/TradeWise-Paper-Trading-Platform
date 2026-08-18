package com.tradewise.user.dto;

import com.tradewise.user.User;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String email, String fullName, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getCreatedAt());
    }
}
