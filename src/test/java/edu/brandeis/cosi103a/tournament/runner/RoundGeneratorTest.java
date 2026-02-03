package edu.brandeis.cosi103a.tournament.runner;

import edu.brandeis.cosi.atg.cards.Card;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

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

    @Test
    void shuffleIntoTables_creates3or4PlayerTables() {
        List<PlayerConfig> players = List.of(
            new PlayerConfig("a", "Alice", "big-money"),
            new PlayerConfig("b", "Bob", "big-money"),
            new PlayerConfig("c", "Charlie", "big-money"),
            new PlayerConfig("d", "Diana", "big-money"),
            new PlayerConfig("e", "Eve", "big-money"),
            new PlayerConfig("f", "Frank", "big-money"),
            new PlayerConfig("g", "Grace", "big-money")
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
            new PlayerConfig("a", "Alice", "big-money"),
            new PlayerConfig("b", "Bob", "big-money"),
            new PlayerConfig("c", "Charlie", "big-money")
        );
        List<List<PlayerConfig>> tables = RoundGenerator.shuffleIntoTables(players);
        assertEquals(1, tables.size());
        assertEquals(3, tables.get(0).size());
    }

    @Test
    void shuffleIntoTables_fivePlayers_twoTables() {
        List<PlayerConfig> players = List.of(
            new PlayerConfig("a", "A", "big-money"),
            new PlayerConfig("b", "B", "big-money"),
            new PlayerConfig("c", "C", "big-money"),
            new PlayerConfig("d", "D", "big-money"),
            new PlayerConfig("e", "E", "big-money")
        );
        List<List<PlayerConfig>> tables = RoundGenerator.shuffleIntoTables(players);
        assertEquals(2, tables.size());
        for (List<PlayerConfig> table : tables) {
            assertTrue(table.size() >= 2 && table.size() <= 4);
        }
    }
}
