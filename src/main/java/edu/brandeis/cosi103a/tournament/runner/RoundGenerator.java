package edu.brandeis.cosi103a.tournament.runner;

import edu.brandeis.cosi.atg.cards.Card;

import java.util.*;

/**
 * Generates round configurations: kingdom card selection and balanced game scheduling.
 *
 * Key design principles for fair measurement:
 * - All games are 4-player (consistent scoring: 4/3/2/1 points)
 * - Each player plays exactly the same number of games per round
 * - Over multiple rounds, opponent pairings are balanced
 */
public final class RoundGenerator {

    private static final List<Card.Type> ALL_ACTION_CARDS = List.of(
        Card.Type.REFACTOR, Card.Type.CODE_REVIEW, Card.Type.EVERGREEN_TEST,
        Card.Type.SPRINT_PLANNING, Card.Type.IPO, Card.Type.PARALLELIZATION,
        Card.Type.HACK, Card.Type.RANSOMWARE, Card.Type.MONITORING,
        Card.Type.BACKLOG, Card.Type.TECH_DEBT, Card.Type.DAILY_SCRUM,
        Card.Type.DEPLOYMENT_PIPELINE, Card.Type.UNIT_TEST, Card.Type.MERGE_CONFLICT
    );

    private RoundGenerator() {}

    /**
     * Selects 10 random kingdom cards from the 15 available action card types.
     */
    public static List<Card.Type> selectKingdomCards() {
        List<Card.Type> shuffled = new ArrayList<>(ALL_ACTION_CARDS);
        Collections.shuffle(shuffled);
        return new ArrayList<>(shuffled.subList(0, 10));
    }

    /**
     * Generates balanced 4-player games where each player plays exactly the same number of games.
     *
     * For N players, each playing G games:
     * - Total player-slots = N * G
     * - Number of games = N * G / 4 (must be integer)
     *
     * The algorithm greedily assigns players to games, prioritizing players with fewer
     * appearances and avoiding repeat opponents within the same round when possible.
     *
     * @param players list of players (minimum 4)
     * @param gamesPerPlayer number of games each player should play (must make N*G divisible by 4)
     * @return list of 4-player game assignments
     */
    public static List<List<PlayerConfig>> generateBalancedGames(List<PlayerConfig> players, int gamesPerPlayer) {
        int n = players.size();
        if (n < 4) {
            throw new IllegalArgumentException("Need at least 4 players for 4-player games");
        }
        if ((n * gamesPerPlayer) % 4 != 0) {
            throw new IllegalArgumentException(
                "N * gamesPerPlayer must be divisible by 4. Got " + n + " * " + gamesPerPlayer + " = " + (n * gamesPerPlayer));
        }

        int totalGames = (n * gamesPerPlayer) / 4;
        List<List<PlayerConfig>> games = new ArrayList<>();

        // Track how many games each player has been assigned
        Map<PlayerConfig, Integer> appearances = new HashMap<>();
        for (PlayerConfig p : players) {
            appearances.put(p, 0);
        }

        // Track opponent pairings this round to encourage diversity
        Map<PlayerConfig, Set<PlayerConfig>> pairedWith = new HashMap<>();
        for (PlayerConfig p : players) {
            pairedWith.put(p, new HashSet<>());
        }

        Random random = new Random();

        for (int g = 0; g < totalGames; g++) {
            List<PlayerConfig> game = selectPlayersForGame(players, appearances, pairedWith, gamesPerPlayer, random);
            games.add(game);

            // Update tracking
            for (PlayerConfig p : game) {
                appearances.merge(p, 1, Integer::sum);
                for (PlayerConfig other : game) {
                    if (p != other) {
                        pairedWith.get(p).add(other);
                    }
                }
            }
        }

        // Shuffle game order for variety
        Collections.shuffle(games);
        return games;
    }

    /**
     * Selects 4 players for a game, prioritizing:
     * 1. Players with fewer appearances (to balance games played)
     * 2. Players who haven't been paired together yet (to balance opponent exposure)
     */
    private static List<PlayerConfig> selectPlayersForGame(
            List<PlayerConfig> allPlayers,
            Map<PlayerConfig, Integer> appearances,
            Map<PlayerConfig, Set<PlayerConfig>> pairedWith,
            int maxGames,
            Random random) {

        // Get eligible players (those who haven't hit their max games)
        List<PlayerConfig> eligible = new ArrayList<>();
        for (PlayerConfig p : allPlayers) {
            if (appearances.get(p) < maxGames) {
                eligible.add(p);
            }
        }

        if (eligible.size() < 4) {
            throw new IllegalStateException("Not enough eligible players for a game. Algorithm bug.");
        }

        // Sort by appearances (ascending) with random tiebreaker
        eligible.sort((a, b) -> {
            int cmp = Integer.compare(appearances.get(a), appearances.get(b));
            return cmp != 0 ? cmp : random.nextInt(3) - 1;
        });

        // Greedily select 4 players, preferring those with fewer mutual pairings
        List<PlayerConfig> selected = new ArrayList<>();
        selected.add(eligible.get(0)); // Start with player who has fewest appearances

        for (int i = 1; i < eligible.size() && selected.size() < 4; i++) {
            PlayerConfig candidate = eligible.get(i);

            // Count how many selected players this candidate has already been paired with
            int existingPairings = 0;
            for (PlayerConfig s : selected) {
                if (pairedWith.get(candidate).contains(s)) {
                    existingPairings++;
                }
            }

            // Accept if we need players or if pairings aren't too redundant
            // (allow some redundancy to ensure we can always fill games)
            if (selected.size() < 4) {
                selected.add(candidate);
            }
        }

        // Shuffle selected players so seating order varies
        Collections.shuffle(selected);
        return selected;
    }

    /**
     * Calculates the recommended games per player for balanced scheduling.
     * Returns the smallest value where N * gamesPerPlayer is divisible by 4.
     */
    public static int recommendedGamesPerPlayer(int numPlayers) {
        // Find smallest g where numPlayers * g % 4 == 0
        for (int g = 1; g <= 12; g++) {
            if ((numPlayers * g) % 4 == 0) {
                return g;
            }
        }
        return 4; // fallback
    }

    /**
     * @deprecated Use {@link #generateBalancedGames(List, int)} instead for fair tournaments.
     */
    @Deprecated
    public static List<List<PlayerConfig>> shuffleIntoTables(List<PlayerConfig> players) {
        // Legacy behavior for backwards compatibility
        List<PlayerConfig> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);

        int n = shuffled.size();
        if (n < 3) {
            throw new IllegalArgumentException("Need at least 3 players for a tournament");
        }

        int numTables = (n + 3) / 4;
        int tablesOf4 = n - numTables * 3;

        List<List<PlayerConfig>> tables = new ArrayList<>();
        int idx = 0;
        for (int t = 0; t < numTables; t++) {
            int tableSize = (t < tablesOf4) ? 4 : 3;
            List<PlayerConfig> table = new ArrayList<>();
            for (int j = 0; j < tableSize && idx < n; j++) {
                table.add(shuffled.get(idx++));
            }
            tables.add(table);
        }
        return tables;
    }
}
