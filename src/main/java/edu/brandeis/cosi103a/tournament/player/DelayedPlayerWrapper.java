package edu.brandeis.cosi103a.tournament.player;

import java.util.Optional;
import java.util.Random;

import com.google.common.collect.ImmutableList;
import edu.brandeis.cosi.atg.decisions.Decision;
import edu.brandeis.cosi.atg.event.Event;
import edu.brandeis.cosi.atg.event.GameObserver;
import edu.brandeis.cosi.atg.player.Player;
import edu.brandeis.cosi.atg.state.GameState;

/**
 * A Player wrapper that simulates network latency by adding configurable random
 * delays to decision-making.
 *
 * This wrapper delegates all Player interface methods to a wrapped Player instance,
 * but adds a random sleep delay before each makeDecision() call to simulate network
 * latency or slow player response times.
 *
 * Example usage:
 * <pre>
 * Player delayed = new DelayedPlayerWrapper(
 *     new NaiveBigMoneyPlayer("Bot"), 25, 100
 * );
 * </pre>
 */
public class DelayedPlayerWrapper implements Player {
    private final Player delegate;
    private final int minDelayMs;
    private final int maxDelayMs;
    private final Random random;

    /**
     * Creates a new DelayedPlayerWrapper.
     *
     * @param delegate the Player instance to wrap
     * @param minDelayMs minimum delay in milliseconds (inclusive)
     * @param maxDelayMs maximum delay in milliseconds (inclusive)
     * @throws IllegalArgumentException if minDelayMs < 0 or maxDelayMs < minDelayMs
     */
    public DelayedPlayerWrapper(Player delegate, int minDelayMs, int maxDelayMs) {
        if (minDelayMs < 0) {
            throw new IllegalArgumentException("minDelayMs must be non-negative");
        }
        if (maxDelayMs < minDelayMs) {
            throw new IllegalArgumentException("maxDelayMs must be >= minDelayMs");
        }
        this.delegate = delegate;
        this.minDelayMs = minDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.random = new Random();
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
        // Generate random delay between minDelayMs and maxDelayMs (inclusive)
        int delay = minDelayMs + random.nextInt(maxDelayMs - minDelayMs + 1);

        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while simulating network delay", e);
        }

        return delegate.makeDecision(state, options, event);
    }
}
