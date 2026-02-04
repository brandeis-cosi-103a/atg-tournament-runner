package edu.brandeis.cosi103a.tournament.viewer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO for player configuration in tournament requests.
 */
public record PlayerConfigRequest(
    @JsonProperty("name") @NotBlank String name,
    @JsonProperty("url") @NotBlank String url,
    @JsonProperty("delay") boolean delay
) {}
