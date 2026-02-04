package edu.brandeis.cosi103a.tournament.player;

import java.util.Optional;

import com.google.common.collect.ImmutableList;
import edu.brandeis.cosi.atg.decisions.Decision;
import edu.brandeis.cosi.atg.event.Event;
import edu.brandeis.cosi.atg.event.GameObserver;
import edu.brandeis.cosi.atg.player.Player;
import edu.brandeis.cosi.atg.state.GameState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DelayedPlayerWrapperTest {

    /**
     * Stub Player implementation for testing. Since Decision is a sealed interface,
     * we can't mock it directly, so we use a simple stub player that returns
     * the first option from the list (or null if empty).
     */
    private static class StubPlayer implements Player {
        private final String name;
        private int callCount = 0;

        StubPlayer(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Optional<GameObserver> getObserver() {
            return Optional.empty();
        }

        @Override
        public Decision makeDecision(GameState state, ImmutableList<Decision> options, Optional<Event> event) {
            callCount++;
            return options.isEmpty() ? null : options.get(0);
        }

        int getCallCount() {
            return callCount;
        }
    }

    @Test
    void constructor_rejectsNegativeMinDelay() {
        Player delegate = new StubPlayer("Test");
        assertThrows(IllegalArgumentException.class, () ->
                new DelayedPlayerWrapper(delegate, -1, 100)
        );
    }

    @Test
    void constructor_rejectsMaxDelayLessThanMinDelay() {
        Player delegate = new StubPlayer("Test");
        assertThrows(IllegalArgumentException.class, () ->
                new DelayedPlayerWrapper(delegate, 100, 50)
        );
    }

    @Test
    void constructor_acceptsEqualMinAndMaxDelay() {
        Player delegate = new StubPlayer("Test");
        assertDoesNotThrow(() ->
                new DelayedPlayerWrapper(delegate, 50, 50)
        );
    }

    @Test
    void getName_delegatesToWrappedPlayer() {
        StubPlayer delegate = new StubPlayer("TestPlayer");
        DelayedPlayerWrapper wrapper = new DelayedPlayerWrapper(delegate, 25, 100);

        assertEquals("TestPlayer", wrapper.getName());
    }

    @Test
    void getObserver_delegatesToWrappedPlayer() {
        Player delegate = mock(Player.class);
        GameObserver observer = mock(GameObserver.class);
        when(delegate.getObserver()).thenReturn(Optional.of(observer));

        DelayedPlayerWrapper wrapper = new DelayedPlayerWrapper(delegate, 25, 100);

        Optional<GameObserver> result = wrapper.getObserver();
        assertTrue(result.isPresent());
        assertEquals(observer, result.get());
        verify(delegate).getObserver();
    }

    @Test
    void makeDecision_delegatesToWrappedPlayer() {
        // Use stub player to verify delegation
        StubPlayer delegate = new StubPlayer("Bot");
        DelayedPlayerWrapper wrapper = new DelayedPlayerWrapper(delegate, 0, 0);

        GameState state = mock(GameState.class);
        ImmutableList<Decision> options = ImmutableList.of();
        Optional<Event> event = Optional.empty();

        // Verify delegate call count increases
        assertEquals(0, delegate.getCallCount());
        wrapper.makeDecision(state, options, event);
        assertEquals(1, delegate.getCallCount());
    }

    @Test
    void makeDecision_addsDelayInExpectedRange() {
        StubPlayer delegate = new StubPlayer("Bot");
        GameState state = mock(GameState.class);
        ImmutableList<Decision> options = ImmutableList.of();
        Optional<Event> event = Optional.empty();

        int minDelayMs = 25;
        int maxDelayMs = 100;
        DelayedPlayerWrapper wrapper = new DelayedPlayerWrapper(delegate, minDelayMs, maxDelayMs);

        // Measure the time taken for makeDecision
        long startTime = System.currentTimeMillis();
        try {
            wrapper.makeDecision(state, options, event);
        } catch (Exception e) {
            // Ignore any exceptions from trying to return options.get(0) on empty list
            // We only care about the timing
        }
        long endTime = System.currentTimeMillis();
        long elapsedTime = endTime - startTime;

        // Verify delay is within expected range (with some tolerance for system timing)
        assertTrue(elapsedTime >= minDelayMs,
                "Elapsed time (" + elapsedTime + "ms) should be at least " + minDelayMs + "ms");
        assertTrue(elapsedTime <= maxDelayMs + 50,
                "Elapsed time (" + elapsedTime + "ms) should not exceed " + maxDelayMs + "ms + 50ms tolerance");
    }

    @Test
    void makeDecision_withZeroDelay_completesQuickly() {
        StubPlayer delegate = new StubPlayer("Bot");
        GameState state = mock(GameState.class);
        ImmutableList<Decision> options = ImmutableList.of();
        Optional<Event> event = Optional.empty();

        DelayedPlayerWrapper wrapper = new DelayedPlayerWrapper(delegate, 0, 0);

        long startTime = System.currentTimeMillis();
        try {
            wrapper.makeDecision(state, options, event);
        } catch (Exception e) {
            // Ignore any exceptions from trying to return options.get(0) on empty list
        }
        long endTime = System.currentTimeMillis();
        long elapsedTime = endTime - startTime;

        // Should complete very quickly (within 10ms)
        assertTrue(elapsedTime < 10,
                "Zero delay should complete in <10ms, but took " + elapsedTime + "ms");
    }

    @Test
    void makeDecision_multipleCallsHaveVariableDelays() {
        StubPlayer delegate = new StubPlayer("Bot");
        GameState state = mock(GameState.class);
        ImmutableList<Decision> options = ImmutableList.of();
        Optional<Event> event = Optional.empty();

        int minDelayMs = 10;
        int maxDelayMs = 50;
        DelayedPlayerWrapper wrapper = new DelayedPlayerWrapper(delegate, minDelayMs, maxDelayMs);

        // Make multiple calls and collect timing data
        long[] timings = new long[10];
        for (int i = 0; i < 10; i++) {
            long startTime = System.currentTimeMillis();
            try {
                wrapper.makeDecision(state, options, event);
            } catch (Exception e) {
                // Ignore any exceptions from trying to return options.get(0) on empty list
            }
            long endTime = System.currentTimeMillis();
            timings[i] = endTime - startTime;
        }

        // At least one timing should differ from the first (randomness check)
        boolean hasVariation = false;
        long firstTiming = timings[0];
        for (int i = 1; i < timings.length; i++) {
            if (timings[i] != firstTiming) {
                hasVariation = true;
                break;
            }
        }
        assertTrue(hasVariation, "Delays should vary across multiple calls");

        // All timings should be within range
        for (long timing : timings) {
            assertTrue(timing >= minDelayMs,
                    "Timing (" + timing + "ms) should be >= " + minDelayMs + "ms");
            assertTrue(timing <= maxDelayMs + 50,
                    "Timing (" + timing + "ms) should be <= " + maxDelayMs + "ms + tolerance");
        }
    }

    @Test
    void makeDecision_handlesInterruption() {
        StubPlayer delegate = new StubPlayer("Bot");
        GameState state = mock(GameState.class);
        ImmutableList<Decision> options = ImmutableList.of();
        Optional<Event> event = Optional.empty();

        DelayedPlayerWrapper wrapper = new DelayedPlayerWrapper(delegate, 1000, 2000);

        // Interrupt the thread before calling makeDecision
        Thread testThread = Thread.currentThread();
        testThread.interrupt();

        // Should throw RuntimeException wrapping InterruptedException
        assertThrows(RuntimeException.class, () ->
                wrapper.makeDecision(state, options, event)
        );

        // Clear the interrupt flag
        Thread.interrupted();
    }
}
