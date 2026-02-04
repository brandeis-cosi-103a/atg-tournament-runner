package edu.brandeis.cosi103a.tournament.tape;

import de.gesundkrank.jskills.GameInfo;
import de.gesundkrank.jskills.Rating;
import edu.brandeis.cosi103a.tournament.runner.Placement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for TrueSkillTracker, ensuring it correctly tracks ratings and points
 * across multiple games.
 */
class TrueSkillTrackerTest {

    private static final double TOLERANCE = 0.1;

    @Test
    void initializesAllPlayersWithDefaultRating() {
        GameInfo gameInfo = GameInfo.getDefaultGameInfo();
        TrueSkillTracker tracker = new TrueSkillTracker(
            List.of("p1", "p2", "p3"), gameInfo
        );

        Map<String, Rating> ratings = tracker.getCurrentRatings();
        assertEquals(3, ratings.size(), "Should have 3 players");
        assertEquals(25.0, ratings.get("p1").getMean(), 0.01, "p1 initial mu");
        assertEquals(25.0, ratings.get("p2").getMean(), 0.01, "p2 initial mu");
        assertEquals(25.0, ratings.get("p3").getMean(), 0.01, "p3 initial mu");

        Map<String, Integer> points = tracker.getCurrentPoints();
        assertEquals(0, points.get("p1"), "p1 initial points");
        assertEquals(0, points.get("p2"), "p2 initial points");
        assertEquals(0, points.get("p3"), "p3 initial points");
    }

    @Test
    void processGameUpdatesRatingsAndPoints() {
        GameInfo gameInfo = GameInfo.getDefaultGameInfo();
        TrueSkillTracker tracker = new TrueSkillTracker(
            List.of("p1", "p2", "p3", "p4"), gameInfo
        );

        // Play a 4-player game: p1 first, p2 second, p3 third, p4 fourth
        List<Placement> placements = List.of(
            new Placement("p1", 40),
            new Placement("p2", 30),
            new Placement("p3", 20),
            new Placement("p4", 10)
        );
        tracker.processGame(placements);

        // Verify ratings changed from initial values
        Map<String, Rating> ratings = tracker.getCurrentRatings();
        assertTrue(ratings.get("p1").getMean() > 25.0, "p1 rating should increase");
        assertTrue(ratings.get("p4").getMean() < 25.0, "p4 rating should decrease");

        // Verify placement points: 4 points for 1st, 3 for 2nd, 2 for 3rd, 1 for 4th
        Map<String, Integer> points = tracker.getCurrentPoints();
        assertEquals(4, points.get("p1"), "p1 should earn 4 points for 1st");
        assertEquals(3, points.get("p2"), "p2 should earn 3 points for 2nd");
        assertEquals(2, points.get("p3"), "p3 should earn 2 points for 3rd");
        assertEquals(1, points.get("p4"), "p4 should earn 1 point for 4th");
    }

    @Test
    void multipleGamesAccumulatePointsAndUpdateRatings() {
        GameInfo gameInfo = GameInfo.getDefaultGameInfo();
        TrueSkillTracker tracker = new TrueSkillTracker(
            List.of("alice", "bob"), gameInfo
        );

        // Game 1: alice wins
        tracker.processGame(List.of(
            new Placement("alice", 30),
            new Placement("bob", 20)
        ));

        Map<String, Integer> pointsAfterGame1 = tracker.getCurrentPoints();
        assertEquals(2, pointsAfterGame1.get("alice"), "alice earns 2 points");
        assertEquals(1, pointsAfterGame1.get("bob"), "bob earns 1 point");

        Rating aliceAfterGame1 = tracker.getCurrentRatings().get("alice");

        // Game 2: alice wins again
        tracker.processGame(List.of(
            new Placement("alice", 35),
            new Placement("bob", 15)
        ));

        Map<String, Integer> pointsAfterGame2 = tracker.getCurrentPoints();
        assertEquals(4, pointsAfterGame2.get("alice"), "alice earns 2 more points (total 4)");
        assertEquals(2, pointsAfterGame2.get("bob"), "bob earns 1 more point (total 2)");

        // Verify ratings continue to update
        Rating aliceAfterGame2 = tracker.getCurrentRatings().get("alice");
        assertNotEquals(aliceAfterGame1.getMean(), aliceAfterGame2.getMean(),
            "alice rating should change after game 2");
    }

    @Test
    void threePlayerGamePointsDistribution() {
        GameInfo gameInfo = GameInfo.getDefaultGameInfo();
        TrueSkillTracker tracker = new TrueSkillTracker(
            List.of("a", "b", "c"), gameInfo
        );

        tracker.processGame(List.of(
            new Placement("a", 30),
            new Placement("b", 20),
            new Placement("c", 10)
        ));

        Map<String, Integer> points = tracker.getCurrentPoints();
        assertEquals(3, points.get("a"), "1st in 3-player game earns 3 points");
        assertEquals(2, points.get("b"), "2nd in 3-player game earns 2 points");
        assertEquals(1, points.get("c"), "3rd in 3-player game earns 1 point");
    }

    @Test
    void getConservativeRating_returnsCorrectValue() {
        GameInfo gameInfo = GameInfo.getDefaultGameInfo();
        TrueSkillTracker tracker = new TrueSkillTracker(
            List.of("p1"), gameInfo
        );

        // Default rating: mu=25, sigma=8.333...
        double conservative = tracker.getConservativeRating("p1");
        Rating rating = tracker.getCurrentRatings().get("p1");
        double expected = rating.getMean() - 3.0 * rating.getStandardDeviation();
        assertEquals(expected, conservative, 0.01, "Conservative rating should be mu - 3*sigma");
    }

    @Test
    void getCurrentRatings_returnsImmutableCopy() {
        GameInfo gameInfo = GameInfo.getDefaultGameInfo();
        TrueSkillTracker tracker = new TrueSkillTracker(
            List.of("p1", "p2"), gameInfo
        );

        Map<String, Rating> ratings1 = tracker.getCurrentRatings();
        tracker.processGame(List.of(
            new Placement("p1", 30),
            new Placement("p2", 20)
        ));
        Map<String, Rating> ratings2 = tracker.getCurrentRatings();

        // Verify first snapshot was not affected by game processing
        assertEquals(25.0, ratings1.get("p1").getMean(), 0.01,
            "First snapshot should retain original values");
        assertNotEquals(ratings1.get("p1").getMean(), ratings2.get("p1").getMean(),
            "Second snapshot should have updated values");
    }

    @Test
    void getCurrentPoints_returnsImmutableCopy() {
        GameInfo gameInfo = GameInfo.getDefaultGameInfo();
        TrueSkillTracker tracker = new TrueSkillTracker(
            List.of("p1", "p2"), gameInfo
        );

        Map<String, Integer> points1 = tracker.getCurrentPoints();
        tracker.processGame(List.of(
            new Placement("p1", 30),
            new Placement("p2", 20)
        ));
        Map<String, Integer> points2 = tracker.getCurrentPoints();

        // Verify first snapshot was not affected by game processing
        assertEquals(0, points1.get("p1"), "First snapshot should retain original values");
        assertEquals(2, points2.get("p1"), "Second snapshot should have updated values");
    }
}
