package edu.brandeis.cosi103a.tournament.player;

import java.time.Duration;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import edu.brandeis.cosi.atg.cards.Card;
import edu.brandeis.cosi.atg.decisions.BuyDecision;
import edu.brandeis.cosi.atg.decisions.Decision;
import edu.brandeis.cosi.atg.decisions.EndPhaseDecision;
import edu.brandeis.cosi.atg.event.Event;
import edu.brandeis.cosi.atg.event.GameObserver;
import edu.brandeis.cosi.atg.player.Player;
import edu.brandeis.cosi.atg.state.GameState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TimedPlayerWrapperTest {

    private TimedPlayerWrapper wrapper;

    /**
     * Stub Player implementation for testing. Returns the first option from the list.
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

    /**
     * Stub Player that sleeps for a configurable duration before returning a decision.
     */
    private static class SlowStubPlayer implements Player {
        private final String name;
        private final Duration delay;
        private int callCount = 0;

        SlowStubPlayer(String name, Duration delay) {
            this.name = name;
            this.delay = delay;
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
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted during slow decision", e);
            }
            return options.isEmpty() ? null : options.get(0);
        }

        int getCallCount() {
            return callCount;
        }
    }

    /**
     * Stub Player that throws an exception in makeDecision.
     */
    private static class ExplodingStubPlayer implements Player {
        private final String name;

        ExplodingStubPlayer(String name) {
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
            throw new RuntimeException("Simulated delegate failure");
        }
    }

    @AfterEach
    void tearDown() {
        if (wrapper != null) {
            wrapper.shutdown();
        }
    }

    @Test
    void getName_delegatesToWrappedPlayer() {
        StubPlayer delegate = new StubPlayer("TestPlayer");
        wrapper = new TimedPlayerWrapper(delegate, Duration.ofSeconds(5), Duration.ofMinutes(2));

        assertEquals("TestPlayer", wrapper.getName());
    }

    @Test
    void getObserver_delegatesToWrappedPlayer() {
        Player delegate = mock(Player.class);
        GameObserver observer = mock(GameObserver.class);
        when(delegate.getObserver()).thenReturn(Optional.of(observer));

        wrapper = new TimedPlayerWrapper(delegate, Duration.ofSeconds(5), Duration.ofMinutes(2));

        Optional<GameObserver> result = wrapper.getObserver();
        assertTrue(result.isPresent());
        assertEquals(observer, result.get());
        verify(delegate).getObserver();
    }

    @Test
    void makeDecision_withinBudget_delegatesNormally() {
        StubPlayer delegate = new StubPlayer("Bot");
        wrapper = new TimedPlayerWrapper(delegate, Duration.ofSeconds(5), Duration.ofMinutes(2));

        GameState state = mock(GameState.class);
        EndPhaseDecision endPhase = new EndPhaseDecision(GameState.TurnPhase.ACTION);
        ImmutableList<Decision> options = ImmutableList.of(endPhase);
        Optional<Event> event = Optional.empty();

        Decision result = wrapper.makeDecision(state, options, event);

        assertEquals(endPhase, result);
        assertEquals(1, delegate.getCallCount());
        assertFalse(wrapper.isForfeited());

        TimingStats stats = wrapper.getTimingStats();
        assertEquals(1, stats.decisionCount());
        assertEquals(0, stats.timeoutCount());
        assertFalse(stats.forfeited());
        assertNull(stats.decisionAtForfeit());
        assertTrue(stats.totalDecisionTimeMs() >= 0);
    }

    @Test
    void makeDecision_perCallTimeout_returnsEndPhaseDecision() {
        SlowStubPlayer delegate = new SlowStubPlayer("SlowBot", Duration.ofSeconds(2));
        wrapper = new TimedPlayerWrapper(delegate, Duration.ofMillis(100), Duration.ofMinutes(2));

        GameState state = mock(GameState.class);
        BuyDecision buyDecision = new BuyDecision(Card.Type.MODULE);
        EndPhaseDecision endPhase = new EndPhaseDecision(GameState.TurnPhase.BUY);
        ImmutableList<Decision> options = ImmutableList.of(buyDecision, endPhase);
        Optional<Event> event = Optional.empty();

        Decision result = wrapper.makeDecision(state, options, event);

        // Should return the EndPhaseDecision as forfeit decision
        assertEquals(endPhase, result);

        TimingStats stats = wrapper.getTimingStats();
        assertEquals(1, stats.decisionCount());
        assertEquals(1, stats.timeoutCount());
    }

    @Test
    void makeDecision_perCallTimeout_noEndPhaseDecision_returnsFirstOption() {
        SlowStubPlayer delegate = new SlowStubPlayer("SlowBot", Duration.ofSeconds(2));
        wrapper = new TimedPlayerWrapper(delegate, Duration.ofMillis(100), Duration.ofMinutes(2));

        GameState state = mock(GameState.class);
        BuyDecision buyDecision1 = new BuyDecision(Card.Type.MODULE);
        BuyDecision buyDecision2 = new BuyDecision(Card.Type.FRAMEWORK);
        ImmutableList<Decision> options = ImmutableList.of(buyDecision1, buyDecision2);
        Optional<Event> event = Optional.empty();

        Decision result = wrapper.makeDecision(state, options, event);

        // No EndPhaseDecision available, should return the first option
        assertEquals(buyDecision1, result);

        TimingStats stats = wrapper.getTimingStats();
        assertEquals(1, stats.timeoutCount());
    }

    @Test
    void makeDecision_budgetExceeded_setsForfeited() {
        // Use a slow player that takes ~150ms per call, with a 200ms budget
        SlowStubPlayer delegate = new SlowStubPlayer("SlowBot", Duration.ofMillis(150));
        wrapper = new TimedPlayerWrapper(delegate, Duration.ofSeconds(5), Duration.ofMillis(200));

        GameState state = mock(GameState.class);
        EndPhaseDecision endPhase = new EndPhaseDecision(GameState.TurnPhase.ACTION);
        ImmutableList<Decision> options = ImmutableList.of(endPhase);
        Optional<Event> event = Optional.empty();

        // First call: ~150ms, within 200ms budget
        wrapper.makeDecision(state, options, event);
        assertFalse(wrapper.isForfeited(), "Should not be forfeited after first call");
        assertEquals(1, delegate.getCallCount());

        // Second call: cumulative ~300ms, exceeds 200ms budget
        wrapper.makeDecision(state, options, event);
        assertTrue(wrapper.isForfeited(), "Should be forfeited after cumulative time exceeds budget");
        assertEquals(2, delegate.getCallCount());

        TimingStats stats = wrapper.getTimingStats();
        assertTrue(stats.forfeited());
        assertNotNull(stats.decisionAtForfeit());
        assertEquals(2, stats.decisionAtForfeit());
    }

    @Test
    void makeDecision_afterForfeit_doesNotCallDelegate() {
        // Use a slow player that takes ~150ms per call, with a 200ms budget
        SlowStubPlayer delegate = new SlowStubPlayer("SlowBot", Duration.ofMillis(150));
        wrapper = new TimedPlayerWrapper(delegate, Duration.ofSeconds(5), Duration.ofMillis(200));

        GameState state = mock(GameState.class);
        EndPhaseDecision endPhase = new EndPhaseDecision(GameState.TurnPhase.ACTION);
        ImmutableList<Decision> options = ImmutableList.of(endPhase);
        Optional<Event> event = Optional.empty();

        // First two calls exceed budget
        wrapper.makeDecision(state, options, event);
        wrapper.makeDecision(state, options, event);
        assertTrue(wrapper.isForfeited());
        assertEquals(2, delegate.getCallCount());

        // Third call should NOT call delegate
        long before = System.currentTimeMillis();
        Decision result = wrapper.makeDecision(state, options, event);
        long elapsed = System.currentTimeMillis() - before;

        assertEquals(endPhase, result);
        assertEquals(2, delegate.getCallCount(), "Delegate should not be called after forfeit");
        assertTrue(elapsed < 50, "Forfeited decision should be instant, took " + elapsed + "ms");

        TimingStats stats = wrapper.getTimingStats();
        assertEquals(3, stats.decisionCount());
    }

    @Test
    void makeDecision_statsAccumulateCorrectly() {
        StubPlayer delegate = new StubPlayer("Bot");
        wrapper = new TimedPlayerWrapper(delegate, Duration.ofSeconds(5), Duration.ofMinutes(2));

        GameState state = mock(GameState.class);
        EndPhaseDecision endPhase = new EndPhaseDecision(GameState.TurnPhase.ACTION);
        ImmutableList<Decision> options = ImmutableList.of(endPhase);
        Optional<Event> event = Optional.empty();

        // Make several decisions
        for (int i = 0; i < 5; i++) {
            wrapper.makeDecision(state, options, event);
        }

        TimingStats stats = wrapper.getTimingStats();
        assertEquals(5, stats.decisionCount());
        assertEquals(0, stats.timeoutCount());
        assertFalse(stats.forfeited());
        assertNull(stats.decisionAtForfeit());
        assertTrue(stats.totalDecisionTimeMs() >= 0);
        assertEquals(5, delegate.getCallCount());
    }

    @Test
    void makeDecision_delegateException_treatedAsTimeout() {
        ExplodingStubPlayer delegate = new ExplodingStubPlayer("ExplodingBot");
        wrapper = new TimedPlayerWrapper(delegate, Duration.ofSeconds(5), Duration.ofMinutes(2));

        GameState state = mock(GameState.class);
        EndPhaseDecision endPhase = new EndPhaseDecision(GameState.TurnPhase.ACTION);
        BuyDecision buyDecision = new BuyDecision(Card.Type.MODULE);
        ImmutableList<Decision> options = ImmutableList.of(buyDecision, endPhase);
        Optional<Event> event = Optional.empty();

        Decision result = wrapper.makeDecision(state, options, event);

        // Should return EndPhaseDecision as forfeit
        assertEquals(endPhase, result);

        TimingStats stats = wrapper.getTimingStats();
        assertEquals(1, stats.decisionCount());
        assertEquals(1, stats.timeoutCount());
    }

    @Test
    void getCumulativeTimeMs_tracksElapsedTime() {
        SlowStubPlayer delegate = new SlowStubPlayer("SlowBot", Duration.ofMillis(50));
        wrapper = new TimedPlayerWrapper(delegate, Duration.ofSeconds(5), Duration.ofMinutes(2));

        GameState state = mock(GameState.class);
        EndPhaseDecision endPhase = new EndPhaseDecision(GameState.TurnPhase.ACTION);
        ImmutableList<Decision> options = ImmutableList.of(endPhase);
        Optional<Event> event = Optional.empty();

        assertEquals(0, wrapper.getCumulativeTimeMs());

        wrapper.makeDecision(state, options, event);
        long afterFirst = wrapper.getCumulativeTimeMs();
        assertTrue(afterFirst >= 40, "Should have recorded at least ~50ms, got " + afterFirst);

        wrapper.makeDecision(state, options, event);
        long afterSecond = wrapper.getCumulativeTimeMs();
        assertTrue(afterSecond > afterFirst, "Cumulative time should increase after second call");
    }

    @Test
    void getTimingStats_returnsConsistentSnapshot() {
        StubPlayer delegate = new StubPlayer("Bot");
        wrapper = new TimedPlayerWrapper(delegate, Duration.ofSeconds(5), Duration.ofMinutes(2));

        TimingStats initial = wrapper.getTimingStats();
        assertEquals(0, initial.totalDecisionTimeMs());
        assertEquals(0, initial.decisionCount());
        assertEquals(0, initial.timeoutCount());
        assertFalse(initial.forfeited());
        assertNull(initial.decisionAtForfeit());
    }
}
