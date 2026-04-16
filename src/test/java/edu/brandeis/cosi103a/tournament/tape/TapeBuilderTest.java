package edu.brandeis.cosi103a.tournament.tape;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gesundkrank.jskills.GameInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TapeBuilderTest {

    private static final GameInfo GAME_INFO = GameInfo.getDefaultGameInfo();

    @Test
    void buildTape_producesCorrectStructure(@TempDir Path tempDir) throws Exception {
        copyResource("sample-tournament/tournament.json", tempDir.resolve("tournament.json"));
        copyResource("sample-tournament/round-01.json", tempDir.resolve("round-01.json"));
        copyResource("sample-tournament/round-02.json", tempDir.resolve("round-02.json"));

        TapeBuilder.buildTape(tempDir, GAME_INFO);

        Path tapeFile = tempDir.resolve("tape.json");
        assertTrue(Files.exists(tapeFile), "tape.json should be created");

        var mapper = new ObjectMapper();
        JsonNode tape = mapper.readTree(tapeFile.toFile());

        // Check players
        assertTrue(tape.has("players"));
        assertEquals(4, tape.get("players").size());
        assertEquals("p1", tape.get("players").get(0).get("id").asText());

        // Check scoring config
        assertTrue(tape.has("scoring"));
        assertEquals("trueskill", tape.get("scoring").get("model").asText());

        // Check events: round-01 has 2 games, round-02 has 1 game = 3 events
        assertTrue(tape.has("events"));
        assertEquals(3, tape.get("events").size());

        // First event
        JsonNode event0 = tape.get("events").get(0);
        assertEquals(0, event0.get("seq").asInt());
        assertEquals(1, event0.get("round").asInt());
        assertEquals(0, event0.get("game").asInt());
        assertTrue(event0.has("tables"));
        assertTrue(event0.has("kingdomCards"));
        assertTrue(event0.has("placements"));
        assertTrue(event0.has("ratings"));

        // Ratings map should have all 4 players
        assertEquals(4, event0.get("ratings").size());

        // Sequential ordering
        JsonNode event1 = tape.get("events").get(1);
        assertEquals(1, event1.get("seq").asInt());

        JsonNode event2 = tape.get("events").get(2);
        assertEquals(2, event2.get("seq").asInt());
        assertEquals(2, event2.get("round").asInt());
    }

    @Test
    void buildTape_ratingsEvolveAcrossEvents(@TempDir Path tempDir) throws Exception {
        copyResource("sample-tournament/tournament.json", tempDir.resolve("tournament.json"));
        copyResource("sample-tournament/round-01.json", tempDir.resolve("round-01.json"));
        copyResource("sample-tournament/round-02.json", tempDir.resolve("round-02.json"));

        TapeBuilder.buildTape(tempDir, GAME_INFO);

        var mapper = new ObjectMapper();
        JsonNode tape = mapper.readTree(tempDir.resolve("tape.json").toFile());

        // Initial conservative rating is 0 (25 - 3*8.333)
        double initialRating = tape.get("scoring").get("initial").asDouble();

        // After first game, ratings should differ from initial
        JsonNode ratings0 = tape.get("events").get(0).get("ratings");
        boolean anyChanged = false;
        var fields = ratings0.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            if (Math.abs(entry.getValue().asDouble() - initialRating) > 0.01) {
                anyChanged = true;
                break;
            }
        }
        assertTrue(anyChanged, "Ratings should change after first game");

        // Ratings in event 2 should differ from event 1 (they evolve)
        JsonNode ratings1 = tape.get("events").get(1).get("ratings");
        JsonNode ratings2 = tape.get("events").get(2).get("ratings");
        assertNotEquals(
                ratings1.get("p1").asDouble(),
                ratings2.get("p1").asDouble(),
                "Ratings should evolve between events"
        );
    }

    @Test
    void buildTape_missingTournamentJson_throwsError(@TempDir Path tempDir) {
        assertThrows(IllegalArgumentException.class, () -> TapeBuilder.buildTape(tempDir, GAME_INFO));
    }

    @Test
    void main_missingTournamentArg_throws() {
        assertThrows(IllegalArgumentException.class, () -> TapeBuilder.main(new String[]{}));
    }

    @Test
    void main_unknownArg_throws() {
        assertThrows(IllegalArgumentException.class, () -> TapeBuilder.main(new String[]{"--bogus"}));
    }

    @Test
    void main_withValidArgs_producesTape(@TempDir Path tempDir) throws Exception {
        copyResource("sample-tournament/tournament.json", tempDir.resolve("tournament.json"));
        copyResource("sample-tournament/round-01.json", tempDir.resolve("round-01.json"));

        TapeBuilder.main(new String[]{"--tournament", tempDir.toString()});

        assertTrue(Files.exists(tempDir.resolve("tape.json")));
        var mapper = new ObjectMapper();
        JsonNode tape = mapper.readTree(tempDir.resolve("tape.json").toFile());
        assertEquals("trueskill", tape.get("scoring").get("model").asText());
    }

    @Test
    void main_kFactorIgnored(@TempDir Path tempDir) throws Exception {
        copyResource("sample-tournament/tournament.json", tempDir.resolve("tournament.json"));
        copyResource("sample-tournament/round-01.json", tempDir.resolve("round-01.json"));

        // --k-factor is accepted for backwards compat but ignored
        TapeBuilder.main(new String[]{"--tournament", tempDir.toString(), "--k-factor", "24"});

        assertTrue(Files.exists(tempDir.resolve("tape.json")));
    }

    @Test
    void buildTape_aggregatesTimingStats(@TempDir Path tempDir) throws Exception {
        copyResource("sample-tournament-timed/tournament.json", tempDir.resolve("tournament.json"));
        copyResource("sample-tournament-timed/round-01.json", tempDir.resolve("round-01.json"));

        TapeBuilder.buildTape(tempDir, GAME_INFO);

        var mapper = new ObjectMapper();
        JsonNode tape = mapper.readTree(tempDir.resolve("tape.json").toFile());

        // timingStats section should exist
        assertTrue(tape.has("timingStats"), "tape should contain timingStats");
        JsonNode timingStats = tape.get("timingStats");

        // p3 forfeited in both games
        JsonNode p3Timing = timingStats.get("p3");
        assertNotNull(p3Timing, "p3 should have timing stats");
        assertEquals(2, p3Timing.get("totalForfeits").asInt(), "p3 forfeited in both games");
        assertEquals(8, p3Timing.get("totalTimeouts").asInt(), "p3 had 5+3 timeouts");
        assertEquals(2, p3Timing.get("gamesPlayed").asInt());
        assertEquals(31000, p3Timing.get("maxGameDecisionTimeMs").asLong());

        // p1 had no forfeits or timeouts
        JsonNode p1Timing = timingStats.get("p1");
        assertNotNull(p1Timing);
        assertEquals(0, p1Timing.get("totalForfeits").asInt());
        assertEquals(0, p1Timing.get("totalTimeouts").asInt());
        assertEquals(2, p1Timing.get("gamesPlayed").asInt());

        // p2 had 3 total timeouts, 0 forfeits
        JsonNode p2Timing = timingStats.get("p2");
        assertNotNull(p2Timing);
        assertEquals(0, p2Timing.get("totalForfeits").asInt());
        assertEquals(3, p2Timing.get("totalTimeouts").asInt());

        // Events should have forfeitedPlayers for games where p3 forfeited
        JsonNode events = tape.get("events");
        for (int i = 0; i < events.size(); i++) {
            JsonNode event = events.get(i);
            if (event.has("forfeitedPlayers")) {
                JsonNode forfeited = event.get("forfeitedPlayers");
                boolean containsP3 = false;
                for (JsonNode f : forfeited) {
                    if ("p3".equals(f.asText())) containsP3 = true;
                }
                assertTrue(containsP3, "forfeitedPlayers should include p3");
            }
        }
    }

    private void copyResource(String resourceName, Path target) throws Exception {
        try (var in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IllegalStateException("Resource not found: " + resourceName);
            }
            Files.copy(in, target);
        }
    }
}
