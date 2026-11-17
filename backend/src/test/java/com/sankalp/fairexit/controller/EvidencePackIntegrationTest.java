package com.sankalp.fairexit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sankalp.fairexit.AbstractIntegrationTest;
import com.sankalp.fairexit.dto.TokenResponse;
import com.sankalp.fairexit.service.Hashing;
import com.sankalp.fairexit.service.vault.PdfText;
import com.sankalp.fairexit.service.vault.TestFiles;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class EvidencePackIntegrationTest extends AbstractIntegrationTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private String auditWithAPhoto(TokenResponse user) throws Exception {
        String tenancyId = createTenancy(user);
        upload(user, tenancyId, Map.of("type", "PHOTO", "phase", "MOVE_IN", "fileName", "bedroom.png",
                "contentType", "image/png", "room", "Bedroom 1"), TestFiles.png());
        String billId = JsonPath.read(mvc.perform(post("/api/tenancies/" + tenancyId + "/bills")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("lines", List.of(
                                Map.of("item", "Full flat repainting", "category", "PAINTING", "amount", 55000),
                                Map.of("item", "Last month rent", "category", "RENT", "amount", 30000))))))
                .andReturn().getResponse().getContentAsString(), "$.id");
        return JsonPath.read(mvc.perform(post("/api/bills/" + billId + "/audit")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    void thePackIsBuiltStoredAndSharedWithASevenDayLink() throws Exception {
        TokenResponse user = newLoggedInUser();
        String auditId = auditWithAPhoto(user);

        String response = mvc.perform(post("/api/audits/" + auditId + "/evidence-pack")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.chainValid").value(true))
                .andExpect(jsonPath("$.url").value(containsString("X-Amz-Expires=604800"))) // 7 days
                .andReturn().getResponse().getContentAsString();

        // Anyone with the link (a lawyer, a mediator) can download it, without logging in
        HttpResponse<byte[]> pdf = HTTP.send(HttpRequest.newBuilder(URI.create(JsonPath.read(response, "$.url"))).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(pdf.statusCode()).isEqualTo(200);
        assertThat(Hashing.sha256Hex(pdf.body())).isEqualTo(JsonPath.read(response, "$.sha256"));

        String text = String.join(" ", PdfText.pages(pdf.body())).replaceAll("\\s+", " ");
        assertThat(text)
                .contains("FairExit evidence pack")
                .contains("Test User")
                .contains("Rs. 1,20,000")         // refund: 1,50,000 minus the fair rent 30,000
                .contains("Verified when this pack was made: all 1 documents")
                .contains(Hashing.sha256Hex(TestFiles.png()));
    }

    @Test
    void anotherUserCannotBuildAPack() throws Exception {
        String auditId = auditWithAPhoto(newLoggedInUser());

        mvc.perform(post("/api/audits/" + auditId + "/evidence-pack")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newLoggedInUser())))
                .andExpect(status().isNotFound());
    }
}
