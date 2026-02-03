package edu.brandeis.cosi103a.tournament.runner;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Top-level tournament configuration parsed from CLI args.
 */
public record TournamentConfig(
    @JsonProperty("name") String name,
    @JsonProperty("rounds") int rounds,
    @JsonProperty("gamesPerTable") int gamesPerTable,
    @JsonProperty("maxTurns") int maxTurns,
    @JsonProperty("players") List<PlayerConfig> players
) {

    /**
     * Convenience constructor with default maxTurns of 100.
     */
    public TournamentConfig(String name, int rounds, int gamesPerTable, List<PlayerConfig> players) {
        this(name, rounds, gamesPerTable, 100, players);
    }
}
