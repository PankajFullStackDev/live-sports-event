package com.sport.event.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sport.event.config.ScoreEventProperties;
import com.sport.event.dto.ScoreUpdateMessage;
import com.sport.event.exception.TransientPublishException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Service responsible for publishing score updates to a Kafka topic.
 * It handles serialization of score update messages and implements retry logic for transient failures.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ScorePublisherService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final ScoreEventProperties properties;


    /**
     * Publishes a score update message to the configured Kafka topic.
     * Implements retry logic for transient exceptions such as timeouts and Kafka exceptions.
     *
     * @param message the score update message to publish
     * @return true if the message was published successfully, false otherwise
     */
    @Retryable(
            retryFor = TransientPublishException.class,
            maxAttemptsExpression = "#{@scoreEventProperties.publishRetryAttempts}",
            backoff = @Backoff(delayExpression = "#{@scoreEventProperties.publishRetryBackoff.toMillis()}")
    )
    public boolean publishScoreUpdate(ScoreUpdateMessage message) {
        final String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            log.error("Failed to serialize score update for eventId={} with currentScore={}",
                    message.eventId(), message.currentScore(), exception);
            return false;
        }

        try {
            kafkaTemplate.send(properties.getKafkaTopic(), message.eventId(), jsonPayload).get();
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | RuntimeException ex) {
            Throwable cause = unwrap(ex);
            if (isRetryable(cause)) {
                throw new TransientPublishException(cause); // triggers retry
            }
            return false; // non-transient, no retry
        }
    }

    /**
     * Recovery method invoked when all retry attempts are exhausted for a TransientPublishException.
     * Logs the failure and returns false to indicate the publish operation ultimately failed.
     * @param ex the exception that caused the retries to be exhausted
     * @param message the score update message that failed to publish
     * @return false indicating the publish operation failed after retries
     */
    @Recover
    public boolean recover(TransientPublishException ex, ScoreUpdateMessage message) {
        log.error("Retries exhausted for eventId={}", message.eventId(), ex);
        return false;
    }


    private boolean isRetryable(Throwable cause) {
        return cause instanceof TimeoutException
                || cause instanceof KafkaException;
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}


