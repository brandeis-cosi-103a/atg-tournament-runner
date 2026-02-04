package edu.brandeis.cosi103a.tournament.runner;

import edu.brandeis.cosi.atg.player.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlayerDiscoveryServiceTest {

    private PlayerDiscoveryService service;

    @BeforeEach
    void setUp() {
        service = new PlayerDiscoveryService();
        service.initialize();
    }

    @Test
    void discoversBuiltInPlayers() {
        List<DiscoveredPlayer> players = service.getDiscoveredPlayers();

        // Should find the built-in tournament runner players
        assertTrue(players.stream().anyMatch(p -> p.simpleName().equals("NaiveBigMoneyPlayer")),
            "Should discover NaiveBigMoneyPlayer");
        assertTrue(players.stream().anyMatch(p -> p.simpleName().equals("ActionHeavyPlayer")),
            "Should discover ActionHeavyPlayer");
        assertTrue(players.stream().anyMatch(p -> p.simpleName().equals("RandomPlayer")),
            "Should discover RandomPlayer");
    }

    @Test
    void findsPlayerByClassName() {
        var player = service.findByClassName("edu.brandeis.cosi103a.tournament.player.NaiveBigMoneyPlayer");

        assertTrue(player.isPresent());
        assertEquals("NaiveBigMoneyPlayer", player.get().simpleName());
    }

    @Test
    void returnsEmptyForUnknownClassName() {
        var player = service.findByClassName("com.example.NonexistentPlayer");

        assertTrue(player.isEmpty());
    }

    @Test
    void createsPlayerWithName() throws Exception {
        var discovered = service.findByClassName("edu.brandeis.cosi103a.tournament.player.NaiveBigMoneyPlayer").orElseThrow();

        Player player = service.createPlayer(discovered, "TestBot");

        assertEquals("TestBot", player.getName());
    }

    @Test
    void discoveredPlayerHasNameConstructor() {
        var player = service.findByClassName("edu.brandeis.cosi103a.tournament.player.NaiveBigMoneyPlayer").orElseThrow();

        assertTrue(player.hasNameConstructor(),
            "NaiveBigMoneyPlayer should have a String (name) constructor");
        assertTrue(player.isInstantiable(),
            "Player should be instantiable");
    }

    @Test
    void getDiscoveredPlayersReturnsSortedList() {
        List<DiscoveredPlayer> players = service.getDiscoveredPlayers();

        // Verify sorted alphabetically by displayName
        for (int i = 1; i < players.size(); i++) {
            assertTrue(players.get(i - 1).displayName().compareTo(players.get(i).displayName()) <= 0,
                "Players should be sorted by displayName: " + players.get(i - 1).displayName() +
                " should come before " + players.get(i).displayName());
        }
    }

    @Test
    void clearRemovesAllDiscoveredPlayers() {
        service.clear();

        List<DiscoveredPlayer> players = service.getDiscoveredPlayers();
        assertTrue(players.isEmpty(), "After clear(), discovered players should be empty");
    }

    @Test
    void rescanFindsPlayersAgain() {
        service.clear();
        assertTrue(service.getDiscoveredPlayers().isEmpty());

        service.scanClasspath();

        assertFalse(service.getDiscoveredPlayers().isEmpty(),
            "After rescan, should find players again");
    }

    @Test
    void displayNameEqualsSimpleNameWhenUnique() {
        // In the tournament-runner, player class names should be unique
        var player = service.findByClassName("edu.brandeis.cosi103a.tournament.player.NaiveBigMoneyPlayer").orElseThrow();

        assertEquals(player.simpleName(), player.displayName(),
            "displayName should equal simpleName when no duplicates exist");
    }
}
