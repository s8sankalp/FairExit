package com.sankalp.fairexit.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sankalp.fairexit.AbstractIntegrationTest;
import com.sankalp.fairexit.dto.TokenResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;

/**
 * Same 60 token bucket, but refilled over an hour instead of a minute, so the bucket
 * cannot refill while the loop runs on a slow CI machine. That keeps the test exact.
 */
@TestPropertySource(properties = "fairexit.rate-limit.period=1h")
class RateLimitIntegrationTest extends AbstractIntegrationTest {

    @Test
    void the61stRequestInAMinuteIs429() throws Exception {
        TokenResponse tokens = newLoggedInUser();

        for (int i = 1; i <= 60; i++) {
            mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(tokens)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-RateLimit-Remaining", String.valueOf(60 - i)));
        }

        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(tokens)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void eachUserHasTheirOwnBucket() throws Exception {
        TokenResponse heavy = newLoggedInUser();
        TokenResponse other = newLoggedInUser();

        for (int i = 0; i < 61; i++) {
            mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(heavy)));
        }

        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isOk());
    }

    @Test
    void healthChecksAreNotLimited() throws Exception {
        for (int i = 0; i < 70; i++) {
            mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        }
    }
}
