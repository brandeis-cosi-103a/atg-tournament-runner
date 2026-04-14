package edu.brandeis.cosi103a.tournament.player;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.common.collect.ImmutableList;
import edu.brandeis.cosi.atg.decisions.Decision;
import edu.brandeis.cosi.atg.decisions.EndPhaseDecision;
import edu.brandeis.cosi.atg.event.Event;
import edu.brandeis.cosi.atg.event.GameObserver;
import edu.brandeis.cosi.atg.player.Player;
import edu.brandeis.cosi.atg.state.GameState;

/**
 * A Player decorator that enforces per-decision timeouts and per-game
 * cumulative time budgets.
 *
 * <p>Each call to {@link #makeDecision} is executed on a background thread
 * with a per-call timeout. Elapsed time is tracked cumulatively against a
 * per-game budget. When the budget is exceeded, the wrapper automatically
 * forfeits by returning an {@link EndPhaseDecision} (or the first available
 * option for forced decisions where no EndPhaseDecision exists).
 *
 * <p>Example usage:
 * <pre>
 * Player timed = new TimedPlayerWrapper(
 *     new NaiveBigMoneyPlayer("Bot"),
 *     Duration.ofSeconds(5),
 *     Duration.ofMinutes(2)
 * );
 * </pre>
 */
public class TimedPlayerWrapper implements Player {
    private final Player delegate;
    private final Duration perCallTimeout;
    private final Duration gameBudget;
    private final ExecutorService executor;

    private long totalDecisionTimeMs = 0;
    private int decisionCount = 0;
    private int timeoutCount = 0;
    private boolean forfeited = false;
    private Integer decisionAtForfeit = null;

    /**
     * Creates a new TimedPlayerWrapper.
     *
     * @param delegate       the Player instance to wrap
     * @param perCallTimeout maximum time allowed for a single makeDecision call
     * @param gameBudget     maximum cumulative time allowed across all decisions
     */
    public TimedPlayerWrapper(Player delegate, Duration perCallTimeout, Duration gameBudget) {
        this.delegate = delegate;
        this.perCallTimeout = perCallTimeout;
        this.gameBudget = gameBudget;
        this.executor = Executors.newSingleThreadExecutor();
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
        decisionCount++;

        // If already forfeited, return forfeit decision immediately (no delegate call)
        if (forfeited) {
            return forfeitDecision(options);
        }

        long startTime = System.nanoTime();
        try {
            Future<Decision> future = executor.submit(() -> delegate.makeDecision(state, options, event));
            Decision result = future.get(perCallTimeout.toMillis(), TimeUnit.MILLISECONDS);
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            totalDecisionTimeMs += elapsedMs;

            // Check if cumulative time exceeds game budget
            if (totalDecisionTimeMs > gameBudget.toMillis()) {
                forfeited = true;
                decisionAtForfeit = decisionCount;
            }

            return result;
        } catch (TimeoutException e) {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            totalDecisionTimeMs += elapsedMs;
            timeoutCount++;

            // Check if cumulative time exceeds game budget
            if (totalDecisionTimeMs > gameBudget.toMillis()) {
                forfeited = true;
                decisionAtForfeit = decisionCount;
            }

            return forfeitDecision(options);
        } catch (ExecutionException e) {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            totalDecisionTimeMs += elapsedMs;
            timeoutCount++;

            // Check if cumulative time exceeds game budget
            if (totalDecisionTimeMs > gameBudget.toMillis()) {
                forfeited = true;
                decisionAtForfeit = decisionCount;
            }

            return forfeitDecision(options);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            totalDecisionTimeMs += elapsedMs;
            timeoutCount++;
            return forfeitDecision(options);
        }
    }

    /**
     * Returns a forfeit decision: prefers EndPhaseDecision if available,
     * otherwise returns the first option.
     */
    private Decision forfeitDecision(ImmutableList<Decision> options) {
        return options.stream()
                .filter(d -> d instanceof EndPhaseDecision)
                .findFirst()
                .orElse(options.get(0));
    }

    /**
     * Returns whether this player has been forfeited due to exceeding the game budget.
     */
    public boolean isForfeited() {
        return forfeited;
    }

    /**
     * Returns the cumulative time spent in makeDecision calls, in milliseconds.
     */
    public long getCumulativeTimeMs() {
        return totalDecisionTimeMs;
    }

    /**
     * Returns a snapshot of the current timing statistics.
     */
    public TimingStats getTimingStats() {
        return new TimingStats(totalDecisionTimeMs, decisionCount, timeoutCount, forfeited, decisionAtForfeit);
    }

    /**
     * Shuts down the background executor. Should be called when this wrapper
     * is no longer needed.
     */
    public void shutdown() {
        executor.shutdownNow();
    }
}
