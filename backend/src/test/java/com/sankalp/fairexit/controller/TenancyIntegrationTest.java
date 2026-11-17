package com.sankalp.fairexit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sankalp.fairexit.AbstractIntegrationTest;
import com.sankalp.fairexit.config.IdempotencyFilter;
import com.sankalp.fairexit.dto.TokenResponse;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class TenancyIntegrationTest extends AbstractIntegrationTest {

    private static Map<String, Object> tenancy(String city) {
        Map<String, Object> body = new HashMap<>();
        body.put("city", city);
        body.put("state", "KA");
        body.put("flatSize", "TWO_BHK");
        body.put("rent", 25000);
        body.put("deposit", 100000);
        body.put("startDate", "2025-06-01");
        body.put("endDate", "2026-05-31");
        body.put("noticeDays", 30);
        return body;
    }

    private ResultActions create(TokenResponse user, Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/tenancies")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)));
    }

    private String createdId(TokenResponse user, String city) throws Exception {
        String body = create(user, tenancy(city))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void createReturns201WithLocationAndTheTenancy() throws Exception {
        TokenResponse user = newLoggedInUser();

        String body = create(user, tenancy("bengaluru"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/tenancies/")))
                .andExpect(jsonPath("$.city").value("Bengaluru"))
                .andExpect(jsonPath("$.stateName").value("Karnataka"))
                .andExpect(jsonPath("$.rulesCoverage").value("FULL"))
                .andExpect(jsonPath("$.startDate").value("2025-06-01"))
                .andReturn().getResponse().getContentAsString();

        String id = JsonPath.read(body, "$.id");
        mvc.perform(get("/api/tenancies/" + id).header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    /** The module's "done when": users only see their own tenancies. */
    @Test
    void usersOnlySeeTheirOwnTenancies() throws Exception {
        TokenResponse alice = newLoggedInUser();
        TokenResponse bob = newLoggedInUser();
        String alicesId = createdId(alice, "Bengaluru");
        createdId(bob, "Kochi");

        // Bob's list holds only his own tenancy
        mvc.perform(get("/api/tenancies").header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].city").value("Kochi"));

        // Bob cannot read Alice's tenancy, and the answer does not reveal that it exists
        mvc.perform(get("/api/tenancies/" + alicesId).header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Tenancy not found"));

        // Alice still can
        mvc.perform(get("/api/tenancies/" + alicesId).header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isOk());
    }

    @Test
    void listIsNewestFirst() throws Exception {
        TokenResponse user = newLoggedInUser();
        Map<String, Object> older = tenancy("Kochi");
        older.put("startDate", "2023-01-01");
        older.put("endDate", "2024-01-01");
        create(user, older).andExpect(status().isCreated());
        create(user, tenancy("Bengaluru")).andExpect(status().isCreated());

        mvc.perform(get("/api/tenancies").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$[0].city").value("Bengaluru"))
                .andExpect(jsonPath("$[1].city").value("Kochi"));
    }

    @Test
    void unknownStateCodeListsTheAllowedValues() throws Exception {
        Map<String, Object> body = tenancy("Bengaluru");
        body.put("state", "XX");

        create(newLoggedInUser(), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("state: must be one of")))
                .andExpect(jsonPath("$.message").value(containsString("KA")));
    }

    @Test
    void endDateBeforeStartIs400() throws Exception {
        Map<String, Object> body = tenancy("Bengaluru");
        body.put("endDate", "2025-01-01");

        create(newLoggedInUser(), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("endDateAfterStartDate: endDate must be after startDate"));
    }

    @Test
    void notAUuidIs400() throws Exception {
        mvc.perform(get("/api/tenancies/not-a-uuid").header(HttpHeaders.AUTHORIZATION, bearer(newLoggedInUser())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginIsRequired() throws Exception {
        mvc.perform(get("/api/tenancies")).andExpect(status().isUnauthorized());
    }

    @Test
    void retriedCreateWithTheSameKeyMakesOneTenancy() throws Exception {
        TokenResponse user = newLoggedInUser();
        String payload = json(tenancy("Bengaluru"));

        String first = postWithKey(user, payload).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = postWithKey(user, payload).andExpect(status().isCreated())
                .andExpect(header().string(IdempotencyFilter.REPLAYED_HEADER, "true"))
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
        mvc.perform(get("/api/tenancies").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    private ResultActions postWithKey(TokenResponse user, String payload) throws Exception {
        return mvc.perform(post("/api/tenancies")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .header(IdempotencyFilter.HEADER, "create-tenancy-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload));
    }
}
