package edu.brandeis.cosi103a.tournament.runner;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A single player's placement in a game outcome.
 */
public record Placement(
    @JsonProperty("playerId") String playerId,
    @JsonProperty("score") int score,
    @JsonProperty("deck") List<String> deck
) {
    /**
     * Constructor without deck for backwards compatibility.
     */
    public Placement(String playerId, int score) {
        this(playerId, score, List.of());
    }
}
