package com.sankalp.fairexit.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sankalp.fairexit.exception.ErrorResponseWriter;
import com.sankalp.fairexit.service.JwtService;
import com.sankalp.fairexit.service.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.time.Duration;
import java.util.List;

/**
 * Request order: JWT filter -> rate limit -> idempotency -> authorization -> controller.
 * Rate limiting runs after JWT so it can count per user, and before idempotency so a
 * flood of retries is still throttled.
 *
 * The three filters are created here with "new", not as @Component beans, so Spring Boot
 * does not also register them a second time as plain servlet filters.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS for the local frontend. Only the listed origins may call the API from a browser;
     * preflight (OPTIONS) requests are answered by Spring Security's CorsFilter before any
     * login check. Headers the frontend must read (Location, Retry-After ...) are exposed.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Request-Id"));
        config.setExposedHeaders(List.of("Location", "X-Request-Id", "Idempotent-Replayed", "Retry-After",
                "X-RateLimit-Limit", "X-RateLimit-Remaining"));
        config.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtService jwtService,
                                                   RedisRateLimiter rateLimiter,
                                                   StringRedisTemplate redis,
                                                   ObjectMapper objectMapper,
                                                   ErrorResponseWriter errors) throws Exception {
        http
                // No cookies or sessions: the token is sent by hand, so CSRF cannot happen
                .cors(cors -> { }) // uses the corsConfigurationSource bean above
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // /actuator/prometheus is open for the local Prometheus; in production put it on a
                        // separate management port that is not exposed to the internet
                        .requestMatchers("/api/auth/**", "/actuator/health", "/actuator/prometheus", "/error")
                        .permitAll()
                        // API docs; the endpoints they describe still need a login
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                errors.write(res, HttpStatus.UNAUTHORIZED, "Authentication required"))
                        .accessDeniedHandler((req, res, ex) ->
                                errors.write(res, HttpStatus.FORBIDDEN, "Access denied")))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new RateLimitFilter(rateLimiter, errors), JwtAuthenticationFilter.class)
                .addFilterAfter(new IdempotencyFilter(redis, objectMapper, errors), RateLimitFilter.class);
        return http.build();
    }
}
