package com.sankalp.fairexit.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sankalp.fairexit.AbstractIntegrationTest;
import com.sankalp.fairexit.dto.TokenResponse;
import com.sankalp.fairexit.service.vault.TestFiles;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class OutcomeIntegrationTest extends AbstractIntegrationTest {

    private ResultActions record(TokenResponse user, String tenancyId, Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/tenancies/" + tenancyId + "/outcome")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    @Test
    void anOutcomeIsRecordedAndCanBeCorrected() throws Exception {
        TokenResponse user = newLoggedInUser();
        String tenancyId = createTenancy(user);

        record(user, tenancyId, Map.of("amountReturned", 120000, "daysTaken", 20))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.percentReturned").value(80.0))
                .andExpect(jsonPath("$.verified").value(false));
        record(user, tenancyId, Map.of("amountReturned", 145300, "daysTaken", 25))
                .andExpect(status().isOk());

        mvc.perform(get("/api/tenancies/" + tenancyId + "/outcome").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$.amountReturned").value(145300))
                .andExpect(jsonPath("$.daysTaken").value(25));
    }

    @Test
    void aStoredReceiptMakesTheOutcomeVerified() throws Exception {
        TokenResponse user = newLoggedInUser();
        String tenancyId = createTenancy(user);
        String receipt = upload(user, tenancyId, Map.of("type", "RECEIPT", "fileName", "credit.pdf",
                "contentType", "application/pdf"), TestFiles.pdf(List.of("Credited Rs 145300")));
        Map<String, Object> body = new HashMap<>(Map.of("amountReturned", 145300, "daysTaken", 12));
        body.put("receiptDocumentId", receipt);

        record(user, tenancyId, body).andExpect(jsonPath("$.verified").value(true));
    }

    @Test
    void impossibleOutcomesAreRejected() throws Exception {
        TokenResponse user = newLoggedInUser();
        String tenancyId = createTenancy(user);

        record(user, tenancyId, Map.of("amountReturned", 200000, "daysTaken", 10)) // more than the deposit
                .andExpect(status().isBadRequest());
        record(user, tenancyId, Map.of("amountReturned", -1, "daysTaken", 10)).andExpect(status().isBadRequest());
        record(user, tenancyId, Map.of("amountReturned", 1000, "daysTaken", 9999)).andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheTenantCanRecordIt() throws Exception {
        String tenancyId = createTenancy(newLoggedInUser());

        record(newLoggedInUser(), tenancyId, Map.of("amountReturned", 1000, "daysTaken", 10))
                .andExpect(status().isNotFound());
    }
}
