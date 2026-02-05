package edu.brandeis.cosi103a.tournament.tape;

import de.gesundkrank.jskills.GameInfo;
import de.gesundkrank.jskills.IPlayer;
import de.gesundkrank.jskills.ITeam;
import de.gesundkrank.jskills.Player;
import de.gesundkrank.jskills.Rating;
import de.gesundkrank.jskills.Team;
import de.gesundkrank.jskills.TrueSkillCalculator;
import edu.brandeis.cosi103a.tournament.runner.Placement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Multiplayer TrueSkill rating calculator using JSkills.
 * Each player is modeled as a 1-person team for free-for-all games.
 * The displayed rating is the conservative estimate: mu - 3*sigma.
 */
public final class TrueSkillRatingCalculator {

    private TrueSkillRatingCalculator() {}

    // Track convergence failures for logging
    private static int convergenceFailures = 0;

    /**
     * Update ratings after a single game.
     *
     * @param ratings    current (mu, sigma) for all players
     * @param placements game results, highest score first
     * @param gameInfo   TrueSkill parameters
     * @return updated ratings for all players (non-participants unchanged)
     */
    public static Map<String, Rating> update(
            Map<String, Rating> ratings,
            List<Placement> placements,
            GameInfo gameInfo) {

        Map<String, Rating> result = new HashMap<>(ratings);

        // Build JSkills player/team objects
        Map<String, Player<String>> jPlayers = new HashMap<>();
        List<ITeam> teams = new ArrayList<>();
        for (Placement p : placements) {
            Player<String> jp = new Player<>(p.playerId());
            jPlayers.put(p.playerId(), jp);
            Rating current = ratings.get(p.playerId());
            teams.add(new Team(jp, current));
        }

        // Compute ranks from scores (1-based, ties get same rank)
        int[] ranks = computeRanks(placements);

        // Calculate new ratings (may fail to converge on edge cases)
        try {
            Map<IPlayer, Rating> newRatings = TrueSkillCalculator.calculateNewRatings(
                    gameInfo, teams, ranks);

            // Map back to our structure
            for (Placement p : placements) {
                Player<String> jp = jPlayers.get(p.playerId());
                result.put(p.playerId(), newRatings.get(jp));
            }
        } catch (RuntimeException e) {
            // JSkills can fail to converge on certain game configurations
            // Keep existing ratings and continue
            convergenceFailures++;
            if (convergenceFailures <= 5) {
                System.err.println("Warning: TrueSkill failed to converge, keeping existing ratings");
            } else if (convergenceFailures == 6) {
                System.err.println("Warning: Suppressing further TrueSkill convergence warnings...");
            }
        }

        return result;
    }

    /**
     * Get the number of games where TrueSkill failed to converge.
     */
    public static int getConvergenceFailures() {
        return convergenceFailures;
    }

    /**
     * Reset the convergence failure counter.
     */
    public static void resetConvergenceFailures() {
        convergenceFailures = 0;
    }

    /**
     * Get the conservative display rating: mu - 3*sigma.
     */
    public static double conservativeRating(Rating rating) {
        return rating.getMean() - 3.0 * rating.getStandardDeviation();
    }

    /**
     * Compute 1-based ranks from placements, preserving ties.
     * Players with the same score receive the same rank.
     * Placements can be in any order - this method sorts by score descending.
     */
    static int[] computeRanks(List<Placement> placements) {
        // Create index list sorted by score descending
        List<Integer> sortedByScore = new ArrayList<>();
        for (int i = 0; i < placements.size(); i++) {
            sortedByScore.add(i);
        }
        sortedByScore.sort((a, b) -> Integer.compare(
            placements.get(b).score(), placements.get(a).score()));

        // Assign ranks, with ties sharing the same rank
        int[] ranks = new int[placements.size()];
        int currentRank = 1;
        for (int i = 0; i < sortedByScore.size(); i++) {
            int idx = sortedByScore.get(i);
            if (i > 0) {
                int prevIdx = sortedByScore.get(i - 1);
                if (placements.get(idx).score() < placements.get(prevIdx).score()) {
                    currentRank = i + 1;
                }
            }
            ranks[idx] = currentRank;
        }
        return ranks;
    }
}
