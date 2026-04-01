package com.sport.event.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sport.event.config.ScoreEventProperties;
import com.sport.event.dto.ScoreUpdateMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScorePublisherService {

	private final KafkaTemplate<String, String> kafkaTemplate;
	private final ObjectMapper objectMapper;
	private final ScoreEventProperties properties;

	public boolean publishScoreUpdate(ScoreUpdateMessage message) {
		final String jsonPayload;

		try {
			jsonPayload = objectMapper.writeValueAsString(message);
		} catch (JsonProcessingException exception) {
			log.error("Failed to serialize score update for eventId={} with currentScore={}",
					message.eventId(), message.currentScore(), exception);
			return false;
		}

		int maxAttempts = properties.getPublishRetryAttempts();
		Duration backoff = properties.getPublishRetryBackoff();

		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				kafkaTemplate.send(properties.getKafkaTopic(), message.eventId(), jsonPayload).get();

				if (attempt > 1) {
					log.info("Recovered Kafka publish for eventId={} with currentScore={} on attempt={}/{} to topic={}",
							message.eventId(), message.currentScore(), attempt, maxAttempts, properties.getKafkaTopic());
				} else {
					log.info("Published score update for eventId={} with currentScore={} to topic={}",
							message.eventId(), message.currentScore(), properties.getKafkaTopic());
				}
				return true;
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				log.error("Kafka publish interrupted for eventId={} with currentScore={}",
						message.eventId(), message.currentScore(), exception);
				return false;
			} catch (ExecutionException | RuntimeException exception) {
				Throwable cause = unwrap(exception);
				if (attempt == maxAttempts || !isRetryable(cause)) {
					log.error("Failed to publish score update for eventId={} with currentScore={} after {} attempt(s). Cause={}",
							message.eventId(), message.currentScore(), attempt, cause.getMessage(), cause);
					return false;
				}

				log.warn("Transient Kafka publish failure for eventId={} with currentScore={} on attempt={}/{}. Retrying in {} ms. Cause={}",
						message.eventId(), message.currentScore(), attempt, maxAttempts, backoff.toMillis(), cause.getMessage());
				sleep(backoff);
			}
		}

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

	private void sleep(Duration backoff) {
		long millis = backoff.toMillis();
		if (millis <= 0) {
			return;
		}

		try {
			Thread.sleep(millis);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Publish retry backoff interrupted", exception);
		}
	}
}


