package com.sport.event.integration;

import com.sport.event.dto.ExternalScoreResponse;
import com.sport.event.dto.EventStatusRequest;
import com.sport.event.dto.ScoreUpdateMessage;
import com.sport.event.service.ScoreEventService;
import com.sport.event.service.ScoreApiRetryExecutor;
import com.sport.event.service.ScorePublisherService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "score.poll-interval=30ms")
@AutoConfigureMockMvc
class MessagePublicationIntegrationTest {

    @Autowired
    private ScoreEventService scoreEventService;

    @MockitoBean
    private ScoreApiRetryExecutor scoreApiRetryExecutor;

    @MockitoBean
    private ScorePublisherService scorePublisherService;

    @AfterEach
    void tearDown() {
        scoreEventService.updateStatus(new EventStatusRequest("evt-publish-ok", false));
        scoreEventService.updateStatus(new EventStatusRequest("evt-publish-fetch-error", false));
        scoreEventService.updateStatus(new EventStatusRequest("evt-publish-fail", false));
    }

    @Test
    void publishesMessageWhenScoreIsFetchedNormally() {
        String eventId = "evt-publish-ok";
        when(scoreApiRetryExecutor.fetchScoreWithRetry(any(), eq(eventId)))
                .thenReturn(new ExternalScoreResponse(eventId, "3:1"));
        when(scorePublisherService.publishScoreUpdate(any())).thenReturn(true);

        scoreEventService.updateStatus(new EventStatusRequest(eventId, true));

        verify(scorePublisherService, timeout(700).atLeastOnce())
                .publishScoreUpdate(argThat(message -> messageMatches(message, eventId, "3:1")));
    }

    @Test
    void skipsPublicationWhenScoreFetchFails() {
        String eventId = "evt-publish-fetch-error";
        when(scoreApiRetryExecutor.fetchScoreWithRetry(any(), eq(eventId)))
                .thenThrow(new ResourceAccessException("upstream unavailable"));

        scoreEventService.updateStatus(new EventStatusRequest(eventId, true));

        verify(scorePublisherService, after(400).never())
                .publishScoreUpdate(argThat(message -> message != null && eventId.equals(message.eventId())));
    }

    @Test
    void stillAttemptsPublicationWhenPublisherReportsFailure() {
        String eventId = "evt-publish-fail";
        when(scoreApiRetryExecutor.fetchScoreWithRetry(any(), eq(eventId)))
                .thenReturn(new ExternalScoreResponse(eventId, "0:0"));
        when(scorePublisherService.publishScoreUpdate(any())).thenReturn(false);

        scoreEventService.updateStatus(new EventStatusRequest(eventId, true));

        verify(scorePublisherService, timeout(700).atLeastOnce())
                .publishScoreUpdate(argThat(message -> message != null && eventId.equals(message.eventId())));
    }

    private boolean messageMatches(ScoreUpdateMessage message, String eventId, String score) {
        return message != null
                && eventId.equals(message.eventId())
                && score.equals(message.currentScore())
                && message.publishedAt() != null
                && !message.publishedAt().isBlank();
    }
}

