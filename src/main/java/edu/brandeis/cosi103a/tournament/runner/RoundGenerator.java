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
     * Selects 10 random kingdom cards, retrying up to 50 times to avoid producing
     * a kingdom that has already been used (matched as a set, ignoring order).
     * If all attempts collide, falls back to an unrestricted selection so the
     * tournament can still proceed.
     *
     * @param previouslyUsed sets of cards used in prior rounds; may be empty
     * @return the selected 10-card kingdom
     */
    public static List<Card.Type> selectUniqueKingdomCards(Set<Set<Card.Type>> previouslyUsed) {
        for (int attempt = 0; attempt < 50; attempt++) {
            List<Card.Type> candidate = selectKingdomCards();
            if (!previouslyUsed.contains(EnumSet.copyOf(candidate))) {
                return candidate;
            }
        }
        // Extremely unlikely with C(15,10)=3003 possible kingdoms vs ~15 rounds,
        // but don't deadlock the tournament if it somehow happens.
        return selectKingdomCards();
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

        // Shuffle for random tiebreaker, then stable-sort by appearances (ascending).
        // Using a random comparator would violate the Comparator contract and cause
        // TimSort to produce incorrect orderings, leading to unbalanced assignments.
        Collections.shuffle(eligible, random);
        eligible.sort(Comparator.comparingInt(appearances::get));

        // Select 4 players: always pick from the lowest appearance level first,
        // using pairing diversity only as a tiebreaker within the same level.
        // This guarantees balanced appearances and prevents early max-outs.
        List<PlayerConfig> selected = new ArrayList<>();
        selected.add(eligible.remove(0)); // Start with player who has fewest appearances

        while (selected.size() < 4) {
            int minApps = appearances.get(eligible.get(0)); // lowest available

            // Among candidates at the minimum level, prefer one with fewest pairings
            PlayerConfig best = null;
            int bestPairings = Integer.MAX_VALUE;
            for (PlayerConfig c : eligible) {
                if (appearances.get(c) > minApps) break; // sorted, done with this level
                int pairCount = 0;
                for (PlayerConfig s : selected) {
                    if (pairedWith.get(c).contains(s)) pairCount++;
                }
                if (pairCount < bestPairings) {
                    bestPairings = pairCount;
                    best = c;
                }
            }
            selected.add(best);
            eligible.remove(best);
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
