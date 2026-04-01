package com.sport.event.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "score")
public class ScoreEventProperties {

    @NotBlank
    private String externalUrl = "http://localhost:8080/mock/score";

    @NotBlank
    private String kafkaTopic = "event-scores";

    @NotNull
    private Duration pollInterval = Duration.ofSeconds(10);

    @Positive
    private int retryAttempts = 3;

    @NotNull
    private Duration retryBackoff = Duration.ofSeconds(1);

    @Positive
    private int publishRetryAttempts = 3;

    @NotNull
    private Duration publishRetryBackoff = Duration.ofSeconds(1);
}

