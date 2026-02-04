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

        // Tournament configuration: 16 players, 5 rounds
        List<PlayerConfig> players = new java.util.ArrayList<>();
        String[] strategies = {"naive-money", "random", "action-heavy", "naive-money"};
        for (int i = 1; i <= 16; i++) {
            players.add(new PlayerConfig("p" + i, "Player" + i, strategies[(i - 1) % 4]));
        }

        TournamentConfig config = new TournamentConfig(
            "perf-test",
            5,      // rounds
            16,     // games per player per round (increased from 4)
            100,    // max turns
            players
        );

        int totalGames = config.rounds() * config.gamesPerPlayer() * players.size() / 4;  // 4-player games

        System.out.println("Configuration:");
        System.out.println("  Players: " + players.size());
        System.out.println("  Rounds: " + config.rounds());
        System.out.println("  Games per player per round: " + config.gamesPerPlayer());
        System.out.println("  Total games: " + totalGames);
        System.out.println("  Network delay: " + MIN_DELAY_MS + "-" + MAX_DELAY_MS + "ms");
        System.out.println();

        // Run tournament with network delays
        System.out.println("Running tournament with " + MIN_DELAY_MS + "-" + MAX_DELAY_MS + "ms delays...");
        long start = System.nanoTime();
        runTournament(tempDir.resolve("tournament"), config, engineLoader, mockMessaging, true);
        long end = System.nanoTime();
        double seconds = (end - start) / 1_000_000_000.0;

        System.out.printf("%nCompleted %d games in %.2f seconds%n", totalGames, seconds);
        System.out.printf(">>> %.2f games/sec <<<%n", totalGames / seconds);
    }

    private void runTournament(Path dataDir, TournamentConfig config, EngineLoader engineLoader,
                               SimpMessagingTemplate messaging, boolean withDelay) throws Exception {
        CountDownLatch completionLatch = new CountDownLatch(1);

        TournamentExecutionService service = withDelay
            ? new TournamentExecutionService(dataDir.toString(), 64, messaging) {
                @Override
                protected TableExecutor createTableExecutor(EngineLoader loader) {
                    return new DelayedTableExecutor(loader, MIN_DELAY_MS, MAX_DELAY_MS);
                }
            }
            : new TournamentExecutionService(dataDir.toString(), 64, messaging);

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
