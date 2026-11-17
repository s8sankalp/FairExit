package com.sankalp.fairexit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sankalp.fairexit.AbstractIntegrationTest;
import com.sankalp.fairexit.dto.TokenResponse;
import com.sankalp.fairexit.service.jobs.JobWorker;
import com.sankalp.fairexit.service.storage.ObjectStorage;
import com.sankalp.fairexit.service.vault.TestFiles;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/** PRD privacy: "user can delete their account and files". Everything must go. */
class AccountDeletionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectStorage storage;
    @Autowired
    private JobWorker worker;

    @BeforeEach
    void emptyQueue() {
        jdbc.update("DELETE FROM jobs");
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    @Test
    void deletingTheAccountRemovesTheDataTheFilesAndTheTokens() throws Exception {
        String email = uniqueEmail();
        register(email);
        TokenResponse user = login(email);
        UUID userId = UUID.fromString(JsonPath.read(mvc.perform(org.springframework.test.web.servlet.request
                .MockMvcRequestBuilders.get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andReturn().getResponse().getContentAsString(), "$.id"));

        // A full account: tenancy, a photo in the vault, a bill, an audit and an evidence pack
        String tenancyId = createTenancy(user);
        upload(user, tenancyId, Map.of("type", "PHOTO", "phase", "MOVE_IN", "fileName", "p.png",
                "contentType", "image/png"), TestFiles.png());
        String billId = JsonPath.read(mvc.perform(post("/api/tenancies/" + tenancyId + "/bills")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("lines", List.of(Map.of("item", "Painting", "category", "PAINTING",
                                "amount", 20000))))))
                .andReturn().getResponse().getContentAsString(), "$.id");
        String auditId = JsonPath.read(mvc.perform(post("/api/bills/" + billId + "/audit")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/audits/" + auditId + "/evidence-pack").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isCreated());
        List<String> keys = jdbc.queryForList("""
                SELECT storage_key FROM documents WHERE tenancy_id = ?
                UNION ALL SELECT storage_key FROM evidence_packs WHERE tenancy_id = ?""",
                String.class, UUID.fromString(tenancyId), UUID.fromString(tenancyId));
        assertThat(keys).hasSize(2).allSatisfy(k -> assertThat(storage.size(k)).isPresent());

        mvc.perform(delete("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", PASSWORD))))
                .andExpect(status().isNoContent());

        // The database rows are gone, through ON DELETE CASCADE
        assertThat(count("SELECT count(*) FROM users WHERE id = ?", userId)).isZero();
        assertThat(count("SELECT count(*) FROM tenancies WHERE user_id = ?", userId)).isZero();
        assertThat(count("SELECT count(*) FROM documents WHERE tenancy_id = ?", UUID.fromString(tenancyId))).isZero();
        assertThat(count("SELECT count(*) FROM audits WHERE id = ?", UUID.fromString(auditId))).isZero();
        assertThat(count("SELECT count(*) FROM evidence_packs WHERE tenancy_id = ?", UUID.fromString(tenancyId)))
                .isZero();

        // The files go with the background job
        assertThat(worker.runOnce()).isEqualTo(1);
        assertThat(keys).allSatisfy(k -> assertThat(storage.size(k)).isEmpty());

        // The refresh token no longer works, and the email can no longer log in
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", user.refreshToken()))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
        // and the same email can sign up again from scratch
        register(email);
    }

    @Test
    void theWrongPasswordDeletesNothing() throws Exception {
        TokenResponse user = newLoggedInUser();
        String tenancyId = createTenancy(user);

        mvc.perform(delete("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", "not-my-password"))))
                .andExpect(status().isForbidden());

        assertThat(count("SELECT count(*) FROM tenancies WHERE id = ?", UUID.fromString(tenancyId))).isEqualTo(1);
    }

    @Test
    void anAccountWithNoFilesQueuesNoJob() throws Exception {
        TokenResponse user = newLoggedInUser();
        createTenancy(user);

        mvc.perform(delete("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", PASSWORD))))
                .andExpect(status().isNoContent());

        assertThat(worker.runOnce()).isZero();
    }
}
