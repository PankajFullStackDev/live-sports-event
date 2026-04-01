package com.sport.event.service;

import com.sport.event.config.ScoreEventProperties;
import com.sport.event.dto.ExternalScoreResponse;
import com.sport.event.dto.EventStatusRequest;
import com.sport.event.dto.ScoreUpdateMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoreEventServiceTest {

    @Mock
    private ScoreApiRetryExecutor scoreApiRetryExecutor;

    @Mock
    private ScorePublisherService scorePublisherService;

    private ScoreEventProperties properties;
    private ScoreEventService scoreEventService;

    @BeforeEach
    void setUp() {
        properties = new ScoreEventProperties();
        properties.setExternalUrl("http://localhost:8088/external/score");
        properties.setKafkaTopic("event-scores");
        properties.setPollInterval(Duration.ofSeconds(10));
        properties.setRetryAttempts(3);
        properties.setRetryBackoff(Duration.ZERO);
        properties.setPublishRetryAttempts(3);
        properties.setPublishRetryBackoff(Duration.ZERO);

        scoreEventService = new ScoreEventService(scoreApiRetryExecutor, scorePublisherService, properties);
    }

    @AfterEach
    void tearDown() {
        scoreEventService.shutdown();
    }

    @Test
    void updateStatusStartsAndStopsPolling() {
        when(scoreApiRetryExecutor.fetchScoreWithRetry(any(), eq("evt-100")))
                .thenReturn(new ExternalScoreResponse("evt-100", "0:0"));
        when(scorePublisherService.publishScoreUpdate(any())).thenReturn(true);

        scoreEventService.updateStatus(new EventStatusRequest("evt-100", true));
        assertThat(scoreEventService.getLiveEventIds()).containsExactly("evt-100");

        scoreEventService.updateStatus(new EventStatusRequest("evt-100", false));
        assertThat(scoreEventService.getLiveEventIds()).isEmpty();
    }

    @Test
    void pollEventPublishesKafkaMessage() {
        when(scoreApiRetryExecutor.fetchScoreWithRetry(any(), eq("evt-101")))
                .thenReturn(new ExternalScoreResponse("evt-101", "3:2"));
        when(scorePublisherService.publishScoreUpdate(any())).thenReturn(true);

        scoreEventService.pollEvent("evt-101");

        ArgumentCaptor<ScoreUpdateMessage> messageCaptor = ArgumentCaptor.forClass(ScoreUpdateMessage.class);
        verify(scorePublisherService).publishScoreUpdate(messageCaptor.capture());
        assertThat(messageCaptor.getValue().eventId()).isEqualTo("evt-101");
        assertThat(messageCaptor.getValue().currentScore()).isEqualTo("3:2");
        assertThat(messageCaptor.getValue().publishedAt()).isNotBlank();
    }

    @Test
    void pollEventSkipsPublishingWhenApiReturnsNull() {
        when(scoreApiRetryExecutor.fetchScoreWithRetry(any(), eq("evt-102"))).thenReturn(null);

        scoreEventService.pollEvent("evt-102");

        verify(scorePublisherService, never()).publishScoreUpdate(any());
    }

    @Test
    void updateStatusRejectsBlankEventId() {
        assertThatThrownBy(() -> scoreEventService.updateStatus(new EventStatusRequest(" ", true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("eventId must not be blank");
    }

    @Test
    void updateStatusRejectsNullRequest() {
        assertThatThrownBy(() -> scoreEventService.updateStatus(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Request body must not be null");
    }

    @Test
    void updateStatusSchedulesRepeatedPollingForLiveEvents() {
        properties.setPollInterval(Duration.ofMillis(25));
        when(scoreApiRetryExecutor.fetchScoreWithRetry(any(), eq("evt-scheduled")))
                .thenReturn(new ExternalScoreResponse("evt-scheduled", "1:0"));
        when(scorePublisherService.publishScoreUpdate(any())).thenReturn(true);

        scoreEventService.updateStatus(new EventStatusRequest("evt-scheduled", true));

        verify(scoreApiRetryExecutor, timeout(500).atLeast(2)).fetchScoreWithRetry(any(), eq("evt-scheduled"));
        verify(scorePublisherService, timeout(500).atLeast(2)).publishScoreUpdate(any());

        scoreEventService.updateStatus(new EventStatusRequest("evt-scheduled", false));
        assertThat(scoreEventService.getLiveEventIds()).doesNotContain("evt-scheduled");
    }

    @Test
    void pollEventDoesNotPublishWhenFetchFails() {
        when(scoreApiRetryExecutor.fetchScoreWithRetry(any(), eq("evt-103")))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("temporary upstream failure"));

        scoreEventService.pollEvent("evt-103");

        verify(scorePublisherService, never()).publishScoreUpdate(any());
    }

    @Test
    void pollEventDelegatesToPublisherEvenWhenPublishReportsFailure() {
        when(scoreApiRetryExecutor.fetchScoreWithRetry(any(), eq("evt-104")))
                .thenReturn(new ExternalScoreResponse("evt-104", "4:4"));
        when(scorePublisherService.publishScoreUpdate(any())).thenReturn(false);

        scoreEventService.pollEvent("evt-104");

        verify(scorePublisherService).publishScoreUpdate(any());
    }
}

