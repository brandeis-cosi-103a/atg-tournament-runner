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
     * Generates balanced 4-player games where each player plays approximately the same number of games.
     *
     * For N players requesting G games each:
     * - If N * G is divisible by 4, each player plays exactly G games
     * - Otherwise, adjusts to the nearest valid configuration (within ±1 game per player)
     *
     * The algorithm greedily assigns players to games, prioritizing players with fewer
     * appearances and avoiding repeat opponents within the same round when possible.
     *
     * @param players list of players (minimum 4)
     * @param gamesPerPlayer target number of games each player should play
     * @return list of 4-player game assignments
     */
    public static List<List<PlayerConfig>> generateBalancedGames(List<PlayerConfig> players, int gamesPerPlayer) {
        int n = players.size();
        if (n < 4) {
            throw new IllegalArgumentException("Need at least 4 players for 4-player games");
        }

        // Adjust gamesPerPlayer to nearest valid value if needed
        int adjustedGamesPerPlayer = adjustGamesPerPlayer(n, gamesPerPlayer);
        int totalGames = (n * adjustedGamesPerPlayer) / 4;
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
            List<PlayerConfig> game = selectPlayersForGame(players, appearances, pairedWith, adjustedGamesPerPlayer, random);
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

        // First pass: try to add players with no existing pairings
        for (int i = 1; i < eligible.size() && selected.size() < 4; i++) {
            PlayerConfig candidate = eligible.get(i);

            // Count how many selected players this candidate has already been paired with
            int existingPairings = 0;
            for (PlayerConfig s : selected) {
                if (pairedWith.get(candidate).contains(s)) {
                    existingPairings++;
                }
            }

            // Prefer candidates who haven't played with any selected players yet
            if (existingPairings == 0) {
                selected.add(candidate);
            }
        }

        // Second pass: if we still need players, add anyone available
        // (allows some pairing redundancy to ensure we can always fill games)
        if (selected.size() < 4) {
            for (int i = 1; i < eligible.size() && selected.size() < 4; i++) {
                PlayerConfig candidate = eligible.get(i);
                if (!selected.contains(candidate)) {
                    selected.add(candidate);
                }
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
     * Adjusts gamesPerPlayer to the nearest valid value where N * G is divisible by 4.
     * Rounds to nearest valid value, preferring down when equidistant.
     *
     * For N players, valid G values are multiples of (4 / gcd(N, 4)).
     *
     * @param numPlayers number of players
     * @param targetGamesPerPlayer requested games per player
     * @return adjusted games per player that makes N * G divisible by 4
     */
    public static int adjustGamesPerPlayer(int numPlayers, int targetGamesPerPlayer) {
        // Valid G must be a multiple of step = 4 / gcd(N, 4)
        int gcd = gcd(numPlayers, 4);
        int step = 4 / gcd;

        // Round to nearest multiple of step (prefer down)
        int adjusted = (targetGamesPerPlayer / step) * step;
        return Math.max(adjusted, step); // ensure at least one valid value
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
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
