package edu.brandeis.cosi103a.tournament.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration test that verifies the fair scheduling algorithm produces balanced results.
 * 
 * NOTE: These tests require a GameEngine implementation JAR. They will be skipped if not available.
 * To run these tests in atg-reference-impl, build the engine module first:
 *   cd automation && mvn clean install
 */
class FairSchedulingIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Checks if we have a GameEngine available for testing.
     * Returns the path if available, otherwise skips the test.
     */
    private Path getEngineJarOrSkip() {
        // Check common locations for the engine JAR
        Path[] candidates = {
            Path.of("../../automation/engine/target/engine-1.0-SNAPSHOT.jar"),
            Path.of("../atg-reference-impl/automation/engine/target/engine-1.0-SNAPSHOT.jar"),
            Path.of("/tmp/test-engine.jar")
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
    void fairScheduling_producesBalancedTournament(@TempDir Path tempDir) throws Exception {
        Path engineJarPath = getEngineJarOrSkip();
        
        // Setup: 8 players, 2 rounds, 2 games per player per round  
        List<PlayerConfig> players = createPlayers(8);
        int rounds = 2;
        int gamesPerPlayer = 2;

        TournamentConfig config = new TournamentConfig(
            "fair-scheduling-test",
            rounds,
            gamesPerPlayer,
            100,
            players
        );

        // Note: This would need the actual GameEngine implementation
        // For now, just test that the scheduling algorithm itself is correct
        
        // Verify scheduling produces correct structure
        for (int round = 0; round < rounds; round++) {
            List<List<PlayerConfig>> games = RoundGenerator.generateBalancedGames(players, gamesPerPlayer);
            
            // All games have 4 players
            for (List<PlayerConfig> game : games) {
                assertEquals(4, game.size());
            }
            
            // Each player plays exactly gamesPerPlayer games
            Map<PlayerConfig, Long> counts = games.stream()
                .flatMap(List::stream)
                .collect(java.util.stream.Collectors.groupingBy(p -> p, java.util.stream.Collectors.counting()));
            
            for (PlayerConfig player : players) {
                assertEquals(gamesPerPlayer, counts.get(player).intValue());
            }
        }
    }

    @Test
    void fairScheduling_correctNumberOfGamesGenerated() {
        List<PlayerConfig> players = createPlayers(12);
        int gamesPerPlayer = 4;

        List<List<PlayerConfig>> games = RoundGenerator.generateBalancedGames(players, gamesPerPlayer);

        // Total games = (N * gamesPerPlayer) / 4
        int expectedGames = (players.size() * gamesPerPlayer) / 4;
        assertEquals(expectedGames, games.size());
        
        // All games have 4 players
        assertTrue(games.stream().allMatch(g -> g.size() == 4));
    }

    @Test
    void fairScheduling_eachPlayerGetsCorrectCount() {
        List<PlayerConfig> players = createPlayers(6);
        int gamesPerPlayer = 2;

        List<List<PlayerConfig>> games = RoundGenerator.generateBalancedGames(players, gamesPerPlayer);

        // Count games per player
        Map<PlayerConfig, Long> counts = games.stream()
            .flatMap(List::stream)
            .collect(java.util.stream.Collectors.groupingBy(p -> p, java.util.stream.Collectors.counting()));

        for (PlayerConfig player : players) {
            assertEquals(gamesPerPlayer, counts.get(player).intValue(),
                "Player " + player.name() + " should play exactly " + gamesPerPlayer + " games");
        }
    }

    private List<PlayerConfig> createPlayers(int count) {
        List<PlayerConfig> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            players.add(new PlayerConfig(
                "player" + i,
                "Player" + i,
                "naive-money"
            ));
        }
        return players;
    }
}
