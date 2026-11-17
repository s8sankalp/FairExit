package com.sankalp.fairexit.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * The worked example through the real API. Agreements and utility bill uploads arrive in
 * module 6, so here the engine runs with no agreement and no bills on file. The full
 * example with an agreement is covered by WorkedExampleTest.
 */
class BillAuditIntegrationTest extends AbstractIntegrationTest {

    private static Map<String, Object> line(String item, String category, int amount) {
        return Map.of("item", item, "category", category, "amount", amount);
    }

    private static Map<String, Object> workedExampleBill() {
        return Map.of("lines", List.of(
                line("Full flat repainting", "PAINTING", 55000),
                line("Lift usage", "SOCIETY_CHARGE", 2000),
                line("Deep cleaning", "CLEANING", 8000),
                line("Broken bathroom tap", "REPAIR", 1500),
                line("Pending electricity bill", "UTILITY", 3200)));
    }

    private String createTenancy(TokenResponse user, String state) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("city", "Bengaluru");
        body.put("state", state);
        body.put("flatSize", "TWO_BHK");
        body.put("rent", 30000);
        body.put("deposit", 150000);
        body.put("startDate", "2024-06-01");
        body.put("endDate", "2026-06-01");
        return JsonPath.read(postJson(user, "/api/tenancies", body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String createBill(TokenResponse user, String tenancyId) throws Exception {
        return JsonPath.read(postJson(user, "/api/tenancies/" + tenancyId + "/bills", workedExampleBill())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions postJson(TokenResponse user, String path, Object body) throws Exception {
        return mvc.perform(post(path)
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)));
    }

    private ResultActions runAudit(TokenResponse user, String billId) throws Exception {
        return mvc.perform(post("/api/bills/" + billId + "/audit")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    @Test
    void manualBillIsStoredConfirmedWithItsTotal() throws Exception {
        TokenResponse user = newLoggedInUser();
        String tenancyId = createTenancy(user, "KA");

        postJson(user, "/api/tenancies/" + tenancyId + "/bills", workedExampleBill())
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/bills/")))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.total").value(69700))
                .andExpect(jsonPath("$.lines", hasSize(5)))
                .andExpect(jsonPath("$.lines[0].position").value(1))
                .andExpect(jsonPath("$.lines[4].item").value("Pending electricity bill"));
    }

    @Test
    void auditGivesAVerdictPerLineAndTotals() throws Exception {
        TokenResponse user = newLoggedInUser();
        String billId = createBill(user, createTenancy(user, "KA"));

        String body = runAudit(user, billId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rulesVersion").value("R1@1,R2@1,R3@2,R4@1,R5@1,R6@1,R7@1"))
                .andExpect(jsonPath("$.rulesCoverage").value("FULL"))
                .andExpect(jsonPath("$.tenancyMonths").value(24))
                .andExpect(jsonPath("$.disclaimer").value("This is information, not legal advice."))
                // painting and cleaning after 24 months: wear and tear
                .andExpect(jsonPath("$.verdicts[0].verdict").value("QUESTIONABLE"))
                .andExpect(jsonPath("$.verdicts[0].ruleIds[0]").value("R3@2"))
                .andExpect(jsonPath("$.verdicts[0].reason").value(containsString("Upload your agreement")))
                .andExpect(jsonPath("$.verdicts[2].verdict").value("QUESTIONABLE"))
                // no agreement on file yet, so the lift charge cannot be called "not allowed"
                .andExpect(jsonPath("$.verdicts[1].verdict").value("NEEDS_INFO"))
                .andExpect(jsonPath("$.verdicts[3].verdict").value("NEEDS_INFO"))
                .andExpect(jsonPath("$.verdicts[4].verdict").value("NEEDS_INFO"))
                .andExpect(jsonPath("$.totals.claimed").value(69700))
                .andExpect(jsonPath("$.totals.fair").value(0))
                .andExpect(jsonPath("$.totals.questionable").value(63000))
                .andExpect(jsonPath("$.totals.needsInfo").value(6700))
                .andExpect(jsonPath("$.totals.suggestedRefund").value(150000))
                .andReturn().getResponse().getContentAsString();

        // The stored audit reads back the same, and the bill is now AUDITED
        String auditId = JsonPath.read(body, "$.id");
        mvc.perform(get("/api/audits/" + auditId).header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdicts", hasSize(5)))
                .andExpect(jsonPath("$.totals.questionable").value(63000));
        mvc.perform(get("/api/bills/" + billId).header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$.status").value("AUDITED"));
    }

    @Test
    void rentMatchingTheTenancyIsFair() throws Exception {
        TokenResponse user = newLoggedInUser();
        String tenancyId = createTenancy(user, "KA");
        String billId = JsonPath.read(postJson(user, "/api/tenancies/" + tenancyId + "/bills",
                Map.of("lines", List.of(line("Last month rent", "RENT", 30000))))
                .andReturn().getResponse().getContentAsString(), "$.id");

        runAudit(user, billId)
                .andExpect(jsonPath("$.verdicts[0].verdict").value("FAIR"))
                .andExpect(jsonPath("$.verdicts[0].ruleIds[0]").value("R1@1"))
                .andExpect(jsonPath("$.totals.fair").value(30000))
                .andExpect(jsonPath("$.totals.suggestedRefund").value(120000));
    }

    @Test
    void aStateWithoutItsOwnRulesIsLabelledGeneralGuidance() throws Exception {
        TokenResponse user = newLoggedInUser();
        String billId = createBill(user, createTenancy(user, "MH"));

        runAudit(user, billId)
                .andExpect(jsonPath("$.rulesCoverage").value("GENERAL_GUIDANCE"))
                .andExpect(jsonPath("$.disclaimer").value(containsString("general guidance")));
    }

    @Test
    void runningAgainCreatesANewAuditAndKeepsTheOldOne() throws Exception {
        TokenResponse user = newLoggedInUser();
        String billId = createBill(user, createTenancy(user, "KA"));

        String first = JsonPath.read(runAudit(user, billId).andReturn().getResponse().getContentAsString(), "$.id");
        String second = JsonPath.read(runAudit(user, billId).andReturn().getResponse().getContentAsString(), "$.id");

        org.assertj.core.api.Assertions.assertThat(second).isNotEqualTo(first);
        mvc.perform(get("/api/audits/" + first).header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk());
    }

    @Test
    void anotherUserCannotSeeOrAuditTheBill() throws Exception {
        TokenResponse alice = newLoggedInUser();
        TokenResponse bob = newLoggedInUser();
        String tenancyId = createTenancy(alice, "KA");
        String billId = createBill(alice, tenancyId);
        String auditId = JsonPath.read(runAudit(alice, billId).andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/bills/" + billId).header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isNotFound());
        runAudit(bob, billId).andExpect(status().isNotFound());
        mvc.perform(get("/api/audits/" + auditId).header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isNotFound());
        // and Bob cannot add a bill to Alice's tenancy
        postJson(bob, "/api/tenancies/" + tenancyId + "/bills", workedExampleBill())
                .andExpect(status().isNotFound());
    }

    @Test
    void aTenancysBillsAreListedNewestFirstWithTheirLatestAudit() throws Exception {
        TokenResponse user = newLoggedInUser();
        String tenancyId = createTenancy(user, "KA");
        String audited = createBill(user, tenancyId);
        String auditId = JsonPath.read(runAudit(user, audited).andReturn().getResponse().getContentAsString(), "$.id");
        String fresh = createBill(user, tenancyId);

        mvc.perform(get("/api/tenancies/" + tenancyId + "/bills").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(fresh))
                .andExpect(jsonPath("$[0].latestAuditId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[1].id").value(audited))
                .andExpect(jsonPath("$[1].latestAuditId").value(auditId))
                .andExpect(jsonPath("$[1].lineCount").value(5))
                .andExpect(jsonPath("$[1].total").value(69700));
        mvc.perform(get("/api/tenancies/" + tenancyId + "/bills").header(HttpHeaders.AUTHORIZATION, bearer(newLoggedInUser())))
                .andExpect(status().isNotFound());
    }

    @Test
    void badLinesAre400() throws Exception {
        TokenResponse user = newLoggedInUser();
        String tenancyId = createTenancy(user, "KA");

        postJson(user, "/api/tenancies/" + tenancyId + "/bills", Map.of("lines", List.of()))
                .andExpect(status().isBadRequest());
        postJson(user, "/api/tenancies/" + tenancyId + "/bills",
                Map.of("lines", List.of(line("Painting", "PAINTING", -5))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("lines[0].amount")));
        postJson(user, "/api/tenancies/" + tenancyId + "/bills",
                Map.of("lines", List.of(line("Painting", "MAGIC", 100))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("must be one of")));
    }
}
