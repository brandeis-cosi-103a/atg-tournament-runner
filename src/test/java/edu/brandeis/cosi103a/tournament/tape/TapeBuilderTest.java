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

    private void copyResource(String resourceName, Path target) throws Exception {
        try (var in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IllegalStateException("Resource not found: " + resourceName);
            }
            Files.copy(in, target);
        }
    }
}
