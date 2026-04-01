package com.sport.event.controller;

import com.sport.event.dto.EventStatusRequest;
import com.sport.event.service.ScoreEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class ScoreEventController {

    private final ScoreEventService scoreEventService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getStatus() {
        Set<String> liveEventIds = scoreEventService.getLiveEventIds();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("liveEventCount", liveEventIds.size());
        response.put("liveEventIds", liveEventIds);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/status")
    public ResponseEntity<Map<String, String>> updateStatus(@Valid @RequestBody EventStatusRequest eventStatusRequest) {
        scoreEventService.updateStatus(eventStatusRequest);

        String action = Boolean.TRUE.equals(eventStatusRequest.getIsLive()) ? "polling-started" : "polling-stopped";
        return ResponseEntity.accepted().body(Map.of(
                "eventId", eventStatusRequest.getEventId(),
                "action", action
        ));
    }
}
