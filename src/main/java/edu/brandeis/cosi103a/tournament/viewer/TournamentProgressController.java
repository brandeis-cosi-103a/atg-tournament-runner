package edu.brandeis.cosi103a.tournament.viewer;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

/**
 * WebSocket controller for tournament progress updates.
 * Clients can subscribe to /topic/tournaments/{tournamentId} to receive real-time status updates.
 */
@Controller
public class TournamentProgressController {

    private final TournamentExecutionService executionService;

    public TournamentProgressController(TournamentExecutionService executionService) {
        this.executionService = executionService;
    }

    /**
     * Handles subscription requests for tournament status.
     * When a client subscribes, returns the current status immediately.
     * Further updates are pushed automatically by TournamentExecutionService.
     *
     * @param tournamentId the tournament ID to subscribe to
     * @return the current tournament status, or null if not found
     */
    @MessageMapping("/tournaments/{tournamentId}/subscribe")
    @SendTo("/topic/tournaments/{tournamentId}")
    public TournamentStatus subscribeTournament(@DestinationVariable String tournamentId) {
        return executionService.getTournamentStatus(tournamentId).orElse(null);
    }
}
