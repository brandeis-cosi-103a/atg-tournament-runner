package edu.brandeis.cosi103a.tournament.runner;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single player's placement in a game outcome.
 */
public record Placement(
    @JsonProperty("playerId") String playerId,
    @JsonProperty("score") int score
) {}
