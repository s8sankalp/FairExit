package com.sankalp.fairexit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sankalp.fairexit.AbstractIntegrationTest;
import com.sankalp.fairexit.config.IdempotencyFilter;
import com.sankalp.fairexit.dto.TokenResponse;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Module 1 has no business write endpoint yet, so a test only controller counts
 * how many times its handler really ran.
 */
@Import(IdempotencyIntegrationTest.CounterController.class)
class IdempotencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CounterController counter;

    @Test
    void repeatedKeyReturnsTheSameResponseAndRunsOnce() throws Exception {
        TokenResponse tokens = newLoggedInUser();
        int before = counter.calls.get();

        String first = send(tokens, "key-1", "rent")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = send(tokens, "key-1", "rent")
                .andExpect(status().isCreated())
                .andExpect(header().string(IdempotencyFilter.REPLAYED_HEADER, "true"))
                .andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
        assertThat(counter.calls.get()).isEqualTo(before + 1);
    }

    @Test
    void sameKeyWithADifferentBodyIs422() throws Exception {
        TokenResponse tokens = newLoggedInUser();
        send(tokens, "key-2", "rent").andExpect(status().isCreated());

        send(tokens, "key-2", "painting").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void differentKeysRunSeparately() throws Exception {
        TokenResponse tokens = newLoggedInUser();
        int before = counter.calls.get();

        send(tokens, "key-a", "rent").andExpect(status().isCreated());
        send(tokens, "key-b", "rent").andExpect(status().isCreated());

        assertThat(counter.calls.get()).isEqualTo(before + 2);
    }

    @Test
    void keysAreScopedPerUser() throws Exception {
        TokenResponse alice = newLoggedInUser();
        TokenResponse bob = newLoggedInUser();
        int before = counter.calls.get();

        send(alice, "shared-key", "rent").andExpect(status().isCreated());
        send(bob, "shared-key", "rent")
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(IdempotencyFilter.REPLAYED_HEADER));

        assertThat(counter.calls.get()).isEqualTo(before + 2);
    }

    @Test
    void withoutAKeyEveryRequestRuns() throws Exception {
        TokenResponse tokens = newLoggedInUser();
        int before = counter.calls.get();

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/test/counter")
                            .header(HttpHeaders.AUTHORIZATION, bearer(tokens))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("item", "rent"))))
                    .andExpect(status().isCreated());
        }

        assertThat(counter.calls.get()).isEqualTo(before + 2);
    }

    @Test
    void unsafeKeyIs400() throws Exception {
        send(newLoggedInUser(), "bad key with spaces", "rent").andExpect(status().isBadRequest());
    }

    private ResultActions send(TokenResponse tokens, String key, String item) throws Exception {
        return mvc.perform(post("/api/test/counter")
                .header(HttpHeaders.AUTHORIZATION, bearer(tokens))
                .header(IdempotencyFilter.HEADER, key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("item", item))));
    }

    @RestController
    static class CounterController {

        final AtomicInteger calls = new AtomicInteger();

        @PostMapping("/api/test/counter")
        @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
        Map<String, Object> increment(@RequestBody Map<String, String> body) {
            return Map.of("call", calls.incrementAndGet(), "item", body.get("item"));
        }
    }
}
