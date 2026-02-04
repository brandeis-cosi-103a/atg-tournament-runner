package edu.brandeis.cosi103a.tournament.performance;

import edu.brandeis.cosi.atg.cards.Card;
import edu.brandeis.cosi.atg.player.Player;
import edu.brandeis.cosi103a.tournament.engine.EngineLoader;
import edu.brandeis.cosi103a.tournament.player.DelayedPlayerWrapper;
import edu.brandeis.cosi103a.tournament.runner.MatchResult;
import edu.brandeis.cosi103a.tournament.runner.PlayerConfig;
import edu.brandeis.cosi103a.tournament.runner.RoundGenerator;
import edu.brandeis.cosi103a.tournament.runner.TableExecutor;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Performance test harness to measure tournament execution time with local vs network-delayed players.
 *
 * This test compares:
 * - Baseline: Local players with no artificial delay
 * - Network simulation: Same players wrapped with 25-100ms delay per decision
 *
 * NOTE: This test is @Disabled by default because it requires a reference engine JAR.
 * To run it:
 * 1. Build the reference engine: cd ../atg-reference-impl/automation && mvn package
 * 2. Copy the JAR: cp automation/engine/target/engine-*.jar /tmp/engine.jar
 * 3. Enable the test by removing @Disabled
 * 4. Run: mvn test -Dtest=TournamentPerformanceTest
 *
 * For CI/local testing without a real engine, use the simplified version that measures
 * just the delay wrapper overhead by calling makeDecision directly.
 */
public class TournamentPerformanceTest {

    // Test configuration
    private static final int ROUNDS = 3;
    private static final int GAMES_PER_PLAYER = 4; // Reduced for faster testing
    private static final int NUM_RUNS = 3; // Multiple runs for averaging
    private static final int MIN_DELAY_MS = 25;
    private static final int MAX_DELAY_MS = 100;

    // Engine configuration - update these paths as needed
    private static final String ENGINE_JAR = "/tmp/engine.jar";
    private static final String ENGINE_CLASS = "edu.brandeis.cosi103a.engine.GameEngine";

    @Test
    @Disabled("Requires reference engine JAR - enable manually for performance testing")
    void measurePerformanceWithNetworkDelay() throws Exception {
        System.out.println("\n=== Tournament Performance Test ===\n");

        // Create player configs (4 players, mix of strategies)
        List<PlayerConfig> baseConfigs = List.of(
            new PlayerConfig("p1", "Player1", "naive-money"),
            new PlayerConfig("p2", "Player2", "random"),
            new PlayerConfig("p3", "Player3", "naive-money"),
            new PlayerConfig("p4", "Player4", "action-heavy")
        );

        // Validate configuration
        int numPlayers = baseConfigs.size();
        if ((numPlayers * GAMES_PER_PLAYER) % 4 != 0) {
            throw new IllegalArgumentException(
                "Invalid configuration: " + numPlayers + " * " + GAMES_PER_PLAYER +
                " must be divisible by 4");
        }

        int gamesPerRound = (numPlayers * GAMES_PER_PLAYER) / 4;
        int totalGames = ROUNDS * gamesPerRound;

        System.out.println("Configuration:");
        System.out.println("  Players: " + numPlayers);
        System.out.println("  Games per player per round: " + GAMES_PER_PLAYER);
        System.out.println("  Games per round: " + gamesPerRound);
        System.out.println("  Rounds: " + ROUNDS);
        System.out.println("  Total games: " + totalGames);
        System.out.println("  Runs per scenario: " + NUM_RUNS);
        System.out.println("  Engine JAR: " + ENGINE_JAR);
        System.out.println("  Engine class: " + ENGINE_CLASS);
        System.out.println();

        // Run multiple iterations for each scenario
        long[] baselineTimes = new long[NUM_RUNS];
        long[] delayedTimes = new long[NUM_RUNS];

        for (int run = 0; run < NUM_RUNS; run++) {
            System.out.println("Run " + (run + 1) + "/" + NUM_RUNS + "...");

            // Scenario 1: Baseline (local players)
            System.out.print("  Baseline... ");
            EngineLoader baselineLoader = new EngineLoader(ENGINE_JAR, ENGINE_CLASS);
            TableExecutor baselineExecutor = new TableExecutor(baselineLoader);

            long baselineStart = System.nanoTime();
            runTournament(baselineExecutor, baseConfigs, ROUNDS, gamesPerRound);
            long baselineEnd = System.nanoTime();
            baselineTimes[run] = baselineEnd - baselineStart;

            System.out.println("done (" + formatNanos(baselineTimes[run]) + " s)");

            // Scenario 2: Network delay (25-100ms)
            System.out.print("  Network-delayed... ");
            EngineLoader delayedLoader = new EngineLoader(ENGINE_JAR, ENGINE_CLASS);
            TestTableExecutor delayedExecutor = new TestTableExecutor(
                delayedLoader, MIN_DELAY_MS, MAX_DELAY_MS);

            long delayedStart = System.nanoTime();
            runTournament(delayedExecutor, baseConfigs, ROUNDS, gamesPerRound);
            long delayedEnd = System.nanoTime();
            delayedTimes[run] = delayedEnd - delayedStart;

            System.out.println("done (" + formatNanos(delayedTimes[run]) + " s)");
        }

        // Calculate averages
        long avgBaseline = average(baselineTimes);
        long avgDelayed = average(delayedTimes);

        // Print results
        printResults(avgBaseline, avgDelayed, totalGames);
    }

