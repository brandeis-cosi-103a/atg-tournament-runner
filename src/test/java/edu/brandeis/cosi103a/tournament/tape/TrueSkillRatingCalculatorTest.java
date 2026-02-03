package edu.brandeis.cosi103a.tournament.tape;

import de.gesundkrank.jskills.GameInfo;
import de.gesundkrank.jskills.Rating;
import edu.brandeis.cosi103a.tournament.runner.Placement;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates TrueSkillRatingCalculator against known reference values
 * from the JSkills test suite (originally validated against the C# implementation).
 */
class TrueSkillRatingCalculatorTest {

    private static final double TOLERANCE = 0.1;

    @Test
    void fourPlayersFreeForAll_matchesReferenceValues() {
        // Reference: FourTeamsOfOneNotDrawn from JSkills test suite
        // Players finish 1st, 2nd, 3rd, 4th with default GameInfo
        GameInfo gameInfo = GameInfo.getDefaultGameInfo();
        Rating defaultRating = gameInfo.getDefaultRating();

        Map<String, Rating> ratings = new HashMap<>();
        ratings.put("p1", defaultRating);
        ratings.put("p2", defaultRating);
        ratings.put("p3", defaultRating);
        ratings.put("p4", defaultRating);

        // Placements: p1 highest score, p4 lowest
        List<Placement> placements = List.of(
                new Placement("p1", 40),
                new Placement("p2", 30),
                new Placement("p3", 20),
                new Placement("p4", 10)
        );

        Map<String, Rating> result = TrueSkillRatingCalculator.update(ratings, placements, gameInfo);

        assertEquals(33.207, result.get("p1").getMean(), TOLERANCE, "p1 mu");
        assertEquals(6.348, result.get("p1").getStandardDeviation(), TOLERANCE, "p1 sigma");

        assertEquals(27.402, result.get("p2").getMean(), TOLERANCE, "p2 mu");
        assertEquals(5.787, result.get("p2").getStandardDeviation(), TOLERANCE, "p2 sigma");

        assertEquals(22.599, result.get("p3").getMean(), TOLERANCE, "p3 mu");
        assertEquals(5.787, result.get("p3").getStandardDeviation(), TOLERANCE, "p3 sigma");

        assertEquals(16.793, result.get("p4").getMean(), TOLERANCE, "p4 mu");
        assertEquals(6.348, result.get("p4").getStandardDeviation(), TOLERANCE, "p4 sigma");
    }

    @Test
    void tiedPlayers_getUniqueRanks() {
        // Ties are broken pseudorandomly to ensure TrueSkill convergence
        int[] ranks = TrueSkillRatingCalculator.computeRanks(List.of(
                new Placement("a", 30),
                new Placement("b", 30),
                new Placement("c", 20),
                new Placement("d", 10)
        ));
        // Tied players (a, b) get unique ranks 1 and 2 (order is deterministic but pseudorandom)
        // Non-tied players get ranks 3 and 4
        assertEquals(4, ranks.length);
        // a and b should have ranks 1 and 2 (in some order)
        assertTrue((ranks[0] == 1 && ranks[1] == 2) || (ranks[0] == 2 && ranks[1] == 1),
                "Tied players should get ranks 1 and 2");
        assertEquals(3, ranks[2], "c should be rank 3");
        assertEquals(4, ranks[3], "d should be rank 4");
    }

    @Test
    void allTied_getUniqueRanks() {
        // All ties broken pseudorandomly
        int[] ranks = TrueSkillRatingCalculator.computeRanks(List.of(
                new Placement("a", 20),
                new Placement("b", 20),
                new Placement("c", 20)
        ));
        // All players should get unique ranks 1, 2, 3
        java.util.Set<Integer> uniqueRanks = new java.util.HashSet<>();
        for (int r : ranks) {
            uniqueRanks.add(r);
        }
        assertEquals(3, uniqueRanks.size(), "All ranks should be unique");
        assertTrue(uniqueRanks.contains(1) && uniqueRanks.contains(2) && uniqueRanks.contains(3),
                "Ranks should be 1, 2, and 3");
    }

    @Test
    void tieBreaking_isDeterministic() {
        // Same input should always produce same output (pseudorandom, not random)
        List<Placement> placements = List.of(
                new Placement("alice", 30),
                new Placement("bob", 30),
                new Placement("carol", 20)
        );
        int[] ranks1 = TrueSkillRatingCalculator.computeRanks(placements);
        int[] ranks2 = TrueSkillRatingCalculator.computeRanks(placements);
        assertArrayEquals(ranks1, ranks2, "Same input should produce same ranks");
    }

    @Test
    void nonParticipants_unchanged() {
        GameInfo gameInfo = GameInfo.getDefaultGameInfo();
        Rating defaultRating = gameInfo.getDefaultRating();
        Rating customRating = new Rating(30.0, 5.0);

        Map<String, Rating> ratings = new HashMap<>();
        ratings.put("p1", defaultRating);
        ratings.put("p2", defaultRating);
        ratings.put("spectator", customRating);

        List<Placement> placements = List.of(
                new Placement("p1", 20),
                new Placement("p2", 10)
        );

        Map<String, Rating> result = TrueSkillRatingCalculator.update(ratings, placements, gameInfo);

        assertEquals(30.0, result.get("spectator").getMean(), 0.001, "spectator mu unchanged");
        assertEquals(5.0, result.get("spectator").getStandardDeviation(), 0.001, "spectator sigma unchanged");
    }

    @Test
    void conservativeRating_isMuMinus3Sigma() {
        Rating r = new Rating(25.0, 8.333);
        assertEquals(25.0 - 3 * 8.333, TrueSkillRatingCalculator.conservativeRating(r), 0.01);
    }
}
