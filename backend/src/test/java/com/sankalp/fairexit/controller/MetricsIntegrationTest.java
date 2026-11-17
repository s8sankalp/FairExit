package com.sankalp.fairexit.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sankalp.fairexit.AbstractIntegrationTest;
import com.sankalp.fairexit.dto.TokenResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * What Prometheus scrapes, and so what the Grafana dashboard can show.
 * Spring Boot turns metric exporters off in tests; @AutoConfigureObservability turns them back on.
 */
@AutoConfigureObservability
class MetricsIntegrationTest extends AbstractIntegrationTest {

    @Test
    void theAppsOwnMetricsAreExposedForPrometheus() throws Exception {
        TokenResponse user = newLoggedInUser();
        String tenancyId = createTenancy(user);
        String billId = JsonPath.read(mvc.perform(post("/api/tenancies/" + tenancyId + "/bills")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("lines", List.of(Map.of("item", "Painting", "category", "PAINTING",
                                "amount", 20000))))))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/bills/" + billId + "/audit").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isCreated());

        mvc.perform(get("/actuator/prometheus")) // no login: Prometheus scrapes it
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("fairexit_audit_duration_seconds_bucket")))
                .andExpect(content().string(containsString("fairexit_jobs_pending")))
                .andExpect(content().string(containsString("fairexit_reminders_queued")))
                .andExpect(content().string(containsString("http_server_requests_seconds_bucket")))
                .andExpect(content().string(containsString("application=\"fairexit\"")));
    }
}
