package edu.brandeis.cosi103a.tournament.runner;

import com.google.common.collect.ImmutableList;
import edu.brandeis.cosi.atg.cards.Card;
import edu.brandeis.cosi.atg.decisions.Decision;
import edu.brandeis.cosi.atg.decisions.EndPhaseDecision;
import edu.brandeis.cosi.atg.engine.Engine;
import edu.brandeis.cosi.atg.event.Event;
import edu.brandeis.cosi.atg.event.GameObserver;
import edu.brandeis.cosi.atg.player.Player;
import edu.brandeis.cosi.atg.state.GameResult;
import edu.brandeis.cosi.atg.state.GameState;
import edu.brandeis.cosi.atg.state.PlayerResult;
import edu.brandeis.cosi103a.tournament.engine.EngineLoader;
import edu.brandeis.cosi103a.tournament.player.TimingStats;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests TableExecutor's TimedPlayerWrapper integration — verifies that
 * timing stats are captured in Placement records when timeouts are configured.
 */
class TableExecutorTimingTest {

    /**
     * Stub player that returns the first option (or EndPhaseDecision) immediately.
     */
    private static class FastStubPlayer implements Player {
        private final String name;

        FastStubPlayer(String name) {
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
            return options.stream()
                    .filter(d -> d instanceof EndPhaseDecision)
                    .findFirst()
                    .orElse(options.get(0));
        }
    }

    @SuppressWarnings("unchecked")
    private EngineLoader mockEngineLoader(List<String> playerNames) throws Exception {
        // Create mock PlayerResult for each player
        List<PlayerResult> playerResults = playerNames.stream()
                .map(name -> {
                    PlayerResult pr = mock(PlayerResult.class);
                    when(pr.playerName()).thenReturn(name);
                    when(pr.score()).thenReturn(10);
                    when(pr.endingDeck()).thenReturn(ImmutableList.of());
                    return pr;
                })
                .toList();

        GameResult gameResult = mock(GameResult.class);
        when(gameResult.playerResults()).thenReturn(ImmutableList.copyOf(playerResults));

        Engine engine = mock(Engine.class);
        when(engine.play()).thenReturn(gameResult);

        EngineLoader loader = mock(EngineLoader.class);
        when(loader.create(any(), any())).thenReturn(engine);
        return loader;
    }

    @Test
    void executeTable_withTimeouts_capturesTimingStats() throws Exception {
        List<String> playerNames = List.of("Player 1", "Player 2", "Player 3", "Player 4");
        EngineLoader loader = mockEngineLoader(playerNames);

        // Create executor with timeouts, override createPlayer to use stubs
        TableExecutor executor = new TableExecutor(loader, null,
                Duration.ofSeconds(10), Duration.ofMinutes(5)) {
            @Override
            protected Player createPlayer(PlayerConfig config) {
                return new FastStubPlayer(config.name());
            }
        };

        List<PlayerConfig> players = List.of(
                new PlayerConfig("p1", "Player 1", "classpath:test", false),
                new PlayerConfig("p2", "Player 2", "classpath:test", false),
                new PlayerConfig("p3", "Player 3", "classpath:test", false),
                new PlayerConfig("p4", "Player 4", "classpath:test", false)
        );

        List<Card.Type> kingdom = List.of(Card.Type.REFACTOR, Card.Type.CODE_REVIEW);

        MatchResult result = executor.executeTable(1, players, kingdom, 1, 100);

        assertEquals(1, result.outcomes().size());
        GameOutcome outcome = result.outcomes().get(0);
        assertEquals(4, outcome.placements().size());

        // All placements should have timing stats since timeouts were configured
        for (Placement placement : outcome.placements()) {
            TimingStats stats = placement.timingStats();
            assertNotNull(stats, "Placement for " + placement.playerId() + " should have timing stats");
            assertFalse(stats.forfeited(), "Should not have forfeited with generous timeouts");
            assertEquals(0, stats.timeoutCount(), "Should have no timeouts with generous limits");
        }
    }

    @Test
    void executeTable_withoutTimeouts_noTimingStats() throws Exception {
        List<String> playerNames = List.of("Player 1", "Player 2", "Player 3", "Player 4");
        EngineLoader loader = mockEngineLoader(playerNames);

        // No timeouts configured
        TableExecutor executor = new TableExecutor(loader, null) {
            @Override
            protected Player createPlayer(PlayerConfig config) {
                return new FastStubPlayer(config.name());
            }
        };

        List<PlayerConfig> players = List.of(
                new PlayerConfig("p1", "Player 1", "classpath:test", false),
                new PlayerConfig("p2", "Player 2", "classpath:test", false),
                new PlayerConfig("p3", "Player 3", "classpath:test", false),
                new PlayerConfig("p4", "Player 4", "classpath:test", false)
        );

        List<Card.Type> kingdom = List.of(Card.Type.REFACTOR, Card.Type.CODE_REVIEW);

        MatchResult result = executor.executeTable(1, players, kingdom, 1, 100);

        // Placements should NOT have timing stats
        for (Placement placement : result.outcomes().get(0).placements()) {
            assertNull(placement.timingStats(), "Should have no timing stats without timeouts");
        }
    }
}
