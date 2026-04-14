package edu.brandeis.cosi103a.tournament.player;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.ImmutableList;
import edu.brandeis.cosi.atg.decisions.Decision;
import edu.brandeis.cosi.atg.decisions.EndPhaseDecision;
import edu.brandeis.cosi.atg.event.Event;
import edu.brandeis.cosi.atg.event.GameObserver;
import edu.brandeis.cosi.atg.player.Player;
import edu.brandeis.cosi.atg.state.GameState;

/**
 * A Player decorator that tracks cumulative decision time against a per-game
 * budget and auto-forfeits when exceeded.
 *
 * <p>This wrapper does NOT enforce per-call timeouts — that responsibility
 * belongs to the transport layer (e.g., HTTP request timeout on
 * {@link edu.brandeis.cosi103a.tournament.network.NetworkPlayer}).
 * This wrapper passively measures elapsed time and catches any exceptions
 * from the delegate (including timeout exceptions from the network layer)
 * to return a forfeit decision.
 *
 * <p>Example usage:
 * <pre>
 * Player timed = new TimedPlayerWrapper(
 *     new NetworkPlayer("Bot", url, requestTimeout),
 *     Duration.ofMinutes(2)
 * );
 * </pre>
 */
public class TimedPlayerWrapper implements Player {
    private final Player delegate;
    private final Duration gameBudget;

    private long totalDecisionTimeMs = 0;
    private int decisionCount = 0;
    private int timeoutCount = 0;
    private boolean forfeited = false;
    private Integer decisionAtForfeit = null;

    /**
     * Creates a new TimedPlayerWrapper.
     *
     * @param delegate   the Player instance to wrap
     * @param gameBudget maximum cumulative decision time allowed per game
     */
    public TimedPlayerWrapper(Player delegate, Duration gameBudget) {
        this.delegate = delegate;
        this.gameBudget = gameBudget;
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

        long startNanos = System.nanoTime();
        try {
            Decision result = delegate.makeDecision(state, options, event);
            totalDecisionTimeMs += TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            checkBudgetExceeded();
            return result;
        } catch (Exception e) {
            // Delegate failed (HTTP timeout, network error, player bug, etc.)
            totalDecisionTimeMs += TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            timeoutCount++;
            checkBudgetExceeded();
            return forfeitDecision(options);
        }
    }

    private void checkBudgetExceeded() {
        if (!forfeited && totalDecisionTimeMs > gameBudget.toMillis()) {
            forfeited = true;
            decisionAtForfeit = decisionCount;
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
}
