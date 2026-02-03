package edu.brandeis.cosi103a.tournament.viewer;

import edu.brandeis.cosi103a.tournament.runner.PlayerConfig;
import edu.brandeis.cosi103a.tournament.runner.TournamentConfig;
import edu.brandeis.cosi103a.tournament.engine.EngineLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TournamentExecutionService.
 */
class TournamentExecutionServiceTest {

    @Test
    void testStartTournamentReturnsId(@TempDir Path tempDir) {
        TournamentExecutionService service = new TournamentExecutionService(tempDir.toString());

        TournamentConfig config = new TournamentConfig(
            "test-tournament",
            1,
            2,
            100,
            List.of(
                new PlayerConfig("p1", "Player1", "naive-money"),
                new PlayerConfig("p2", "Player2", "action-heavy"),
                new PlayerConfig("p3", "Player3", "random")
            )
        );

        // Note: We can't create a real EngineLoader without a valid JAR, so this test
        // will fail at runtime but demonstrates the API
        try {
            String tournamentId = service.startTournament(config, null, null);
            assertNotNull(tournamentId);
            assertFalse(tournamentId.isEmpty());
        } finally {
            service.shutdown();
        }
    }

    @Test
    void testGetTournamentStatusReturnsInitialState(@TempDir Path tempDir) throws InterruptedException {
        TournamentExecutionService service = new TournamentExecutionService(tempDir.toString());

        TournamentConfig config = new TournamentConfig(
            "test-tournament",
            2,
            5,
            100,
            List.of(
                new PlayerConfig("p1", "Player1", "naive-money"),
                new PlayerConfig("p2", "Player2", "action-heavy"),
                new PlayerConfig("p3", "Player3", "random")
            )
        );

        CountDownLatch latch = new CountDownLatch(1);

        try {
            String tournamentId = service.startTournament(config, null, status -> {
                // Verify initial status is QUEUED
                if (status.state() == TournamentStatus.State.QUEUED) {
                    latch.countDown();
                }
            });

            // Wait briefly for initial status callback
            assertTrue(latch.await(1, TimeUnit.SECONDS));

            // Verify we can retrieve the status
            Optional<TournamentStatus> status = service.getTournamentStatus(tournamentId);
            assertTrue(status.isPresent());
            assertEquals(tournamentId, status.get().id());
            assertEquals(2, status.get().totalRounds());
            assertTrue(status.get().totalGames() > 0);
        } finally {
            service.shutdown();
        }
    }

    @Test
    void testGetTournamentStatusReturnsEmptyForUnknownId(@TempDir Path tempDir) {
        TournamentExecutionService service = new TournamentExecutionService(tempDir.toString());

        try {
            Optional<TournamentStatus> status = service.getTournamentStatus("unknown-id");
            assertFalse(status.isPresent());
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
}
