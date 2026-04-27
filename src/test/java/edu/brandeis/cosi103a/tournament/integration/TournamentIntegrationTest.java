package edu.brandeis.cosi103a.tournament.integration;

import edu.brandeis.cosi103a.tournament.engine.EngineLoader;
import edu.brandeis.cosi103a.tournament.runner.PlayerConfig;
import edu.brandeis.cosi103a.tournament.runner.PlayerDiscoveryService;
import edu.brandeis.cosi103a.tournament.runner.TournamentConfig;
import edu.brandeis.cosi103a.tournament.viewer.TournamentExecutionService;
import edu.brandeis.cosi103a.tournament.viewer.TournamentStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

/**
 * Integration tests that run full tournaments through TournamentExecutionService.
 * Requires engine.jar in the project root (gitignored, not checked in).
 */
class TournamentIntegrationTest {

    private static final Path ENGINE_JAR = Path.of("engine.jar");
    private static final String ENGINE_CLASS = "edu.brandeis.cosi103a.engine.GameEngine";

    @BeforeAll
    static void checkEngine() {
        assumeTrue(Files.exists(ENGINE_JAR),
            "Skipping integration tests: engine.jar not found in project root");
    }

    /**
     * Runs a full tournament with N identical built-in players (the duplicate-id scenario).
     * This is the exact codepath that was broken: multiple instances of the same player
     * type get the same id, and the scheduling HashMap collapsed them into one entry.
     */
    @ParameterizedTest(name = "tournament with {0} identical players")
    @ValueSource(ints = {4, 5, 6, 7, 8})
    void fullTournamentWithDuplicatePlayers(int playerCount, @TempDir Path tempDir) throws Exception {
        // Build players the same way TournamentController does after the dedup fix
        List<PlayerConfig> players = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            String suffix = i == 0 ? "" : " " + (i + 1);
            String idSuffix = i == 0 ? "" : "-" + (i + 1);
            players.add(new PlayerConfig(
                "actionheavyplayer" + idSuffix,
                "ActionHeavyPlayer" + suffix,
                "classpath:edu.brandeis.cosi103a.tournament.player.ActionHeavyPlayer",
                false));
        }

        runTournamentToCompletion(tempDir, players, 2, 12);
    }

    /**
     * Runs a tournament with mixed player types — the typical student scenario.
     */
    @ParameterizedTest(name = "tournament with {0} mixed players")
    @ValueSource(ints = {4, 6, 8})
    void fullTournamentWithMixedPlayers(int playerCount, @TempDir Path tempDir) throws Exception {
        String[] types = {
            "classpath:edu.brandeis.cosi103a.tournament.player.NaiveBigMoneyPlayer",
            "classpath:edu.brandeis.cosi103a.tournament.player.ActionHeavyPlayer",
            "classpath:edu.brandeis.cosi103a.tournament.player.RandomPlayer",
        };

        List<PlayerConfig> players = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            players.add(new PlayerConfig(
                "player-" + i,
                "Player" + i,
                types[i % types.length],
                false));
        }

        runTournamentToCompletion(tempDir, players, 2, 12);
    }

    private void runTournamentToCompletion(Path tempDir, List<PlayerConfig> players,
                                            int rounds, int gamesPerPlayer) throws Exception {
        EngineLoader engineLoader = new EngineLoader(ENGINE_JAR.toAbsolutePath().toString(), ENGINE_CLASS);
        SimpMessagingTemplate mockMessaging = mock(SimpMessagingTemplate.class);
        TournamentExecutionService service = new TournamentExecutionService(
            tempDir.toString(), 8, 0, 0, mockMessaging, new PlayerDiscoveryService());

        TournamentConfig config = new TournamentConfig(
            "integration-test", rounds, gamesPerPlayer, 100, players);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<TournamentStatus> finalStatus = new AtomicReference<>();

        try {
            service.startTournament(config, engineLoader, status -> {
                if (status.state() == TournamentStatus.State.COMPLETED ||
                    status.state() == TournamentStatus.State.FAILED) {
                    finalStatus.set(status);
                    latch.countDown();
                }
            });

            assertTrue(latch.await(2, TimeUnit.MINUTES), "Tournament should complete within 2 minutes");

            TournamentStatus status = finalStatus.get();
            assertNotNull(status);
            assertEquals(TournamentStatus.State.COMPLETED, status.state(),
                "Tournament should complete successfully, but got: " + status.error());
            assertEquals(rounds, status.currentRound());
            assertTrue(status.completedGames() > 0);
            assertNull(status.error());
        } finally {
            service.shutdown();
        }
    }
}
