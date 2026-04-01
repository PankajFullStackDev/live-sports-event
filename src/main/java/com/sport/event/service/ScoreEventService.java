package com.sport.event.service;

import com.sport.event.config.ScoreEventProperties;
import com.sport.event.dto.ExternalScoreResponse;
import com.sport.event.dto.EventStatusRequest;
import com.sport.event.dto.ScoreUpdateMessage;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScoreEventService {

	private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
	private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(5);

	private final ScoreApiRetryExecutor scoreApiRetryExecutor;
	private final ScorePublisherService scorePublisherService;
	private final ScoreEventProperties properties;

	public void updateStatus(EventStatusRequest eventStatusRequest) {
		if (eventStatusRequest == null) {
			throw new IllegalArgumentException("Request body must not be null");
		}

		String eventId = eventStatusRequest.getEventId();
		Boolean status = eventStatusRequest.getIsLive();

		if (!StringUtils.hasText(eventId)) {
			throw new IllegalArgumentException("eventId must not be blank");
		}

		if (status == null) {
			throw new IllegalArgumentException("isLive must not be null");
		}

		if (status) {
			startPolling(eventId);
			return;
		}

		stopPolling(eventId);
	}

	public Set<String> getLiveEventIds() {
		return new TreeSet<>(scheduledTasks.keySet());
	}

	void pollEvent(String eventId) {
		try {
			ExternalScoreResponse payload = scoreApiRetryExecutor.fetchScoreWithRetry(
					UriComponentsBuilder.fromUriString(properties.getExternalUrl())
							.queryParam("eventId", eventId)
							.build()
							.toUri(),
					eventId
			);

			if (payload == null) {
				log.warn("Score API returned no payload for eventId={}", eventId);
				return;
			}

			String responseEventId = StringUtils.hasText(payload.eventId()) ? payload.eventId() : eventId;
			if (!eventId.equals(responseEventId)) {
				log.warn("Score API responded with mismatched eventId. requested={}, received={}", eventId, responseEventId);
			}

			log.info("Fetched score for eventId={}: currentScore={}", responseEventId, payload.currentScore());

			boolean published = scorePublisherService.publishScoreUpdate(new ScoreUpdateMessage(
					responseEventId,
					payload.currentScore(),
					Instant.now().toString()
			));
			if (!published) {
				log.debug("Kafka publish did not succeed for eventId={}", responseEventId);
			}
		} catch (RestClientException exception) {
			log.error("Failed to fetch score for eventId={}", eventId, exception);
		} catch (Exception exception) {
			log.error("Unexpected polling error for eventId={}", eventId, exception);
		}
	}

	private void startPolling(String eventId) {
		long pollIntervalMillis = properties.getPollInterval().toMillis();
		if (pollIntervalMillis <= 0) {
			throw new IllegalStateException("score.poll-interval must be greater than zero");
		}

		ScheduledFuture<?> future = scheduledExecutorService.scheduleAtFixedRate(
				() -> pollEvent(eventId),
				0,
				pollIntervalMillis,
				TimeUnit.MILLISECONDS
		);

		ScheduledFuture<?> existing = scheduledTasks.putIfAbsent(eventId, future);
		if (existing != null) {
			future.cancel(false);
			log.info("Polling already active for eventId={}", eventId);
			return;
		}

		log.info("Started polling for eventId={}", eventId);
	}

	private void stopPolling(String eventId) {
		ScheduledFuture<?> future = scheduledTasks.remove(eventId);
		if (future != null) {
			future.cancel(false);
			log.info("Stopped polling for eventId={}", eventId);
			return;
		}

		log.debug("No polling task to stop for eventId={}", eventId);
	}

	@SuppressWarnings("unused")
	@PreDestroy
	public void shutdown() {
		scheduledTasks.forEach((id, future) -> future.cancel(false));
		scheduledExecutorService.shutdownNow();
		log.info("ScoreEventService shutdown complete");
	}
}
