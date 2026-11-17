package com.sankalp.fairexit.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sankalp.fairexit.AbstractIntegrationTest;
import com.sankalp.fairexit.dto.TokenResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class ReplyIntegrationTest extends AbstractIntegrationTest {

    private String postJson(TokenResponse user, String path, Object body) throws Exception {
        return mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
    }

    private String auditFor(TokenResponse user) throws Exception {
        Map<String, Object> tenancy = new HashMap<>();
        tenancy.put("city", "Bengaluru");
        tenancy.put("state", "KA");
        tenancy.put("flatSize", "TWO_BHK");
        tenancy.put("rent", 30000);
        tenancy.put("deposit", 150000);
        tenancy.put("startDate", "2024-06-01");
        tenancy.put("endDate", "2026-06-01");
        String tenancyId = JsonPath.read(postJson(user, "/api/tenancies", tenancy), "$.id");
        String billId = JsonPath.read(postJson(user, "/api/tenancies/" + tenancyId + "/bills", Map.of("lines", List.of(
                Map.of("item", "Full flat repainting", "category", "PAINTING", "amount", 55000),
                Map.of("item", "Last month rent", "category", "RENT", "amount", 30000),
                Map.of("item", "Broken bathroom tap", "category", "REPAIR", "amount", 1500)))), "$.id");
        return JsonPath.read(postJson(user, "/api/bills/" + billId + "/audit", Map.of()), "$.id");
    }

    @Test
    void repliesAreBuiltFromTheStoredAudit() throws Exception {
        TokenResponse user = newLoggedInUser();
        String auditId = auditFor(user);

        mvc.perform(get("/api/audits/" + auditId + "/replies").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auditId").value(auditId))
                .andExpect(jsonPath("$.disclaimer").value("This is information, not legal advice."))
                // fair: rent 30,000; disputed: painting 55,000; needs info: tap 1,500
                .andExpect(jsonPath("$.polite").value(containsString("I agree to this charge, ₹30,000 in total:")))
                .andExpect(jsonPath("$.polite").value(containsString("₹55,000")))
                .andExpect(jsonPath("$.polite").value(containsString("the refund comes to ₹1,20,000")))
                .andExpect(jsonPath("$.polite").value(containsString("Regards,\nTest User")))
                .andExpect(jsonPath("$.firm").value(containsString("Please return ₹1,20,000 within 7 days")))
                .andExpect(jsonPath("$.firm").value(not(containsString("legal advice"))));
    }

    @Test
    void anotherUserCannotGetTheReplies() throws Exception {
        String auditId = auditFor(newLoggedInUser());

        mvc.perform(get("/api/audits/" + auditId + "/replies")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newLoggedInUser())))
                .andExpect(status().isNotFound());
    }
}
