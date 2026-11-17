package com.sankalp.fairexit.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sankalp.fairexit.exception.ErrorResponseWriter;
import com.sankalp.fairexit.service.Hashing;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Makes a write safe to retry. When a client sends the same Idempotency-Key again,
 * it gets the first response back and the work is not done twice.
 *
 * 1. SET key NX claims the key (only one request can win).
 * 2. The winner runs and its response is saved under the key for 24 hours.
 * 3. A repeat with the same body gets the saved response; with a different body, 422;
 *    while the first is still running, 409.
 * Keys are per user, so two users can never see each other's responses.
 */
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9_-]{1,100}");
    private static final Duration TTL = Duration.ofHours(24);
    private static final int MAX_BODY_BYTES = 1024 * 1024; // JSON only; files go straight to storage

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ErrorResponseWriter errors;

    public IdempotencyFilter(StringRedisTemplate redis, ObjectMapper objectMapper, ErrorResponseWriter errors) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !WRITE_METHODS.contains(request.getMethod()) || request.getHeader(HEADER) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (!SAFE_KEY.matcher(key).matches()) {
            errors.write(response, HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must be 1 to 100 letters, digits, '-' or '_'");
            return;
        }
        AuthenticatedUser user = currentUser();
        if (user == null) {
            // Not logged in: Spring Security will answer 401, nothing to store
            chain.doFilter(request, response);
            return;
        }

        CachedBodyRequest cached;
        try {
            cached = new CachedBodyRequest(request, MAX_BODY_BYTES);
        } catch (CachedBodyRequest.TooLargeException e) {
            errors.write(response, HttpStatus.PAYLOAD_TOO_LARGE, "Request body too large");
            return;
        }

        String redisKey = "idem:" + user.id() + ":" + key;
        String fingerprint = fingerprint(request, cached.body());

        try {
            Boolean claimed = redis.opsForValue().setIfAbsent(
                    redisKey, toJson(IdempotencyRecord.inProgress(fingerprint)), TTL);
            if (!Boolean.TRUE.equals(claimed)) {
                answerRepeat(redisKey, fingerprint, response);
                return;
            }
        } catch (DataAccessException e) {
            // Fail closed: without Redis we cannot promise "only once", so do not run the write
            log.warn("Idempotency store unavailable: {}", e.getMessage());
            errors.write(response, HttpStatus.SERVICE_UNAVAILABLE, "Service temporarily unavailable");
            return;
        }

        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        boolean saved = false;
        try {
            chain.doFilter(cached, wrapped);
            int status = wrapped.getStatus();
            // A 5xx may be temporary, so free the key and let the client retry for real
            if (status < 500) {
                String body = new String(wrapped.getContentAsByteArray(), StandardCharsets.UTF_8);
                redis.opsForValue().set(redisKey, toJson(IdempotencyRecord.completed(
                        fingerprint, status, wrapped.getContentType(),
                        wrapped.getHeader(HttpHeaders.LOCATION), body)), TTL);
                saved = true;
            }
        } finally {
            if (!saved) {
                safeDelete(redisKey);
            }
            wrapped.copyBodyToResponse();
        }
    }

    private void answerRepeat(String redisKey, String fingerprint, HttpServletResponse response)
            throws IOException {
        String json = redis.opsForValue().get(redisKey);
        if (json == null) {
            // Expired or freed between our two calls; the client can simply retry
            errors.write(response, HttpStatus.CONFLICT, "Request with this Idempotency-Key is being retried");
            return;
        }
        IdempotencyRecord existing = objectMapper.readValue(json, IdempotencyRecord.class);
        if (!existing.fingerprint().equals(fingerprint)) {
            errors.write(response, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Idempotency-Key was already used for a different request");
            return;
        }
        if (existing.isInProgress()) {
            errors.write(response, HttpStatus.CONFLICT, "A request with this Idempotency-Key is still running");
            return;
        }
        response.setStatus(existing.status());
        if (existing.contentType() != null) {
            response.setContentType(existing.contentType());
        }
        if (existing.location() != null) {
            response.setHeader(HttpHeaders.LOCATION, existing.location());
        }
        response.setHeader(REPLAYED_HEADER, "true");
        if (existing.body() != null && !existing.body().isEmpty()) {
            response.getOutputStream().write(existing.body().getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Same key + different method, path or body means the client made a mistake. */
    private static String fingerprint(HttpServletRequest request, byte[] body) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write((request.getMethod() + " " + request.getRequestURI() + "\n").getBytes(StandardCharsets.UTF_8));
        bytes.write(body);
        return Hashing.sha256Hex(bytes.toByteArray());
    }

    private String toJson(IdempotencyRecord idempotencyRecord) throws IOException {
        return objectMapper.writeValueAsString(idempotencyRecord);
    }

    private void safeDelete(String redisKey) {
        try {
            redis.delete(redisKey);
        } catch (DataAccessException e) {
            log.warn("Could not free Idempotency-Key {}: {}", redisKey, e.getMessage());
        }
    }

    private static AuthenticatedUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthenticatedUser user ? user : null;
    }
}
