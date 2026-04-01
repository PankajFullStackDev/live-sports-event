package com.sport.event.service;

import com.sport.event.config.ScoreEventProperties;
import com.sport.event.dto.ExternalScoreResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoreApiRetryExecutorTest {

    @Mock
    private RestTemplate restTemplate;

    private ScoreApiRetryExecutor scoreApiRetryExecutor;

    @BeforeEach
    void setUp() {
        ScoreEventProperties properties = new ScoreEventProperties();
        properties.setRetryAttempts(3);
        properties.setRetryBackoff(Duration.ZERO);
        scoreApiRetryExecutor = new ScoreApiRetryExecutor(restTemplate, properties);
    }

    @Test
    void fetchScoreWithRetryRetriesAndEventuallySucceeds() {
        when(restTemplate.getForObject(any(URI.class), eq(ExternalScoreResponse.class)))
                .thenThrow(new ResourceAccessException("temporary network issue"))
                .thenThrow(new ResourceAccessException("temporary timeout"))
                .thenReturn(new ExternalScoreResponse("evt-300", "1:0"));

        ExternalScoreResponse response = scoreApiRetryExecutor.fetchScoreWithRetry(URI.create("http://localhost/score?eventId=evt-300"), "evt-300");

        assertThat(response).isEqualTo(new ExternalScoreResponse("evt-300", "1:0"));
        verify(restTemplate, times(3)).getForObject(any(URI.class), eq(ExternalScoreResponse.class));
    }

    @Test
    void fetchScoreWithRetryThrowsAfterConfiguredAttempts() {
        when(restTemplate.getForObject(any(URI.class), eq(ExternalScoreResponse.class)))
                .thenThrow(new ResourceAccessException("broker timeout"));

        assertThatThrownBy(() -> scoreApiRetryExecutor.fetchScoreWithRetry(URI.create("http://localhost/score?eventId=evt-301"), "evt-301"))
                .isInstanceOf(ResourceAccessException.class)
                .hasMessageContaining("broker timeout");

        verify(restTemplate, times(3)).getForObject(any(URI.class), eq(ExternalScoreResponse.class));
    }
}

