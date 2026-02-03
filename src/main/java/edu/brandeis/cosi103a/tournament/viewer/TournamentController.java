package edu.brandeis.cosi103a.tournament.viewer;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 * REST controller for tournament data endpoints.
 */
@RestController
@RequestMapping("/api/tournaments")
public class TournamentController {

    private final TournamentService tournamentService;

    public TournamentController(TournamentService tournamentService) {
        this.tournamentService = tournamentService;
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

    @ExceptionHandler(TournamentNotFoundException.class)
    public ResponseEntity<String> handleNotFound(TournamentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }
}
