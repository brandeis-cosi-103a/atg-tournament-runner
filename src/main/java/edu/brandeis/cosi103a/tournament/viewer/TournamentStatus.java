package edu.brandeis.cosi103a.tournament.viewer;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Represents the current status of a running tournament.
 */
public record TournamentStatus(
    String id,
    State state,
    int currentRound,
    int totalRounds,
    int completedGames,
    int totalGames,
    Map<String, Double> ratings,
    String error,  // null when no error
    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<String> excludedPlayers  // null when none excluded
) {
    public enum State {
        QUEUED,
        RUNNING,
        COMPLETED,
        FAILED
    }

    /**
     * Creates a new tournament status in QUEUED state.
     */
    public static TournamentStatus queued(String id, int totalRounds, int totalGames) {
        return new TournamentStatus(id, State.QUEUED, 0, totalRounds, 0, totalGames, null, null, null);
    }

    /**
     * Creates a new tournament status in RUNNING state.
     */
    public static TournamentStatus running(String id, int currentRound, int totalRounds,
                                           int completedGames, int totalGames,
                                           Map<String, Double> ratings) {
        return new TournamentStatus(id, State.RUNNING, currentRound, totalRounds,
            completedGames, totalGames, ratings, null, null);
    }

    /**
     * Creates a running status that also reports excluded players from health checks.
     */
    public static TournamentStatus running(String id, int currentRound, int totalRounds,
                                           int completedGames, int totalGames,
                                           Map<String, Double> ratings,
                                           List<String> excludedPlayers) {
        return new TournamentStatus(id, State.RUNNING, currentRound, totalRounds,
            completedGames, totalGames, ratings, null, excludedPlayers);
    }

    /**
     * Creates a new tournament status in COMPLETED state.
     */
    public static TournamentStatus completed(String id, int totalRounds, int totalGames,
                                             Map<String, Double> ratings) {
        return new TournamentStatus(id, State.COMPLETED, totalRounds, totalRounds,
            totalGames, totalGames, ratings, null, null);
    }

    /**
     * Creates a new tournament status in FAILED state.
     */
    public static TournamentStatus failed(String id, int currentRound, int totalRounds,
                                          int completedGames, int totalGames, String error) {
        return new TournamentStatus(id, State.FAILED, currentRound, totalRounds,
            completedGames, totalGames, null, error, null);
    }
}
