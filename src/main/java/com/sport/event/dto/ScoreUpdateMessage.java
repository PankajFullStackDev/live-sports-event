package com.sport.event.dto;

public record ScoreUpdateMessage(String eventId, String currentScore, String publishedAt) {
}

