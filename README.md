# Event Score Polling Prototype

A Spring Boot prototype that accepts event live/not-live updates, polls a score API every 10 seconds for live events, and publishes score updates to Kafka.

## Checklist
- [x] REST endpoint to mark an event as live or not live
- [x] Per-event polling every 10 seconds while an event is live
- [x] External score API call with retry for transient failures
- [x] Kafka message publication with retry for transient failures
- [x] Logging for success and failure paths
- [x] Local Kafka startup via Docker Compose
- [x] Unit/integration-style test coverage for key flows

---

## 1. What this project does

When you mark an event as `live`, the application:
1. starts a scheduled polling task for that `eventId`
2. calls an external score API every 10 seconds
3. expects a response like:
   ```json
   {
     "eventId": "1234",
     "currentScore": "0:0"
   }
   ```
4. transforms that into a Kafka message
5. publishes the message to the Kafka topic `event-scores`

When you mark the event as `not live`, polling stops.

For local demo usage, the app already includes a mock score endpoint so you do not need a second external service to try it.

---

## 2. Tech stack

- Java 17
- Spring Boot 3.x
- Spring Web
- Spring Kafka
- Maven Wrapper (`./mvnw`)
- Docker Compose for Kafka
- JUnit + Mockito for tests

---

## 3. Project structure

Important files:

- `src/main/java/com/sport/event/controller/ScoreEventController.java`
  - REST API for event status updates
- `src/main/java/com/sport/event/controller/MockScoreController.java`
  - local mock score provider for demo/testing
- `src/main/java/com/sport/event/service/ScoreEventService.java`
  - manages live-event polling tasks
- `src/main/java/com/sport/event/service/ScoreApiRetryExecutor.java`
  - retries transient external API failures
- `src/main/java/com/sport/event/service/ScorePublisherService.java`
  - publishes Kafka messages with retry logic
- `src/main/resources/application.properties`
  - runtime configuration
- `docker-compose.yml`
  - local Kafka setup

---

## 4. Prerequisites

You should have:
- Java 17 installed
- Docker installed and running
- Internet access the first time Maven downloads dependencies

Optional but useful:
- `curl`
- `nc`

Check Java:

```zsh
java -version
```

Check Docker:

```zsh
docker --version
docker compose version
```

---

## 5. Configuration

Current defaults from `src/main/resources/application.properties`:

```properties
spring.application.name=event

score.external-url=http://localhost:${server.port:8080}/mock/score
score.kafka-topic=event-scores
score.poll-interval=10s
score.retry-attempts=3
score.retry-backoff=1s
score.publish-retry-attempts=3
score.publish-retry-backoff=1s

spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
logging.level.com.sport.event=INFO
```

### What these mean

- `score.external-url`
  - score API endpoint to poll
  - defaults to the built-in mock endpoint
- `score.kafka-topic`
  - topic where messages are published
- `score.poll-interval`
  - how often live events are polled
- `score.retry-attempts`
  - retry count for external API calls
- `score.retry-backoff`
  - wait time between external API retries
- `score.publish-retry-attempts`
  - retry count for Kafka publish attempts
- `score.publish-retry-backoff`
  - wait time between Kafka publish retries
- `spring.kafka.bootstrap-servers`
  - Kafka broker address

---

## 6. Quick start

### Step 1: start Kafka

From the project root (replace `/path/to/event` with your local clone directory if needed):

```zsh
cd /path/to/event
docker compose up -d
```

Check it is running:

```zsh
docker ps
nc -zv localhost 9092
```

If Docker is healthy, your app can publish to Kafka on `localhost:9092`.

---

### Step 2: run the application

```zsh
cd /path/to/event
./mvnw spring-boot:run
```

The app should start on:

```text
http://localhost:8080
```

---

### Step 3: verify the app is alive

```zsh
curl http://localhost:8080/events
```

Expected response initially:

```json
{
  "liveEventCount": 0,
  "liveEventIds": []
}
```

---

## 7. API usage

### 7.1 Mark an event live

```zsh
curl -X POST http://localhost:8080/events/status \
  -H 'Content-Type: application/json' \
  -d '{"eventId":"1234","isLive":true}'
```

Expected response:

```json
{
  "eventId": "1234",
  "action": "score-updates-started",
  "message": "Live score updates started for this event."
}
```

---

### 7.2 Mark an event not live

```zsh
curl -X POST http://localhost:8080/events/status \
  -H 'Content-Type: application/json' \
  -d '{"eventId":"1234","isLive":false}'
```

Expected response:

```json
{
  "eventId": "1234",
  "action": "score-updates-stopped",
  "message": "Live score updates stopped for this event."
}
```

---

