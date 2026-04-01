package com.sport.event.controller;

import com.sport.event.dto.EventStatusResponse;
import com.sport.event.service.ScoreEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;

import static com.sport.event.constant.EventStatusConstants.SCORE_UPDATES_STARTED_ACTION;
import static com.sport.event.constant.EventStatusConstants.SCORE_UPDATES_STARTED_MESSAGE;
import static com.sport.event.constant.EventStatusConstants.SCORE_UPDATES_STOPPED_ACTION;
import static com.sport.event.constant.EventStatusConstants.SCORE_UPDATES_STOPPED_MESSAGE;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ScoreEventControllerTest {

    private MockMvc mockMvc;
    private ScoreEventService scoreEventService;

    @BeforeEach
    void setUp() {
        scoreEventService = mock(ScoreEventService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ScoreEventController(scoreEventService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void updateStatusAcceptsValidPayload() throws Exception {
        when(scoreEventService.updateStatus(any()))
                .thenReturn(new EventStatusResponse("evt-200", SCORE_UPDATES_STARTED_ACTION, SCORE_UPDATES_STARTED_MESSAGE));

        mockMvc.perform(post("/events/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-200",
                                  "isLive": true
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").value("evt-200"))
                .andExpect(jsonPath("$.action").value(SCORE_UPDATES_STARTED_ACTION));

        verify(scoreEventService).updateStatus(any());
    }

    @Test
    void updateStatusReturnsStoppedActionWhenEventIsNotLive() throws Exception {
        when(scoreEventService.updateStatus(any()))
                .thenReturn(new EventStatusResponse("evt-200", SCORE_UPDATES_STOPPED_ACTION, SCORE_UPDATES_STOPPED_MESSAGE));

        mockMvc.perform(post("/events/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-200",
                                  "isLive": false
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").value("evt-200"))
                .andExpect(jsonPath("$.action").value(SCORE_UPDATES_STOPPED_ACTION));

        verify(scoreEventService).updateStatus(any());
    }

    @Test
    void updateStatusRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/events/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-200"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("isLive must not be null"));
    }

    @Test
    void updateStatusRejectsBlankEventId() throws Exception {
        mockMvc.perform(post("/events/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "   ",
                                  "isLive": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("eventId must not be blank"));
    }

    @Test
    void getStatusReturnsLiveEventSummary() throws Exception {
        when(scoreEventService.getLiveEventIds()).thenReturn(Set.of("evt-201", "evt-202"));

        mockMvc.perform(get("/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liveEventCount").value(2));
    }
}
