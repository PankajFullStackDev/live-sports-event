package com.sport.event.dto;

public record EventStatusResponse(
        String eventId,
        String action,
        String message
) {
}
