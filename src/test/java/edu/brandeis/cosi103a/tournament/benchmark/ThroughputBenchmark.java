package edu.brandeis.cosi103a.tournament.benchmark;

import edu.brandeis.cosi.atg.cards.Card;
import edu.brandeis.cosi.atg.decisions.Decision;
import edu.brandeis.cosi.atg.engine.Engine;
import edu.brandeis.cosi.atg.event.Event;
import edu.brandeis.cosi.atg.event.GameObserver;
import edu.brandeis.cosi.atg.player.Player;
import edu.brandeis.cosi.atg.state.GameResult;
import edu.brandeis.cosi.atg.state.GameState;
import edu.brandeis.cosi103a.tournament.engine.EngineLoader;
import edu.brandeis.cosi103a.tournament.player.ActionHeavyPlayer;
import edu.brandeis.cosi103a.tournament.player.DelayedPlayerWrapper;
import edu.brandeis.cosi103a.tournament.player.NaiveBigMoneyPlayer;
import edu.brandeis.cosi103a.tournament.player.RandomPlayer;
import edu.brandeis.cosi103a.tournament.runner.RoundGenerator;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Throughput benchmark measuring tournament games/hour across a matrix of
 * simulated network latencies and thread pool sizes.
 *
 * <p>Run manually with: {@code mvn test -Dtest=ThroughputBenchmark}
 *
 * <p>This benchmark bypasses TableExecutor to directly control latency via
 * DelayedPlayerWrapper and measure raw engine throughput.
 */
@Disabled("Manual benchmark - run with -Dtest=ThroughputBenchmark")
public class ThroughputBenchmark {

    private static final int[] LATENCIES_MS = {0, 5, 20, 50, 100};
    private static final int[] THREAD_POOL_SIZES = {8, 32, 64, 128};
    private static final int GAMES_PER_COMBINATION = 64;

    private static final String ENGINE_CLASS_NAME = "edu.brandeis.cosi103a.engine.GameEngine";

    /**
     * A Player wrapper that counts makeDecision calls for benchmarking.
     */
    private static class CountingPlayerWrapper implements Player {
        private final Player delegate;
        private final AtomicLong decisionCount;

        CountingPlayerWrapper(Player delegate, AtomicLong decisionCount) {
            this.delegate = delegate;
            this.decisionCount = decisionCount;
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public Optional<GameObserver> getObserver() {
            return delegate.getObserver();
        }

        @Override
        public Decision makeDecision(GameState state, ImmutableList<Decision> options, Optional<Event> event) {
            decisionCount.incrementAndGet();
            return delegate.makeDecision(state, options, event);
        }
    }

    /**
     * A GameObserver that counts notifyEvent calls for benchmarking.
     */
    private static class CountingObserver implements GameObserver {
        private final AtomicLong eventCount;

        CountingObserver(AtomicLong eventCount) {
            this.eventCount = eventCount;
        }

        @Override
        public void notifyEvent(GameState state, Event event) {
            eventCount.incrementAndGet();
        }
    }

    @SuppressWarnings("unchecked")
    private EngineLoader createEngineLoader() throws Exception {
        Class<?> engineClass = Class.forName(ENGINE_CLASS_NAME);
        return new EngineLoader((Class<? extends Engine>) engineClass);
    }

    /**
     * Creates 4 benchmark players wrapped with latency simulation and decision counting.
     */
    private List<Player> createBenchmarkPlayers(int latencyMs, AtomicLong decisionCount) {
        Player[] bases = {
            new NaiveBigMoneyPlayer("P1"),
            new ActionHeavyPlayer("P2"),
            new RandomPlayer("P3"),
            new NaiveBigMoneyPlayer("P4")
        };

        List<Player> players = new ArrayList<>();
        for (Player base : bases) {
            Player wrapped = new CountingPlayerWrapper(base, decisionCount);
            if (latencyMs > 0) {
                wrapped = new DelayedPlayerWrapper(wrapped, latencyMs, latencyMs);
            }
            players.add(wrapped);
        }
        return players;
    }

