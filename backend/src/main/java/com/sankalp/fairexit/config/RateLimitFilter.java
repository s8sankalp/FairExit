package com.sankalp.fairexit.config;

import com.sankalp.fairexit.exception.ErrorResponseWriter;
import com.sankalp.fairexit.service.RedisRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 60 requests per minute per logged in user. Requests without a login (register,
 * login, refresh) are limited per IP address instead, which also slows password guessing.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisRateLimiter limiter;
    private final ErrorResponseWriter errors;

    public RateLimitFilter(RedisRateLimiter limiter, ErrorResponseWriter errors) {
        this.limiter = limiter;
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        RedisRateLimiter.Result result = limiter.tryConsume(bucketKey(request));

        response.setHeader("X-RateLimit-Limit", String.valueOf(limiter.capacity()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));

        if (!result.allowed()) {
            long retrySeconds = Math.max(1, (result.retryAfterMs() + 999) / 1000);
            response.setHeader("Retry-After", String.valueOf(retrySeconds));
            errors.write(response, HttpStatus.TOO_MANY_REQUESTS,
                    "Too many requests, try again in " + retrySeconds + " seconds");
            return;
        }
        chain.doFilter(request, response);
    }

    private static String bucketKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return "rl:user:" + user.id();
        }
        return "rl:ip:" + request.getRemoteAddr();
    }
}
