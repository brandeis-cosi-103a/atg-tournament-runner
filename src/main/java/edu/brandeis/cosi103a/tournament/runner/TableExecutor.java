package edu.brandeis.cosi103a.tournament.runner;

import edu.brandeis.cosi.atg.cards.Card;
import edu.brandeis.cosi.atg.engine.Engine;
import edu.brandeis.cosi.atg.player.Player;
import edu.brandeis.cosi.atg.state.GameResult;
import edu.brandeis.cosi.atg.state.PlayerResult;
import edu.brandeis.cosi103a.tournament.engine.EngineLoader;
import edu.brandeis.cosi103a.tournament.network.NetworkPlayer;
import edu.brandeis.cosi103a.tournament.player.ActionHeavyPlayer;
import edu.brandeis.cosi103a.tournament.player.DelayedPlayerWrapper;
import edu.brandeis.cosi103a.tournament.player.NaiveBigMoneyPlayer;
import edu.brandeis.cosi103a.tournament.player.RandomPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Executes all games for a single table within a round.
 */
public class TableExecutor {

    private final EngineLoader engineLoader;

    /**
     * Creates a TableExecutor with the given engine loader.
     *
     * @param engineLoader the loader for creating Engine instances
     */
    public TableExecutor(EngineLoader engineLoader) {
        this.engineLoader = engineLoader;
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
                    placements.add(new Placement(id, pr.score()));
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
     * Supports built-in bot aliases and network player URLs.
     *
     * @param config the player configuration
     * @return a Player instance
     */
    protected Player createPlayer(PlayerConfig config) {
        return switch (config.url()) {
            case "naive-money" -> new NaiveBigMoneyPlayer(config.name());
            case "action-heavy" -> new ActionHeavyPlayer(config.name());
            case "random" -> new RandomPlayer(config.name());
            default -> new NetworkPlayer(config.name(), config.url());
        };
    }
}
