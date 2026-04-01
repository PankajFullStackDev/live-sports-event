package com.sport.event.service;

import com.sport.event.config.ScoreEventProperties;
import com.sport.event.dto.EventStatusResponse;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.sport.event.constant.EventStatusConstants.SCORE_UPDATES_STARTED_ACTION;
import static com.sport.event.constant.EventStatusConstants.SCORE_UPDATES_STARTED_MESSAGE;
import static com.sport.event.constant.EventStatusConstants.SCORE_UPDATES_STOPPED_ACTION;
import static com.sport.event.constant.EventStatusConstants.SCORE_UPDATES_STOPPED_MESSAGE;

/**
 * Service responsible for managing the live status of sports events and polling an external score API for updates.
 * It maintains a concurrent map of scheduled tasks for each live event and provides methods to start and stop polling.
 * The service also handles graceful shutdown of scheduled tasks when the application is terminated.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ScoreEventService {

	private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
	private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(5);

	private final ScoreApiRetryExecutor scoreApiRetryExecutor;
	private final ScorePublisherService scorePublisherService;
	private final ScoreEventProperties properties;

	/**
	 * Updates the live status of an event. If isLive is true, starts polling for score updates.
	 * If isLive is false, stops polling and returns a response indicating the action taken.
	 *
	 * @param eventStatusRequest the request containing eventId and isLive status
	 * @return an EventStatusResponse indicating the result of the operation
	 * @throws IllegalArgumentException if the request body is null
	 * @throws IllegalStateException if the poll interval configuration is invalid
	 */
	public EventStatusResponse updateStatus(EventStatusRequest eventStatusRequest) {
		if (eventStatusRequest == null) {
			throw new IllegalArgumentException("Request body must not be null");
		}

		String eventId = eventStatusRequest.getEventId();
		if (!StringUtils.hasText(eventId)) {
			throw new IllegalArgumentException("eventId must not be blank");
		}

		Boolean status = eventStatusRequest.getIsLive();

		if (status) {
			startPolling(eventId);
			return buildStatusResponse(eventId, true);
		}

		stopPolling(eventId);

		return buildStatusResponse(eventId, false);
	}

	private EventStatusResponse buildStatusResponse(String eventId, Boolean isLive) {
		String action = isLive ? SCORE_UPDATES_STARTED_ACTION : SCORE_UPDATES_STOPPED_ACTION;
		String message = isLive ? SCORE_UPDATES_STARTED_MESSAGE : SCORE_UPDATES_STOPPED_MESSAGE;

		return new EventStatusResponse(eventId, action, message);
	}


	public Set<String> getLiveEventIds() {
		return new TreeSet<>(scheduledTasks.keySet());
	}

	/**
	 * Polls the external score API for the given eventId, processes the response, and publishes score updates.
	 * Handles exceptions gracefully and logs relevant information at each step.
	 *
	 * @param eventId the ID of the event to poll for score updates
	 */
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

			// Publish the score update to Kafka and log the result
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

	/**
	 * Starts a scheduled task to poll the external score API at fixed intervals for the given eventId.
	 * Validates the poll interval configuration and ensures that only one polling task is active per eventId.
	 *
	 * @param eventId the ID of the event to start polling for score updates
	 * @throws IllegalStateException if the poll interval configuration is invalid
	 */
	private void startPolling(String eventId) {
		long pollIntervalMillis = properties.getPollInterval().toMillis();
		if (pollIntervalMillis <= 0) {
			throw new IllegalStateException("score.poll-interval must be greater than zero");
		}

		// Schedule a task to poll the external score API at fixed intervals
		ScheduledFuture<?> future = scheduledExecutorService.scheduleAtFixedRate(
				() -> pollEvent(eventId),
				0,
				pollIntervalMillis,
				TimeUnit.MILLISECONDS
		);

		// Use putIfAbsent to ensure only one polling task is active for the same eventId
		ScheduledFuture<?> existing = scheduledTasks.putIfAbsent(eventId, future);
		if (existing != null) {
			future.cancel(false);
			log.info("Polling already active for eventId={}", eventId);
			return;
		}

		log.info("Started polling for eventId={}", eventId);
	}

	/**
	 * Stops the scheduled polling task for the given eventId if it exists. Logs the result of the operation.
	 *
	 * @param eventId the ID of the event to stop polling for score updates
	 */
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
