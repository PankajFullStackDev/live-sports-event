package com.sport.event.constant;

public final class EventStatusConstants {

    private EventStatusConstants() {
        // Prevent instantiation
    }

    public static final String SCORE_UPDATES_STARTED_ACTION = "score-updates-started";
    public static final String SCORE_UPDATES_STOPPED_ACTION = "score-updates-stopped";

    public static final String SCORE_UPDATES_STARTED_MESSAGE =
            "Live score updates started for this event.";
    public static final String SCORE_UPDATES_STOPPED_MESSAGE =
            "Live score updates stopped for this event.";
}