### 7.3 Check active live events

```zsh
curl http://localhost:8080/events
```

Example response:

```json
{
  "liveEventCount": 1,
  "liveEventIds": ["1234"]
}
```

---

### 7.4 Validation example

```zsh
curl -X POST http://localhost:8080/events/status \
  -H 'Content-Type: application/json' \
  -d '{"eventId":"1234"}'
```

Example error response:

```json
{
  "timestamp": "2026-04-01T00:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "isLive must not be null"
}
```

---

## 8. Built-in mock score API

The app includes a mock endpoint:

```text
GET /mock/score?eventId=1234
```

Try it directly:

```zsh
curl "http://localhost:8080/mock/score?eventId=1234"
```

Example response:

```json
{
  "eventId": "1234",
  "currentScore": "4:2"
}
```

This is the default upstream used by the polling service unless you override `score.external-url`.

---

## 9. Kafka verification

### Consume messages from the topic

With Kafka running via Docker Compose:

```zsh
docker exec -it local-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic event-scores \
  --from-beginning
```

Then mark an event live from another terminal:

```zsh
curl -X POST http://localhost:8080/events/status \
  -H 'Content-Type: application/json' \
  -d '{"eventId":"1234","isLive":true}'
```

You should start seeing JSON messages like:

```json
{"eventId":"1234","currentScore":"1:0","publishedAt":"2026-04-01T10:15:30.000Z"}
```

Stop polling when done:

```zsh
curl -X POST http://localhost:8080/events/status \
  -H 'Content-Type: application/json' \
  -d '{"eventId":"1234","isLive":false}'
```

---

## 10. Logging behavior

You should see logs like:

### Score fetched
```text
Fetched score for eventId=1234: currentScore=2:1
```

### Kafka publish success
```text
Published score update for eventId=1234 with currentScore=2:1 to topic=event-scores
```

### External API retry
```text
Transient score API failure for eventId=1234 on attempt=1/3. Retrying in 1000 ms. Cause=...
Recovered score API call for eventId=1234 on attempt=2/3
```

### Kafka publish retry
```text
Transient Kafka publish failure for eventId=1234 with currentScore=2:1 on attempt=1/3. Retrying in 1000 ms. Cause=...
Recovered Kafka publish for eventId=1234 with currentScore=2:1 on attempt=2/3 to topic=event-scores
```

---

## 11. Running tests

Run the full suite:

```zsh
cd /path/to/event
./mvnw test
```

Verified in this workspace: the test suite passes.

Current coverage includes:
- status update validation and controller responses
- scheduled repeated polling for live events
- publish success and failure paths
- external API retry behavior
- Kafka publish retry behavior

---

## 12. Architecture summary

### `ScoreEventController`
Handles:
- `POST /events/status`
- `GET /events`

### `ScoreEventService`
Handles:
- in-memory registry of live events
- starting/stopping polling tasks
- building the outbound score update message

### `ScoreApiRetryExecutor`
Handles:
- retries for transient upstream API failures

### `ScorePublisherService`
Handles:
- Kafka serialization
- Kafka publishing
- retries for transient Kafka failures

### `MockScoreController`
Handles:
- local mock score responses for easy demos

---

## 13. Common workflows

### Workflow A: fastest local demo
1. start Kafka with Docker
2. run the app
3. mark an event live
4. watch logs and/or consume from Kafka
5. mark the event not live

### Workflow B: test only the API
You can still call the REST endpoints even if Kafka is not running, but publish attempts will fail and be logged.

---

## 14. Troubleshooting

### Kafka connection warning
If you see logs like:

```text
Connection to node -1 (localhost/127.0.0.1:9092) could not be established
```

Kafka is not reachable.

Check:

```zsh
docker ps
nc -zv localhost 9092
```

Restart Kafka if needed:

```zsh
cd /path/to/event
docker compose down
docker compose up -d
```

---

### Port 8080 already in use
Run the app on another port:

```zsh
cd /path/to/event
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

Then adjust your curl commands accordingly.

---

### Kafka topic inspection
List topics:

```zsh
docker exec -it local-kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --list
```

---

### Maven dependency download issues
This project uses a Spring Boot snapshot parent in `pom.xml`, so the first build may download snapshot artifacts from Spring's snapshot repository.

If your network is slow, simply rerun:

```zsh
cd /path/to/event
./mvnw test
```

---

## 15. Stop everything

### Stop the Spring Boot app
Use `Ctrl+C` in the terminal running the app.

### Stop Kafka

```zsh
cd /path/to/event
docker compose down
```

---

## 16. AI usage note

This project documentation was generated and refined with AI assistance based on the current workspace implementation and verified commands/tests run in the workspace.

