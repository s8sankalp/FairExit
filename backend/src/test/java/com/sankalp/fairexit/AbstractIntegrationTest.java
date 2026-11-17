package com.sankalp.fairexit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sankalp.fairexit.dto.TokenResponse;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Base class for integration tests. Starts real PostgreSQL 16 and Redis 7
 * containers once and shares them across all subclasses (static fields).
 * {@code @ServiceConnection} wires their host and port into Spring for us.
 * MockMvc runs the full filter chain (logging, JWT, rate limit, idempotency).
 */
@SpringBootTest(properties = {
        "fairexit.jwt.secret=integration-test-secret-that-is-long-enough-123",
        // Tests call JobWorker.runOnce() themselves, so nothing runs behind their back
        "fairexit.jobs.worker-enabled=false",
        "fairexit.jobs.retry-backoff=0s",
        // Never use a real key in tests, even if GEMINI_API_KEY is set on this machine
        "fairexit.llm.api-key=",
        "fairexit.prices.nightly-refresh-enabled=false",
        "fairexit.reminders.worker-enabled=false",
        "fairexit.admin.emails=" + AbstractIntegrationTest.ADMIN_EMAIL
})
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    protected static final String PASSWORD = "password123";
    protected static final String ADMIN_EMAIL = "admin@fairexit.test";

    // Shared, not closed per call: HttpClient only became AutoCloseable in Java 21, we target 17
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    protected static final String MINIO_USER = "test-user";
    protected static final String MINIO_PASSWORD = "test-password";

    static final GenericContainer<?> MINIO = new GenericContainer<>("cgr.dev/chainguard/minio:latest")
            .withCreateContainerCmdModifier(cmd -> cmd.withUser("0:0"))
            .withCommand("server", "/data")
            .withEnv("MINIO_ROOT_USER", MINIO_USER)
            .withEnv("MINIO_ROOT_PASSWORD", MINIO_PASSWORD)
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    static {
        // Start all three in parallel, once for the whole run
        POSTGRES.start();
        REDIS.start();
        MINIO.start();
    }

    /** MinIO has no Spring Boot service connection, so its address is passed by hand. */
    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        String url = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
        registry.add("fairexit.storage.endpoint", () -> url);
        registry.add("fairexit.storage.public-endpoint", () -> url);
        registry.add("fairexit.storage.access-key", () -> MINIO_USER);
        registry.add("fairexit.storage.secret-key", () -> MINIO_PASSWORD);
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate baseJdbc;

    /**
     * Fresh Redis for every test (rate limit buckets, tokens, idempotency keys, caches),
     * and no price data, because prices change verdicts in every audit of the same city.
     */
    @BeforeEach
    void cleanSharedState() {
        try (RedisConnection connection = redis.getRequiredConnectionFactory().getConnection()) {
            connection.serverCommands().flushAll();
        }
        baseJdbc.update("DELETE FROM price_points");
        baseJdbc.update("DELETE FROM price_stats");
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    protected String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    protected void register(String email) throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD, "name", "Test User"))))
                .andExpect(status().isCreated());
    }

    protected TokenResponse login(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), TokenResponse.class);
    }

    /** Registers a new user and returns their tokens. */
    protected TokenResponse newLoggedInUser() throws Exception {
        String email = uniqueEmail();
        register(email);
        return login(email);
    }

    /** The admin account (on the fairexit.admin.emails list). Registered once, then reused. */
    protected TokenResponse admin() throws Exception {
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", ADMIN_EMAIL, "password", PASSWORD, "name", "Admin"))));
        return login(ADMIN_EMAIL);
    }

    /** Imports price rows as the admin and rebuilds the stats. */
    protected void importPrices(String csv) throws Exception {
        TokenResponse admin = admin();
        mvc.perform(post("/api/admin/price-points")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType("text/csv").content(csv))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/price-stats/refresh").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
    }

    /** n rows "category,city,flat_size,amount" with amounts from, from+step, ... */
    protected static String priceRows(String category, String city, String flatSize, int n, int from, int step) {
        StringBuilder csv = new StringBuilder("category,city,flat_size,amount\n");
        for (int i = 0; i < n; i++) {
            csv.append(category).append(',').append(city).append(',').append(flatSize).append(',')
                    .append(from + i * step).append('\n');
        }
        return csv.toString();
    }

    /** A Karnataka 2BHK, 24 months long, deposit 1,50,000 (the worked example's tenancy). */
    protected String createTenancy(TokenResponse user) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("city", "Bengaluru");
        body.put("state", "KA");
        body.put("flatSize", "TWO_BHK");
        body.put("rent", 30000);
        body.put("deposit", 150000);
        body.put("startDate", "2024-06-01");
        body.put("endDate", "2026-06-01");
        return JsonPath.read(mvc.perform(post("/api/tenancies")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    /** Asks for an upload URL and returns the response body (documentId, uploadUrl ...). */
    protected String requestUpload(TokenResponse user, String tenancyId, Map<String, Object> request)
            throws Exception {
        return mvc.perform(post("/api/tenancies/" + tenancyId + "/uploads")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    /** What a browser does with the ticket: PUT the bytes straight to MinIO. Returns the HTTP status. */
    protected int putToPresignedUrl(String ticket, byte[] bytes) throws Exception {
        String url = JsonPath.read(ticket, "$.uploadUrl");
        String contentType = JsonPath.read(ticket, "$.headers['Content-Type']");
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    /** Full upload: ticket, PUT, complete. Returns the stored document's id. */
    protected String upload(TokenResponse user, String tenancyId, Map<String, Object> meta, byte[] bytes)
            throws Exception {
        Map<String, Object> request = new HashMap<>(meta);
        request.put("sizeBytes", bytes.length);
        String ticket = requestUpload(user, tenancyId, request);
        if (putToPresignedUrl(ticket, bytes) != 200) {
            throw new IllegalStateException("Upload to MinIO failed");
        }
        String documentId = JsonPath.read(ticket, "$.documentId");
        mvc.perform(post("/api/tenancies/" + tenancyId + "/uploads/" + documentId + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk());
        return documentId;
    }

    protected static String bearer(TokenResponse tokens) {
        return "Bearer " + tokens.accessToken();
    }
}
