package edu.brandeis.cosi103a.tournament.network.dto;

import edu.brandeis.cosi.atg.event.Event;
import edu.brandeis.cosi.atg.state.GameState;

/**
 * Request object for the /log-event endpoint.
 * Represents a game event that can be logged.
 */
public class LogEventRequest {
    private GameState state;
    private Event event;
    private String playerUuid;

    /**
     * No-args constructor for Jackson deserialization.
     */
    public LogEventRequest() {
    }

    /**
     * Full constructor.
     */
    public LogEventRequest(GameState state, Event event, String playerUuid) {
        this.state = state;
        this.event = event;
        this.playerUuid = playerUuid;
    }

    public GameState getState() {
        return state;
    }

    public void setState(GameState state) {
        this.state = state;
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    public String getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(String playerUuid) {
        this.playerUuid = playerUuid;
    }
}
