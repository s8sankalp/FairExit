package com.sankalp.fairexit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sankalp.fairexit.AbstractIntegrationTest;
import com.sankalp.fairexit.dto.TokenResponse;
import com.sankalp.fairexit.service.jobs.JobWorker;
import com.sankalp.fairexit.service.llm.LlmClient;
import com.sankalp.fairexit.service.llm.LlmException;
import com.sankalp.fairexit.service.llm.LlmException.Kind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Paste a bill, the job reads it, the tenant confirms, the audit runs.
 * Gemini is replaced by a mock, so these tests never use the network or a real key.
 */
class ExtractionIntegrationTest extends AbstractIntegrationTest {

    // The registered test user is called "Test User", so that name must be masked
    private static final String MESSAGE = "Dear Test User, deductions from your deposit: "
            + "Painting 55,000. Lift usage 2000. Total 60000. Call 9876543210 or mail owner@example.com";

    private static final String ANSWER = """
            {"lines":[
              {"item":"Painting","category":"PAINTING","amount":55000,"room":null},
              {"item":"Lift usage","category":"SOCIETY_CHARGE","amount":2000,"room":null}],
             "statedTotal":60000}
            """;

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

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               TokenResponse user, Object body) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private String pasteBill(TokenResponse user, String tenancyId, String text) throws Exception {
        return JsonPath.read(send(post("/api/tenancies/" + tenancyId + "/bills"), user, Map.of("text", text))
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/bills/")))
                .andExpect(jsonPath("$.status").value("EXTRACTING"))
                .andExpect(jsonPath("$.source").value("TEXT"))
                .andExpect(jsonPath("$.lines", hasSize(0)))
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions getBill(TokenResponse user, String billId) throws Exception {
        return mvc.perform(get("/api/bills/" + billId).header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions audit(TokenResponse user, String billId) throws Exception {
        return mvc.perform(post("/api/bills/" + billId + "/audit").header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private static Map<String, Object> line(String item, String category, int amount) {
        return Map.of("item", item, "category", category, "amount", amount);
    }

    /** Module 4 "done when", first half: a pasted message becomes lines. */
    @Test
    void aPastedMessageBecomesLinesThenTheTenantConfirmsAndAudits() throws Exception {
        when(llm.generateJson(anyString(), anyString())).thenReturn(ANSWER);
        TokenResponse user = newLoggedInUser();
        String billId = pasteBill(user, createTenancy(user), MESSAGE);

        assertThat(worker.runOnce()).isEqualTo(1);

        // The lines are there, with a warning: 55,000 + 2,000 is not the stated 60,000
        getBill(user, billId)
                .andExpect(jsonPath("$.status").value("EXTRACTED"))
                .andExpect(jsonPath("$.lines", hasSize(2)))
                .andExpect(jsonPath("$.lines[0].item").value("Painting"))
                .andExpect(jsonPath("$.lines[1].category").value("SOCIETY_CHARGE"))
                .andExpect(jsonPath("$.total").value(57000))
                .andExpect(jsonPath("$.statedTotal").value(60000))
                .andExpect(jsonPath("$.totalMismatch").value(true));

        // What went to the LLM had no phone, email or name in it, and the amounts were intact
        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(llm).generateJson(anyString(), sent.capture());
        assertThat(sent.getValue())
                .doesNotContain("9876543210").doesNotContain("owner@example.com").doesNotContain("Test User")
                .contains("[PHONE]").contains("[EMAIL]").contains("[NAME]")
                .contains("Painting 55,000").contains("Total 60000");

        // No audit before the tenant confirms
        audit(user, billId).andExpect(status().isConflict());

        // The tenant keeps the painting line and corrects the lift amount
        send(put("/api/bills/" + billId + "/lines"), user, Map.of("lines", List.of(
                line("Painting", "PAINTING", 55000), line("Lift usage", "SOCIETY_CHARGE", 2500))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.lines[0].editedByUser").value(false))
                .andExpect(jsonPath("$.lines[1].editedByUser").value(true))
                .andExpect(jsonPath("$.lines[1].amount").value(2500));

        audit(user, billId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totals.claimed").value(57500));
    }

    @Test
    void theSameMessageIsSentToTheLlmOnlyOnce() throws Exception {
        when(llm.generateJson(anyString(), anyString())).thenReturn(ANSWER);
        TokenResponse user = newLoggedInUser();
        String tenancyId = createTenancy(user);

        String first = pasteBill(user, tenancyId, MESSAGE);
        worker.runOnce();
        String second = pasteBill(user, tenancyId, MESSAGE);
        worker.runOnce();

        getBill(user, first).andExpect(jsonPath("$.status").value("EXTRACTED"));
        getBill(user, second).andExpect(jsonPath("$.status").value("EXTRACTED"))
                .andExpect(jsonPath("$.lines", hasSize(2)));
        verify(llm, times(1)).generateJson(anyString(), anyString()); // the second came from the cache
    }

    @Test
    void aBadAnswerIsRetriedAndTheNextGoodOneIsUsed() throws Exception {
        when(llm.generateJson(anyString(), anyString()))
                .thenReturn("Sure! The painting costs 55000.")
                .thenReturn(ANSWER);
        TokenResponse user = newLoggedInUser();
        String billId = pasteBill(user, createTenancy(user), MESSAGE);

        worker.runOnce();
        getBill(user, billId).andExpect(jsonPath("$.status").value("EXTRACTING")); // still waiting
        worker.runOnce();

        getBill(user, billId).andExpect(jsonPath("$.status").value("EXTRACTED"));
    }

    /** Module 4 "done when", second half: manual entry still works when the LLM is down. */
    @Test
    void whenTheLlmIsDownItGivesUpAfterThreeTriesAndManualEntryWorks() throws Exception {
        when(llm.generateJson(anyString(), anyString()))
                .thenThrow(new LlmException(Kind.UNAVAILABLE, "timeout"));
        TokenResponse user = newLoggedInUser();
        String billId = pasteBill(user, createTenancy(user), MESSAGE);

        worker.runOnce();
        worker.runOnce();
        worker.runOnce();
        assertThat(worker.runOnce()).isZero(); // nothing left to try

        verify(llm, times(3)).generateJson(anyString(), anyString());
        getBill(user, billId)
                .andExpect(jsonPath("$.status").value("EXTRACTION_FAILED"))
                .andExpect(jsonPath("$.extractionError").value(containsString("enter the lines by hand")));

        send(put("/api/bills/" + billId + "/lines"), user,
                Map.of("lines", List.of(line("Painting", "PAINTING", 55000))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.lines[0].editedByUser").value(false)); // nothing was suggested
        audit(user, billId).andExpect(status().isCreated());
    }

    @Test
    void theTenantCanTypeLinesWithoutWaitingAndTheJobThenDoesNothing() throws Exception {
        TokenResponse user = newLoggedInUser();
        String billId = pasteBill(user, createTenancy(user), MESSAGE);

        send(put("/api/bills/" + billId + "/lines"), user,
                Map.of("lines", List.of(line("Painting", "PAINTING", 40000))))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        worker.runOnce();

        verify(llm, never()).generateJson(anyString(), anyString());
        getBill(user, billId)
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.lines", hasSize(1)))
                .andExpect(jsonPath("$.lines[0].amount").value(40000));
    }

    @Test
    void aMessageWithNoChargesFailsWithAClearReasonAndNoRetry() throws Exception {
        when(llm.generateJson(anyString(), anyString())).thenReturn("{\"lines\":[],\"statedTotal\":null}");
        TokenResponse user = newLoggedInUser();
        String billId = pasteBill(user, createTenancy(user), "Hello, when can I collect the keys?");

        worker.runOnce();
        worker.runOnce();

        verify(llm, times(1)).generateJson(anyString(), anyString());
        getBill(user, billId)
                .andExpect(jsonPath("$.status").value("EXTRACTION_FAILED"))
                .andExpect(jsonPath("$.extractionError").value(containsString("No deduction lines")));
    }

    @Test
    void linesAreFrozenOnceTheBillIsAudited() throws Exception {
        TokenResponse user = newLoggedInUser();
        String tenancyId = createTenancy(user);
        String billId = JsonPath.read(send(post("/api/tenancies/" + tenancyId + "/bills"), user,
                Map.of("lines", List.of(line("Painting", "PAINTING", 55000))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        audit(user, billId).andExpect(status().isCreated());

        send(put("/api/bills/" + billId + "/lines"), user,
                Map.of("lines", List.of(line("Painting", "PAINTING", 1))))
                .andExpect(status().isConflict());
    }

    @Test
    void anotherUserCannotEditTheLines() throws Exception {
        TokenResponse alice = newLoggedInUser();
        TokenResponse bob = newLoggedInUser();
        String billId = pasteBill(alice, createTenancy(alice), MESSAGE);

        send(put("/api/bills/" + billId + "/lines"), bob,
                Map.of("lines", List.of(line("Painting", "PAINTING", 1))))
                .andExpect(status().isNotFound());
    }

    @Test
    void aBillNeedsEitherLinesOrTextNotBothOrNeither() throws Exception {
        TokenResponse user = newLoggedInUser();
        String path = "/api/tenancies/" + createTenancy(user) + "/bills";

        send(post(path), user, Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("send exactly one of lines, text or documentId")));
        send(post(path), user, Map.of("text", "Painting 5000",
                "lines", List.of(line("Painting", "PAINTING", 5000))))
                .andExpect(status().isBadRequest());
        send(post(path), user, Map.of("text", "x".repeat(5001)))
                .andExpect(status().isBadRequest());
    }
}
