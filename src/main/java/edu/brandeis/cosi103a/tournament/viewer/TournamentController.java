package edu.brandeis.cosi103a.tournament.viewer;

import edu.brandeis.cosi103a.tournament.engine.EngineLoader;
import edu.brandeis.cosi103a.tournament.runner.PlayerConfig;
import edu.brandeis.cosi103a.tournament.runner.TournamentConfig;
import edu.brandeis.cosi103a.tournament.viewer.dto.PlayerConfigRequest;
import edu.brandeis.cosi103a.tournament.viewer.dto.TournamentConfigRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for tournament data endpoints.
 */
@RestController
@RequestMapping("/api/tournaments")
public class TournamentController {

    private final TournamentService tournamentService;
    private final TournamentExecutionService executionService;
    private final String engineJarPath;
    private final String engineClassName;

    public TournamentController(
            TournamentService tournamentService,
            TournamentExecutionService executionService,
            @Value("${tournament.engine-jar:}") String engineJarPath,
            @Value("${tournament.engine-class:}") String engineClassName) {
        this.tournamentService = tournamentService;
        this.executionService = executionService;
        this.engineJarPath = engineJarPath;
        this.engineClassName = engineClassName;
    }

    /**
     * Lists all available tournaments with summary info.
     */
    @GetMapping
    public List<TournamentService.TournamentSummary> listTournaments() throws IOException {
        return tournamentService.listTournaments();
    }

    /**
     * Serves the tape.json file for a given tournament.
     */
    @GetMapping(value = "/{name}/tape.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getTape(@PathVariable String name) throws IOException {
        String json = tournamentService.getTapeJson(name);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json);
    }

    /**
     * Serves the tournament.json file for a given tournament.
     */
    @GetMapping(value = "/{name}/tournament.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getTournamentMeta(@PathVariable String name) throws IOException {
        String json = tournamentService.getTournamentJson(name);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json);
    }

    /**
     * Starts a new tournament execution.
     * Returns 202 Accepted with tournament ID for tracking progress.
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> startTournament(
            @Valid @RequestBody TournamentConfigRequest request) {

        // Validate input
        if (request.players().size() < 4) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "At least 4 players are required"));
        }

        // Check if engine is configured
        if (engineJarPath.isEmpty() || engineClassName.isEmpty()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Tournament engine not configured. Set tournament.engine-jar and tournament.engine-class properties."));
        }

        try {
            // Convert DTOs to domain objects
            List<PlayerConfig> playerConfigs = request.players().stream()
                    .map(p -> new PlayerConfig(
                            generatePlayerId(p.name()),
                            p.name(),
                            p.url()))
                    .toList();

            TournamentConfig config = new TournamentConfig(
                    request.tournamentName(),
                    request.rounds(),
                    request.gamesPerPlayer(),
                    100, // maxTurns default
                    playerConfigs
            );

            // Create engine loader
            EngineLoader engineLoader = new EngineLoader(engineJarPath, engineClassName);

            // Start tournament asynchronously
            String tournamentId = executionService.startTournament(config, engineLoader, null);

            // Return 202 Accepted with tournament ID
            Map<String, String> response = new HashMap<>();
            response.put("tournamentId", tournamentId);
            response.put("status", "ACCEPTED");
            response.put("message", "Tournament queued for execution");

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to start tournament: " + e.getMessage()));
        }
    }

    /**
     * Gets the status of a running tournament.
     */
    @GetMapping("/{tournamentId}/status")
    public ResponseEntity<?> getTournamentStatus(@PathVariable String tournamentId) {
        return executionService.getTournamentStatus(tournamentId)
                .map(status -> ResponseEntity.ok().body(status))
                .orElse(ResponseEntity.notFound().build());
    }

    private String generatePlayerId(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]", "-");
    }

    @ExceptionHandler(TournamentNotFoundException.class)
    public ResponseEntity<String> handleNotFound(TournamentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }
}
