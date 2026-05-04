package edu.brandeis.cosi103a.tournament.network.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.brandeis.cosi.atg.event.Event;
import edu.brandeis.cosi.atg.state.GameState;

/**
 * Request object for the /log-event endpoint.
 * Represents a game event that can be logged.
 */
public class LogEventRequest {
    private GameState state;
    private Event event;
    // The assignment spec uses snake_case "player_uuid"; keep the Java
    // identifier camelCase but emit/read the spec-compliant JSON key.
    @JsonProperty("player_uuid")
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
