package edu.brandeis.cosi103a.tournament.runner;

import com.google.common.collect.ImmutableList;
import edu.brandeis.cosi.atg.cards.Card;
import edu.brandeis.cosi.atg.decisions.Decision;
import edu.brandeis.cosi.atg.event.Event;
import edu.brandeis.cosi.atg.event.GameObserver;
import edu.brandeis.cosi.atg.player.Player;
import edu.brandeis.cosi.atg.state.GameState;
import edu.brandeis.cosi103a.tournament.engine.EngineLoader;
import edu.brandeis.cosi103a.tournament.player.NaiveBigMoneyPlayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Real performance test that runs actual games with simulated network delays.
 *
 * Run with: mvn test -Dtest=NetworkDelayPerformanceTest
 */
public class NetworkDelayPerformanceTest {

    /**
     * Player that adds network delay to every decision.
     */
    static class DelayedNaiveMoney implements Player {
        private final String name;
        private final int delayMs;
        private final NaiveBigMoneyPlayer delegate;
        private final AtomicInteger decisionCount = new AtomicInteger(0);

        DelayedNaiveMoney(String name, int delayMs) {
            this.name = name;
            this.delayMs = delayMs;
            this.delegate = new NaiveBigMoneyPlayer(name);
        }

        @Override
        public Decision makeDecision(GameState state, ImmutableList<Decision> options, Optional<Event> event) {
            decisionCount.incrementAndGet();

            // Simulate network round-trip time
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during simulated network delay", e);
                }
            }

            return delegate.makeDecision(state, options, event);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Optional<GameObserver> getObserver() {
            return Optional.empty();
        }

