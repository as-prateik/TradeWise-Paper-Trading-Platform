package com.tradewise.auth;

import com.tradewise.auth.dto.AuthResponse;
import com.tradewise.auth.dto.LoginRequest;
import com.tradewise.auth.dto.RegisterRequest;
import com.tradewise.exception.ApiException;
import com.tradewise.exception.ErrorCode;
import com.tradewise.security.JwtService;
import com.tradewise.user.User;
import com.tradewise.user.UserService;
import com.tradewise.user.dto.UserResponse;
import com.tradewise.wallet.WalletService;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates registration and login. Registration creates the user and seeds the
 * wallet atomically: no user ever exists without a wallet.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /**
     * A real BCrypt hash of a random throwaway value. Login verifies against this when
     * the email is unknown so response timing does not reveal whether an account exists.
     */
    private static final String TIMING_DEFENSE_HASH =
            "$2a$12$sD4G0drHnpZWaMBrJHZQO.O9IxHguYLREaDzhrhZ5rZbkAbC9vgxi";

    private final UserService userService;
    private final WalletService walletService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());

        // Pre-check is UX only; the unique index on lower(email) is the source of truth.
        if (userService.findByEmail(email).isPresent()) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED, "This email is already registered");
        }

        User user;
        try {
            user = userService.createUser(email, passwordEncoder.encode(request.password()),
                    request.fullName().trim());
            walletService.openWallet(user.getId());
        } catch (DataIntegrityViolationException raceLoser) {
            // Two concurrent registrations with the same email: the constraint decides.
            throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED, "This email is already registered");
        }

        String token = jwtService.issueToken(user.getId(), user.getEmail());
        return AuthResponse.bearer(token, jwtService.expirySeconds(), UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        Optional<User> userLookup = userService.findByEmail(email);

        // Identical work and identical response for unknown email and wrong password:
        // no user-enumeration oracle, by content or by timing.
        String hashToCheck = userLookup.map(User::getPasswordHash).orElse(TIMING_DEFENSE_HASH);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCheck);

        if (userLookup.isEmpty() || !passwordMatches) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
        }

        User user = userLookup.get();
        String token = jwtService.issueToken(user.getId(), user.getEmail());
        return AuthResponse.bearer(token, jwtService.expirySeconds(), UserResponse.from(user));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
