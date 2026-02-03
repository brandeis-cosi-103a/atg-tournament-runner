package edu.brandeis.cosi103a.tournament.viewer;

import edu.brandeis.cosi103a.tournament.engine.EngineLoader;
import edu.brandeis.cosi103a.tournament.runner.PlayerConfig;
import edu.brandeis.cosi103a.tournament.runner.TournamentConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

/**
 * Tests for TournamentExecutionService.
 *
 * NOTE: Integration tests require a GameEngine implementation JAR. They will be skipped if not available.
 */
class TournamentExecutionServiceTest {

    /**
     * Checks if we have a GameEngine available for testing.
     * Returns the path if available, otherwise skips the test.
     */
    private Path getEngineJarOrSkip() {
        // Check common locations for the engine JAR
        Path[] candidates = {
            Path.of("../../automation/engine/target/engine-1.0-SNAPSHOT.jar"),
            Path.of("../atg-reference-impl/automation/engine/target/engine-1.0-SNAPSHOT.jar")
        };

        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }

        // No engine available - skip test
        assumeTrue(false, "GameEngine JAR not available. Build atg-reference-impl/automation/engine first.");
        return null; // unreachable
    }

    @Test
    void testStartTournamentReturnsValidId(@TempDir Path tempDir) {
        SimpMessagingTemplate mockMessagingTemplate = mock(SimpMessagingTemplate.class);
        TournamentExecutionService service = new TournamentExecutionService(tempDir.toString(), mockMessagingTemplate);

        TournamentConfig config = new TournamentConfig(
            "test-tournament",
            1,
            4,
            100,
            List.of(
                new PlayerConfig("p1", "Player1", "naive-money"),
                new PlayerConfig("p2", "Player2", "action-heavy"),
                new PlayerConfig("p3", "Player3", "random"),
                new PlayerConfig("p4", "Player4", "naive-money")
            )
        );

        EngineLoader mockEngineLoader = mock(EngineLoader.class);

        try {
            String tournamentId = service.startTournament(config, mockEngineLoader, null);
            assertNotNull(tournamentId);
            assertFalse(tournamentId.isEmpty());

            // Verify we can retrieve the status
            Optional<TournamentStatus> status = service.getTournamentStatus(tournamentId);
            assertTrue(status.isPresent());
            assertEquals(tournamentId, status.get().id());
        } finally {
            service.shutdown();
        }
    }

    @Test
    void testGetTournamentStatusReturnsInitialQueuedState(@TempDir Path tempDir) throws InterruptedException {
        SimpMessagingTemplate mockMessagingTemplate = mock(SimpMessagingTemplate.class);
        TournamentExecutionService service = new TournamentExecutionService(tempDir.toString(), mockMessagingTemplate);

        TournamentConfig config = new TournamentConfig(
            "test-tournament",
            2,
            4,
            100,
            List.of(
                new PlayerConfig("p1", "Player1", "naive-money"),
                new PlayerConfig("p2", "Player2", "action-heavy"),
                new PlayerConfig("p3", "Player3", "random"),
                new PlayerConfig("p4", "Player4", "naive-money")
            )
        );

        EngineLoader mockEngineLoader = mock(EngineLoader.class);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<TournamentStatus> capturedStatus = new AtomicReference<>();

        try {
            String tournamentId = service.startTournament(config, mockEngineLoader, status -> {
                // Capture initial QUEUED status
                if (status.state() == TournamentStatus.State.QUEUED) {
                    capturedStatus.set(status);
                    latch.countDown();
                }
            });

            // Wait for initial status callback
            assertTrue(latch.await(1, TimeUnit.SECONDS), "Should receive QUEUED status");

            // Verify initial status properties
            TournamentStatus status = capturedStatus.get();
            assertNotNull(status);
            assertEquals(TournamentStatus.State.QUEUED, status.state());
            assertEquals(tournamentId, status.id());
            assertEquals(2, status.totalRounds());
            assertEquals(0, status.currentRound());
            assertEquals(0, status.completedGames());
            assertTrue(status.totalGames() > 0);
            assertFalse(status.error().isPresent());

            // Verify we can retrieve the status via the service
            Optional<TournamentStatus> retrievedStatus = service.getTournamentStatus(tournamentId);
            assertTrue(retrievedStatus.isPresent());
            assertEquals(tournamentId, retrievedStatus.get().id());
        } finally {
            service.shutdown();
        }
    }

    @Test
    void testGetTournamentStatusReturnsEmptyForUnknownId(@TempDir Path tempDir) {
        SimpMessagingTemplate mockMessagingTemplate = mock(SimpMessagingTemplate.class);
        TournamentExecutionService service = new TournamentExecutionService(tempDir.toString(), mockMessagingTemplate);

        try {
            Optional<TournamentStatus> status = service.getTournamentStatus("unknown-id");
            assertFalse(status.isPresent());
        } finally {
            service.shutdown();
        }
    }

    @Test
    void testGetAllTournamentsReturnsRunningTournaments(@TempDir Path tempDir) {
        SimpMessagingTemplate mockMessagingTemplate = mock(SimpMessagingTemplate.class);
        TournamentExecutionService service = new TournamentExecutionService(tempDir.toString(), mockMessagingTemplate);

        TournamentConfig config = new TournamentConfig(
            "test-tournament",
            1,
            4,
            100,
            List.of(
                new PlayerConfig("p1", "Player1", "naive-money"),
                new PlayerConfig("p2", "Player2", "action-heavy"),
                new PlayerConfig("p3", "Player3", "random"),
                new PlayerConfig("p4", "Player4", "naive-money")
            )
        );

        EngineLoader mockEngineLoader = mock(EngineLoader.class);

        try {
            String id1 = service.startTournament(config, mockEngineLoader, null);
            String id2 = service.startTournament(config, mockEngineLoader, null);

            var allTournaments = service.getAllTournaments();
            assertTrue(allTournaments.size() >= 2);
            assertTrue(allTournaments.containsKey(id1));
            assertTrue(allTournaments.containsKey(id2));
        } finally {
            service.shutdown();
        }
    }

    @Test
    void testTournamentStatusFactoryMethods() {
        TournamentStatus queued = TournamentStatus.queued("id1", 5, 100);
        assertEquals(TournamentStatus.State.QUEUED, queued.state());
        assertEquals(0, queued.currentRound());
        assertEquals(5, queued.totalRounds());
        assertEquals(0, queued.completedGames());
        assertEquals(100, queued.totalGames());
        assertFalse(queued.error().isPresent());

        TournamentStatus running = TournamentStatus.running("id2", 3, 5, 50, 100);
        assertEquals(TournamentStatus.State.RUNNING, running.state());
        assertEquals(3, running.currentRound());
        assertEquals(50, running.completedGames());

        TournamentStatus completed = TournamentStatus.completed("id3", 5, 100);
        assertEquals(TournamentStatus.State.COMPLETED, completed.state());
        assertEquals(5, completed.currentRound());
        assertEquals(100, completed.completedGames());

        TournamentStatus failed = TournamentStatus.failed("id4", 2, 5, 30, 100, "Error occurred");
        assertEquals(TournamentStatus.State.FAILED, failed.state());
        assertTrue(failed.error().isPresent());
        assertEquals("Error occurred", failed.error().get());
    }

    /**
     * Integration test that runs a real tournament end-to-end.
     */
    @Test
    void integrationTest_runCompleteSmallTournament(@TempDir Path tempDir) throws Exception {
        Path engineJarPath = getEngineJarOrSkip();

        SimpMessagingTemplate mockMessagingTemplate = mock(SimpMessagingTemplate.class);
        TournamentExecutionService service = new TournamentExecutionService(tempDir.toString(), mockMessagingTemplate);

        TournamentConfig config = new TournamentConfig(
            "integration-test",
            2,
            1,  // 1 game per player per round
            100,
            List.of(
                new PlayerConfig("p1", "Player1", "naive-money"),
                new PlayerConfig("p2", "Player2", "action-heavy"),
                new PlayerConfig("p3", "Player3", "random"),
                new PlayerConfig("p4", "Player4", "naive-money")
            )
        );

        EngineLoader engineLoader = new EngineLoader(
            engineJarPath.toString(),
            "edu.brandeis.cosi103a.engine.GameEngine"
        );

        CountDownLatch completionLatch = new CountDownLatch(1);
        AtomicReference<TournamentStatus> finalStatus = new AtomicReference<>();

        try {
            String tournamentId = service.startTournament(config, engineLoader, status -> {
                if (status.state() == TournamentStatus.State.COMPLETED) {
                    finalStatus.set(status);
                    completionLatch.countDown();
                }
            });

            // Wait for tournament to complete (give it up to 30 seconds)
            assertTrue(completionLatch.await(30, TimeUnit.SECONDS),
                "Tournament should complete within 30 seconds");

            // Verify final status
            TournamentStatus status = finalStatus.get();
            assertNotNull(status);
            assertEquals(TournamentStatus.State.COMPLETED, status.state());
            assertEquals(2, status.currentRound());
            assertEquals(config.rounds(), status.totalRounds());
            assertEquals(status.totalGames(), status.completedGames());
            assertFalse(status.error().isPresent());

            // Verify files were written
            Path outputDir = tempDir.resolve(config.name());
            assertTrue(Files.exists(outputDir.resolve("tournament.json")));
            assertTrue(Files.exists(outputDir.resolve("round-01.json")));
            assertTrue(Files.exists(outputDir.resolve("round-02.json")));
            assertTrue(Files.exists(outputDir.resolve("tape.json")));
        } finally {
            service.shutdown();
        }
    }

    /**
     * Integration test that verifies failure handling.
     */
    @Test
    void integrationTest_handlesInvalidConfiguration(@TempDir Path tempDir) throws Exception {
        Path engineJarPath = getEngineJarOrSkip();

        SimpMessagingTemplate mockMessagingTemplate = mock(SimpMessagingTemplate.class);
        TournamentExecutionService service = new TournamentExecutionService(tempDir.toString(), mockMessagingTemplate);

        // Invalid config: 3 players with 1 game each (3*1=3, not divisible by 4)
        TournamentConfig config = new TournamentConfig(
            "invalid-test",
            1,
            1,
            100,
            List.of(
                new PlayerConfig("p1", "Player1", "naive-money"),
                new PlayerConfig("p2", "Player2", "action-heavy"),
                new PlayerConfig("p3", "Player3", "random")
            )
        );

        EngineLoader engineLoader = new EngineLoader(
            engineJarPath.toString(),
            "edu.brandeis.cosi103a.engine.GameEngine"
        );

        CountDownLatch failureLatch = new CountDownLatch(1);
        AtomicReference<TournamentStatus> failedStatus = new AtomicReference<>();

        try {
            String tournamentId = service.startTournament(config, engineLoader, status -> {
                if (status.state() == TournamentStatus.State.FAILED) {
                    failedStatus.set(status);
                    failureLatch.countDown();
                }
            });

            // Wait for tournament to fail
            assertTrue(failureLatch.await(10, TimeUnit.SECONDS),
                "Tournament should fail within 10 seconds");

            // Verify failure status
            TournamentStatus status = failedStatus.get();
            assertNotNull(status);
            assertEquals(TournamentStatus.State.FAILED, status.state());
            assertTrue(status.error().isPresent());
            assertTrue(status.error().get().contains("divisible by 4") ||
                      status.error().get().contains("4 players"),
                "Error message should mention the validation failure");
        } finally {
            service.shutdown();
        }
    }
}
