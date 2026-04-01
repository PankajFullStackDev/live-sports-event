package com.sport.event.integration;

import com.sport.event.dto.ExternalScoreResponse;
import com.sport.event.service.ScoreApiRetryExecutor;
import com.sport.event.service.ScorePublisherService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = "score.poll-interval=25ms")
@AutoConfigureMockMvc
class ScheduledCallsIntegrationTest {

    private static final String EVENT_ID = "evt-scheduled-it";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScoreApiRetryExecutor scoreApiRetryExecutor;

    @MockitoBean
    private ScorePublisherService scorePublisherService;

    @BeforeEach
    void setUp() {
        when(scoreApiRetryExecutor.fetchScoreWithRetry(any(), anyString()))
                .thenAnswer(invocation -> new ExternalScoreResponse(invocation.getArgument(1), "2:1"));
        when(scorePublisherService.publishScoreUpdate(any())).thenReturn(true);
    }

    @AfterEach
    void cleanUp() throws Exception {
        mockMvc.perform(post("/events/status")
                .contentType("application/json")
                .content("{\"eventId\":\"" + EVENT_ID + "\",\"isLive\":false}"));
    }

    @Test
    void scheduledPollingRunsRepeatedlyAndStopsAfterDisable() throws Exception {
        mockMvc.perform(post("/events/status")
                .contentType("application/json")
                .content("{\"eventId\":\"" + EVENT_ID + "\",\"isLive\":true}"));

        verify(scoreApiRetryExecutor, timeout(700).atLeast(2)).fetchScoreWithRetry(any(), eq(EVENT_ID));
        verify(scorePublisherService, timeout(700).atLeast(2)).publishScoreUpdate(any());

        mockMvc.perform(post("/events/status")
                .contentType("application/json")
                .content("{\"eventId\":\"" + EVENT_ID + "\",\"isLive\":false}"));

        Thread.sleep(100);
        clearInvocations(scoreApiRetryExecutor, scorePublisherService);
        Thread.sleep(250);

        verifyNoInteractions(scoreApiRetryExecutor, scorePublisherService);
        Mockito.reset(scoreApiRetryExecutor, scorePublisherService);
        when(scoreApiRetryExecutor.fetchScoreWithRetry(any(), anyString()))
                .thenAnswer(invocation -> new ExternalScoreResponse(invocation.getArgument(1), "2:1"));
        when(scorePublisherService.publishScoreUpdate(any())).thenReturn(true);
    }
}

