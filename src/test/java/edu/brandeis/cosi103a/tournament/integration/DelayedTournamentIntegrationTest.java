package edu.brandeis.cosi103a.tournament.integration;

import edu.brandeis.cosi103a.tournament.engine.EngineLoader;
import edu.brandeis.cosi103a.tournament.player.DelayedPlayerWrapper;
import edu.brandeis.cosi103a.tournament.runner.PlayerConfig;
import edu.brandeis.cosi103a.tournament.runner.TableExecutor;
import edu.brandeis.cosi103a.tournament.runner.TournamentConfig;
import edu.brandeis.cosi103a.tournament.viewer.TournamentExecutionService;
import edu.brandeis.cosi103a.tournament.viewer.TournamentStatus;
import edu.brandeis.cosi.atg.player.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;

/**
 * Integration test to measure real tournament performance with network-delayed players.
 *
 * This test runs actual tournaments through TournamentExecutionService to measure
 * the impact of network delays on tournament execution time with real parallel
 * execution, TrueSkill updates, and all production code paths.
 */
public class DelayedTournamentIntegrationTest {

    private static final String ENGINE_JAR = "/tmp/engine.jar";
    private static final String ENGINE_CLASS = "edu.brandeis.cosi103a.engine.GameEngine";

    private static final int MIN_DELAY_MS = 2;
    private static final int MAX_DELAY_MS = 5;

    /**
     * Custom TableExecutor that wraps all players with DelayedPlayerWrapper.
     */
    private static class DelayedTableExecutor extends TableExecutor {
        private final int minDelayMs;
        private final int maxDelayMs;

        DelayedTableExecutor(EngineLoader loader, int minDelayMs, int maxDelayMs) {
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

    @Test
    // @Disabled("Requires reference engine JAR - enable manually for performance testing")
    void measureRealTournamentWithNetworkDelay(@TempDir Path tempDir) throws Exception {
        System.out.println("\n=== Real Tournament Performance Test ===\n");

        EngineLoader engineLoader = new EngineLoader(ENGINE_JAR, ENGINE_CLASS);
        SimpMessagingTemplate mockMessaging = mock(SimpMessagingTemplate.class);

        // Tournament configuration: 4 players, 3 rounds
        List<PlayerConfig> players = List.of(
            new PlayerConfig("p1", "Player1", "naive-money"),
            new PlayerConfig("p2", "Player2", "random"),
            new PlayerConfig("p3", "Player3", "naive-money"),
            new PlayerConfig("p4", "Player4", "action-heavy")
        );

        TournamentConfig config = new TournamentConfig(
            "perf-test",
            3,      // rounds
            4,      // games per player per round
            100,    // max turns
            players
        );

        int totalGames = config.rounds() * players.size();  // 3 * 4 = 12 games

        System.out.println("Configuration:");
        System.out.println("  Players: " + players.size());
        System.out.println("  Rounds: " + config.rounds());
        System.out.println("  Games per player per round: " + config.gamesPerPlayer());
        System.out.println("  Total games: " + totalGames);
        System.out.println("  Network delay: " + MIN_DELAY_MS + "-" + MAX_DELAY_MS + "ms");
        System.out.println();

        // Run baseline (no delays)
        System.out.println("Running baseline tournament (no delays)...");
        long baselineStart = System.nanoTime();
        runTournament(tempDir.resolve("baseline"), config, engineLoader, mockMessaging, false);
        long baselineEnd = System.nanoTime();
        double baselineSeconds = (baselineEnd - baselineStart) / 1_000_000_000.0;
        System.out.printf("  Completed in %.2f seconds%n%n", baselineSeconds);

        // Run with network delays
        System.out.println("Running tournament with network delays (" + MIN_DELAY_MS + "-" + MAX_DELAY_MS + "ms)...");
        long delayedStart = System.nanoTime();
        runTournament(tempDir.resolve("delayed"), config, engineLoader, mockMessaging, true);
        long delayedEnd = System.nanoTime();
        double delayedSeconds = (delayedEnd - delayedStart) / 1_000_000_000.0;
        System.out.printf("  Completed in %.2f seconds%n%n", delayedSeconds);

        // Print comparison
        System.out.println("=== Results ===\n");
        System.out.printf("Baseline (no delays):   %.2f seconds (%.2f games/sec)%n",
            baselineSeconds, totalGames / baselineSeconds);
        System.out.printf("With delays (2-5ms):    %.2f seconds (%.2f games/sec)%n",
            delayedSeconds, totalGames / delayedSeconds);
        System.out.printf("Slowdown factor:        %.2fx%n", delayedSeconds / baselineSeconds);
        System.out.println();

        System.out.println("This test measures REAL tournament execution with:");
        System.out.println("  - Parallel game execution via thread pool");
        System.out.println("  - CompletionService result collection");
        System.out.println("  - Live TrueSkill rating updates");
        System.out.println("  - All production code paths");
    }

    private void runTournament(Path dataDir, TournamentConfig config, EngineLoader engineLoader,
                               SimpMessagingTemplate messaging, boolean withDelay) throws Exception {
        CountDownLatch completionLatch = new CountDownLatch(1);

        TournamentExecutionService service = withDelay
            ? new TournamentExecutionService(dataDir.toString(), messaging) {
                @Override
                protected TableExecutor createTableExecutor(EngineLoader loader) {
                    return new DelayedTableExecutor(loader, MIN_DELAY_MS, MAX_DELAY_MS);
                }
            }
            : new TournamentExecutionService(dataDir.toString(), messaging);

        try {
            service.startTournament(config, engineLoader, status -> {
                if (status.state() == TournamentStatus.State.COMPLETED ||
                    status.state() == TournamentStatus.State.FAILED) {
                    completionLatch.countDown();
                }
            });

            // Wait for tournament to complete (5 minutes max)
            boolean completed = completionLatch.await(5, TimeUnit.MINUTES);
            if (!completed) {
                throw new RuntimeException("Tournament did not complete within timeout");
            }
        } finally {
            service.shutdown();
        }
    }
}
