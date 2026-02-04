package edu.brandeis.cosi103a.tournament.viewer;

import java.util.Map;
import java.util.Optional;

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
    Optional<String> error
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
        return new TournamentStatus(id, State.QUEUED, 0, totalRounds, 0, totalGames, null, Optional.empty());
    }

    /**
     * Creates a new tournament status in RUNNING state.
     */
    public static TournamentStatus running(String id, int currentRound, int totalRounds, int completedGames, int totalGames, Map<String, Double> ratings) {
        return new TournamentStatus(id, State.RUNNING, currentRound, totalRounds, completedGames, totalGames, ratings, Optional.empty());
    }

    /**
     * Creates a new tournament status in COMPLETED state.
     */
    public static TournamentStatus completed(String id, int totalRounds, int totalGames, Map<String, Double> ratings) {
        return new TournamentStatus(id, State.COMPLETED, totalRounds, totalRounds, totalGames, totalGames, ratings, Optional.empty());
    }

    /**
     * Creates a new tournament status in FAILED state.
     */
    public static TournamentStatus failed(String id, int currentRound, int totalRounds, int completedGames, int totalGames, String error) {
        return new TournamentStatus(id, State.FAILED, currentRound, totalRounds, completedGames, totalGames, null, Optional.of(error));
    }
}
