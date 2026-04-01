package com.sport.event.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sport.event.config.ScoreEventProperties;
import com.sport.event.dto.ScoreUpdateMessage;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScorePublisherServiceTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private ScorePublisherService scorePublisherService;

    @BeforeEach
    void setUp() {
        ScoreEventProperties properties = new ScoreEventProperties();
        properties.setKafkaTopic("event-scores");
        properties.setPublishRetryAttempts(3);
        properties.setPublishRetryBackoff(Duration.ZERO);
        scorePublisherService = new ScorePublisherService(kafkaTemplate, new ObjectMapper(), properties);
    }

    @Test
    void publishScoreUpdateRetriesAndEventuallySucceeds() {
        CompletableFuture<SendResult<String, String>> failedFirst = new CompletableFuture<>();
        failedFirst.completeExceptionally(new TimeoutException("temporary broker timeout"));
        CompletableFuture<SendResult<String, String>> failedSecond = new CompletableFuture<>();
        failedSecond.completeExceptionally(new TimeoutException("temporary broker timeout"));
        CompletableFuture<SendResult<String, String>> success = CompletableFuture.completedFuture(null);

        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(failedFirst)
                .thenReturn(failedSecond)
                .thenReturn(success);

        boolean published = scorePublisherService.publishScoreUpdate(new ScoreUpdateMessage("evt-400", "2:2", "2026-04-01T00:00:00Z"));

        assertThat(published).isTrue();
        verify(kafkaTemplate, times(3)).send(eq("event-scores"), eq("evt-400"), anyString());
    }

    @Test
    void publishScoreUpdateStopsAfterConfiguredAttempts() {
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new KafkaException("broker unavailable"));

        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(failed)
                .thenReturn(failed)
                .thenReturn(failed);

        boolean published = scorePublisherService.publishScoreUpdate(new ScoreUpdateMessage("evt-401", "1:1", "2026-04-01T00:00:00Z"));

        assertThat(published).isFalse();
        verify(kafkaTemplate, times(3)).send(eq("event-scores"), eq("evt-401"), anyString());
    }

    @Test
    void publishScoreUpdateDoesNotRetryNonTransientFailure() {
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalArgumentException("invalid record"));

        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failed);

        boolean published = scorePublisherService.publishScoreUpdate(new ScoreUpdateMessage("evt-401b", "1:1", "2026-04-01T00:00:00Z"));

        assertThat(published).isFalse();
        verify(kafkaTemplate, times(1)).send(eq("event-scores"), eq("evt-401b"), anyString());
    }

    @Test
    void publishScoreUpdateReturnsFalseWhenSerializationFails() {
        ScorePublisherService failingPublisher = new ScorePublisherService(kafkaTemplate, new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws JsonProcessingException {
                throw new JsonProcessingException("bad payload") {
                };
            }
        }, properties());

        boolean published = failingPublisher.publishScoreUpdate(new ScoreUpdateMessage("evt-402", "0:0", "2026-04-01T00:00:00Z"));

        assertThat(published).isFalse();
    }

    private ScoreEventProperties properties() {
        ScoreEventProperties properties = new ScoreEventProperties();
        properties.setKafkaTopic("event-scores");
        properties.setPublishRetryAttempts(3);
        properties.setPublishRetryBackoff(Duration.ZERO);
        return properties;
    }
}

