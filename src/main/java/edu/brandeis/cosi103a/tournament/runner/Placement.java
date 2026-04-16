package edu.brandeis.cosi103a.tournament.runner;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.brandeis.cosi103a.tournament.player.TimingStats;

import java.util.List;

/**
 * A single player's placement in a game outcome.
 */
public record Placement(
    @JsonProperty("playerId") String playerId,
    @JsonProperty("score") int score,
    @JsonProperty("deck") List<String> deck,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("timingStats") TimingStats timingStats
) {}
