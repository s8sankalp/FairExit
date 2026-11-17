package com.sankalp.fairexit.service;

import com.sankalp.fairexit.config.AuthenticatedUser;
import com.sankalp.fairexit.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/** Creates and checks short lived access tokens (HS256 signed JWTs). */
@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32; // HS256 needs a 256 bit key

    private final SecretKey key;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        String secret = properties.secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set and at least " + MIN_SECRET_BYTES + " bytes long");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.properties = properties;
        this.clock = clock;
    }

    public String createAccessToken(UUID userId, String email) {
        return createAccessToken(userId, email, false);
    }

    public String createAccessToken(UUID userId, String email, boolean admin) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("admin", admin)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTtl())))
                .signWith(key)
                .compact();
    }

    /** Empty when the token is expired, tampered with or not a JWT at all. */
    public Optional<AuthenticatedUser> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new AuthenticatedUser(UUID.fromString(claims.getSubject()),
                    claims.get("email", String.class), Boolean.TRUE.equals(claims.get("admin", Boolean.class))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long accessTtlSeconds() {
        return properties.accessTtl().toSeconds();
    }
}
