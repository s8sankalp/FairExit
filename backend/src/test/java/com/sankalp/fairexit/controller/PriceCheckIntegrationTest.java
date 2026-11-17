package com.sankalp.fairexit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sankalp.fairexit.AbstractIntegrationTest;
import com.sankalp.fairexit.dto.TokenResponse;
import com.sankalp.fairexit.service.RedisLock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/** Rule 5 with real data: CSV import, nightly rebuild, Redis cache, audit. */
class PriceCheckIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private RedisLock lock;

    @BeforeEach
    void noPrices() {
        jdbc.update("DELETE FROM price_points");
        jdbc.update("DELETE FROM price_stats");
    }

    private ResultActions auditOneLine(TokenResponse user, String tenancyId, String category, int amount)
            throws Exception {
        String billId = JsonPath.read(mvc.perform(post("/api/tenancies/" + tenancyId + "/bills")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("lines", List.of(Map.of("item", "Tap repair", "category", category,
                                "amount", amount))))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        return mvc.perform(post("/api/bills/" + billId + "/audit").header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    /** Module 7 "done when", part 1: above p90 with 10 or more points is flagged. */
    @Test
    void aboveP90WithTwelvePricesIsQuestionable() throws Exception {
        importPrices(priceRows("REPAIR", "Bengaluru", "TWO_BHK", 12, 500, 100)); // 500 ... 1600
        TokenResponse user = newLoggedInUser();

        auditOneLine(user, createTenancy(user), "REPAIR", 5000)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verdicts[0].verdict").value("QUESTIONABLE"))
                .andExpect(jsonPath("$.verdicts[0].ruleIds[0]").value("R5@1"))
                .andExpect(jsonPath("$.verdicts[0].reason").value(containsString("of 12 known prices")));
    }

    /** Module 7 "done when", part 2: fewer points gives NEEDS_INFO. */
    @Test
    void withOnlyNinePricesTheSameChargeNeedsInfo() throws Exception {
        importPrices(priceRows("REPAIR", "Bengaluru", "TWO_BHK", 9, 500, 100));
        TokenResponse user = newLoggedInUser();

        auditOneLine(user, createTenancy(user), "REPAIR", 5000)
                .andExpect(jsonPath("$.verdicts[0].verdict").value("NEEDS_INFO"))
                .andExpect(jsonPath("$.verdicts[0].ruleIds[0]").value("R7@1"));
    }

    @Test
    void anAmountInsideTheRangeIsNotFlagged() throws Exception {
        importPrices(priceRows("REPAIR", "Bengaluru", "TWO_BHK", 12, 500, 100));
        TokenResponse user = newLoggedInUser();

        auditOneLine(user, createTenancy(user), "REPAIR", 1200)
                .andExpect(jsonPath("$.verdicts[0].verdict").value("NEEDS_INFO")); // in range, but no photos
    }

    @Test
    void pricesFromAnotherCityOrFlatSizeAreNotUsed() throws Exception {
        importPrices(priceRows("REPAIR", "Kochi", "TWO_BHK", 12, 500, 100)
                + priceRows("REPAIR", "Bengaluru", "ONE_BHK", 12, 500, 100).replace("category,city,flat_size,amount\n", ""));
        TokenResponse user = newLoggedInUser();

        auditOneLine(user, createTenancy(user), "REPAIR", 5000)
                .andExpect(jsonPath("$.verdicts[0].verdict").value("NEEDS_INFO"));
    }

    @Test
    void statsAreServedFromTheRedisCacheAfterARebuild() throws Exception {
        importPrices(priceRows("CLEANING", "Bengaluru", "TWO_BHK", 10, 3000, 300));

        String cached = redis.opsForValue().get("price:stats:CLEANING:TWO_BHK:bengaluru");
        assertThat(cached).contains("\"n\":10").contains("\"p90\"");
    }

    @Test
    void onlyOneRebuildRunsAtATime() throws Exception {
        TokenResponse admin = admin();
        String token = lock.tryLock("lock:price-stats-refresh", Duration.ofMinutes(1)).orElseThrow();
        try {
            mvc.perform(post("/api/admin/price-stats/refresh").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                    .andExpect(jsonPath("$.ran").value(false)); // another instance holds the lock
        } finally {
            lock.unlock("lock:price-stats-refresh", token);
        }
        mvc.perform(post("/api/admin/price-stats/refresh").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(jsonPath("$.ran").value(true));
    }

    @Test
    void aLockCanOnlyBeReleasedByItsHolder() {
        String token = lock.tryLock("lock:test", Duration.ofMinutes(1)).orElseThrow();

        assertThat(lock.tryLock("lock:test", Duration.ofMinutes(1))).isEmpty();
        assertThat(lock.unlock("lock:test", "someone-else")).isFalse();
        assertThat(lock.unlock("lock:test", token)).isTrue();
        assertThat(lock.tryLock("lock:test", Duration.ofMinutes(1))).isPresent();
    }

    @Test
    void aBadCsvImportsNothing() throws Exception {
        mvc.perform(post("/api/admin/price-points")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin()))
                        .contentType("text/csv")
                        .content("category,city,flat_size,amount\nREPAIR,Bengaluru,TWO_BHK,500\nREPAIR,Bengaluru,TWO_BHK,x\n"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Line 3")));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM price_points", Integer.class)).isZero();
    }

    @Test
    void onlyAdminsCanImport() throws Exception {
        mvc.perform(post("/api/admin/price-points")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newLoggedInUser()))
                        .contentType("text/csv").content("category,city,flat_size,amount\n"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mvc.perform(post("/api/admin/price-stats/refresh")).andExpect(status().isUnauthorized());
    }
}
