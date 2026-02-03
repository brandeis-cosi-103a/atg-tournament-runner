package edu.brandeis.cosi103a.tournament.viewer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TournamentController and TournamentService.
 */
class TournamentControllerTest {

    @TempDir
    Path tempDir;

    private TournamentController controller;
    private TournamentService service;

    @BeforeEach
    void setUp() {
        service = new TournamentService(tempDir.toString(), new ObjectMapper());
        controller = new TournamentController(service);
    }

    @Test
    void listTournaments_emptyDir() throws Exception {
        List<TournamentService.TournamentSummary> result = controller.listTournaments();
        assertTrue(result.isEmpty());
    }

    @Test
    void listTournaments_withTournamentData() throws Exception {
        Path t = Files.createDirectory(tempDir.resolve("spring-2026"));
        Files.writeString(t.resolve("tape.json"),
                "{\"players\":[{\"id\":\"p1\",\"name\":\"Alice\"},{\"id\":\"p2\",\"name\":\"Bob\"}],\"events\":[{\"seq\":0}]}");

        List<TournamentService.TournamentSummary> result = controller.listTournaments();
        assertEquals(1, result.size());
        assertEquals("spring-2026", result.get(0).name());
        assertEquals(2, result.get(0).playerCount());
        assertEquals(1, result.get(0).eventCount());
    }

    @Test
    void listTournaments_dirWithoutTape() throws Exception {
        Files.createDirectory(tempDir.resolve("no-tape"));

        List<TournamentService.TournamentSummary> result = controller.listTournaments();
        assertEquals(1, result.size());
        assertEquals("no-tape", result.get(0).name());
        assertEquals(0, result.get(0).playerCount());
        assertEquals(0, result.get(0).eventCount());
    }

    @Test
    void getTape_returnsContent() throws Exception {
        Path t = Files.createDirectory(tempDir.resolve("test"));
        String content = "{\"players\":[],\"events\":[]}";
        Files.writeString(t.resolve("tape.json"), content);

        ResponseEntity<String> response = controller.getTape("test");
        assertEquals(200, response.getStatusCode().value());
        assertEquals(content, response.getBody());
    }

    @Test
    void getTape_notFound() {
        assertThrows(TournamentNotFoundException.class, () -> controller.getTape("nonexistent"));
    }

    @Test
    void getTournamentJson_returnsContent() throws Exception {
        Path t = Files.createDirectory(tempDir.resolve("meta"));
        String content = "{\"name\":\"meta\",\"config\":{}}";
        Files.writeString(t.resolve("tournament.json"), content);

        ResponseEntity<String> response = controller.getTournamentMeta("meta");
        assertEquals(200, response.getStatusCode().value());
        assertEquals(content, response.getBody());
    }

    @Test
    void getTournamentJson_notFound() {
        assertThrows(TournamentNotFoundException.class, () -> controller.getTournamentMeta("nonexistent"));
    }

    @Test
    void getTape_rejectsPathTraversal() {
        assertThrows(TournamentNotFoundException.class, () -> controller.getTape("../../etc"));
        assertThrows(TournamentNotFoundException.class, () -> controller.getTape("foo/bar"));
        assertThrows(TournamentNotFoundException.class, () -> controller.getTape("foo\\bar"));
    }

    @Test
    void getTournamentMeta_rejectsPathTraversal() {
        assertThrows(TournamentNotFoundException.class, () -> controller.getTournamentMeta("../secret"));
    }

    @Test
    void listTournaments_ignoresFiles() throws Exception {
        // Files in data dir (not directories) should be ignored
        Files.writeString(tempDir.resolve("readme.txt"), "hello");
        List<TournamentService.TournamentSummary> result = controller.listTournaments();
        assertTrue(result.isEmpty());
    }
}
