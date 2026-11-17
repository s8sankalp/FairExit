package com.sankalp.fairexit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sankalp.fairexit.config.AuthenticatedUser;
import com.sankalp.fairexit.config.JwtProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-that-is-at-least-32-bytes";
    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static JwtService serviceAt(Instant instant, String secret) {
        JwtProperties props = new JwtProperties(secret, Duration.ofMinutes(15), Duration.ofDays(7));
        return new JwtService(props, Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void validTokenGivesBackTheUser() {
        JwtService jwt = serviceAt(NOW, SECRET);
        String token = jwt.createAccessToken(USER_ID, "a@example.com");

        Optional<AuthenticatedUser> user = jwt.parse(token);

        assertThat(user).contains(new AuthenticatedUser(USER_ID, "a@example.com"));
    }

    @Test
    void tokenStillWorksJustBefore15Minutes() {
        String token = serviceAt(NOW, SECRET).createAccessToken(USER_ID, "a@example.com");

        assertThat(serviceAt(NOW.plus(Duration.ofMinutes(14)), SECRET).parse(token)).isPresent();
    }

    @Test
    void tokenIsRejectedAfter15Minutes() {
        String token = serviceAt(NOW, SECRET).createAccessToken(USER_ID, "a@example.com");

        assertThat(serviceAt(NOW.plus(Duration.ofMinutes(16)), SECRET).parse(token)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String forged = serviceAt(NOW, "another-secret-that-is-also-32-bytes-long")
                .createAccessToken(USER_ID, "a@example.com");

        assertThat(serviceAt(NOW, SECRET).parse(forged)).isEmpty();
    }

    @Test
    void garbageIsRejected() {
        assertThat(serviceAt(NOW, SECRET).parse("not-a-jwt")).isEmpty();
        assertThat(serviceAt(NOW, SECRET).parse("")).isEmpty();
    }

    @Test
    void shortSecretStopsStartup() {
        assertThatThrownBy(() -> serviceAt(NOW, "too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> serviceAt(NOW, null))
                .isInstanceOf(IllegalStateException.class);
    }
}