    /**
     * Simplified performance test that doesn't require a real engine JAR.
     * Measures the overhead of the DelayedPlayerWrapper itself.
     */
    @Test
    void measureDelayWrapperOverhead() {
        System.out.println("\n=== DelayedPlayerWrapper Overhead Test ===\n");

        int numCalls = 100;
        int numRuns = 5;

        System.out.println("Configuration:");
        System.out.println("  Decision calls per run: " + numCalls);
        System.out.println("  Runs per scenario: " + numRuns);
        System.out.println("  Delay range: " + MIN_DELAY_MS + "-" + MAX_DELAY_MS + "ms");
        System.out.println();

        long[] baselineTimes = new long[numRuns];
        long[] delayedTimes = new long[numRuns];

        // Create test players
        edu.brandeis.cosi103a.tournament.player.NaiveBigMoneyPlayer basePlayer =
            new edu.brandeis.cosi103a.tournament.player.NaiveBigMoneyPlayer("TestPlayer");
        DelayedPlayerWrapper delayedPlayer =
            new DelayedPlayerWrapper(basePlayer, MIN_DELAY_MS, MAX_DELAY_MS);

        for (int run = 0; run < numRuns; run++) {
            System.out.println("Run " + (run + 1) + "/" + numRuns + "...");

            // Measure getName() calls (no delay expected)
            long start = System.nanoTime();
            for (int i = 0; i < numCalls; i++) {
                basePlayer.getName();
            }
            long end = System.nanoTime();
            baselineTimes[run] = end - start;
            System.out.println("  Baseline: " + formatNanos(baselineTimes[run]) + " s");

            // Measure getName() calls through wrapper (still no delay expected)
            start = System.nanoTime();
            for (int i = 0; i < numCalls; i++) {
                delayedPlayer.getName();
            }
            end = System.nanoTime();
            delayedTimes[run] = end - start;
            System.out.println("  Wrapped:  " + formatNanos(delayedTimes[run]) + " s");
        }

        long avgBaseline = average(baselineTimes);
        long avgDelayed = average(delayedTimes);

        System.out.println();
        System.out.println("=== Results (averaged over " + numRuns + " runs) ===");
        System.out.println();
        System.out.printf("Baseline:    %.6f s for %d calls (%.6f ms/call)%n",
            avgBaseline / 1_000_000_000.0, numCalls,
            (avgBaseline / 1_000_000.0) / numCalls);
        System.out.printf("Wrapped:     %.6f s for %d calls (%.6f ms/call)%n",
            avgDelayed / 1_000_000_000.0, numCalls,
            (avgDelayed / 1_000_000.0) / numCalls);
        System.out.printf("Overhead:    %.6f ms per call%n",
            ((avgDelayed - avgBaseline) / 1_000_000.0) / numCalls);
        System.out.println();
    }

    /**
     * Runs a tournament with the given configuration.
     */
    private void runTournament(TableExecutor executor, List<PlayerConfig> configs,
                               int rounds, int gamesPerRound) throws Exception {
        for (int r = 0; r < rounds; r++) {
            List<Card.Type> kingdom = RoundGenerator.selectKingdomCards();
            List<List<PlayerConfig>> tables = RoundGenerator.generateBalancedGames(
                configs, GAMES_PER_PLAYER);

            // Run games sequentially (simpler for measurement)
            for (List<PlayerConfig> table : tables) {
                MatchResult result = executor.executeTable(1, table, kingdom, 1, 100);
                // Result is computed but not used - we're just measuring execution time
            }
        }
    }

    /**
     * Test-specific TableExecutor that wraps all players with DelayedPlayerWrapper.
     */
    private static class TestTableExecutor extends TableExecutor {
        private final int minDelayMs;
        private final int maxDelayMs;

        TestTableExecutor(EngineLoader loader, int minDelayMs, int maxDelayMs) {
            super(loader);
            this.minDelayMs = minDelayMs;
            this.maxDelayMs = maxDelayMs;
        }

        @Override
        protected Player createPlayer(PlayerConfig config) {
            Player base = super.createPlayer(config);
            return new DelayedPlayerWrapper(base, minDelayMs, maxDelayMs);
        }
    }

    /**
     * Prints performance comparison results in a formatted table.
     */
    private void printResults(long avgBaselineNanos, long avgDelayedNanos, int totalGames) {
        double baselineSeconds = avgBaselineNanos / 1_000_000_000.0;
        double delayedSeconds = avgDelayedNanos / 1_000_000_000.0;
        double gamesPerSecBaseline = totalGames / baselineSeconds;
        double gamesPerSecDelayed = totalGames / delayedSeconds;
        double slowdownFactor = delayedSeconds / baselineSeconds;

        System.out.println("\n=== Results (averaged over " + NUM_RUNS + " runs) ===\n");
        System.out.printf("%-22s | %10s | %11s | %12s%n",
            "Scenario", "Time (s)", "Games/sec", "Total Games");
        System.out.println("----------------------|------------|-------------|-------------");
        System.out.printf("%-22s | %10.2f | %11.2f | %12d%n",
            "Baseline (local)", baselineSeconds, gamesPerSecBaseline, totalGames);
        System.out.printf("%-22s | %10.2f | %11.2f | %12d%n",
            "Network (" + MIN_DELAY_MS + "-" + MAX_DELAY_MS + "ms)",
            delayedSeconds, gamesPerSecDelayed, totalGames);
        System.out.println();
        System.out.printf("Slowdown factor: %.2fx%n", slowdownFactor);
        System.out.println();
    }

    /**
     * Calculates the average of an array of longs.
     */
    private long average(long[] values) {
        long sum = 0;
        for (long value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    /**
     * Formats nanoseconds as seconds with 2 decimal places.
     */
    private String formatNanos(long nanos) {
        return String.format("%.2f", nanos / 1_000_000_000.0);
    }
}
