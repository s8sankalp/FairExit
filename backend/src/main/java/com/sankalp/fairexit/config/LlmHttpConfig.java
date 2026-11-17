package com.sankalp.fairexit.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** The HTTP client for the LLM: plain Spring RestClient with a hard timeout, no SDK. */
@Configuration
public class LlmHttpConfig {

    @Bean
    @Qualifier("llmRestClient")
    public RestClient llmRestClient(RestClient.Builder builder, LlmProperties properties) {
        int timeoutMs = (int) properties.timeout().toMillis();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return builder.baseUrl(properties.baseUrl()).requestFactory(factory).build();
    }
}
