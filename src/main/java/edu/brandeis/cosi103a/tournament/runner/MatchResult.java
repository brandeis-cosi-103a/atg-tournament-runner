package edu.brandeis.cosi103a.tournament.runner;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Result of all games played at a single table in a round.
 */
public record MatchResult(
    @JsonProperty("tableNumber") int tableNumber,
    @JsonProperty("playerIds") List<String> playerIds,
    @JsonProperty("outcomes") List<GameOutcome> outcomes
) {}