        public int getDecisionCount() {
            return decisionCount.get();
        }
    }

    /**
     * Custom engine loader that creates engines with delayed players.
     */
    static class DelayedEngineLoader extends EngineLoader {
        private final int delayMs;
        private final String actualEngineJar;
        private final String actualEngineClass;

        DelayedEngineLoader(String engineJar, String engineClass, int delayMs) throws Exception {
            super(engineJar, engineClass);
            this.actualEngineJar = engineJar;
            this.actualEngineClass = engineClass;
            this.delayMs = delayMs;
        }

        /**
         * Creates delayed versions of the players.
         */
        public List<Player> createDelayedPlayers(List<PlayerConfig> configs) {
            return configs.stream()
                .map(config -> new DelayedNaiveMoney(config.name(), delayMs))
                .map(p -> (Player) p)
                .toList();
        }
    }

    @Test
    public void benchmarkTournamentWithNetworkDelay(@TempDir Path tempDir) throws Exception {
        // Test configuration - small but representative
        int numPlayers = 4;
        int rounds = 2;
        int gamesPerTable = 10;
        int delayMs = 75; // 75ms network RTT

        System.out.println("\n" + "=".repeat(60));
        System.out.println("TOURNAMENT PERFORMANCE BENCHMARK");
        System.out.println("Simulating network-based players with latency");
        System.out.println("=".repeat(60));
        System.out.println();

        System.out.println("Configuration:");
        System.out.printf("  Players:           %d\n", numPlayers);
        System.out.printf("  Rounds:            %d\n", rounds);
        System.out.printf("  Games per table:   %d\n", gamesPerTable);
        System.out.printf("  Network delay:     %d ms per decision\n", delayMs);
        System.out.printf("  Total games:       %d\n", rounds * gamesPerTable);
        System.out.println();

        // Create delayed players
        List<DelayedNaiveMoney> delayedPlayers = List.of(
            new DelayedNaiveMoney("Player 1", delayMs),
            new DelayedNaiveMoney("Player 2", delayMs),
            new DelayedNaiveMoney("Player 3", delayMs),
            new DelayedNaiveMoney("Player 4", delayMs)
        );

        System.out.println("Starting tournament execution...");
        System.out.println();

        Instant tournamentStart = Instant.now();
        TableExecutor tableExecutor = new TableExecutor(getTestEngineLoader());

        int totalDecisions = 0;
        int totalGames = 0;

        for (int round = 1; round <= rounds; round++) {
            Instant roundStart = Instant.now();
            System.out.printf("Round %d/%d starting...\n", round, rounds);

            // Generate kingdom cards
            List<Card.Type> kingdomCards = RoundGenerator.selectKingdomCards();

            // Run table (single table for 4 players)
            MatchResult result = tableExecutor.executeTable(
                1,
                convertToPlayerConfigs(delayedPlayers),
                kingdomCards,
                gamesPerTable,
                100 // maxTurns
            );

            Duration roundDuration = Duration.between(roundStart, Instant.now());
            int roundDecisions = delayedPlayers.stream()
                .mapToInt(DelayedNaiveMoney::getDecisionCount)
                .sum();

            totalDecisions = roundDecisions; // Running total
            totalGames += gamesPerTable;

            System.out.printf("Round %d complete:\n", round);
            System.out.printf("  Time:              %.2f seconds\n", roundDuration.toMillis() / 1000.0);
            System.out.printf("  Games:             %d\n", gamesPerTable);
            System.out.printf("  Decisions so far:  %d\n", roundDecisions);
            System.out.printf("  Avg per game:      %.2f seconds\n",
                roundDuration.toMillis() / 1000.0 / gamesPerTable);
            System.out.println();
        }

        Duration tournamentDuration = Duration.between(tournamentStart, Instant.now());

        // Final statistics
        System.out.println("=".repeat(60));
        System.out.println("FINAL RESULTS");
        System.out.println("=".repeat(60));
        System.out.println();

        System.out.printf("Total execution time:     %.2f seconds (%.2f minutes)\n",
            tournamentDuration.toMillis() / 1000.0,
            tournamentDuration.toMillis() / 60000.0);
        System.out.printf("Total games:              %d\n", totalGames);
        System.out.printf("Total decisions:          %d\n", totalDecisions);
        System.out.println();

        double avgGameTime = tournamentDuration.toMillis() / 1000.0 / totalGames;
        double avgDecisionsPerGame = (double) totalDecisions / totalGames;
        double avgTimePerDecision = tournamentDuration.toMillis() / (double) totalDecisions;

        System.out.printf("Average per game:         %.2f seconds\n", avgGameTime);
        System.out.printf("Average decisions/game:   %.1f\n", avgDecisionsPerGame);
        System.out.printf("Average time/decision:    %.1f ms\n", avgTimePerDecision);
        System.out.println();

        // Calculate update frequency
        double timePerRound = tournamentDuration.toMillis() / 1000.0 / rounds;
        System.out.println("Live tracking implications:");
        System.out.printf("  Current granularity:    Every %.1f seconds (%d games)\n",
            timePerRound, gamesPerTable);
        System.out.printf("  Per-game granularity:   Every %.1f seconds (1 game)\n", avgGameTime);
        System.out.printf("  Improvement factor:     %.1fx more frequent updates\n",
            gamesPerTable / 1.0);
        System.out.println();

        // Extrapolate to full tournament
        int fullRounds = 15;
        int fullGamesPerTable = 50;
        double fullTournamentMinutes = (fullRounds * fullGamesPerTable * avgGameTime) / 60.0;
        double currentUpdateInterval = fullGamesPerTable * avgGameTime;

        System.out.println("Extrapolated for full tournament (15 rounds, 50 games/table):");
        System.out.printf("  Estimated total time:   %.1f minutes\n", fullTournamentMinutes);
        System.out.printf("  Current update interval: %.1f seconds (every %d games)\n",
            currentUpdateInterval, fullGamesPerTable);
        System.out.printf("  Per-game update:        %.1f seconds (every game)\n", avgGameTime);
        System.out.println();

        System.out.println("=".repeat(60));
    }

    private List<PlayerConfig> convertToPlayerConfigs(List<DelayedNaiveMoney> players) {
        return players.stream()
            .map(p -> new PlayerConfig(
                p.getName().toLowerCase().replace(" ", "-"),
                p.getName(),
                "naive-money"
            ))
            .toList();
    }

    /**
     * Gets a test engine loader. In a real scenario, this would point to an actual engine JAR.
     */
    private EngineLoader getTestEngineLoader() throws Exception {
        // Try to find the reference engine in standard locations
        String engineJar = System.getenv("TOURNAMENT_ENGINE_JAR");
        String engineClass = System.getenv("TOURNAMENT_ENGINE_CLASS");

        if (engineJar == null) {
            // Try common test locations
            engineJar = "/workspaces/atg-reference-impl/automation/engine/target/engine-1.0-SNAPSHOT.jar";
        }
        if (engineClass == null) {
            engineClass = "edu.brandeis.cosi103a.engine.GameEngine";
        }

        return new EngineLoader(engineJar, engineClass);
    }
}