    @Test
    void measureThroughputMatrix() throws Exception {
        System.out.println("\n=== ATG Tournament Throughput Benchmark ===\n");

        EngineLoader engineLoader = createEngineLoader();
        List<Card.Type> kingdomCards = RoundGenerator.selectKingdomCards();

        // --- Baseline run to collect per-game stats ---
        AtomicLong baselineDecisions = new AtomicLong();
        AtomicLong baselineEvents = new AtomicLong();
        int baselineGames = 8;

        for (int i = 0; i < baselineGames; i++) {
            List<Player> players = createBenchmarkPlayers(0, baselineDecisions);
            CountingObserver observer = new CountingObserver(baselineEvents);
            Engine engine = engineLoader.create(players, kingdomCards);
            engine.setObserver(observer);
            engine.play();
        }

        double avgDecisions = (double) baselineDecisions.get() / baselineGames;
        double avgEvents = (double) baselineEvents.get() / baselineGames;
        double avgTotalCalls = avgDecisions + avgEvents;

        System.out.println("Game stats (from baseline run):");
        System.out.printf("  Avg decisions/game: %.0f%n", avgDecisions);
        System.out.printf("  Avg events/game: %.0f%n", avgEvents);
        System.out.printf("  Total calls/game: %.0f%n", avgTotalCalls);
        System.out.println();

        // --- Throughput matrix ---
        double[][] results = new double[LATENCIES_MS.length][THREAD_POOL_SIZES.length];

        for (int li = 0; li < LATENCIES_MS.length; li++) {
            int latency = LATENCIES_MS[li];
            for (int ti = 0; ti < THREAD_POOL_SIZES.length; ti++) {
                int poolSize = THREAD_POOL_SIZES[ti];
                List<Card.Type> comboKingdom = RoundGenerator.selectKingdomCards();

                ExecutorService executor = Executors.newFixedThreadPool(poolSize);
                AtomicInteger failures = new AtomicInteger();
                AtomicLong decisionCount = new AtomicLong();

                long startNanos = System.nanoTime();

                List<Future<?>> futures = new ArrayList<>();
                for (int g = 0; g < GAMES_PER_COMBINATION; g++) {
                    futures.add(executor.submit(() -> {
                        try {
                            List<Player> players = createBenchmarkPlayers(latency, decisionCount);
                            Engine engine = engineLoader.create(players, comboKingdom);
                            engine.play();
                        } catch (Exception e) {
                            failures.incrementAndGet();
                        }
                    }));
                }

                // Wait for all games to complete
                for (Future<?> f : futures) {
                    try {
                        f.get(5, TimeUnit.MINUTES);
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    }
                }

                long elapsedNanos = System.nanoTime() - startNanos;
                double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
                double gamesPerHour = (GAMES_PER_COMBINATION - failures.get()) / elapsedSeconds * 3600.0;

                results[li][ti] = gamesPerHour;

                executor.shutdown();
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }

                if (failures.get() > 0) {
                    System.out.printf("  [%dms / %d threads] %d/%d games failed%n",
                            latency, poolSize, failures.get(), GAMES_PER_COMBINATION);
                }
            }
        }

        // --- Print formatted results table ---
        System.out.println("Throughput (games/hour):");
        System.out.printf("%-10s", "Latency");
        for (int poolSize : THREAD_POOL_SIZES) {
            System.out.printf("  %12s", poolSize + " threads");
        }
        System.out.println();

        for (int li = 0; li < LATENCIES_MS.length; li++) {
            System.out.printf("%-10s", LATENCIES_MS[li] + "ms");
            for (int ti = 0; ti < THREAD_POOL_SIZES.length; ti++) {
                System.out.printf("  %9.0f g/hr", results[li][ti]);
            }
            System.out.println();
        }

        System.out.println();
        System.out.println("Note: DelayedPlayerWrapper only delays makeDecision(), not observer events.");
        System.out.println("Actual network throughput will be lower due to event notification latency.");
    }
}
