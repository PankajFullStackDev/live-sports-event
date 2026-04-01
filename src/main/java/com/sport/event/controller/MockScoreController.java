package com.sport.event.controller;

import com.sport.event.dto.ExternalScoreResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Slf4j
@RestController
@RequestMapping("/mock")
public class MockScoreController {

    @GetMapping("/score")
    public ExternalScoreResponse getScore(@RequestParam String eventId) {
        int homeScore = Math.abs(eventId.hashCode()) % 5;
        int awayScore = (int) (Instant.now().getEpochSecond() / 10 % 5);
        ExternalScoreResponse response = new ExternalScoreResponse(eventId, homeScore + ":" + awayScore);
        log.debug("Returning mock score response for eventId={}: {}", eventId, response);
        return response;
    }
}

