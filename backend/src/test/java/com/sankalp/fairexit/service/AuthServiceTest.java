package com.sankalp.fairexit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sankalp.fairexit.config.AdminProperties;
import com.sankalp.fairexit.dto.LoginRequest;
import com.sankalp.fairexit.dto.RegisterRequest;
import com.sankalp.fairexit.dto.TokenResponse;
import com.sankalp.fairexit.dto.UserResponse;
import com.sankalp.fairexit.exception.ApiException;
import com.sankalp.fairexit.model.User;
import com.sankalp.fairexit.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository users;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokens;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        // The constructor hashes a dummy password once
        lenient().when(passwordEncoder.encode(anyString())).thenReturn("dummy-hash");
        authService = new AuthService(users, passwordEncoder, jwtService, refreshTokens,
                new AdminProperties(List.of("boss@example.com")));
    }

    private static User savedUser(String email, String hash) {
        User user = new User(email, hash, "Asha");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    @Test
    void registerStoresLowerCaseEmailAndHashedPassword() {
        when(users.existsByEmail("asha@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("bcrypt-hash");
        when(users.saveAndFlush(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
            return u;
        });

        UserResponse response = authService.register(
                new RegisterRequest("  Asha@Example.com ", "password123", " Asha "));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("asha@example.com");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(response.name()).isEqualTo("Asha");
    }

    @Test
    void registerWithTakenEmailIs409() {
        when(users.existsByEmail("asha@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("asha@example.com", "password123", "Asha")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void registerRaceOnUniqueIndexIsAlso409() {
        when(users.existsByEmail("asha@example.com")).thenReturn(false);
        when(users.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("asha@example.com", "password123", "Asha")))
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void loginWithRightPasswordIssuesBothTokens() {
        User user = savedUser("asha@example.com", "bcrypt-hash");
        when(users.findByEmail("asha@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "bcrypt-hash")).thenReturn(true);
        when(jwtService.createAccessToken(user.getId(), "asha@example.com", false)).thenReturn("access");
        when(refreshTokens.issue(user.getId())).thenReturn("refresh");
        when(jwtService.accessTtlSeconds()).thenReturn(900L);

        TokenResponse tokens = authService.login(new LoginRequest("ASHA@example.com", "password123"));

        assertThat(tokens).isEqualTo(new TokenResponse("access", "refresh", "Bearer", 900));
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameAnswer() {
        User user = savedUser("asha@example.com", "bcrypt-hash");
        when(users.findByEmail("asha@example.com")).thenReturn(Optional.of(user));
        when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches("wrong", "bcrypt-hash")).thenReturn(false);
        when(passwordEncoder.matches("wrong", "dummy-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("asha@example.com", "wrong")))
                .hasMessage(AuthService.BAD_LOGIN);
        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "wrong")))
                .hasMessage(AuthService.BAD_LOGIN);
        // The unknown email still ran a BCrypt check, so both take the same time
        verify(passwordEncoder).matches("wrong", "dummy-hash");
    }

    @Test
    void refreshWithUnknownTokenIs401() {
        when(refreshTokens.consume("old")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("old"))
                .hasMessage(AuthService.BAD_REFRESH)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void refreshForDeletedUserIs401() {
        UUID id = UUID.randomUUID();
        when(refreshTokens.consume("old")).thenReturn(Optional.of(id));
        when(users.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("old")).hasMessage(AuthService.BAD_REFRESH);
    }

    @Test
    void refreshIssuesANewPair() {
        User user = savedUser("asha@example.com", "bcrypt-hash");
        when(refreshTokens.consume("old")).thenReturn(Optional.of(user.getId()));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(jwtService.createAccessToken(user.getId(), "asha@example.com", false)).thenReturn("access2");
        when(refreshTokens.issue(user.getId())).thenReturn("refresh2");

        TokenResponse tokens = authService.refresh("old");

        assertThat(tokens.accessToken()).isEqualTo("access2");
        assertThat(tokens.refreshToken()).isEqualTo("refresh2");
    }

    @Test
    void anEmailOnTheAdminListIsPromotedAtLoginAndGetsAnAdminToken() {
        User boss = savedUser("boss@example.com", "bcrypt-hash");
        when(users.findByEmail("boss@example.com")).thenReturn(Optional.of(boss));
        when(passwordEncoder.matches("password123", "bcrypt-hash")).thenReturn(true);
        when(jwtService.createAccessToken(boss.getId(), "boss@example.com", true)).thenReturn("admin-access");

        TokenResponse tokens = authService.login(new LoginRequest("boss@example.com", "password123"));

        assertThat(boss.isAdmin()).isTrue();
        assertThat(tokens.accessToken()).isEqualTo("admin-access");
    }

    @Test
    void logoutRevokesTheRefreshToken() {
        authService.logout("refresh");

        verify(refreshTokens).revoke("refresh");
    }
}
