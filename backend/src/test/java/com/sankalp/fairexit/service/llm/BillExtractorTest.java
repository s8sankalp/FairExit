package com.sankalp.fairexit.service.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sankalp.fairexit.config.FairExitMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.sankalp.fairexit.service.llm.LlmException.Kind;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BillExtractorTest {

    private static final UUID USER = UUID.randomUUID();
    private static final String TEXT = "Painting 55,000";
    private static final String GOOD_ANSWER =
            "{\"lines\":[{\"item\":\"Painting\",\"category\":\"PAINTING\",\"amount\":55000}]}";

    @Mock
    private LlmClient llm;
    @Mock
    private LlmAnswerCache cache;
    @Mock
    private LlmQuota quota;

    private BillExtractor extractor;
    private FairExitMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new FairExitMetrics(new SimpleMeterRegistry());
        extractor = new BillExtractor(llm, new ExtractionParser(new ObjectMapper()),
                new LlmGateway(cache, quota, metrics));
    }

    @Test
    void aCacheHitCostsNothing() {
        when(cache.get(anyString())).thenReturn(Optional.of(GOOD_ANSWER));

        assertThat(extractor.extract(USER, TEXT).lines()).hasSize(1);
        verifyNoInteractions(llm, quota);
        assertThat(metrics.registry().counter("fairexit.llm.calls", "result", "cache_hit").count()).isEqualTo(1);
    }

    @Test
    void aMissCallsTheLlmOnceAndCachesTheAnswerOnlyAfterItIsChecked() {
        when(cache.get(anyString())).thenReturn(Optional.empty());
        when(quota.tryAcquire(USER)).thenReturn(true);
        when(llm.generateJson(BillExtractor.INSTRUCTION, TEXT)).thenReturn(GOOD_ANSWER);

        ExtractionResult result = extractor.extract(USER, TEXT);

        assertThat(result.lines()).hasSize(1);
        verify(cache).put(anyString(), eq(GOOD_ANSWER));
    }

    @Test
    void theSameTextAlwaysUsesTheSameCacheKey() {
        when(cache.get(anyString())).thenReturn(Optional.empty());
        when(quota.tryAcquire(any())).thenReturn(true);
        when(llm.generateJson(anyString(), anyString())).thenReturn(GOOD_ANSWER);

        extractor.extract(USER, TEXT);
        extractor.extract(UUID.randomUUID(), TEXT); // the key depends on the text, not the user

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(cache, times(2)).get(keys.capture());
        assertThat(keys.getAllValues().get(0)).isEqualTo(keys.getAllValues().get(1)).hasSize(64);
    }

    @Test
    void aFileIsSentAsAnAttachmentAndCachedByItsHash() {
        when(cache.get(anyString())).thenReturn(Optional.empty());
        when(quota.tryAcquire(USER)).thenReturn(true);
        when(llm.generateJson(eq(BillExtractor.INSTRUCTION), eq(BillExtractor.FILE_PROMPT), anyList()))
                .thenReturn(GOOD_ANSWER);

        ExtractionResult result = extractor.extractFile(USER, "a".repeat(64), "image/png", new byte[] {1, 2});

        assertThat(result.lines()).hasSize(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LlmAttachment>> files = ArgumentCaptor.forClass(List.class);
        verify(llm).generateJson(anyString(), anyString(), files.capture());
        assertThat(files.getValue()).singleElement().extracting(LlmAttachment::mimeType).isEqualTo("image/png");
    }

    @Test
    void overTheDailyLimitTheLlmIsNotCalled() {
        when(cache.get(anyString())).thenReturn(Optional.empty());
        when(quota.tryAcquire(USER)).thenReturn(false);

        assertThatThrownBy(() -> extractor.extract(USER, TEXT))
                .isInstanceOf(LlmException.class)
                .satisfies(e -> assertThat(((LlmException) e).kind()).isEqualTo(Kind.QUOTA_EXCEEDED));
        verifyNoInteractions(llm);
    }

    @Test
    void aBadAnswerIsNeverCached() {
        when(cache.get(anyString())).thenReturn(Optional.empty());
        when(quota.tryAcquire(USER)).thenReturn(true);
        when(llm.generateJson(anyString(), anyString())).thenReturn("I think the painting is 55000");

        assertThatThrownBy(() -> extractor.extract(USER, TEXT))
                .satisfies(e -> assertThat(((LlmException) e).kind()).isEqualTo(Kind.BAD_OUTPUT));
        verify(cache, never()).put(anyString(), anyString());
    }
}
