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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
     * Compute 1-based ranks from placements, breaking ties randomly.
     * Placements can be in any order - this method sorts by score descending.
     *
     * JSkills fails to converge when players have tied ranks, so we break ties
     * using a deterministic pseudorandom shuffle based on the game's player IDs
     * and scores. This ensures reproducibility while avoiding systematic bias.
     */
    static int[] computeRanks(List<Placement> placements) {
        // Create seed from game data for reproducible randomness
        long seed = computeGameSeed(placements);
        Random random = new Random(seed);

        // Create index list sorted by score descending
        List<Integer> sortedByScore = new ArrayList<>();
        for (int i = 0; i < placements.size(); i++) {
            sortedByScore.add(i);
        }
        sortedByScore.sort((a, b) -> Integer.compare(
            placements.get(b).score(), placements.get(a).score()));

        // Build list of indices grouped by score, then shuffle within each group
        List<Integer> orderedIndices = new ArrayList<>();
        int i = 0;
        while (i < sortedByScore.size()) {
            int idx = sortedByScore.get(i);
            int score = placements.get(idx).score();
            List<Integer> tiedIndices = new ArrayList<>();
            while (i < sortedByScore.size() &&
                   placements.get(sortedByScore.get(i)).score() == score) {
                tiedIndices.add(sortedByScore.get(i));
                i++;
            }
            // Shuffle tied players randomly
            Collections.shuffle(tiedIndices, random);
            orderedIndices.addAll(tiedIndices);
        }

        // Assign unique ranks based on shuffled order
        int[] ranks = new int[placements.size()];
        for (int r = 0; r < orderedIndices.size(); r++) {
            ranks[orderedIndices.get(r)] = r + 1;  // 1-based ranks
        }
        return ranks;
    }

    /**
     * Compute a deterministic seed from game data for reproducible tie-breaking.
     * Uses player IDs and scores to ensure same game always breaks ties the same way.
     */
    private static long computeGameSeed(List<Placement> placements) {
        long seed = 0;
        for (Placement p : placements) {
            seed = seed * 31 + p.playerId().hashCode();
            seed = seed * 31 + p.score();
        }
        return seed;
    }
}
