package com.sankalp.fairexit.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sankalp.fairexit.AbstractIntegrationTest;
import com.sankalp.fairexit.dto.TokenResponse;
import com.sankalp.fairexit.service.vault.TestFiles;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class EvidenceLinkIntegrationTest extends AbstractIntegrationTest {

    private record Setup(TokenResponse user, String tenancyId, String billId, String lineId) {
    }

    private Setup bill() throws Exception {
        TokenResponse user = newLoggedInUser();
        String tenancyId = createTenancy(user);
        String bill = mvc.perform(post("/api/tenancies/" + tenancyId + "/bills")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("lines", List.of(
                                Map.of("item", "Broken tap", "category", "REPAIR", "amount", 1500))))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Setup(user, tenancyId, JsonPath.read(bill, "$.id"), JsonPath.read(bill, "$.lines[0].id"));
    }

    private String photo(Setup s, String phase) throws Exception {
        return upload(s.user(), s.tenancyId(), Map.of("type", "PHOTO", "phase", phase, "fileName", "p.png",
                "contentType", "image/png"), TestFiles.png());
    }

    private ResultActions link(Setup s, TokenResponse as, String documentId) throws Exception {
        return mvc.perform(put("/api/bills/" + s.billId() + "/lines/" + s.lineId() + "/evidence")
                .header(HttpHeaders.AUTHORIZATION, bearer(as))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("links", List.of(Map.of("documentId", documentId, "observation", "NO_DAMAGE"))))));
    }

    @Test
    void linksReplaceTheLinesPreviousLinks() throws Exception {
        Setup s = bill();
        link(s, s.user(), photo(s, "MOVE_IN")).andExpect(status().isOk());
        link(s, s.user(), photo(s, "MOVE_OUT")).andExpect(status().isOk());

        mvc.perform(get("/api/bills/" + s.billId() + "/lines/" + s.lineId() + "/evidence")
                        .header(HttpHeaders.AUTHORIZATION, bearer(s.user())))
                .andExpect(jsonPath("$.links", hasSize(1)))
                .andExpect(jsonPath("$.links[0].phase").value("MOVE_OUT"));
    }

    @Test
    void onlyMoveInOrMoveOutPhotosCanBeEvidence() throws Exception {
        Setup s = bill();
        String receipt = upload(s.user(), s.tenancyId(), Map.of("type", "RECEIPT", "fileName", "r.pdf",
                "contentType", "application/pdf"), TestFiles.pdf(List.of("Deposit paid")));

        link(s, s.user(), receipt).andExpect(status().isUnprocessableEntity());
        link(s, s.user(), photo(s, "OTHER")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void aPhotoFromAnotherTenancyCannotBeLinked() throws Exception {
        Setup s = bill();
        String otherTenancy = createTenancy(s.user());
        String foreign = upload(s.user(), otherTenancy, Map.of("type", "PHOTO", "phase", "MOVE_IN",
                "fileName", "p.png", "contentType", "image/png"), TestFiles.png());

        link(s, s.user(), foreign).andExpect(status().isNotFound());
    }

    @Test
    void evidenceIsFrozenOnceTheBillIsAudited() throws Exception {
        Setup s = bill();
        String photo = photo(s, "MOVE_IN");
        mvc.perform(post("/api/bills/" + s.billId() + "/audit").header(HttpHeaders.AUTHORIZATION, bearer(s.user())))
                .andExpect(status().isCreated());

        link(s, s.user(), photo).andExpect(status().isConflict());
    }

    @Test
    void anotherUserCannotLinkEvidence() throws Exception {
        Setup s = bill();
        String photo = photo(s, "MOVE_IN");

        link(s, newLoggedInUser(), photo).andExpect(status().isNotFound());
    }
}
