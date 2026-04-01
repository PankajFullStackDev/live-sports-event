package com.sport.event.integration;

import com.sport.event.dto.ExternalScoreResponse;
import com.sport.event.service.ScoreApiRetryExecutor;
import com.sport.event.service.ScorePublisherService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.sport.event.constant.EventStatusConstants.SCORE_UPDATES_STARTED_ACTION;
import static com.sport.event.constant.EventStatusConstants.SCORE_UPDATES_STARTED_MESSAGE;
import static com.sport.event.constant.EventStatusConstants.SCORE_UPDATES_STOPPED_ACTION;
import static com.sport.event.constant.EventStatusConstants.SCORE_UPDATES_STOPPED_MESSAGE;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "score.poll-interval=50ms")
@AutoConfigureMockMvc
class StatusUpdatesIntegrationTest {

    private static final String EVENT_ID = "evt-status-it";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScoreApiRetryExecutor scoreApiRetryExecutor;

    @MockitoBean
    private ScorePublisherService scorePublisherService;

    @BeforeEach
    void setUp() {
        when(scoreApiRetryExecutor.fetchScoreWithRetry(any(), anyString()))
                .thenAnswer(invocation -> new ExternalScoreResponse(invocation.getArgument(1), "1:0"));
        when(scorePublisherService.publishScoreUpdate(any())).thenReturn(true);
    }

    @AfterEach
    void cleanUp() throws Exception {
        mockMvc.perform(post("/events/status")
                .contentType("application/json")
                .content("{\"eventId\":\"" + EVENT_ID + "\",\"isLive\":false}"));
    }

    @Test
    void updateStatusStartsAndStopsLiveEvent() throws Exception {
        mockMvc.perform(post("/events/status")
                        .contentType("application/json")
                        .content("""
                                {
                                  "eventId": "evt-status-it",
                                  "isLive": true
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").value(EVENT_ID))
                .andExpect(jsonPath("$.action").value(SCORE_UPDATES_STARTED_ACTION))
                .andExpect(jsonPath("$.message").value(SCORE_UPDATES_STARTED_MESSAGE));

        mockMvc.perform(get("/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liveEventCount").value(1))
                .andExpect(jsonPath("$.liveEventIds", hasItem(EVENT_ID)));

        mockMvc.perform(post("/events/status")
                        .contentType("application/json")
                        .content("""
                                {
                                  "eventId": "evt-status-it",
                                  "isLive": false
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").value(EVENT_ID))
                .andExpect(jsonPath("$.action").value(SCORE_UPDATES_STOPPED_ACTION))
                .andExpect(jsonPath("$.message").value(SCORE_UPDATES_STOPPED_MESSAGE));

        mockMvc.perform(get("/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liveEventCount").value(0));
    }
}

