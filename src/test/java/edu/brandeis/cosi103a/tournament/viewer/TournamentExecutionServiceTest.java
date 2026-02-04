package edu.brandeis.cosi103a.tournament.viewer;

import edu.brandeis.cosi103a.tournament.engine.EngineLoader;
import edu.brandeis.cosi103a.tournament.runner.PlayerConfig;
import edu.brandeis.cosi103a.tournament.runner.TournamentConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Tests for TournamentExecutionService.
 */
class TournamentExecutionServiceTest {

    @Test
    void testStartTournamentReturnsValidId(@TempDir Path tempDir) {
        SimpMessagingTemplate mockMessagingTemplate = mock(SimpMessagingTemplate.class);
        TournamentExecutionService service = new TournamentExecutionService(tempDir.toString(), 64, mockMessagingTemplate);

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
        TournamentExecutionService service = new TournamentExecutionService(tempDir.toString(), 64, mockMessagingTemplate);

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
        TournamentExecutionService service = new TournamentExecutionService(tempDir.toString(), 64, mockMessagingTemplate);

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
        TournamentExecutionService service = new TournamentExecutionService(tempDir.toString(), 64, mockMessagingTemplate);

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

        TournamentStatus running = TournamentStatus.running("id2", 3, 5, 50, 100, null);
        assertEquals(TournamentStatus.State.RUNNING, running.state());
        assertEquals(3, running.currentRound());
        assertEquals(50, running.completedGames());

        TournamentStatus completed = TournamentStatus.completed("id3", 5, 100, null);
        assertEquals(TournamentStatus.State.COMPLETED, completed.state());
        assertEquals(5, completed.currentRound());
        assertEquals(100, completed.completedGames());

        TournamentStatus failed = TournamentStatus.failed("id4", 2, 5, 30, 100, "Error occurred");
        assertEquals(TournamentStatus.State.FAILED, failed.state());
        assertTrue(failed.error().isPresent());
        assertEquals("Error occurred", failed.error().get());
    }
}
