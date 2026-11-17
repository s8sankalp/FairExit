package com.sankalp.fairexit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sankalp.fairexit.AbstractIntegrationTest;
import com.sankalp.fairexit.dto.TokenResponse;
import com.sankalp.fairexit.model.Document;
import com.sankalp.fairexit.repository.DocumentRepository;
import com.sankalp.fairexit.service.Hashing;
import com.sankalp.fairexit.service.storage.ObjectStorage;
import com.sankalp.fairexit.service.vault.TestFiles;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/** The vault against real PostgreSQL and real MinIO, with real presigned URLs. */
class VaultIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ObjectStorage storage;
    @Autowired
    private DocumentRepository documents;
    @Autowired
    private JdbcTemplate jdbc;

    private static Map<String, Object> photo(String room) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", "PHOTO");
        m.put("phase", "MOVE_IN");
        m.put("fileName", "bedroom.png");
        m.put("contentType", "image/png");
        m.put("room", room);
        m.put("item", "Wall");
        return m;
    }

    private ResultActions verify(TokenResponse user, String tenancyId) throws Exception {
        return mvc.perform(get("/api/tenancies/" + tenancyId + "/verify").header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private Document stored(String documentId) {
        return documents.findById(UUID.fromString(documentId)).orElseThrow();
    }

    @Test
    void anUploadIsHashedByTheServerAndChained() throws Exception {
        TokenResponse user = newLoggedInUser();
        String tenancyId = createTenancy(user);
        byte[] png = TestFiles.png();

        String first = upload(user, tenancyId, photo("Bedroom 1"), png);
        String second = upload(user, tenancyId, photo("Kitchen"), png);

        mvc.perform(get("/api/tenancies/" + tenancyId + "/documents").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(first))
                .andExpect(jsonPath("$[0].status").value("STORED"))
                .andExpect(jsonPath("$[0].sha256").value(Hashing.sha256Hex(png)))
                .andExpect(jsonPath("$[0].chainSeq").value(1))
                .andExpect(jsonPath("$[0].prevHash").value("0".repeat(64)))
                .andExpect(jsonPath("$[1].chainSeq").value(2));
        // The second record points at the first one's hash
        assertThat(stored(second).getPrevHash()).isEqualTo(stored(first).getRecordHash());
        // The file lives under a vault key; the staging copy is gone
        assertThat(stored(first).getStorageKey()).startsWith("vault/");
        assertThat(storage.size("staging/" + tenancyId + "/" + first)).isEmpty();

        verify(user, tenancyId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.documentsChecked").value(2))
                .andExpect(jsonPath("$.breaks", hasSize(0)));
    }

    /** Module 6 "done when": changing a stored file makes verify report a break. */
    @Test
    void changingAStoredFileMakesVerifyReportABreak() throws Exception {
        TokenResponse user = newLoggedInUser();
        String tenancyId = createTenancy(user);
        upload(user, tenancyId, photo("Bedroom 1"), TestFiles.png());
        String tampered = upload(user, tenancyId, photo("Bathroom"), TestFiles.png());
        upload(user, tenancyId, photo("Kitchen"), TestFiles.png());
        verify(user, tenancyId).andExpect(jsonPath("$.valid").value(true));

        // Someone with storage access swaps the bathroom photo for another file
        storage.write(stored(tampered).getStorageKey(), "a different picture".getBytes(StandardCharsets.UTF_8),
                "image/png");

        verify(user, tenancyId)
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.breaks", hasSize(1)))
                .andExpect(jsonPath("$.breaks[0].documentId").value(tampered))
                .andExpect(jsonPath("$.breaks[0].chainSeq").value(2))
                .andExpect(jsonPath("$.breaks[0].problem").value("FILE_CHANGED"));
    }

    @Test
    void editingARowInTheDatabaseIsAlsoFound() throws Exception {
        TokenResponse user = newLoggedInUser();
        String tenancyId = createTenancy(user);
        String id = upload(user, tenancyId, photo("Bedroom 1"), TestFiles.png());

        jdbc.update("UPDATE documents SET room = 'Kitchen' WHERE id = ?", UUID.fromString(id));

        verify(user, tenancyId)
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.breaks[0].problem").value("RECORD_CHANGED"));
    }

    @Test
    void deletingARowIsFound() throws Exception {
        TokenResponse user = newLoggedInUser();
        String tenancyId = createTenancy(user);
        String first = upload(user, tenancyId, photo("Bedroom 1"), TestFiles.png());
        upload(user, tenancyId, photo("Kitchen"), TestFiles.png());

        jdbc.update("DELETE FROM documents WHERE id = ?", UUID.fromString(first));

        verify(user, tenancyId)
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.breaks[*].problem", org.hamcrest.Matchers.hasItems("CHAIN_BROKEN", "HEAD_MISMATCH")));
    }

    @Test
    void aFileThatIsNotWhatItClaimsIsRejected() throws Exception {
        TokenResponse user = newLoggedInUser();
        String tenancyId = createTenancy(user);
        byte[] fake = "MZ this is really a program".getBytes(StandardCharsets.UTF_8);
        Map<String, Object> request = photo("Bedroom 1");
        request.put("sizeBytes", fake.length);
        String ticket = requestUpload(user, tenancyId, request);
        assertThat(putToPresignedUrl(ticket, fake)).isEqualTo(200);
        String documentId = JsonPath.read(ticket, "$.documentId");

        mvc.perform(post("/api/tenancies/" + tenancyId + "/uploads/" + documentId + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("not a real image/png")));
        // It stays rejected and never joins the chain
        assertThat(stored(documentId).getStatus().name()).isEqualTo("REJECTED");
        verify(user, tenancyId).andExpect(jsonPath("$.documentsChecked").value(0));
    }

    @Test
    void thePresignedUrlOnlyAcceptsTheDeclaredSize() throws Exception {
        TokenResponse user = newLoggedInUser();
        String tenancyId = createTenancy(user);
        Map<String, Object> request = photo("Bedroom 1");
        request.put("sizeBytes", 10);
        String ticket = requestUpload(user, tenancyId, request);

        assertThat(putToPresignedUrl(ticket, TestFiles.png())).isNotEqualTo(200); // 67 bytes, not 10
    }

    @Test
    void completingTwiceIsSafe() throws Exception {
        TokenResponse user = newLoggedInUser();
        String tenancyId = createTenancy(user);
        String id = upload(user, tenancyId, photo("Bedroom 1"), TestFiles.png());

        mvc.perform(post("/api/tenancies/" + tenancyId + "/uploads/" + id + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chainSeq").value(1));
        verify(user, tenancyId).andExpect(jsonPath("$.documentsChecked").value(1));
    }

    @Test
    void completingBeforeUploadingIs409() throws Exception {
        TokenResponse user = newLoggedInUser();
        String tenancyId = createTenancy(user);
        Map<String, Object> request = photo("Bedroom 1");
        request.put("sizeBytes", 70);
        String documentId = JsonPath.read(requestUpload(user, tenancyId, request), "$.documentId");

        mvc.perform(post("/api/tenancies/" + tenancyId + "/uploads/" + documentId + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isConflict());
    }

    @Test
    void uploadRulesAreEnforced() throws Exception {
        TokenResponse user = newLoggedInUser();
        String tenancyId = createTenancy(user);
        Map<String, Object> gif = photo("Bedroom 1");
        gif.put("contentType", "image/gif");
        gif.put("sizeBytes", 100);
        Map<String, Object> huge = photo("Bedroom 1");
        huge.put("sizeBytes", 11 * 1024 * 1024);
        Map<String, Object> billWithoutAmount = photo("Kitchen");
        billWithoutAmount.put("type", "UTILITY_BILL");
        billWithoutAmount.put("sizeBytes", 100);

        for (Map<String, Object> bad : List.of(gif, huge, billWithoutAmount)) {
            mvc.perform(post("/api/tenancies/" + tenancyId + "/uploads")
                            .header(HttpHeaders.AUTHORIZATION, bearer(user))
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(json(bad)))
                    .andExpect(status().is4xxClientError());
        }
    }

    @Test
    void downloadLinksAreShortLivedAndOnlyForTheOwner() throws Exception {
        TokenResponse alice = newLoggedInUser();
        String tenancyId = createTenancy(alice);
        String id = upload(alice, tenancyId, photo("Bedroom 1"), TestFiles.png());

        mvc.perform(get("/api/documents/" + id + "/download").header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(startsWith("http")))
                .andExpect(jsonPath("$.url").value(containsString("X-Amz-Expires=600")));

        TokenResponse bob = newLoggedInUser();
        mvc.perform(get("/api/documents/" + id + "/download").header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isNotFound());
        verify(bob, tenancyId).andExpect(status().isNotFound());
        mvc.perform(post("/api/tenancies/" + tenancyId + "/uploads")
                        .header(HttpHeaders.AUTHORIZATION, bearer(bob))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "PHOTO", "fileName", "x.png", "contentType", "image/png",
                                "sizeBytes", 70))))
                .andExpect(status().isNotFound());
    }
}
