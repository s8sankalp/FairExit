package com.sankalp.fairexit.service;

import com.sankalp.fairexit.config.AdminProperties;
import com.sankalp.fairexit.dto.LoginRequest;
import com.sankalp.fairexit.dto.RegisterRequest;
import com.sankalp.fairexit.dto.TokenResponse;
import com.sankalp.fairexit.dto.UserResponse;
import com.sankalp.fairexit.exception.ApiException;
import com.sankalp.fairexit.model.User;
import com.sankalp.fairexit.repository.UserRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    static final String BAD_LOGIN = "Invalid email or password";
    static final String BAD_REFRESH = "Invalid or expired refresh token";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;
    private final AdminProperties admins;

    // Checked against when the email does not exist, so a wrong email takes
    // as long as a wrong password and response time does not reveal who has an account
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder,
                       JwtService jwtService, RefreshTokenService refreshTokens, AdminProperties admins) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.admins = admins;
        this.dummyHash = passwordEncoder.encode("timing-equaliser-not-a-real-password");
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalise(request.email());
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }
        try {
            User newUser = new User(email, passwordEncoder.encode(request.password()), request.name().trim());
            if (admins.isAdmin(email)) {
                newUser.makeAdmin();
            }
            User user = users.saveAndFlush(newUser);
            return UserResponse.from(user);
        } catch (DataIntegrityViolationException e) {
            // Two sign ups with the same email at the same moment: the unique index decides
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        Optional<User> user = users.findByEmail(normalise(request.email()));
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !passwordMatches) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, BAD_LOGIN);
        }
        // An email added to the admin list after sign up is promoted at its next login
        if (!user.get().isAdmin() && admins.isAdmin(user.get().getEmail())) {
            user.get().makeAdmin();
        }
        return issueTokens(user.get());
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken) {
        UUID userId = refreshTokens.consume(refreshToken)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, BAD_REFRESH));
        User user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, BAD_REFRESH));
        return issueTokens(user);
    }

    public void logout(String refreshToken) {
        refreshTokens.revoke(refreshToken);
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(UUID userId) {
        return users.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private TokenResponse issueTokens(User user) {
        String access = jwtService.createAccessToken(user.getId(), user.getEmail(), user.isAdmin());
        String refresh = refreshTokens.issue(user.getId());
        return TokenResponse.bearer(access, refresh, jwtService.accessTtlSeconds());
    }

    private static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
