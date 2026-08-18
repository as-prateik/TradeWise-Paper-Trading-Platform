package com.tradewise.user;

import java.util.Optional;
import java.util.UUID;

/**
 * The user module's contract. Other modules call this interface, never {@link UserRepository}.
 */
public interface UserService {

    User createUser(String email, String passwordHash, String fullName);

    Optional<User> findByEmail(String email);

    Optional<User> findById(UUID id);
}
