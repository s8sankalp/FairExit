package com.sankalp.fairexit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sankalp.fairexit.AbstractIntegrationTest;
import com.sankalp.fairexit.dto.TokenResponse;
import com.sankalp.fairexit.service.jobs.JobWorker;
import com.sankalp.fairexit.service.llm.LlmAttachment;
import com.sankalp.fairexit.service.llm.LlmClient;
import com.sankalp.fairexit.service.vault.TestFiles;
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
 * Agreements and file bills end to end. The LLM is a mock; MinIO and PostgreSQL are real.
 * The last test runs the research report's worked example through the API, now that the
 * agreement and the electricity bill are on file.
 */
class AgreementIntegrationTest extends AbstractIntegrationTest {

    private static final String PAGE_1 = "LEAVE AND LICENCE AGREEMENT. This agreement is made at Bengaluru. "
            + "The Licensee shall pay a security deposit of Rs. 1,50,000 which shall be refunded at the end.";
    private static final String PAGE_2 = "The Licensee shall bear the cost of normal cleaning at the time of "
            + "vacating the premises. Either party may end this agreement with one month notice.";

    /** One real clause, one invented: its quote is not in the agreement. */
    private static final String CLAUSE_ANSWER = """
            {"pages":[],"clauses":[
              {"type":"CLEANING","page":2,"quote":"The Licensee shall bear the cost of normal cleaning at the time of vacating the premises.","tenantPays":true},
              {"type":"PAINTING","page":2,"quote":"The Licensee shall repaint the entire flat before vacating.","tenantPays":true},
              {"type":"DEPOSIT","page":1,"quote":"The Licensee shall pay a security deposit of Rs. 1,50,000","value":150000}]}
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

    private String uploadAgreement(TokenResponse user, String tenancyId) throws Exception {
        return upload(user, tenancyId, Map.of("type", "AGREEMENT", "fileName", "agreement.pdf",
                "contentType", "application/pdf"), TestFiles.pdf(List.of(PAGE_1, PAGE_2)));
    }

    private ResultActions startAgreement(TokenResponse user, String tenancyId, String documentId) throws Exception {
        return mvc.perform(post("/api/tenancies/" + tenancyId + "/agreement")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("documentId", documentId))));
    }

    private ResultActions agreement(TokenResponse user, String tenancyId) throws Exception {
        return mvc.perform(get("/api/tenancies/" + tenancyId + "/agreement").header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions postJson(TokenResponse user, String path, Object body) throws Exception {
        return mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    @Test
    void clausesAreKeptOnlyWhenTheirQuoteIsInTheAgreement() throws Exception {
        when(llm.generateJson(anyString(), anyString())).thenReturn(CLAUSE_ANSWER);
        TokenResponse user = newLoggedInUser();
        String tenancyId = createTenancy(user);
        String documentId = uploadAgreement(user, tenancyId);

        startAgreement(user, tenancyId, documentId)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("EXTRACTING"));
        worker.runOnce();

        agreement(user, tenancyId)
                .andExpect(jsonPath("$.status").value("EXTRACTED"))
                .andExpect(jsonPath("$.droppedClauses").value(1)) // the invented painting clause
                .andExpect(jsonPath("$.clauses", hasSize(2)))
                .andExpect(jsonPath("$.clauses[*].type", not(org.hamcrest.Matchers.hasItem("PAINTING"))))
                .andExpect(jsonPath("$.clauses[0].checkedAgainst").value("TEXT_LAYER"));

        // A PDF with a text layer is sent as text, page by page, never as a file
        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(llm).generateJson(anyString(), sent.capture());
        assertThat(sent.getValue()).contains("--- Page 1 ---").contains("--- Page 2 ---").contains("normal cleaning");
    }

    @Test
    void aPhotoOfABillIsReadAsAFile() throws Exception {
        when(llm.generateJson(anyString(), anyString(), anyList())).thenReturn(
                "{\"lines\":[{\"item\":\"Painting\",\"category\":\"PAINTING\",\"amount\":55000}],\"statedTotal\":55000}");
        TokenResponse user = newLoggedInUser();
        String tenancyId = createTenancy(user);
        String photo = upload(user, tenancyId, Map.of("type", "BILL", "fileName", "bill.png",
                "contentType", "image/png"), TestFiles.png());

        String billId = JsonPath.read(postJson(user, "/api/tenancies/" + tenancyId + "/bills", Map.of("documentId", photo))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.source").value("FILE"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        worker.runOnce();

        mvc.perform(get("/api/bills/" + billId).header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$.status").value("EXTRACTED"))
                .andExpect(jsonPath("$.lines[0].amount").value(55000))
                .andExpect(jsonPath("$.totalMismatch").value(false));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LlmAttachment>> files = ArgumentCaptor.forClass(List.class);
        verify(llm).generateJson(anyString(), anyString(), files.capture());
        assertThat(files.getValue()).singleElement().satisfies(f -> {
            assertThat(f.mimeType()).isEqualTo("image/png");
            assertThat(f.data()).isEqualTo(TestFiles.png());
        });
    }

    @Test
    void aBillMustBeABillDocumentOfTheSameTenancy() throws Exception {
        TokenResponse user = newLoggedInUser();
        String tenancyId = createTenancy(user);
        String agreementDoc = uploadAgreement(user, tenancyId);

        postJson(user, "/api/tenancies/" + tenancyId + "/bills", Map.of("documentId", agreementDoc))
                .andExpect(status().isUnprocessableEntity());
        String otherTenancy = createTenancy(user);
        postJson(user, "/api/tenancies/" + otherTenancy + "/bills", Map.of("documentId", agreementDoc))
                .andExpect(status().isNotFound());
    }

    /**
     * The research report's worked example, end to end through the API, with everything on
     * file: agreement, electricity bill, tap photos linked to the tap line, and real prices.
     * The answer is the report's: ₹65,000 questionable, agree to ₹4,700, refund ₹1,45,300.
     */
    @Test
    void theWorkedExampleGivesTheReportsAnswer() throws Exception {
        when(llm.generateJson(anyString(), anyString())).thenReturn(CLAUSE_ANSWER);
        TokenResponse user = newLoggedInUser();
        String tenancyId = createTenancy(user);

        // On file: agreement, electricity bill, move in and move out photos of the tap
        startAgreement(user, tenancyId, uploadAgreement(user, tenancyId)).andExpect(status().isAccepted());
        worker.runOnce();
        upload(user, tenancyId, Map.of("type", "UTILITY_BILL", "fileName", "bescom.pdf",
                "contentType", "application/pdf", "amount", 3200),
                TestFiles.pdf(List.of("BESCOM electricity bill. Amount due Rs 3200.")));
        String tapAtMoveIn = upload(user, tenancyId, Map.of("type", "PHOTO", "phase", "MOVE_IN",
                "fileName", "tap-in.png", "contentType", "image/png", "room", "Bathroom", "item", "Tap"), TestFiles.png());
        String tapAtMoveOut = upload(user, tenancyId, Map.of("type", "PHOTO", "phase", "MOVE_OUT",
                "fileName", "tap-out.png", "contentType", "image/png", "room", "Bathroom", "item", "Tap"), TestFiles.png());

        // Usual prices for a 2BHK in Bengaluru: cleaning p90 ₹5,475 (12 quotes), repair p90 ₹2,060 (15 quotes)
        importPrices(priceRows("CLEANING", "Bengaluru", "TWO_BHK", 12, 3000, 250)
                + priceRows("REPAIR", "Bengaluru", "TWO_BHK", 15, 800, 100)
                        .replace("category,city,flat_size,amount\n", ""));

        String bill = postJson(user, "/api/tenancies/" + tenancyId + "/bills", Map.of("lines", List.of(
                Map.of("item", "Full flat repainting", "category", "PAINTING", "amount", 55000),
                Map.of("item", "Lift usage", "category", "SOCIETY_CHARGE", "amount", 2000),
                Map.of("item", "Deep cleaning", "category", "CLEANING", "amount", 8000),
                Map.of("item", "Broken bathroom tap", "category", "REPAIR", "amount", 1500, "room", "Bathroom"),
                Map.of("item", "Pending electricity bill", "category", "UTILITY", "amount", 3200))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String billId = JsonPath.read(bill, "$.id");
        String tapLineId = JsonPath.read(bill, "$.lines[3].id");

        // The tenant links the two photos to the tap line
        mvc.perform(put("/api/bills/" + billId + "/lines/" + tapLineId + "/evidence")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("links", List.of(
                                Map.of("documentId", tapAtMoveIn, "observation", "NO_DAMAGE"),
                                Map.of("documentId", tapAtMoveOut, "observation", "DAMAGE_PRESENT"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.links", hasSize(2)))
                .andExpect(jsonPath("$.links[0].phase").value("MOVE_IN"));

        String audit = postJson(user, "/api/bills/" + billId + "/audit", Map.of())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verdicts[0].verdict").value("QUESTIONABLE"))     // wear and tear
                .andExpect(jsonPath("$.verdicts[1].verdict").value("NOT_IN_AGREEMENT")) // no lift clause
                .andExpect(jsonPath("$.verdicts[2].verdict").value("QUESTIONABLE"))     // above p90
                .andExpect(jsonPath("$.verdicts[2].ruleIds[0]").value("R5@1"))
                .andExpect(jsonPath("$.verdicts[3].verdict").value("FAIR"))             // photos + fair price
                .andExpect(jsonPath("$.verdicts[3].evidenceIds", hasSize(2)))
                .andExpect(jsonPath("$.verdicts[4].verdict").value("FAIR"))             // matches the bill
                .andExpect(jsonPath("$.totals.claimed").value(69700))
                .andExpect(jsonPath("$.totals.questionable").value(65000))
                .andExpect(jsonPath("$.totals.fair").value(4700))
                .andExpect(jsonPath("$.totals.needsInfo").value(0))
                .andExpect(jsonPath("$.totals.suggestedRefund").value(145300))
                .andReturn().getResponse().getContentAsString();

        mvc.perform(get("/api/audits/" + JsonPath.read(audit, "$.id") + "/replies")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$.polite").value(containsString("I agree to these charges, ₹4,700 in total:")))
                .andExpect(jsonPath("$.polite").value(containsString("I request a refund of ₹1,45,300")));
    }

    @Test
    void anotherUserCannotReadOrStartTheAgreement() throws Exception {
        TokenResponse alice = newLoggedInUser();
        String tenancyId = createTenancy(alice);
        String documentId = uploadAgreement(alice, tenancyId);
        TokenResponse bob = newLoggedInUser();

        startAgreement(bob, tenancyId, documentId).andExpect(status().isNotFound());
        agreement(bob, tenancyId).andExpect(status().isNotFound());
    }

    @Test
    void anUnreadableAgreementFailsWithAReason() throws Exception {
        when(llm.generateJson(anyString(), anyString()))
                .thenThrow(new com.sankalp.fairexit.service.llm.LlmException(
                        com.sankalp.fairexit.service.llm.LlmException.Kind.REJECTED, "bad request"));
        TokenResponse user = newLoggedInUser();
        String tenancyId = createTenancy(user);
        startAgreement(user, tenancyId, uploadAgreement(user, tenancyId));
        worker.runOnce();

        agreement(user, tenancyId)
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error").value(containsString("could not read this agreement")));
    }
}
