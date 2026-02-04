package edu.brandeis.cosi103a.tournament.tape;

import de.gesundkrank.jskills.GameInfo;
import de.gesundkrank.jskills.Rating;
import edu.brandeis.cosi103a.tournament.runner.Placement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks TrueSkill ratings and placement points across multiple games.
 * Provides a stateful wrapper around TrueSkillRatingCalculator for both
 * batch processing (TapeBuilder) and live tournament scenarios.
 */
public class TrueSkillTracker {
    private final Map<String, Rating> ratings;
    private final Map<String, Integer> points;
    private final GameInfo gameInfo;

    /**
     * Create a new tracker with all players initialized to default ratings.
     *
     * @param playerIds list of all player IDs participating in the tournament
     * @param gameInfo  TrueSkill parameters
     */
    public TrueSkillTracker(List<String> playerIds, GameInfo gameInfo) {
        this.gameInfo = gameInfo;
        this.ratings = new HashMap<>();
        this.points = new HashMap<>();
        Rating defaultRating = gameInfo.getDefaultRating();
        for (String id : playerIds) {
            ratings.put(id, defaultRating);
            points.put(id, 0);
        }
    }

    /**
     * Process a single game's results, updating ratings and points.
     *
     * @param placements game results, highest score first
     */
    public void processGame(List<Placement> placements) {
        // Update TrueSkill ratings
        Map<String, Rating> updated = TrueSkillRatingCalculator.update(
            ratings, placements, gameInfo
        );
        ratings.putAll(updated);

        // Update placement points: N points for 1st, N-1 for 2nd, etc.
        int n = placements.size();
        int[] ranks = TrueSkillRatingCalculator.computeRanks(placements);
        for (int r = 0; r < placements.size(); r++) {
            String playerId = placements.get(r).playerId();
            int earnedPoints = n - ranks[r] + 1;
            points.merge(playerId, earnedPoints, Integer::sum);
        }
    }

    /**
     * Get current ratings for all players.
     *
     * @return immutable snapshot of current ratings
     */
    public Map<String, Rating> getCurrentRatings() {
        return new HashMap<>(ratings);
    }

    /**
     * Get current placement points for all players.
     *
     * @return immutable snapshot of current points
     */
    public Map<String, Integer> getCurrentPoints() {
        return new HashMap<>(points);
    }

    /**
     * Get the conservative rating for a specific player (mu - 3*sigma).
     *
     * @param playerId player to query
     * @return conservative rating estimate
     */
    public double getConservativeRating(String playerId) {
        Rating rating = ratings.get(playerId);
        return TrueSkillRatingCalculator.conservativeRating(rating);
    }
}
