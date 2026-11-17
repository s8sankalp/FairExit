package com.sankalp.fairexit.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.sankalp.fairexit.config.FairExitMetrics;
import com.sankalp.fairexit.config.LlmProperties;
import com.sankalp.fairexit.service.llm.LlmException.Kind;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Calls the Gemini generateContent REST API with Spring RestClient (no SDK). */
@Component
public class GeminiClient implements LlmClient {

    private final RestClient restClient;
    private final LlmProperties properties;
    private final FairExitMetrics metrics;

    public GeminiClient(@Qualifier("llmRestClient") RestClient restClient, LlmProperties properties,
                        FairExitMetrics metrics) {
        this.restClient = restClient;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override
    public String generateJson(String instruction, String text, List<LlmAttachment> attachments) {
        if (!properties.configured()) {
            throw new LlmException(Kind.NOT_CONFIGURED, "GEMINI_API_KEY is not set");
        }
        // Files go inline, base64 encoded; they are at most 10 MB, under Gemini's inline limit
        List<Map<String, Object>> parts = new ArrayList<>();
        for (LlmAttachment file : attachments) {
            parts.add(Map.of("inlineData", Map.of(
                    "mimeType", file.mimeType(),
                    "data", Base64.getEncoder().encodeToString(file.data()))));
        }
        parts.add(Map.of("text", text));
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", instruction))),
                "contents", List.of(Map.of("role", "user", "parts", parts)),
                "generationConfig", Map.of("responseMimeType", "application/json"));

        JsonNode root;
        try {
            root = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent", properties.model())
                    // The key goes in a header, never in the URL, so it cannot end up in logs
                    .header("x-goog-api-key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            boolean temporary = status == 429 || status >= 500;
            throw new LlmException(temporary ? Kind.UNAVAILABLE : Kind.REJECTED, "Gemini answered HTTP " + status);
        } catch (RestClientException e) {
            // Timeouts and connection errors
            throw new LlmException(Kind.UNAVAILABLE, "Gemini could not be reached: " + e.getClass().getSimpleName());
        }

        if (root != null) {
            JsonNode usage = root.path("usageMetadata");
            metrics.llmTokens(usage.path("promptTokenCount").asLong(0), usage.path("candidatesTokenCount").asLong(0));
        }
        String answer = root == null ? null
                : root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText(null);
        if (answer == null || answer.isBlank()) {
            throw new LlmException(Kind.BAD_OUTPUT, "Gemini returned no text");
        }
        return answer;
    }
}
