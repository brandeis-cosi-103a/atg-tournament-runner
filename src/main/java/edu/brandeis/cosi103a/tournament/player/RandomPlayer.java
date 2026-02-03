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
 * A random player that chooses decisions uniformly at random.
 * Used as a baseline bot for testing and practice.
 */
public class RandomPlayer implements Player {
    private final String name;
    private final Random random;

    public RandomPlayer() {
        this("Random");
    }

    public RandomPlayer(String name) {
        this.name = name;
        this.random = new Random();
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
        if (options.isEmpty()) {
            throw new IllegalStateException("No decisions available");
        }
        return options.get(random.nextInt(options.size()));
    }
}
