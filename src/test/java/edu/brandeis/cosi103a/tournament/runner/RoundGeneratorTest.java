package edu.brandeis.cosi103a.tournament.runner;

import edu.brandeis.cosi.atg.cards.Card;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class RoundGeneratorTest {

    @Test
    void selectKingdomCards_returns10Cards() {
        List<Card.Type> cards = RoundGenerator.selectKingdomCards();
        assertEquals(10, cards.size());
    }

    @Test
    void selectKingdomCards_returnsOnlyActionCards() {
        List<Card.Type> cards = RoundGenerator.selectKingdomCards();
        for (Card.Type card : cards) {
            assertEquals(Card.Type.Category.ACTION, card.category(),
                "Kingdom card " + card + " should be an action card");
        }
    }

    // Tests for generateBalancedGames() - the core fair scheduling feature

    @Test
    void generateBalancedGames_allGamesHave4Players() {
        List<PlayerConfig> players = createPlayers(8);
        int gamesPerPlayer = 4;

        List<List<PlayerConfig>> games = RoundGenerator.generateBalancedGames(players, gamesPerPlayer);

        for (List<PlayerConfig> game : games) {
            assertEquals(4, game.size(),
                "Every game must have exactly 4 players for fair scoring");
        }
    }

    @Test
    void generateBalancedGames_eachPlayerPlaysExactlyGGames() {
        List<PlayerConfig> players = createPlayers(8);
        int gamesPerPlayer = 4;

        List<List<PlayerConfig>> games = RoundGenerator.generateBalancedGames(players, gamesPerPlayer);

        // Count games per player
        Map<PlayerConfig, Integer> gameCounts = new HashMap<>();
        for (List<PlayerConfig> game : games) {
            for (PlayerConfig player : game) {
                gameCounts.merge(player, 1, Integer::sum);
            }
        }

        // Verify each player played exactly gamesPerPlayer games
        for (PlayerConfig player : players) {
            assertEquals(gamesPerPlayer, gameCounts.get(player),
                "Player " + player.name() + " should play exactly " + gamesPerPlayer + " games");
        }
    }

    @Test
    void generateBalancedGames_correctTotalNumberOfGames() {
        List<PlayerConfig> players = createPlayers(12);
        int gamesPerPlayer = 4;

        List<List<PlayerConfig>> games = RoundGenerator.generateBalancedGames(players, gamesPerPlayer);

        // Total games = (N * gamesPerPlayer) / 4
        int expectedGames = (players.size() * gamesPerPlayer) / 4;
        assertEquals(expectedGames, games.size());
    }

    @Test
    void generateBalancedGames_rejectsFewerThan4Players() {
        List<PlayerConfig> players = createPlayers(3);
        int gamesPerPlayer = 4;

        assertThrows(IllegalArgumentException.class,
            () -> RoundGenerator.generateBalancedGames(players, gamesPerPlayer),
            "Should reject fewer than 4 players");
    }

    @Test
    void generateBalancedGames_adjustsInvalidGamesPerPlayer() {
        List<PlayerConfig> players = createPlayers(5);
        int gamesPerPlayer = 3; // 5 * 3 = 15, not divisible by 4

        // Should adjust to 4 games per player (5 * 4 = 20, divisible by 4)
        List<List<PlayerConfig>> games = RoundGenerator.generateBalancedGames(players, gamesPerPlayer);

        // Verify games were generated (5 * 4 / 4 = 5 total games)
        assertEquals(5, games.size());
        assertTrue(games.stream().allMatch(g -> g.size() == 4));
    }

    @Test
    void adjustGamesPerPlayer_returnsValidValue() {
        // 9 players, 25 games -> should adjust to 24 (9 * 24 = 216, divisible by 4)
        assertEquals(24, RoundGenerator.adjustGamesPerPlayer(9, 25));

        // Already valid: 8 players, 3 games -> 8 * 3 = 24, divisible by 4
        assertEquals(3, RoundGenerator.adjustGamesPerPlayer(8, 3));

        // 5 players, 3 games -> 5 * 3 = 15, adjust to 4 (5 * 4 = 20)
        assertEquals(4, RoundGenerator.adjustGamesPerPlayer(5, 3));

        // 7 players, 10 games -> 7 * 10 = 70, adjust to 8 (7 * 8 = 56)
        assertEquals(8, RoundGenerator.adjustGamesPerPlayer(7, 10));
    }

    @Test
    void generateBalancedGames_encouragesOpponentDiversity() {
        List<PlayerConfig> players = createPlayers(12);
        int gamesPerPlayer = 4;

        List<List<PlayerConfig>> games = RoundGenerator.generateBalancedGames(players, gamesPerPlayer);

        // Track how many times each pair of players face each other
        Map<Set<String>, Integer> pairings = new HashMap<>();
        for (List<PlayerConfig> game : games) {
            List<String> playerIds = game.stream().map(PlayerConfig::id).toList();
            for (int i = 0; i < playerIds.size(); i++) {
                for (int j = i + 1; j < playerIds.size(); j++) {
                    Set<String> pair = Set.of(playerIds.get(i), playerIds.get(j));
                    pairings.merge(pair, 1, Integer::sum);
                }
            }
        }

        // With 12 players, each playing 4 games, opponent diversity should be good
        // Count how many pairs never play together vs. play multiple times
        long neverPaired = pairings.values().stream().filter(count -> count == 0).count();
        long pairedOnce = pairings.values().stream().filter(count -> count == 1).count();
        long pairedMultiple = pairings.values().stream().filter(count -> count > 1).count();

        // Most pairs should play together at most once (good diversity)
        assertTrue(pairedOnce + neverPaired > pairedMultiple,
            "Algorithm should encourage opponent diversity (once+never=" + (pairedOnce + neverPaired) +
            ", multiple=" + pairedMultiple + ")");
    }

    @Test
    void recommendedGamesPerPlayer_findsSmallestValid() {
        // 4 players: 1*4=4 (divisible), so should return 1
        assertEquals(1, RoundGenerator.recommendedGamesPerPlayer(4));

        // 5 players: need 4 games (5*4=20, divisible)
        assertEquals(4, RoundGenerator.recommendedGamesPerPlayer(5));

        // 8 players: 1*8=8 (divisible), so should return 1
        assertEquals(1, RoundGenerator.recommendedGamesPerPlayer(8));

        // 6 players: need 2 games (6*2=12, divisible)
        assertEquals(2, RoundGenerator.recommendedGamesPerPlayer(6));
    }

    @Test
    void generateBalancedGames_worksWithDifferentPlayerCounts() {
        // Test various valid configurations
        testBalancedGamesConfiguration(4, 1); // 4 players, 1 game each = 1 total game
        testBalancedGamesConfiguration(8, 2); // 8 players, 2 games each = 4 total games
        testBalancedGamesConfiguration(12, 4); // 12 players, 4 games each = 12 total games
        testBalancedGamesConfiguration(6, 2); // 6 players, 2 games each = 3 total games
    }

    private void testBalancedGamesConfiguration(int numPlayers, int gamesPerPlayer) {
        List<PlayerConfig> players = createPlayers(numPlayers);
        List<List<PlayerConfig>> games = RoundGenerator.generateBalancedGames(players, gamesPerPlayer);

        // All games have 4 players
        assertTrue(games.stream().allMatch(g -> g.size() == 4),
            "All games should have 4 players for " + numPlayers + " players");

        // Each player plays exactly gamesPerPlayer games
        Map<PlayerConfig, Long> counts = games.stream()
            .flatMap(List::stream)
            .collect(Collectors.groupingBy(p -> p, Collectors.counting()));
        for (PlayerConfig player : players) {
            assertEquals(gamesPerPlayer, counts.get(player).intValue(),
                "Player should play exactly " + gamesPerPlayer + " games");
        }
    }

    private List<PlayerConfig> createPlayers(int count) {
        List<PlayerConfig> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            players.add(new PlayerConfig("player" + i, "Player" + i, "big-money", false));
        }
        return players;
    }

    @Test
    void shuffleIntoTables_creates3or4PlayerTables() {
        List<PlayerConfig> players = List.of(
            new PlayerConfig("a", "Alice", "big-money", false),
            new PlayerConfig("b", "Bob", "big-money", false),
            new PlayerConfig("c", "Charlie", "big-money", false),
            new PlayerConfig("d", "Diana", "big-money", false),
            new PlayerConfig("e", "Eve", "big-money", false),
            new PlayerConfig("f", "Frank", "big-money", false),
            new PlayerConfig("g", "Grace", "big-money", false)
        );
        List<List<PlayerConfig>> tables = RoundGenerator.shuffleIntoTables(players);
        for (List<PlayerConfig> table : tables) {
            assertTrue(table.size() >= 3 && table.size() <= 4,
                "Table size should be 3 or 4, was " + table.size());
        }
        // All players assigned
        int total = tables.stream().mapToInt(List::size).sum();
        assertEquals(players.size(), total);
    }

    @Test
    void shuffleIntoTables_exactlyThreePlayers() {
        List<PlayerConfig> players = List.of(
            new PlayerConfig("a", "Alice", "big-money", false),
            new PlayerConfig("b", "Bob", "big-money", false),
            new PlayerConfig("c", "Charlie", "big-money", false)
        );
        List<List<PlayerConfig>> tables = RoundGenerator.shuffleIntoTables(players);
        assertEquals(1, tables.size());
        assertEquals(3, tables.get(0).size());
    }

    @Test
    void shuffleIntoTables_fivePlayers_twoTables() {
        List<PlayerConfig> players = List.of(
            new PlayerConfig("a", "A", "big-money", false),
            new PlayerConfig("b", "B", "big-money", false),
            new PlayerConfig("c", "C", "big-money", false),
            new PlayerConfig("d", "D", "big-money", false),
            new PlayerConfig("e", "E", "big-money", false)
        );
        List<List<PlayerConfig>> tables = RoundGenerator.shuffleIntoTables(players);
        assertEquals(2, tables.size());
        for (List<PlayerConfig> table : tables) {
            assertTrue(table.size() >= 2 && table.size() <= 4);
        }
    }
}
