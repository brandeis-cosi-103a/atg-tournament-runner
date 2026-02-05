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
    void tiedPlayers_getSameRank() {
        // Tied players should get the same rank
        int[] ranks = TrueSkillRatingCalculator.computeRanks(List.of(
                new Placement("a", 30),
                new Placement("b", 30),
                new Placement("c", 20),
                new Placement("d", 10)
        ));
        assertEquals(4, ranks.length);
        assertEquals(1, ranks[0], "a should be rank 1");
        assertEquals(1, ranks[1], "b should be rank 1 (tied with a)");
        assertEquals(3, ranks[2], "c should be rank 3");
        assertEquals(4, ranks[3], "d should be rank 4");
    }

    @Test
    void allTied_getSameRank() {
        // All tied players should get rank 1
        int[] ranks = TrueSkillRatingCalculator.computeRanks(List.of(
                new Placement("a", 20),
                new Placement("b", 20),
                new Placement("c", 20)
        ));
        assertEquals(1, ranks[0], "a should be rank 1");
        assertEquals(1, ranks[1], "b should be rank 1");
        assertEquals(1, ranks[2], "c should be rank 1");
    }

    @Test
    void computeRanks_isDeterministic() {
        // Same input should always produce same output
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
