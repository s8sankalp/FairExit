package com.sankalp.fairexit.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sankalp.fairexit.AbstractIntegrationTest;
import com.sankalp.fairexit.dto.TokenResponse;
import com.sankalp.fairexit.service.jobs.JobWorker;
import com.sankalp.fairexit.service.llm.LlmClient;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/** The real limit is 20 a day. Here it is 2, so the test stays short. */
@TestPropertySource(properties = "fairexit.llm.daily-limit=2")
class LlmDailyLimitIntegrationTest extends AbstractIntegrationTest {

    @MockBean
    private LlmClient llm;
    @Autowired
    private JobWorker worker;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void emptyQueue() {
        jdbc.update("DELETE FROM jobs");
    }

    private String create(TokenResponse user, String path, Object body) throws Exception {
        return JsonPath.read(mvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String statusOf(TokenResponse user, String billId, String expected) throws Exception {
        return mvc.perform(get("/api/bills/" + billId).header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$.status").value(expected))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void afterTheDailyLimitTheLlmIsNotCalledAndTheUserIsToldWhy() throws Exception {
        when(llm.generateJson(anyString(), anyString())).thenReturn(
                "{\"lines\":[{\"item\":\"Painting\",\"category\":\"PAINTING\",\"amount\":5000}]}");
        TokenResponse user = newLoggedInUser();
        Map<String, Object> tenancy = new HashMap<>();
        tenancy.put("city", "Kochi");
        tenancy.put("state", "KL");
        tenancy.put("flatSize", "ONE_BHK");
        tenancy.put("rent", 15000);
        tenancy.put("deposit", 50000);
        tenancy.put("startDate", "2024-01-01");
        String bills = "/api/tenancies/" + create(user, "/api/tenancies", tenancy) + "/bills";

        // Three different messages, so the cache cannot answer any of them
        String first = create(user, bills, Map.of("text", "Painting 5000"));
        String second = create(user, bills, Map.of("text", "Painting 6000"));
        String third = create(user, bills, Map.of("text", "Painting 7000"));
        worker.runOnce();

        statusOf(user, first, "EXTRACTED");
        statusOf(user, second, "EXTRACTED");
        mvc.perform(get("/api/bills/" + third).header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$.status").value("EXTRACTION_FAILED"))
                .andExpect(jsonPath("$.extractionError").value(containsString("today's automatic readings")));
        verify(llm, times(2)).generateJson(anyString(), anyString());

        // Another user has their own limit
        TokenResponse other = newLoggedInUser();
        String othersBills = "/api/tenancies/" + create(other, "/api/tenancies", tenancy) + "/bills";
        String othersBill = create(other, othersBills, Map.of("text", "Painting 8000"));
        worker.runOnce();
        statusOf(other, othersBill, "EXTRACTED");
    }
}
