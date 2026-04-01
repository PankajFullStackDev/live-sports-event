package com.sport.event.controller;

import com.sport.event.dto.EventStatusRequest;
import com.sport.event.dto.EventStatusResponse;
import com.sport.event.service.ScoreEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class ScoreEventController {

    private final ScoreEventService scoreEventService;

    /**
     * Endpoint to retrieve the current status of live events.
     * Returns a JSON response containing the count of live events and their IDs.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getStatus() {
        Set<String> liveEventIds = scoreEventService.getLiveEventIds();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("liveEventCount", liveEventIds.size());
        response.put("liveEventIds", liveEventIds);
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint to update the status of an event (start or stop polling).
     * Accepts a JSON body with eventId and isLive fields.
     * Returns a response indicating the action taken (polling started or stopped).
     */
    @PostMapping("/status")
    public ResponseEntity<EventStatusResponse> updateStatus(
            @Valid @RequestBody EventStatusRequest eventStatusRequest) {
        EventStatusResponse statusResponse = scoreEventService.updateStatus(eventStatusRequest);
        return ResponseEntity.accepted().body(statusResponse);
    }

}
