package com.sport.event.service;

import com.sport.event.config.ScoreEventProperties;
import com.sport.event.dto.ExternalScoreResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Duration;

@Component
@Slf4j
@RequiredArgsConstructor
public class ScoreApiRetryExecutor {

    private final RestTemplate restTemplate;
    private final ScoreEventProperties properties;

    public ExternalScoreResponse fetchScoreWithRetry(URI scoreUri, String eventId) {
        int maxAttempts = properties.getRetryAttempts();
        Duration backoff = properties.getRetryBackoff();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ExternalScoreResponse response = restTemplate.getForObject(scoreUri, ExternalScoreResponse.class);
                if (attempt > 1) {
                    log.info("Recovered score API call for eventId={} on attempt={}/{}", eventId, attempt, maxAttempts);
                }
                return response;
            } catch (RestClientException exception) {
                if (attempt == maxAttempts) {
                    log.error("Score API call failed for eventId={} after {} attempt(s)", eventId, maxAttempts, exception);
                    throw exception;
                }

                log.warn("Transient score API failure for eventId={} on attempt={}/{}. Retrying in {} ms. Cause={}",
                        eventId, attempt, maxAttempts, backoff.toMillis(), exception.getMessage());
                sleep(backoff);
            }
        }

        throw new IllegalStateException("Retry loop exited unexpectedly for eventId=" + eventId);
    }

    private void sleep(Duration backoff) {
        long millis = backoff.toMillis();
        if (millis <= 0) {
            return;
        }

        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry backoff interrupted", exception);
        }
    }
}

