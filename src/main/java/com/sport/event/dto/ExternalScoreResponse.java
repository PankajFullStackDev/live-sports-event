package com.sport.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExternalScoreResponse(String eventId, String currentScore) {
}

