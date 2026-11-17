package com.sankalp.fairexit.service.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.sankalp.fairexit.config.FairExitMetrics;
import com.sankalp.fairexit.config.LlmProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.sankalp.fairexit.service.llm.LlmException.Kind;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** No network: a fake server stands in for Gemini. */
class GeminiClientTest {

    private static final String URL = "https://llm.test/v1beta/models/gemini-test:generateContent";

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://llm.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final FairExitMetrics metrics = new FairExitMetrics(new SimpleMeterRegistry());

    private GeminiClient client(String apiKey) {
        LlmProperties properties = new LlmProperties(apiKey, "gemini-test", "https://llm.test",
                Duration.ofSeconds(20), 20, Duration.ofDays(30));
        return new GeminiClient(builder.build(), properties, metrics);
    }

    @Test
    void sendsTheKeyInAHeaderAndReturnsTheModelsText() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "secret-key"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value("the instruction"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("masked text"))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":"{\\"lines\\":[]}"}]}}],
                         "usageMetadata":{"promptTokenCount":120,"candidatesTokenCount":30}}
                        """, MediaType.APPLICATION_JSON));

        String answer = client("secret-key").generateJson("the instruction", "masked text");

        assertThat(answer).isEqualTo("{\"lines\":[]}");
        server.verify();
        // tokens are counted, so Grafana can show the cost per day
        assertThat(metrics.registry().counter("fairexit.llm.tokens", "type", "input").count()).isEqualTo(120);
        assertThat(metrics.registry().counter("fairexit.llm.tokens", "type", "output").count()).isEqualTo(30);
    }

    @ParameterizedTest
    @CsvSource({"503, UNAVAILABLE", "500, UNAVAILABLE", "429, UNAVAILABLE", "400, REJECTED", "403, REJECTED"})
    void httpErrorsAreSortedIntoRetryOrNot(int status, Kind expected) {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.valueOf(status)));

        assertThatThrownBy(() -> client("secret-key").generateJson("i", "t"))
                .isInstanceOf(LlmException.class)
                .satisfies(e -> assertThat(((LlmException) e).kind()).isEqualTo(expected))
                .hasMessageNotContaining("secret-key");
    }

    @Test
    void anAnswerWithoutTextIsBadOutput() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"candidates\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client("secret-key").generateJson("i", "t"))
                .satisfies(e -> assertThat(((LlmException) e).kind()).isEqualTo(Kind.BAD_OUTPUT));
    }

    @Test
    void withoutAnApiKeyNoRequestIsMade() {
        assertThatThrownBy(() -> client("  ").generateJson("i", "t"))
                .satisfies(e -> assertThat(((LlmException) e).kind()).isEqualTo(Kind.NOT_CONFIGURED));
        server.verify(); // nothing was expected, nothing was sent
    }
}
