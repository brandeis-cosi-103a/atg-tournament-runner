package edu.brandeis.cosi103a.tournament.runner;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Top-level tournament configuration parsed from CLI args.
 */
public record TournamentConfig(
    @JsonProperty("name") String name,
    @JsonProperty("rounds") int rounds,
    @JsonProperty("gamesPerPlayer") int gamesPerPlayer,
    @JsonProperty("maxTurns") int maxTurns,
    @JsonProperty("players") List<PlayerConfig> players
) {

    /**
     * Convenience constructor with default maxTurns of 100 and auto-calculated gamesPerPlayer.
     */
    public TournamentConfig(String name, int rounds, int gamesPerPlayer, List<PlayerConfig> players) {
        this(name, rounds, gamesPerPlayer, 100, players);
    }
}
