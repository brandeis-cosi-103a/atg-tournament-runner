package edu.brandeis.cosi103a.tournament.runner;

import edu.brandeis.cosi.atg.cards.Card;
import edu.brandeis.cosi.atg.engine.Engine;
import edu.brandeis.cosi.atg.player.Player;
import edu.brandeis.cosi.atg.state.GameResult;
import edu.brandeis.cosi.atg.state.PlayerResult;
import edu.brandeis.cosi103a.tournament.engine.EngineLoader;
import edu.brandeis.cosi103a.tournament.network.NetworkPlayer;
import edu.brandeis.cosi103a.tournament.player.DelayedPlayerWrapper;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Executes all games for a single table within a round.
 */
public class TableExecutor {

    private final EngineLoader engineLoader;
    private final PlayerDiscoveryService discoveryService;

    /**
     * Creates a TableExecutor with the given engine loader.
     * Uses no discovery service (backward compatibility for CLI mode).
     *
     * @param engineLoader the loader for creating Engine instances
     */
    public TableExecutor(EngineLoader engineLoader) {
        this(engineLoader, null);
    }

    /**
     * Creates a TableExecutor with the given engine loader and discovery service.
     *
     * @param engineLoader     the loader for creating Engine instances
     * @param discoveryService the service for discovering classpath players (may be null)
     */
    public TableExecutor(EngineLoader engineLoader, PlayerDiscoveryService discoveryService) {
        this.engineLoader = engineLoader;
        this.discoveryService = discoveryService;
    }

    /**
     * Executes all games for a table and returns the match result.
     *
     * @param tableNumber   the table number (1-based)
     * @param playerConfigs the players at this table
     * @param kingdomCards  the kingdom cards for this round
     * @param gamesPerTable number of games to play
     * @param maxTurns      max turns per game (unused in current implementation)
     * @return the match result containing all game outcomes
     */
    public MatchResult executeTable(int tableNumber, List<PlayerConfig> playerConfigs,
                                     List<Card.Type> kingdomCards, int gamesPerTable, int maxTurns) {
        List<String> playerIds = playerConfigs.stream().map(PlayerConfig::id).toList();
        // Build a name-to-id mapping for resolving game results
        Map<String, String> nameToId = new ConcurrentHashMap<>();
        List<GameOutcome> outcomes = new ArrayList<>();

        for (int gameIndex = 0; gameIndex < gamesPerTable; gameIndex++) {
            List<Player> players = createPlayers(playerConfigs, nameToId);
            try {
                Engine engine = engineLoader.create(players, kingdomCards);
                GameResult result = engine.play();
                List<Placement> placements = new ArrayList<>();
                for (PlayerResult pr : result.playerResults()) {
                    String id = nameToId.getOrDefault(pr.playerName(), pr.playerName().toLowerCase());
                    List<String> deckTypes = pr.endingDeck().stream()
                        .map(card -> card.type().name())
                        .toList();
                    placements.add(new Placement(id, pr.score(), deckTypes));
                }
                outcomes.add(new GameOutcome(gameIndex, placements));
            } catch (Exception e) {
                // Game timeout, engine error, or player violation: all players get score 0
                List<Placement> placements = playerIds.stream()
                    .map(id -> new Placement(id, 0))
                    .toList();
                outcomes.add(new GameOutcome(gameIndex, placements));
            }
        }

        return new MatchResult(tableNumber, playerIds, outcomes);
    }

    private List<Player> createPlayers(List<PlayerConfig> configs, Map<String, String> nameToId) {
        List<Player> players = new ArrayList<>();
        for (PlayerConfig config : configs) {
            Player player = createPlayer(config);
            if (config.delay()) {
                player = new DelayedPlayerWrapper(player, 2, 5);
            }
            nameToId.put(player.getName(), config.id());
            players.add(player);
        }
        return players;
    }

    /**
     * Creates a Player instance based on the configuration.
     * Supports:
     * <ul>
     *   <li>Classpath players: classpath:com.example.MyPlayer</li>
     *   <li>Network player URLs: https://...</li>
     * </ul>
     *
     * @param config the player configuration
     * @return a Player instance
     */
    protected Player createPlayer(PlayerConfig config) {
        String url = config.url();

        // Handle classpath: scheme (full class name)
        if (url.startsWith("classpath:")) {
            String className = url.substring("classpath:".length());
            return createFromClassName(className, config.name());
        }

        // Default: treat as network player URL
        return new NetworkPlayer(config.name(), url);
    }

    private Player createFromClassName(String className, String playerName) {
        // Try discovery service first if available
        if (discoveryService != null) {
            var discovered = discoveryService.findByClassName(className);
            if (discovered.isPresent()) {
                try {
                    return discoveryService.createPlayer(discovered.get(), playerName);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException("Failed to create player from " + className, e);
                }
            }
        }

        // Fall back to direct reflection
        return createViaReflection(className, playerName);
    }

    private Player createViaReflection(String className, String playerName) {
        try {
            Class<?> playerClass = Class.forName(className);
            // Try name constructor first
            try {
                Constructor<?> ctor = playerClass.getConstructor(String.class);
                return (Player) ctor.newInstance(playerName);
            } catch (NoSuchMethodException e) {
                // Fall back to zero-arg
                Constructor<?> ctor = playerClass.getConstructor();
                return (Player) ctor.newInstance();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create player via reflection: " + className, e);
        }
    }
}
